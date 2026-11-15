package com.oersted.trainer.sim

/**
 * Port of pedestal.ts: state for the centre-pedestal controls that have no effect on the aircraft systems model
 * (radios, transponder, CDU, trim indications, test switches). Everything that DOES affect the systems (thrust levers,
 * fuel levers, flaps, speed brake, fire handles, bottles, cargo fire arm / discharge, parking brake) lives in SimStore.
 *
 * The zustand store becomes [PedestalState] (a data class with the TS defaults) plus [Holder], which mirrors `set`
 * (merge via copy) and `reset`. Frequencies are kHz as in the web code.
 */
object Pedestal {
    enum class FireTest { OFF, FAULT, OVHT_FIRE }
    enum class OvhtDet { NORMAL, A, B }
    enum class CargoDet { NORM, A, B }
    enum class XpdrMode { SBY, AUTO, ON, TEST }
    enum class TcasMode { TA_RA, TA, ABV, OFF, BLW, TRAFFIC }
    enum class WxrMode { OFF, STBY, TEST, WX, WX_TURB, MAP }
    enum class MicMask { MASK, BOOM }
    enum class ExtTest { ONE, OFF, TWO }

    data class Freq(val act: Int, val stby: Int)
    data class Hf(val act: Int, val stby: Int, val mode: Int)
    data class Pair2<T>(val l: T, val r: T)

    data class CduSide(
        val pad: String = "",
        val page: String,
        val exec: Boolean = false,
        val msg: String = "",
        val fields: Map<String, String> = emptyMap(),
    )

