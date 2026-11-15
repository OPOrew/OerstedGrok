package com.oersted.trainer.sim

/**
 * Port of navReceiver.ts (ATA 34 NAV receiver authority) and the training-station table from radionav.ts.
 *
 * The centre pedestal NAV 1 / NAV 2 active frequencies are the only authority for the VHF NAV receivers.
 * Map auto-tune, Flight Plan Load and the Navigation Trainer may write into the pedestal; the pilot's knobs always win.
 * The web functions write to two zustand stores. Here each function returns the patch it would have written,
 * so the stateful layer can apply it to the pedestal and MIP holders. Frequencies: MHz as Double, pedestal raw as Int (kHz x 1000).
 */
object NavReceiver {

    /** web TRAINING_NAV_STATIONS (radionav.ts): synthetic, local-only training aids. */
    data class TrainingStation(val id: String, val kind: String, val frequencyMHz: Double, val label: String, val scene: String?)

    val TRAINING_NAV_STATIONS: List<TrainingStation> = listOf(
        TrainingStation("SIM-VOR-1", "VOR", 112.0, "Training VOR 1", null),
        TrainingStation("SIM-RPLL-ILS-06", "ILS", 109.1, "Simulated RPLL ILS RWY 06", "RPLL06"),
        TrainingStation(NavData.WSSS_ILS_02L.stationId, "ILS", NavData.WSSS_ILS_02L.frequencyMHz,
            "Simulated WSSS ILS RWY 02L (ICW)", "WSSS02L"),
    )

    fun findTrainingNavStation(frequencyMHz: Double): TrainingStation? =
        TRAINING_NAV_STATIONS.firstOrNull { Math.abs(it.frequencyMHz - frequencyMHz) < 0.0001 }

    /** Pedestal stores kHz x 1000 ints (e.g. 110900 = 110.90 MHz). */
    fun mhzFromPedestalRaw(raw: Int): Double = raw / 1000.0

    fun pedestalRawFromMhz(mhz: Double): Int = FlightPhaseControl.jsRound(mhz * 1000.0).toInt()

    /** Which physical receiver feeds a pilot side. navSel: 0 = BOTH ON 1, 1 = NORMAL (CAPT->1, F/O->2), 2 = BOTH ON 2. */
    fun navSideForPilot(pilotCapt: Boolean, navSel: Int): Char = when (navSel) {
        0 -> 'L'
        2 -> 'R'
        else -> if (pilotCapt) 'L' else 'R'
    }

    data class ResolvedNavReceiver(
        val side: Char,
        val frequencyMHz: Double,
        /** "VOR", "ILS" or null when off-frequency / unknown */
        val kind: String?,
        val stationId: String?,
    )

    /** web resolveNavReceiver: the receiver that should drive one pilot's instruments. */
    fun resolveNavReceiver(pilotCapt: Boolean, navSel: Int, navLActRaw: Int, navRActRaw: Int): ResolvedNavReceiver {
        val side = navSideForPilot(pilotCapt, navSel)
        val mhz = mhzFromPedestalRaw(if (side == 'L') navLActRaw else navRActRaw)
        val training = findTrainingNavStation(mhz)
        val catalog = findChangiNavaid(mhz, "VOR") ?: findChangiNavaid(mhz, "ILS")
        val station = training?.let { ResolvedStation(it.id, it.kind) }
            ?: catalog?.let { ResolvedStation(it.id, if (it.kind == NavaidsChangi.NavaidKind.ILS || it.kind == NavaidsChangi.NavaidKind.LOC) "ILS" else "VOR") }
        return ResolvedNavReceiver(side, mhz, station?.kind, station?.id)
    }

    private data class ResolvedStation(val id: String, val kind: String)

    /** Result of [tuneNav1]: the pedestal NAV 1 raw values to store, and the MIP fields to merge. */
    data class Nav1Tune(
        val navLAct: Int,
        val navLStby: Int,
        val mipFrequencyMHz: Double,
        /** new MIP navTuned, null when the frequency is not a known station */
        val mipNavTuned: String?,
        /** true when the pilot's NAV power should be switched on */
        val mipNavOn: Boolean,
    )

    /**
     * web tuneNav1(mhz, opts). [stbyMhz] is opts.stbyMhz (null keeps the current standby); [powerOn] is opts.powerOn.
     * Does not engage the autopilot. [automatic] mirrors automaticTuneWrite for the auto-tune source.
     */
    fun tuneNav1(mhz: Double, currentNavLStby: Int, powerOn: Boolean = false, stbyMhz: Double? = null): Nav1Tune {
        val raw = pedestalRawFromMhz(mhz)
        val stby = if (stbyMhz != null) pedestalRawFromMhz(stbyMhz) else currentNavLStby
        val training = findTrainingNavStation(mhz)
        val catalog = findChangiNavaid(mhz, "VOR") ?: findChangiNavaid(mhz, "ILS")
        val kind: String? = training?.kind
            ?: catalog?.let { if (it.kind == NavaidsChangi.NavaidKind.ILS || it.kind == NavaidsChangi.NavaidKind.LOC) "ILS" else "VOR" }
        return Nav1Tune(raw, stby, mhz, kind, powerOn)
    }

    /** web tuneNav2(mhz): pedestal authority only. Returns the new NAV 2 active raw value and keeps standby. */
    fun tuneNav2(mhz: Double, currentNavRStby: Int): Pair<Int, Int> = pedestalRawFromMhz(mhz) to currentNavRStby

    /** Result of [syncMipNavFromPedestal]. */
    data class MipNavSync(val frequencyMHz: Double, val kind: String?, val changed: Boolean)

    /**
     * web syncMipNavFromPedestal: copy the pedestal NAV 1 frequency and kind into the MIP mirror when they differ.
     * The MIP fields are only written when [changed] is true (the caller applies the patch).
     */
    fun syncMipNavFromPedestal(navLActRaw: Int, mipNavFrequencyMHz: Double, mipNavTuned: String?): MipNavSync {
        val mhz = mhzFromPedestalRaw(navLActRaw)
        val training = findTrainingNavStation(mhz)
        val catalog = findChangiNavaid(mhz, "VOR") ?: findChangiNavaid(mhz, "ILS")
        val kind: String? = training?.kind
            ?: catalog?.let { if (it.kind == NavaidsChangi.NavaidKind.ILS || it.kind == NavaidsChangi.NavaidKind.LOC) "ILS" else "VOR" }
        val freqChanged = Math.abs(mipNavFrequencyMHz - mhz) > 0.0005
        val kindChanged = kind != null && mipNavTuned != kind
        return MipNavSync(mhz, kind, freqChanged || kindChanged)
    }
}
