package com.oersted.trainer.sim

import com.oersted.trainer.sim.AutoFlight.Light
import com.oersted.trainer.sim.DmeModel.DmeChannelState
import com.oersted.trainer.sim.DmeModel.IDLE_DME
import com.oersted.trainer.sim.InstrumentSources.DEFAULT_SRC_POWER
import com.oersted.trainer.sim.InstrumentSources.GyroMemories
import com.oersted.trainer.sim.InstrumentSources.GyroMemory
import com.oersted.trainer.sim.InstrumentSources.SrcPower
import com.oersted.trainer.sim.MarkerBeacons.MarkerState
import com.oersted.trainer.sim.MarkerBeacons.NO_MARKERS
import com.oersted.trainer.sim.RadioNav.NavSceneId
import com.oersted.trainer.sim.RadioNav.RadioNavData
import com.oersted.trainer.sim.RadioNav.NO_NAV
import com.oersted.trainer.sim.Sources.NO_SRC_FAIL
import com.oersted.trainer.sim.Sources.SrcFail

/**
 * Port of mipLocal.ts: main-instrument-panel items the systems model does not simulate (clocks, GPWS, test switches,
 * selectors, radios). The TS Zustand store becomes a data class with the TS defaults plus a holder. The one TS subscriber
 * (mirror attLive into the sim's afPanelOwned) becomes the pure function [afPanelOwnedMirror].
 */
object MipLocal {
    /** Instrument flag failures, one per system. A flag shows only when its own system has failed, lost power, or its TEST is held. */
    data class MipFail(
        val att: Boolean = false,
        val fd: Boolean = false,
        val gs: Boolean = false,
        val loc: Boolean = false,
        val spd: Boolean = false,
        val ht: Boolean = false,
        val hdg: Boolean = false,
        val dme1: Boolean = false,
        val dme2: Boolean = false,
        val adf: Boolean = false,
        val vsi: Boolean = false,
        val tat: Boolean = false,
    )

    val NO_FAIL = MipFail()

    /** Collins FD-108 flight director command (show, bank, pitch, flag). */
    data class FdCmd(val show: Boolean, val bank: Double, val pitch: Double, val flag: Boolean)

    data class Attitude(val pitch: Double, val roll: Double, val hdg: Double)

    data class AttitudeSide(
        val pitch: Double,
        val roll: Double,
        val hdg: Double,
        val attValid: Boolean,
        val hdgValid: Boolean,
    )

    data class ClockState(val running: Boolean, val elapsed: Double)

    /** TS mip.hfCh channel: the tuning and usability subset. */
    data class HfTuneChannel(val tuning: Boolean, val usability: Int)

    data class LrraStatus(val h1: Double?, val h2: Double?, val dh1: Boolean, val dh2: Boolean)

    data class AttHdgRequest(val n: Int, val hdg: Double)

    data class ApAnnunciators(val voc: Light, val gs: Light)

    data class CompSync(val c1: Boolean, val c2: Boolean)

    data class DmeChannels(val ch1: DmeChannelState, val ch2: DmeChannelState)

    data class HfChannels(val ch1: HfTuneChannel, val ch2: HfTuneChannel)

