package com.oersted.trainer.sim

import kotlin.math.floor
import kotlin.math.max

/**
 * First stage of the web useAttitude tick (MainInstrumentPanel.tsx ~690–850): position advance, the pedestal-driven
 * radio receivers, instrument sources and the IRS / EFI transfer overrides, the NAV / ILS solutions with source and
 * self-test blanking, ADF, DME, dual LRRA and HF couplers.
 *
 * Pure: the caller passes the sim, derived, MIP and pedestal state and the live navigation values, and applies the
 * returned values. The web side effects (MIP writes, the HF tune request clear, the situation publish, the gyro memory
 * write) are left to the caller. The attitude targets, integration and flight director steps follow in AttitudeHook.tick.
 * Not verified against the web yet; the ADF frequency units in particular need a parity check.
 */
object RadioStage {
    data class SideAtt(val pitch: Double, val roll: Double, val hdg: Double, val attValid: Boolean, val hdgValid: Boolean)

    data class Inputs(
        val dt: Double,
        val sim: SimState,
        val derived: Derived,
        val m: MipLocal.MipState,
        val ped: Pedestal.PedestalState,
        /** true heading position and attitude from the previous tick (the web's a / r.pos) */
        val pos: RadioNav.Point,
        val pitch: Double,
        val roll: Double,
        val hdg: Double,
        val tas: Double,
        val simAltFt: Double,
        val flying: Boolean,
        /** DC power available (the web's `power`) */
        val power: Boolean,
        val erectTimers: InstrumentSources.ErectTimers,
        val pitchRate: Double = 0.0,
        val rollRate: Double = 0.0,
        /** web r.vsFpm, which the web never writes; kept as an input so the trainer value can be passed */
        val vsFpm: Double = 0.0,
        /** live flight navigation state (getLiveFlightNavigation) and live arrival (getLiveArrival) */
        val liveNav: FlightNavigationState?,
        val liveArr: ActiveArrival?,
        /** the restored or live navigation state (readFlightNavigationState), used for the DME altitude */
        val navState: FlightNavigationState?,
    )

    data class Output(
        val pos: RadioNav.Point,
        val sceneId: RadioNav.NavSceneId,
        val captKind: String,
        val foKind: String,
        val synced: NavReceiver.MipNavSync,
        val inst: InstrumentSources.ResolvedInstruments,
        /** capt and fo instrument sides after the IRS / EFI overrides */
        val capt: InstrumentSources.SideInstruments,
        val fo: InstrumentSources.SideInstruments,
        val captRx: NavReceiver.ResolvedNavReceiver,
        val foRx: NavReceiver.ResolvedNavReceiver,
        val nv: RadioNav.RadioNavData,
        val nvFo: RadioNav.RadioNavData,
        val sideAtt: Pair<SideAtt, SideAtt>,
        val attBad: Boolean,
        val hdgBad: Boolean,
        val effectiveNavSel: Int,
        val effectiveCompSel: Int,
        val effectiveGyroSel: Int,
        val adf: AdfBearing.AdfResult,
        val dme1: DmeModel.DmeChannelState,
        val dme2: DmeModel.DmeChannelState,
        val lrra: Lrra.LrraResult,
        val hf1: HfModel.HfChannelState,
        val hf2: HfModel.HfChannelState,
        /** true when the pedestal's HF tune request was consumed this tick (the caller clears it) */
        val clearHfTuneReq: Boolean,
    )

    /** web navXfr: BOTH ON 1 forces NAV 1, BOTH ON 2 forces NAV 2; NORMAL keeps the MIP selector. */
    fun effectiveNavSel(navXfr: String, navSel: Int): Int = when (navXfr) {
        "BOTH ON 1" -> 0
        "BOTH ON 2" -> 2
        else -> navSel
    }

    /** web efiXfr: BOTH ON L / BOTH ON R select one side of the EFIS sources, null when NORMAL. */
    fun efiSide(efiXfr: String): Int? = when (efiXfr) {
        "BOTH ON L" -> 0
        "BOTH ON R" -> 2
        else -> null
    }

    /** web ILS test heuristic (AMM 34-31): an odd tenth of a MHz in the ILS band is treated as an ILS. */
    fun isIlsTestFrequency(mhz: Double): Boolean =
        mhz >= 108.1 && (floor(mhz * 100 + 0.5).toLong() % 10) % 2 == 1L

    private fun toKind(kind: String): RadioNav.NavStationKind =
        if (kind == "VOR") RadioNav.NavStationKind.VOR else RadioNav.NavStationKind.ILS

