package com.oersted.trainer.sim

import java.util.WeakHashMap

/**
 * Port of the state holder in src/lib/sim/store.ts without the React plumbing.
 *
 * The web store is a Zustand store read by React panels through a throttled snapshot. Here the state
 * is held once and observers are notified on every change. The Android layer can wrap [subscribe] in a
 * StateFlow for Compose. The render throttle, the switch-click sound, and the urgent-commit flag are UI
 * concerns, so they are NOT ported here: the caller decides when to click (see [update]).
 *
 * Not ported yet: readyApu, readyForDeparture, startEngine, shutdownEngine, toggleFault,
 * injectLandingGearFault, clearLandingGearFault, runScenario. They are large mutation sequences and
 * come next (see docs/HANDOFF.md).
 */
class SimStore(
    initial: SimState = initialSimState(),
    private val nav: FlightNavigator? = null,
    private val planProvider: () -> ActiveFlightPlan? = { null },
    private val clock: () -> Double = { System.currentTimeMillis().toDouble() },
) {
    private var state: SimState = initial
    private val derivedCache = WeakHashMap<SimState, Derived>()
    private val listeners = mutableListOf<(SimState) -> Unit>()

    fun current(): SimState = state

    /** TS getDerived: derived values for a committed state, computed once per state object. */
    fun derived(s: SimState = state): Derived = derivedCache.getOrPut(s) { SimDerive.computeAll(s) }

    /** Registers a listener, called after every committed change. Returns a function that unregisters it. */
    fun subscribe(listener: (SimState) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    private fun commit(next: SimState, derivedValues: Derived? = null) {
        state = next
        if (derivedValues != null) derivedCache[next] = derivedValues
        for (l in listeners.toList()) l(next)
    }

    /**
     * TS patch: applies [transform] to a copy of the current state. A [note] is written to the log;
     * otherwise a log line is written for the first changed control when [transform] names it via
     * [changedKey]. Pass the control name the caller changed so the log reads the same as the web.
     */
    fun update(note: String? = null, changedKey: String? = null, before: String? = null, after: String? = null, transform: (SimState) -> Unit) {
        val next = state.copy()
        transform(next)
        val out = when {
            note != null -> StepSim.pushLog(next, note, "info", nowMs = clock())
            changedKey != null -> StepSim.pushLog(
                next,
                "${StepSim.controlLabel(changedKey)} → ${(after ?: "").uppercase()}",
                "info",
                control = changedKey,
                before = before,
                after = after,
                nowMs = clock(),
            )
            else -> next
        }
        commit(out)
    }

    /** Commits the state returned by a pure action (see SimActions). */
    fun apply(transform: (SimState) -> SimState) = commit(transform(state))

    /** TS tick: one simulation step of [dt] real seconds (scaled by the time scale inside the tick). */
    fun tick(dt: Double, liveWxIntensity: Double = 0.0) {
        val s = state
        val prev = derived(s)
        val (next, now) = SimTick.tick(s, dt, prev, nav, planProvider(), clock(), liveWxIntensity)
        commit(next, now)
    }

    /** TS reset: back to the initial state. The log and faults are copied, as in the web. */
    fun reset() = commit(initialSimState())

    /** TS clearLog. */
    fun clearLog() {
        val next = state.copy()
        next.log = mutableListOf()
        commit(next)
    }
}