    data class MipState(
        /** FD control panel: mode 0 GA, 1 OFF, 2 HDG, 3 VOR/LOC, 4 AUTO APPR; ALT HOLD toggle; PITCH CMD knob (deg) */
        val fdMode: Int = 1,
        val fdAlt: Boolean = false,
        val fdPitch: Double = 0.0,
        /** first-officer FD controller (glareshield, right): panel controls only */
        val fdMode2: Int = 1,
        val fdAlt2: Boolean = false,
        val fdPitch2: Double = 0.0,
        /** selected course, selected heading, first-officer course; deg */
        val selCrs: Double = 52.0,
        val selHdg: Double = 58.0,
        val selCrs2: Double = 59.0,
        /** SP-77 control panel: NAV 0 MAN, 1 VOR LOC, 2 AUTO APP, 3 MAN G/S */
        val apNav: Int = 0,
        /** pitch 0 TURB, 1 OFF, 2 ALT HOLD */
        val apPitch: Int = 1,
        /** HDG toggle 0 HDG OFF, 1 centre (hold), 2 HDG SEL */
        val apHdgSw: Int = 1,
        val apAil: Boolean = false,
        val apElev: Boolean = false,
        /** autopilot disengage warning lights flash until reset */
        val apWarn: Boolean = false,
        /** why the autopilot last disconnected or released a mode */
        val apWhy: String = "",
        /** failures of the individual gyro / compass / VHF NAV sources */
        val srcFail: SrcFail = NO_SRC_FAIL,
        /** per-radio / per-gyro circuit breakers and fast-erect */
        val srcPower: SrcPower = DEFAULT_SRC_POWER,
        /** compass HDG flag from fast-sync abuse (AMM 34-21) */
        val compSyncFlag: CompSync = CompSync(c1 = false, c2 = false),
        /** gyro frozen-memory (independent channel attitudes); the TS default has wasPowered true */
        val gyroMem: GyroMemories = GyroMemories(
            g1 = GyroMemory(wasPowered = true),
            g2 = GyroMemory(wasPowered = true),
            gAux = GyroMemory(wasPowered = true),
        ),
        /** DME channel search/track state */
        val dmeCh: DmeChannels = DmeChannels(IDLE_DME, IDLE_DME),
        val hfCh: HfChannels = HfChannels(HfTuneChannel(tuning = false, usability = 0), HfTuneChannel(tuning = false, usability = 0)),
        /** LRRA heights / DH lights */
        val lrra: LrraStatus = LrraStatus(h1 = null, h2 = null, dh1 = false, dh2 = false),
        val navOn: Boolean = false,
        /** "VOR" or "ILS" */
        val navTuned: String = "ILS",
        /** Active simulated NAV frequency; only frequencies in TRAINING_NAV_STATIONS can produce guidance. */
        val navFrequencyMHz: Double = 109.1,
        val navResetN: Int = 0,
        /** radio-nav scene the receiver works in */
        val navScene: NavSceneId = NavSceneId.RPLL06,
        /** PLAN NAV: the active flight plan steers the modeled autopilot while AIL / ELEV are engaged */
        val planNav: Boolean = false,
        /** one-shot request to set the aircraft heading; `n` increments per request */
        val attHdgReq: AttHdgRequest = AttHdgRequest(n = 0, hdg = 0.0),
        /** status published by the PLAN NAV logic for displays */
        val planNavStatus: String = "OFF",
        /** Captain-side receiver */
        val nav: RadioNavData = NO_NAV,
        /** First-officer-side receiver */
        val navFo: RadioNavData = NO_NAV,
        /** ADF relative bearings (deg) for RMI needles; null = no lock / failed / off */
        val adfBrg1: Double? = null,
        val adfBrg2: Double? = null,
        /** flight-director steering commands for the ADI V-pointers */
        val fdCmd: FdCmd = FdCmd(show = false, bank = 0.0, pitch = 0.0, flag = false),
        /** approach progress display lights */
        val apAnn: ApAnnunciators = ApAnnunciators(voc = Light.OFF, gs = Light.OFF),
        /** True aircraft attitude from the flight model (AFCS / shared). */
        val att: Attitude = Attitude(0.0, 0.0, 0.0),
        /** Captain-side displayed attitude from selected VG + compass */
        val attCapt: AttitudeSide = AttitudeSide(0.0, 0.0, 0.0, attValid = true, hdgValid = true),
        /** First-officer-side displayed attitude */
        val attFo: AttitudeSide = AttitudeSide(0.0, 0.0, 0.0, attValid = true, hdgValid = true),
        /** true while a mounted panel is running the attitude / autopilot / nav model */
        val attLive: Boolean = false,
        /** injected instrument failures (all clear by default) */
        val fail: MipFail = NO_FAIL,
        val clock: ClockState = ClockState(running = false, elapsed = 0.0),
        val gpwsInhibit: Boolean = false,
        val gpwsTest: Boolean = false,
        val nwsAlt: Boolean = false,
        val raDh: Double = 200.0,
        val raTest: Boolean = false,
        val navSel: Int = 1,
        val compSel: Int = 1,
        val gyroSel: Int = 1,
        val markerHigh: Boolean = true,
        /** ILS marker beacon reception (trainer geometry, see MarkerBeacons) */
        val markers: MarkerState = NO_MARKERS,
        val annTest: Int = 0,
        val floodCapt: Double = 0.6,
        val floodFo: Double = 0.6,
        val footAirCapt: Boolean = false,
        val footAirFo: Boolean = false,
        val masterCautionAck: Boolean = false,
        val recall: Boolean = false,
        /** LIGHT TEST key: 0 BRT, 1 DIM, 2 TEST */
        val lightKey: Int = 0,
        /** BRAKE TEST push-buttons (0 = none held) */
        val brakeTest: Int = 0,
        /** YAW DAMPER test: 0 off, 1 L, 2 R */
        val yawTest: Int = 0,
        val oilQtyTest: Boolean = false,
        val qtyTest: Boolean = false,
        val fuelSel: Boolean = true,
        val compTest: Boolean = false,
        /** ADI TEST buttons (AMM 34-22 / 34-26) */
        val adiTestCapt: Boolean = false,
        val adiTestFo: Boolean = false,
        /** baro settings (inHg); deprecated in the TS, kept for shape parity */
        val baroCapt: Double = 29.92,
        val baroFo: Double = 29.92,
        /** ASI speed bug (knots) */
        val bugCapt: Double = 140.0,
        val bugFo: Double = 140.0,
        val baroStby: Double = 30.62,
        /** VHF COMM (kHz / 10, i.e. 11900 = 119.00) */
        val comAct: Int = 11900,
        val comStby: Int = 13590,
        val comTest: Boolean = false,
        val cvrTest: Boolean = false,
        val gpsOn: Boolean = false,
        /** Sperry Primus 90 weather radar (AMM 34-41-01): mode pushbuttons 0 PUSH 1 STBY 2 WX 3 CYC 4 MAP */
        val radarMode: Int = 0,
        /** GAIN knob: 0 = PRESET detent, >0 = variable gain (WX and MAP only) */
        val radarGain: Double = 0.0,
        /** antenna tilt, +/-15 degrees */
        val radarTilt: Double = 0.0,
        /** RANGE switch index: 0 TEST, then 10 / 25 / 50 / 100 / 200 / 300 NM */
        val radarRange: Int = 4,
        /** INT knob (display intensity) 0..1 */
        val radarInt: Double = 0.6,
        val radarStab: Boolean = true,
        /** SEC SCAN in = 80 degree sector, out = 160 degrees */
        val radarSector: Boolean = false,
        val radarFrz: Boolean = false,
        val radarAzim: Boolean = false,
        val radarTgt: Boolean = false,
        /** time (ms) the radar left the all-buttons-out state; 0 = cold. STBY warm-up is 70 s. */
        val radarStbyAt: Double = 0.0,
        /** transponder: 0 STBY 1 ALT OFF 2 ALT ON 3 TEST */
        val xpdrMode: Int = 0,
        val xpdrCode: String = "1200",
        val atcSel: Int = 1,
    )

    /**
     * TS useMip subscriber: a mounted panel that runs attitude / autopilot / nav telemetry owns the systems model's
     * afPanelOwned flag. Returns the value to write, or null when the flag already matches.
     */
    fun afPanelOwnedMirror(state: MipState, simAfPanelOwned: Boolean): Boolean? =
        if (simAfPanelOwned != state.attLive) state.attLive else null

    /** Holder for the panel state. `set` applies a transform; `onChange` runs after each set, like the TS subscribe. */
    class Holder(
        initial: MipState = MipState(),
        private val onChange: ((MipState) -> Unit)? = null,
    ) {
        var state: MipState = initial
            private set

        fun set(transform: (MipState) -> MipState) {
            state = transform(state)
            onChange?.invoke(state)
        }
    }
}
