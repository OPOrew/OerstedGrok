package com.oersted.trainer.ui.cockpit

import com.oersted.trainer.platform.PhaseTimer
import com.oersted.trainer.sim.ActiveFlightPlan
import com.oersted.trainer.sim.AudioFeed
import com.oersted.trainer.sim.AttitudeTicker
import com.oersted.trainer.sim.FlightPhaseControl
import com.oersted.trainer.sim.Situation
import com.oersted.trainer.sim.CockpitReadout
import com.oersted.trainer.sim.Derived
import com.oersted.trainer.sim.SimActions
import com.oersted.trainer.sim.FlightNavigationState
import com.oersted.trainer.sim.FlightNavigator
import com.oersted.trainer.sim.MipLocal.AttHdgRequest
import com.oersted.trainer.sim.FlightPlanDraft
import com.oersted.trainer.sim.FlightRoute
import com.oersted.trainer.sim.LogEntry
import com.oersted.trainer.sim.SimState
import com.oersted.trainer.sim.FlightPhases.Phase
import com.oersted.trainer.sim.MipLocal
import com.oersted.trainer.sim.OverheadLocal
import com.oersted.trainer.sim.PitotStatic
import com.oersted.trainer.sim.SixPackData
import com.oersted.trainer.sim.Pedestal
import com.oersted.trainer.sim.PhaseController
import com.oersted.trainer.sim.SimPhaseWorld
import com.oersted.trainer.sim.SimStore
import com.oersted.trainer.sim.initialSimState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Owns the sim, the flight-phase controller and their timers for the cockpit screen.
 * Wiring matches the parity test (PhaseLongE2EWebParityTest): SimStore + MIP/pedestal holders + SimPhaseWorld + PhaseController.
 * The sim step follows SimTicker.tsx: accumulate real time, step once at least 30 ms has passed, each step capped at 0.25 s.
 * Everything runs on [scope]; use a main-thread scope so the UI and the sim never race.
 * Not compiled in the sandbox (needs the Android SDK and kotlinx-coroutines).
 */
