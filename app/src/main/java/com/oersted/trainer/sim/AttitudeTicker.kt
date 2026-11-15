package com.oersted.trainer.sim

import kotlin.math.min

/**
 * Caller of the attitude tick: the web useAttitude interval (MainInstrumentPanel.tsx ~643–1145), run every 100 ms.
 * Gathers the inputs from the stores, runs [RadioStage], [AutopilotStage] and [AttitudeHook.tick], and applies the
 * results: MIP and pedestal writes, the flight navigation advance, the dynamic altitude and vertical speed on the sim,
 * and the situation sample. The web's weather radar publish is not ported.
 *
 * Not compiled in the sandbox at the time of writing; the call sites are in CockpitModel. Not verified against the web yet.
 */
class AttitudeTicker(
    private val sim: SimStore,
    private val mip: MipLocal.Holder,
    private val pedestal: Pedestal.Holder,
    private val nav: FlightNavigator,
    private val plan: () -> ActiveFlightPlan?,
    /** web applyPhaseToGuidance(getLiveFlightGuidance(), altFt) with the current phase */
    private val guidanceFor: (Double) -> GuidanceResult,
    val situation: Situation.SituationHolder,
) {
    private var attState = AttitudeHook.State(attitude = MipLocal.Attitude(0.0, 0.0, 0.0), gamma = 0.0)
    private var apState = AutopilotStage.State()
    private var erect = InstrumentSources.ErectTimers()
    private var pos = RadioNav.Point(RadioNav.NAV_START_X, RadioNav.NAV_START_Y)
    private var resetN = 0
    private var appliedHdgReqN = 0
    private var lastTickSec = Double.NaN

    /** web useAttitude enable: the panel model owns attitude and heading, so the six-pack shows the model's values. */
    fun start() {
        mip.set { it.copy(attLive = true) }
        attState = attState.copy(attitude = mip.state.att)
    }

    /** web useAttitude cleanup: the six-pack goes back to the sim's own attitude. */
    fun stop() {
        mip.set { it.copy(attLive = false) }
    }

    /** Runs one tick when at least 100 ms have passed (web setInterval 100), with dt capped at 0.5 s as the web. */
    fun maybeTick(nowSec: Double) {
        if (lastTickSec.isNaN()) {
            lastTickSec = nowSec
            return
        }
        val elapsed = nowSec - lastTickSec
        if (elapsed < TICK_INTERVAL_S) return
        lastTickSec = nowSec
        tick(min(0.5, elapsed))
    }

    fun tick(dt: Double) {
        val g = sim.current()
        val d = sim.derived(g)
        val m0 = mip.state
        val ped = pedestal.state

        // Navigation reset and one-shot heading request (the web applies both before the radio section).
        if (m0.navResetN != resetN) {
            resetN = m0.navResetN
            pos = RadioNav.Point(RadioNav.NAV_START_X, RadioNav.NAV_START_Y)
            attState = attState.copy(attitude = attState.attitude.copy(hdg = RadioNav.NAV_START_HDG))
            apState = apState.copy(latSub = AutoFlight.LatSub.OFF, gsPhaseAp = AutoFlight.GsPhase.OFF, gsCapT = 0.0)
        }
        if (m0.attHdgReq.n != appliedHdgReqN) {
            appliedHdgReqN = m0.attHdgReq.n
            attState = attState.copy(attitude = attState.attitude.copy(hdg = m0.attHdgReq.hdg))
        }

        val air = PitotStatic.computeAirData(g, d.dcPower, d.acPower).capt
        val ias = air.iasKt
        val vs = air.vsiFpm
        val alt = air.altFt
        val power = d.dcPower
        val hydBOk = d.hydB >= 1500.0
        val a = attState.attitude
        val tas = AutopilotStage.trueAirspeed(ias, alt)
        val simAlt = g.psFlightAltFt
        val tgtAlt = if (g.psFlightTargetAltFt.isFinite()) g.psFlightTargetAltFt else simAlt
        val flying = !g.wow && ias > 60

        val radio = RadioStage.tick(
            RadioStage.Inputs(
                dt = dt,
                sim = g,
                derived = d,
                m = m0,
                ped = ped,
                pos = pos,
                pitch = a.pitch,
                roll = a.roll,
                hdg = a.hdg,
                tas = tas,
                simAltFt = simAlt,
                flying = flying,
                power = power,
                erectTimers = erect,
                liveNav = nav.liveState(),
                liveArr = nav.liveArrival(),
                navState = nav.readState(),
            ),
        )
        erect = radio.inst.erectTimers
        pos = radio.pos
        if (radio.clearHfTuneReq) pedestal.set { p -> p.copy(hfTuneReq = Pair(false, false)) }

        val ap = AutopilotStage.tick(
            AutopilotStage.Inputs(
                dt = dt,
                ias = ias,
                vs = vs,
                alt = alt,
                tas = tas,
                simAltFt = simAlt,
                yokePitch = g.yokePitch,
                yokeRoll = g.yokeRoll,
                wow = g.wow,
                power = power,
                hydBOk = hydBOk,
                trainer = PitotStatic.flightStateActive(g),
                psFlightProgressive = g.psFlightProgressive,
                tgtAltFt = tgtAlt,
                pitch = a.pitch,
                roll = a.roll,
                hdg = a.hdg,
                m = m0,
                ped = ped,
                radio = radio,
                state = apState,
                guide = guidanceFor(simAlt),
                liveArr = nav.liveArrival(),
                sceneId = radio.sceneId,
            ),
        )
        apState = ap.state

        // Pedestal writes from the ILS handoff (NAV 1 retune, NAV 2 active frequency).
        ap.nav1Tune?.let { t -> pedestal.set { p -> p.copy(navL = Pedestal.Freq(t.navLAct, t.navLStby)) } }
        ap.navRAct?.let { raw -> pedestal.set { p -> p.copy(navR = Pedestal.Freq(raw, p.navR.stby)) } }
        mip.set { ap.mip }

        val res = AttitudeHook.tick(
            AttitudeHook.State(attitude = a, gamma = ap.state.gamma),
            AttitudeHook.Inputs(dt = dt, tas = tas, simAltFt = simAlt, targets = ap.targets, fd = ap.fdTemplate),
        )
        attState = res.state

        val fdc = res.fdCommand
        mip.set { it.copy(fdCmd = MipLocal.FdCmd(show = fdc.show, bank = fdc.bank, pitch = fdc.pitch, flag = fdc.flag)) }
        if (res.writeAttitude) mip.set { it.copy(att = res.attitude) }

        // Dynamic (trainer) vertical path: the altitude and vertical speed go back to the sim.
        if (ap.dyn) {
            sim.apply { s ->
                val c = s.copy()
                c.psFlightAltFt = res.navAltitudeFt
                c.psFlightTargetAltFt = res.navAltitudeFt
                res.psFlightVsFpm?.let { c.psFlightVsFpm = it }
                c
            }
        }

        // The panel model owns the live heading; the route tracker follows it.
        nav.advance(
            plan(),
            NavigationTelemetry(
                dt = dt,
                groundSpeedKt = g.groundSpeedKt,
                headingDeg = res.attitude.hdg,
                altitudeFt = res.navAltitudeFt,
                wow = g.wow,
            ),
        )

        ap.situation?.let { situation.publish(it) }
    }

    private companion object {
        const val TICK_INTERVAL_S = 0.1
    }
}