    data class PedestalState(
        // communications
        val commL: Freq = Freq(121950, 121800),
        val commR: Freq = Freq(131550, 121500),
        val navL: Freq = Freq(109950, 112500),
        val navR: Freq = Freq(109950, 112500),
        val navAutoL: Boolean = false,
        val navAutoR: Boolean = false,
        val adf: Pair2<Int> = Pair2(353, 316),
        val mic: Map<String, Boolean> = MIC_DEFAULT,
        val micMask: Pair2<MicMask> = Pair2(MicMask.MASK, MicMask.MASK),
        /** false = R/T, true = I/C */
        val micRtIc: Pair2<Boolean> = Pair2(false, false),
        val micVbr: Pair2<String> = Pair2("B", "B"),
        /** false = NORM */
        val micAlt: Pair2<Boolean> = Pair2(false, false),
        val adiBrt: Pair2<Double> = Pair2(0.7, 0.7),
        val hsiBrt: Pair2<Double> = Pair2(0.7, 0.7),
        // transponder / TCAS
        val xpdrCode: String = "0000",
        /** digits being typed */
        val xpdrEntry: String = "",
        val xpdrMode: XpdrMode = XpdrMode.SBY,
        val xpdrSel: Int = 1,
        val identUntil: Double = 0.0,
        val tcasMode: TcasMode = TcasMode.TA,
        val xpdrFault: Boolean = true,
        val adfAnt: Pair<Boolean, Boolean> = Pair(false, false),
        /** ADF control-head mode per side: 0 OFF, 1 ANT, 2 ADF, 3 TEST, 4 LOOP */
        val adfMode: Pair<Int, Int> = Pair(2, 2),
        /** DME mode per side: 0 STBY, 1 NORM, 2 ORIDE (AMM 34-55) */
        val dmeMode: Pair<Int, Int> = Pair(1, 1),
        /** DME self-test held */
        val dmeTest: Pair<Boolean, Boolean> = Pair(false, false),
        /** VHF NAV control-head self-test held (AMM 34-31) */
        val navTest: Pair<Boolean, Boolean> = Pair(false, false),
        /** HF COMM (kHz) active / standby + mode 0 OFF 1 USB 2 AM */
        val hfL: Hf = Hf(5628, 8942, 0),
        val hfR: Hf = Hf(5628, 8942, 0),
        val adfTone: Boolean = false,
        /** RMI needle source selectors per side: 0 VOR, 1 ADF */
        val rmiNeedle1: Pair<Int, Int> = Pair(0, 0),
        val rmiNeedle2: Pair<Int, Int> = Pair(1, 1),
        /** Compass mode: 0 SLAVED, 1 DG */
        val compMode: Pair<Int, Int> = Pair(0, 0),
        /** LRRA decision height ft */
        val lrraDh: Pair<Int, Int> = Pair(200, 200),
        /** LRRA self-test held */
        val lrraTest: Pair<Boolean, Boolean> = Pair(false, false),
        /** HF coupler retune request (momentary) */
        val hfTuneReq: Pair<Boolean, Boolean> = Pair(false, false),
        /** Push-to-talk held (either side). Transmit only while true and a TX radio is selected. */
        val ptt: Boolean = false,
        /** COMM TEST: opens squelch (receiver hiss) when powered. */
        val commTest: Boolean = false,
        // flight-deck lighting knobs not modelled in the lights system
        val flood: Double = 0.0,
        // trim
        /** units, 0..17 (green band about 3.5..7.5 for T/O) */
        val stabTrim: Double = 5.0,
        val stabCutoutA: Boolean = false,
        val stabCutoutB: Boolean = false,
        val stabOverride: Boolean = false,
        /** -1..1 (L..R) */
        val rudderTrim: Double = 0.0,
        /** -1..1 */
        val aileronTrim: Double = 0.0,
        // thrust-lever extras
        val atDisc: Pair<Boolean, Boolean> = Pair(false, false),
        // ADI / HSI / radar
        val dhRefL: Double = 0.0,
        val dhRefR: Double = 0.0,
        /** 0 APP, 1 VOR, 2 MAP, 3 PLN */
        val hsiModeL: Int = 2,
        val hsiModeR: Int = 2,
        /** 0..5 -> 10 20 40 80 160 320 */
        val hsiRangeL: Int = 2,
        val hsiRangeR: Int = 2,
        val wxrMode: WxrMode = WxrMode.STBY,
        val wxrGain: Double = 0.6,
        val wxrTilt: Double = 0.5,
        val wxrTest: Boolean = false,
        val wxrRange: Int = 3,
        val wxrStab: Boolean = true,
        val gpsPage: String = "NAV",
        // fire / overheat panel (P8)
        val ovhtDet: Pair<OvhtDet, OvhtDet> = Pair(OvhtDet.NORMAL, OvhtDet.NORMAL),
        val fireTest: FireTest = FireTest.OFF,
        val fireOverrideUntil: Double = 0.0,
        val bellSilenced: Boolean = false,
        val cargoDetFwd: CargoDet = CargoDet.NORM,
        val cargoDetAft: CargoDet = CargoDet.NORM,
        val cargoExtTest: Boolean = false,
        /** ENGINES EXT TEST switch (spring-loaded) */
        val extTest: ExtTest = ExtTest.OFF,
        // cab door annunciator
        val cabDoorUnlocked: Boolean = false,
        /** generic persisted knob / switch state used by the photo-accurate pedestal */
        val knobs: Map<String, Double> = emptyMap(),
        val sw: Map<String, Boolean> = emptyMap(),
        // CDUs
        val cduL: CduSide = CduSide(page = "CRZ"),
        val cduR: CduSide = CduSide(page = "LEGS"),
    )

    /** web `fresh()` audio-selector toggles: all ON (green lamps lit) in the reference photo. */
    val MIC_DEFAULT: Map<String, Boolean> = buildMap {
        for (side in listOf("L", "R")) {
            for (k in listOf("rx:1", "rx:NAV", "rx:2", "rx:ADF-1", "rx:ADF-2", "rx:MKR", "rx:SPKR")) put("$side:$k", true)
        }
        for (side in listOf("L", "R")) {
            for (k in listOf("vol:VHF-1", "vol:VHF-2", "vol:HF-1", "vol:HF-2", "vol:CABIN", "vol:INT", "vol:PA")) put("$side:$k", true)
        }
    }

    /** Store replacement: `set` merges a transform, `reset` restores the TS defaults. */
    class Holder(
        initial: PedestalState = PedestalState(),
        private val onChange: ((PedestalState) -> Unit)? = null,
    ) {
        var state: PedestalState = initial
            private set

        fun set(transform: (PedestalState) -> PedestalState) {
            state = transform(state)
            onChange?.invoke(state)
        }

        /** web setCdu(side, p) */
        fun setCdu(left: Boolean, transform: (CduSide) -> CduSide) = set { s ->
            if (left) s.copy(cduL = transform(s.cduL)) else s.copy(cduR = transform(s.cduR))
        }

        /** web reset(): fresh() plus the CDU initial pages */
        fun reset() = set { PedestalState() }
    }
}
