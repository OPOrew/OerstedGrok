package com.oersted.trainer.sim

import kotlin.math.floor

/**
 * Rules behind the Fault injection and Scenarios modules (components/panel/Overhead.tsx, FaultModule and ScenarioModule).
 * The fault toggles, landing-gear fault and scenarios are already in [SimActions] (toggleFault, injectLandingGearFault,
 * clearLandingGearFault, runScenario). This file holds the remaining handler and the adverse-yaw cue text.
 */
object FaultInjectionRules {
    /**
     * Web CENTER PUMP AUTO-SHUTOFF button: switches the pre-SB / post-SB (28A1228) installation and clears both relay
     * trips and their low-pressure timers. The web logs this as a plain control change.
     */
    fun toggleCenterPumpAutoShutoff(s: SimState): SimState {
        val c = s.copy()
        c.fuelCenterPumpAutoShutoffInstalled = !s.fuelCenterPumpAutoShutoffInstalled
        c.fuelCenterPump1RelayTripped = false
        c.fuelCenterPump2RelayTripped = false
        c.fuelCenterPump1LowPressureTimer = 0.0
        c.fuelCenterPump2LowPressureTimer = 0.0
        return c
    }

    /** Web label: "CENTER PUMP AUTO-SHUTOFF · POST-SB 28A1228" or "PRE-SB". */
    fun centerPumpLabel(installed: Boolean): String = if (installed) "POST-SB 28A1228" else "PRE-SB"

    /** Web ADVERSE YAW CUE line: OFF, or the cue as a whole percentage and its direction (JS Math.round). */
    fun adverseYawCueLine(scenario: Boolean, cue: Double, direction: String): String =
        if (!scenario) "OFF" else "${floor(cue * 100 + 0.5).toLong()}% $direction"

    /** Web second line under the cue: the coordinated-turn advice, shown only with the scenario on. */
    fun adverseYawAdvice(scenario: Boolean, corrected: Boolean): String? = when {
        !scenario -> null
        corrected -> "COORDINATED \u2014 CUE REDUCED"
        else -> "ADD RUDDER IN TURN DIRECTION"
    }
}
