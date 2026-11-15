package com.oersted.trainer.sim

/**
 * Port of overheadLocal.ts: P5 forward-overhead controls with no effect on the systems model
 * (IRS, ELT, audio selectors, flight recorder, oxygen, warning tests, yaw damper, and so on).
 * The TS file is a Zustand store with no logic beyond its default values and `set`; the Kotlin
 * version keeps the state shape, the defaults, and a holder whose `set` merges a partial update.
 */
object OverheadLocal {
    enum class IrsMode(val label: String) { OFF("OFF"), ALIGN("ALIGN"), NAV("NAV"), ATT("ATT") }
    enum class DsplSel(val label: String) { TK_GS("TK/GS"), PPOS("PPOS"), WIND("WIND"), HDG_STS("HDG/STS") }
    enum class EltMode(val label: String) { OFF("OFF"), ARM("ARM"), ON("ON") }
    enum class ServiceMode(val label: String) { OFF("OFF"), ON("ON") }
    enum class DomeMode(val label: String) { DIM("DIM"), OFF("OFF"), BRIGHT("BRIGHT") }
    enum class AudioMask(val label: String) { MASK("MASK"), BOOM("BOOM") }
    enum class FlightRecorderMode(val label: String) { TEST("TEST"), NORMAL("NORMAL") }
    enum class PassOxy(val label: String) { NORMAL("NORMAL"), ON("ON") }
    enum class EquipMode(val label: String) { NORMAL("NORMAL"), ALTERNATE("ALTERNATE") }
    enum class NavXfr(val label: String) { BOTH_ON_1("BOTH ON 1"), NORMAL("NORMAL"), BOTH_ON_2("BOTH ON 2") }
    enum class IrsXfr(val label: String) { BOTH_ON_L("BOTH ON L"), NORMAL("NORMAL"), BOTH_ON_R("BOTH ON R") }
    enum class EfiXfr(val label: String) { BOTH_ON_1("BOTH ON 1"), NORMAL("NORMAL"), BOTH_ON_2("BOTH ON 2") }

    /** Mirrors OverheadLocalStore (without the `set` action). Defaults are the TS initial values. */
    data class OverheadLocalState(
        val elt: EltMode = EltMode.OFF,
        val irsL: IrsMode = IrsMode.OFF,
        val irsR: IrsMode = IrsMode.OFF,
        /** 0..1 alignment progress */
        val irsAlignL: Double = 0.0,
        val irsAlignR: Double = 0.0,
        val irsSysDspl: String = "L",
        val irsDsplSel: DsplSel = DsplSel.PPOS,
        val irsEntry: String = "",
        val irsKeyTest: Boolean = false,
        val service: ServiceMode = ServiceMode.OFF,
        val dome: DomeMode = DomeMode.OFF,
        val audioRx: Map<String, Boolean> = emptyMap(),
        val audioTx: String = "",
        val audioMask: AudioMask = AudioMask.MASK,
        val audioRtIc: Boolean = false,
        val audioAlt: Boolean = false,
        val flightRecorder: FlightRecorderMode = FlightRecorderMode.NORMAL,
        val recorderOff: Boolean = false,
        /** 0 none, 1 test #1, 2 test #2 */
        val machWarn: Int = 0,
        /** 0 none, 1 test #1, 2 test #2 */
        val stallTest: Int = 0,
        val passOxy: PassOxy = PassOxy.NORMAL,
        val oxyPsi: Double = 1850.0,
        val yawDamper: Boolean = false,
        val machTrimTest: Boolean = false,
        val compassDg: Boolean = false,
        val compassHdg: Double = 0.0,
        val equipSupply: EquipMode = EquipMode.NORMAL,
        val equipExhaust: EquipMode = EquipMode.NORMAL,
        val cbBright: Double = 0.5,
        val panelBright: Double = 0.5,
        val attendCall: Boolean = false,
        val callLight: Boolean = false,
        val rainRepL: Boolean = false,
        val rainRepR: Boolean = false,
        val hornCutout: Boolean = false,
        val cvrTest: Boolean = false,
        val cvrErase: Boolean = false,
        val navXfr: NavXfr = NavXfr.NORMAL,
        val irsXfr: IrsXfr = IrsXfr.NORMAL,
        val efiXfr: EfiXfr = EfiXfr.NORMAL,
        val gen1Disc: Boolean = false,
        val gen2Disc: Boolean = false,
        val reverserTest: Pair<Boolean, Boolean> = Pair(false, false),
    )

    /** Holder for the overhead state. `set` merges a partial update, like the TS Zustand `set(p)`. */
    class Holder(initial: OverheadLocalState = OverheadLocalState()) {
        var state: OverheadLocalState = initial
            private set

        fun set(transform: (OverheadLocalState) -> OverheadLocalState) {
            state = transform(state)
        }
    }
}