class CockpitModel(
    private val scope: CoroutineScope,
    private val monoSec: () -> Double = { System.nanoTime() / 1e9 },
) {
    private var plan: ActiveFlightPlan? = null
    private val clockMs: () -> Long = { System.currentTimeMillis() }
    private val nav = FlightNavigator()
    private val sim = SimStore(initialSimState(), nav, { plan }, { clockMs().toDouble() })
    private val _mip = MutableStateFlow(MipLocal.MipState())
    private val _pedestal = MutableStateFlow(Pedestal.PedestalState())
    private val mip = MipLocal.Holder(onChange = { _mip.value = it })
    private val pedestal = Pedestal.Holder(onChange = { _pedestal.value = it })
    private val overheadLocal = OverheadLocal.Holder()
    private val world = SimPhaseWorld(sim, mip, pedestal, nav, { clockMs() }, monoSec, { plan })
    private val controller = PhaseController(world)
    private val situation = Situation.SituationHolder()
    private val attitude = AttitudeTicker(
        sim,
        mip,
        pedestal,
        nav,
        { plan },
        { alt -> FlightPhaseControl.applyPhaseToGuidance(nav.liveGuidance(), controller.phase, alt, plan) },
        situation,
    )
    private val phaseTimer = PhaseTimer(scope, controller, monoSec)
    private var simJob: Job? = null

    private val _sixPack = MutableStateFlow(sixPackSnapshot())
    /** Primary flight instruments (attitude, heading, both sides' airspeed / altitude / VSI). */
    val sixPack: StateFlow<SixPackData> = _sixPack.asStateFlow()

    private val _air = MutableStateFlow(airSnapshot())
    /** Air data for both sides and the standby instruments (web computeAirData), for the main panel faces. */
    val air: StateFlow<PitotStatic.AirData> = _air.asStateFlow()

    private val _activePlanLabel = MutableStateFlow<String?>(null)
    /** e.g. "RPLL → WSSS" when a plan is active, null otherwise. */
    val activePlanLabel: StateFlow<String?> = _activePlanLabel.asStateFlow()

    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    /** The sim's event log, newest entries as the sim keeps them; drives the LOG drawer. */
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()

    private val _derived = MutableStateFlow(sim.derived(sim.current()))
    /** Derived values for the current sim state (transfer-bus power, engine running, ...). */
    val derived: StateFlow<Derived> = _derived.asStateFlow()

    private val _activePlan = MutableStateFlow<ActiveFlightPlan?>(null)
    /** The active flight plan (web readActiveFlightPlan), for the pedestal GPS. */
    val activePlan: StateFlow<ActiveFlightPlan?> = _activePlan.asStateFlow()

    private val _navState = MutableStateFlow<FlightNavigationState?>(null)
    /** The navigator's latest state (web readFlightNavigationState), for the pedestal GPS. */
    val navState: StateFlow<FlightNavigationState?> = _navState.asStateFlow()

    private val _simState = MutableStateFlow(sim.current())
    /** Latest sim state, for the TRAINING console's switch labels. */
    val simState: StateFlow<SimState> = _simState.asStateFlow()

    /** Applies a pure state change (TrainingConsoleRules and similar) and publishes it. */
    fun applySim(transform: (SimState) -> SimState) {
        val before = if (onSwitchClick != null) sim.current() else null
        sim.apply(transform)
        publish()
        if (before != null) clickIfSwitch(before)
    }

    /** Kotlin form of the web `s.patch({...})`: copy the state, apply the edit, publish. */
    fun patchSim(edit: SimState.() -> Unit) {
        applySim { s -> s.copy().apply(edit) }
    }

    /** Called on a switch-type change (web store.ts patch -> playSwitchClick). Set by the audio layer. */
    var onSwitchClick: (() -> Unit)? = null

    /** web patch(): click for switch-type changes only (booleans, strings, small whole-number detents); sliders stay silent. */
    private fun clickIfSwitch(before: SimState) {
        val cb = onSwitchClick ?: return
        if (AudioFeed.isSwitchChange(before, sim.current())) cb()
    }

    /** Inputs for the audio engine (cockpit.tsx useSimAudio argument), built from the live sim and the local panel state. */
    fun audioFeed(): AudioFeed.Feed {
        val st = sim.current()
        val m = mip.state
        val o = overheadLocal.state
        return AudioFeed.build(
            s = st,
            d = sim.derived(st),
            ped = pedestal.state,
            apWarn = m.apWarn,
            gpwsTest = m.gpwsTest,
            o = AudioFeed.Overhead(hornCutout = o.hornCutout, machWarn = o.machWarn, stallTest = o.stallTest),
            markers = m.markers,
            navOn = m.navOn,
        )
    }

    /** Web StatusStrip time button: 1x -> 4x -> 10x -> 1x. */
    fun cycleTimeScale() {
        applySim { s ->
            val c = s.copy()
            c.timeScale = if (s.timeScale == 1.0) 4.0 else if (s.timeScale == 4.0) 10.0 else 1.0
            c
        }
    }

    /** Kotlin form of the web `s.patch({...}, note)`: applies the edit to a copy, writes [note] to the event log, publishes. */
    fun patchSimNote(note: String, edit: SimState.() -> Unit) {
        val before = sim.current()
        sim.update(note = note) { it.edit() }
        publish()
        clickIfSwitch(before)
    }

    /** Web "Set APU running" (SimActions.readyApu preset). */
    fun readyApu() {
        applySim { SimActions.readyApu(System.currentTimeMillis().toDouble()) }
    }

    /** Web "Cold & dark" (reset to the initial state). */
    fun coldAndDark() = applySim { initialSimState() }

    /** MIP state (FD, autopilot, warning lamps) for the glareshield panel. */
    val mipState: StateFlow<MipLocal.MipState> = _mip.asStateFlow()

    /** Pedestal state (fire test, bell cut-out) for the glareshield panel. */
    val pedestalState: StateFlow<Pedestal.PedestalState> = _pedestal.asStateFlow()

    /** Applies a MIP reducer (GlareshieldReducer, WarningReducer) and republishes the sim. */
    fun updateMip(transform: (MipLocal.MipState) -> MipLocal.MipState) {
        mip.set(transform)
        publish()
    }

    /** Applies an overhead-local reducer (web useOverheadLocal.set), e.g. the pedestal horn cutout. Nothing in the sim reads it yet. */
    fun updateOverhead(transform: (OverheadLocal.OverheadLocalState) -> OverheadLocal.OverheadLocalState) {
        overheadLocal.set(transform)
    }

    /** Applies a pedestal reducer (WarningReducer.silenceBell and similar) and republishes the sim. */
    fun updatePedestal(transform: (Pedestal.PedestalState) -> Pedestal.PedestalState) {
        pedestal.set(transform)
        publish()
    }

    private val _readout = MutableStateFlow(snapshot())
    val readout: StateFlow<CockpitReadout> = _readout.asStateFlow()

    /**
     * Activates a draft, as the web's publishAndArm: publish the plan, set PLAN NAV and the runway-heading request,
     * then load the plan into the controls. The aircraft stays parked until TAKEOFF is pressed.
     */
    fun activate(draft: FlightPlanDraft) {
        val active = draft.toActive(java.time.Instant.now().toString())
        plan = active
        val hdg = FlightRoute.departureRunwayHeadingMag(active)
        mip.set { m -> m.copy(planNav = true, attHdgReq = if (hdg != null) AttHdgRequest(n = m.attHdgReq.n + 1, hdg = hdg) else m.attHdgReq) }
        controller.loadPlanIntoControls()
        _activePlanLabel.value = "${active.departure} → ${active.destination}"
        publish()
    }

    /** Activates a plan (null clears it). Loads it into the controls, as the web's loadPlanIntoControls does. */
    fun setPlan(p: ActiveFlightPlan?) {
        plan = p
        if (p != null) controller.loadPlanIntoControls()
        publish()
    }

    fun start() {
        if (simJob?.isActive == true) return
        phaseTimer.start()
        attitude.start()
        simJob = scope.launch {
            var last = monoSec()
            var acc = 0.0
            while (isActive) {
                delay(STEP_POLL_MS)
                val now = monoSec()
                acc += min(0.25, now - last)
                last = now
                if (acc >= SIM_STEP_S) {
                    sim.tick(min(0.25, acc))
                    acc = 0.0
                }
                attitude.maybeTick(now)
                publish()
            }
        }
    }

    fun stop() {
        simJob?.cancel()
        simJob = null
        phaseTimer.stop()
        attitude.stop()
    }

    fun startPhase(p: Phase) {
        controller.startPhase(p)
        publish()
    }

    private fun snapshot(): CockpitReadout = CockpitReadout.build(
        controller.phase, controller.hint, sim.current(), { p: Phase -> controller.canStart(p) },
    )

    private fun airSnapshot(): PitotStatic.AirData {
        val st = sim.current()
        val d = sim.derived(st)
        return PitotStatic.computeAirData(st, d.dcPower, d.acPower)
    }

    private fun sixPackSnapshot(air: PitotStatic.AirData = airSnapshot()): SixPackData {
        val m = mip.state
        return SixPackData.build(sim.current(), m.attLive, m.att, air)
    }

    /** LOG drawer: clears the sim's event log, as the web's clearLog. */
    fun clearLog() {
        sim.clearLog()
        publish()
    }

    private fun publish() {
        _readout.value = snapshot()
        val air = airSnapshot()
        _air.value = air
        _sixPack.value = sixPackSnapshot(air)
        _log.value = sim.current().log.toList()
        _activePlan.value = plan
        _navState.value = nav.readState()
        _simState.value = sim.current()
        _derived.value = sim.derived(sim.current())
    }

    private companion object {
        const val SIM_STEP_S = 0.03
        const val STEP_POLL_MS = 30L
    }
}
