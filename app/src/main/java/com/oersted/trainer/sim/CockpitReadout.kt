package com.oersted.trainer.sim

import com.oersted.trainer.sim.FlightPhaseControl.StartGate
import com.oersted.trainer.sim.FlightPhases.Phase

/**
 * What the cockpit screen shows, built from the sim and the controller (pure, no Android).
 * [gates] holds the start gate for each phase button, in the order of [BUTTON_PHASES].
 */
data class CockpitReadout(
    val phaseLabel: String,
    val hint: String,
    val altFt: Double,
    val casKt: Double,
    val vsFpm: Double,
    val headingDeg: Double,
    val throttle1: Double,
    val gates: Map<Phase, StartGate>,
) {
    companion object {
        /** Phase buttons on the cockpit screen, in web order. */
        val BUTTON_PHASES: List<Phase> = listOf(Phase.TAKEOFF, Phase.CLIMB, Phase.CRUISE, Phase.DESCENT, Phase.APPROACH, Phase.LAND)

        fun build(phase: Phase, hint: String, sim: SimState, gateFor: (Phase) -> StartGate): CockpitReadout =
            CockpitReadout(
                phaseLabel = phase.label,
                hint = hint,
                altFt = sim.psFlightAltFt,
                casKt = sim.psFlightCasKt,
                vsFpm = sim.psFlightVsFpm,
                headingDeg = sim.attitudeHeadingDeg,
                throttle1 = sim.throttle1,
                gates = BUTTON_PHASES.associateWith(gateFor),
            )
    }
}