    private fun fromSolution(s: RadioNavChangi.NavSolution): RadioNav.RadioNavData = RadioNav.RadioNavData(
        vorValid = s.vorValid,
        locValid = s.locValid,
        gsValid = s.gsValid,
        radial = s.radialFrom,
        locDev = s.locDevDeg,
        gsErr = s.gsDevDeg,
        vorDist = if (s.vorValid) s.distanceNm else 0.0,
        thrDist = if (s.locValid || s.gsValid) s.distanceNm else 0.0,
    )

    fun tick(p: Inputs): Output {
        val sim = p.sim
        val m = p.m
        val ped = p.ped
        val dt = p.dt
        val effNav = effectiveNavSel(sim.navXfr, m.navSel)
        val efi = efiSide(sim.efiXfr)
        val effComp = efi ?: m.compSel
        val effGyro = efi ?: m.gyroSel

        // Scene: inside 40 nm of a PLAN NAV vectored-ILS destination the receiver works in that runway's frame.
        val liveNav = p.liveNav
        val sceneId = if (
            NavData.isWsssArrivalSceneId(p.liveArr?.sceneId) && liveNav != null &&
            (liveNav.distanceToDestinationNm ?: 999.0) < 40.0
        ) RadioNav.NavSceneId.WSSS02L else RadioNav.NavSceneId.RPLL06

        var pos = p.pos
        val tas = p.tas
        if (sceneId == RadioNav.NavSceneId.WSSS02L && liveNav != null) {
            pos = RadioNav.wsssLocalPos(liveNav.latitude, liveNav.longitude)
        } else if (m.navOn && p.flying) {
            pos = RadioNav.stepPos(pos, p.hdg, tas, dt)
        }

        // Pedestal NAV 1 / 2 is the sole frequency authority; the MIP mirror follows it.
        val captRx = NavReceiver.resolveNavReceiver(true, effNav, ped.navL.act, ped.navR.act)
        val foRx = NavReceiver.resolveNavReceiver(false, effNav, ped.navL.act, ped.navR.act)
        val synced = NavReceiver.syncMipNavFromPedestal(ped.navL.act, m.navFrequencyMHz, m.navTuned)

        val sourceSelection = Sources.SourceSelect(navSel = effNav, compSel = effComp, gyroSel = effGyro)
        val inst = InstrumentSources.resolveInstrumentSources(
            truePitch = p.pitch,
            trueRoll = p.roll,
            trueHdg = p.hdg,
            acPower = p.derived.acPower,
            dcPower = p.power,
            ac115 = p.derived.acPower,
            ac26 = p.derived.acPower,
            srcFail = m.srcFail,
            srcPower = m.srcPower,
            sel = sourceSelection,
            erectTimers = p.erectTimers,
            dtSec = dt,
            compSyncFlag = InstrumentSources.CompassSync(c1 = m.compSyncFlag.c1, c2 = m.compSyncFlag.c2),
            compMode = InstrumentSources.CompassMode(c1 = ped.compMode.first, c2 = ped.compMode.second),
            gyroMem = m.gyroMem,
            pitchRate = p.pitchRate,
            rollRate = p.rollRate,
        )

        // IRS transfer selects one aligned IRS attitude / heading source for both sides; EFI selects one side's sensors.
        var capt = inst.capt
        var fo = inst.fo
        if (sim.irsXfr != "NORMAL") {
            val useLeft = sim.irsXfr == "BOTH ON L"
            val mode = if (useLeft) sim.irsLMode else sim.irsRMode
            val align = if (useLeft) sim.irsAlignL else sim.irsAlignR
            val hdgIrs = if (useLeft) sim.irsHeadingL else sim.irsHeadingR
            val validAtt = p.power && align >= 1.0 && (mode == "NAV" || mode == "ATT")
            val validHdg = validAtt && mode == "NAV"
            val irsSource = capt.copy(
                attValid = validAtt,
                hdgValid = validHdg,
                pitch = sim.attitudePitchDeg,
                roll = sim.attitudeRollDeg,
                hdg = if (validHdg) hdgIrs else 0.0,
            )
            capt = irsSource
            fo = irsSource
        } else if (efi != null) {
            val left = efi == 0
            val gyroPowered = if (left) inst.gyros.gyro1 else inst.gyros.gyro2
            val compass = if (left) inst.compass.comp1 else inst.compass.comp2
            val efiSource = capt.copy(
                attValid = gyroPowered.valid,
                hdgValid = compass.valid,
                pitch = gyroPowered.pitch,
                roll = gyroPowered.roll,
                hdg = if (compass.powered && !compass.failed) compass.hdg else 0.0,
            )
            capt = efiSource
            fo = efiSource
        }

        val captKind = captRx.kind ?: m.navTuned
        val foKind = foRx.kind ?: m.navTuned

        // Radio solutions: the tuned receiver (pedestal authority), overridden by the geographic station when a plan is live.
        var nv: RadioNav.RadioNavData = if (
            (if (effNav == 2) inst.radioPower.nav2 else inst.radioPower.nav1) && m.navOn && captRx.kind != null
        ) RadioNav.radioNav(pos, p.simAltFt, toKind(captKind), captRx.frequencyMHz, sceneId) else RadioNav.NO_NAV
        var nvFo: RadioNav.RadioNavData = if (inst.fo.navPowered && m.navOn && foRx.kind != null) {
            RadioNav.radioNav(pos, p.simAltFt, toKind(foKind), foRx.frequencyMHz, sceneId)
        } else RadioNav.NO_NAV

        if (liveNav != null && captRx.kind != null) {
            val station = findChangiNavaid(captRx.frequencyMHz, if (captRx.kind == "ILS") "ILS" else "VOR")
            if (station != null) {
                nv = fromSolution(
                    RadioNavChangi.computeChangiRadioNav(
                        liveNav.latitude, liveNav.longitude, p.simAltFt, captRx.frequencyMHz, captRx.kind, m.selCrs,
                    ),
                )
            }
        }
        if (liveNav != null && foRx.kind != null) {
            val station = findChangiNavaid(foRx.frequencyMHz, if (foRx.kind == "ILS") "ILS" else "VOR")
            if (station != null) {
                nvFo = fromSolution(
                    RadioNavChangi.computeChangiRadioNav(
                        liveNav.latitude, liveNav.longitude, p.simAltFt, foRx.frequencyMHz, foRx.kind, m.selCrs2,
                    ),
                )
            }
        }

        // Source failures and the receivers' own power blank the solutions.
        val sources = Sources.sourcesOf(sourceSelection, m.srcFail)
        val sf = sources.capt
        val sfFo = sources.fo
        val attBad = m.fail.att || !capt.attValid
        val hdgBad = m.fail.hdg || !capt.hdgValid
        if (m.fail.loc || sf.nav || !capt.navPowered) nv = nv.copy(vorValid = false, locValid = false, gsValid = false)
        if (m.fail.gs || !capt.navGsPowered) nv = nv.copy(gsValid = false)
        if (m.fail.loc || sfFo.nav || !fo.navPowered) nvFo = nvFo.copy(vorValid = false, locValid = false, gsValid = false)
        if (m.fail.gs || !fo.navGsPowered) nvFo = nvFo.copy(gsValid = false)

        // DME STBY / CB open blanks the distance.
        nv = blankDme(nv, inst.radioPower.dme1, ped.dmeMode.first)
        nvFo = blankDme(nvFo, inst.radioPower.dme2, ped.dmeMode.second)

        // AMM 34-31 VHF NAV self-test: VOR centres; ILS drives the LOC left and the GS up.
        nv = applyNavTest(nv, ped.navTest.first, ped.navL.act, p.hdg)
        nvFo = applyNavTest(nvFo, ped.navTest.second, ped.navR.act, p.hdg)

        val adf = AdfBearing.computeAdfBearings(
            hdgMag = if (capt.hdgValid) capt.hdg else p.hdg,
            powered = p.power,
            failAdf = m.fail.adf,
            nav = liveNav?.let { AdfBearing.NavPos(it.latitude, it.longitude) },
            freqL = ped.adf.l.toDouble(),
            freqR = ped.adf.r.toDouble(),
            mode1 = ped.adfMode.first,
            mode2 = ped.adfMode.second,
            adf1Powered = inst.radioPower.adf1,
            adf2Powered = inst.radioPower.adf2,
        )

        // DME search / track on both channels.
        val dAlt = p.navState?.altitudeFt?.takeIf { it.isFinite() } ?: 0.0
        val geom1 = if (m.navOn && (nv.vorValid || nv.locValid)) (if (m.navTuned == "VOR") nv.vorDist else nv.thrDist) else null
        val geom2 = if (m.navOn && (nvFo.vorValid || nvFo.locValid)) {
            if (nvFo.vorValid && !nvFo.locValid) nvFo.vorDist else nvFo.thrDist
        } else null
        val dme1 = DmeModel.stepDmeChannel(
            prev = m.dmeCh.ch1,
            mode = ped.dmeMode.first,
            test = ped.dmeTest.first,
            powered = inst.radioPower.dme1,
            geomNm = geom1,
            deltaAltFt = dAlt,
            dtSec = dt,
        )
        val dme2 = DmeModel.stepDmeChannel(
            prev = m.dmeCh.ch2,
            mode = ped.dmeMode.second,
            test = ped.dmeTest.second,
            powered = inst.radioPower.dme2,
            geomNm = geom2,
            deltaAltFt = dAlt,
            dtSec = dt,
        )

        // Dual LRRA: AGL is known on the ground and in the modelled approach; elsewhere both channels blank.
        val agl: Double? = when {
            p.sim.wow -> 0.0
            p.sim.rwyPhase == "APPROACH" && p.sim.rwyAltFt.isFinite() -> max(0.0, p.sim.rwyAltFt)
            else -> null
        }
        val lrra = Lrra.resolveLrra(
            aglFt = agl,
            vsFpm = p.vsFpm,
            dcPower = p.power,
            cb1 = m.srcPower.lrra1Cb,
            cb2 = m.srcPower.lrra2Cb,
            fail1 = false,
            fail2 = false,
            dh1 = ped.lrraDh.first.toDouble(),
            dh2 = ped.lrraDh.second.toDouble(),
            test1 = ped.lrraTest.first,
            test2 = ped.lrraTest.second,
        )

        // HF couplers: the coupler state carries over from the MIP mirror; the pedestal supplies frequency and mode.
        val hfPrev1 = HfModel.IDLE_HF.copy(
            mode = ped.hfL.mode,
            actKhz = ped.hfL.act,
            stbyKhz = ped.hfL.stby,
            tuning = m.hfCh.ch1.tuning,
            usability = m.hfCh.ch1.usability,
        )
        val hfPrev2 = HfModel.IDLE_HF.copy(
            mode = ped.hfR.mode,
            actKhz = ped.hfR.act,
            stbyKhz = ped.hfR.stby,
            tuning = m.hfCh.ch2.tuning,
            usability = m.hfCh.ch2.usability,
        )
        val hf1 = HfModel.stepHfChannel(
            prev = hfPrev1,
            mode = ped.hfL.mode,
            actKhz = ped.hfL.act,
            stbyKhz = ped.hfL.stby,
            powered = inst.radioPower.hf1,
            retune = ped.hfTuneReq.first,
            isDay = true,
            dtSec = dt,
        )
        val hf2 = HfModel.stepHfChannel(
            prev = hfPrev2,
            mode = ped.hfR.mode,
            actKhz = ped.hfR.act,
            stbyKhz = ped.hfR.stby,
            powered = inst.radioPower.hf2,
            retune = ped.hfTuneReq.second,
            isDay = true,
            dtSec = dt,
        )

        return Output(
            pos = pos,
            sceneId = sceneId,
            captKind = captKind,
            foKind = foKind,
            synced = synced,
            inst = inst,
            capt = capt,
            fo = fo,
            captRx = captRx,
            foRx = foRx,
            nv = nv,
            nvFo = nvFo,
            sideAtt = Pair(
                SideAtt(capt.pitch, capt.roll, capt.hdg, capt.attValid && !m.fail.att, capt.hdgValid && !m.fail.hdg),
                SideAtt(fo.pitch, fo.roll, fo.hdg, fo.attValid && !m.fail.att, fo.hdgValid && !m.fail.hdg),
            ),
            attBad = attBad,
            hdgBad = hdgBad,
            effectiveNavSel = effNav,
            effectiveCompSel = effComp,
            effectiveGyroSel = effGyro,
            adf = adf,
            dme1 = dme1,
            dme2 = dme2,
            lrra = lrra,
            hf1 = hf1,
            hf2 = hf2,
            clearHfTuneReq = ped.hfTuneReq.first || ped.hfTuneReq.second,
        )
    }

    private fun blankDme(data: RadioNav.RadioNavData, powered: Boolean, dmeMode: Int): RadioNav.RadioNavData {
        if (!powered || dmeMode == 0) return data.copy(vorDist = 0.0, thrDist = 0.0)
        return data
    }

    private fun applyNavTest(data: RadioNav.RadioNavData, testOn: Boolean, navActRaw: Int, hdg: Double): RadioNav.RadioNavData {
        if (!testOn) return data
        val mhz = navActRaw / 1000.0
        if (!isIlsTestFrequency(mhz)) {
            return data.copy(vorValid = true, locValid = false, gsValid = false, locDev = 0.0, gsErr = 0.0, radial = hdg)
        }
        return data.copy(vorValid = false, locValid = true, gsValid = true, locDev = -1.0, gsErr = -1.0)
    }
}
