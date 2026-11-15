package com.oersted.trainer.sim

/**
 * Port of flightModePatch (components/panel/FlightModeControl.tsx): the self-consistent airborne preset shared by the
 * FLIGHT button, the navigation trainer's 12 nm final, and the phase controller's ensureAirborne path.
 * Returned as a map for the stateful layer to apply to the sim store.
 */
object FlightModes {
    fun flightModePatch(altFt: Double): Map<String, Any> = mapOf(
        "battery" to true,
        "wow" to false,
        "airborneTest" to true,
        "groundSenseTest" to false,
        "lever1" to true,
        "lever2" to true,
        "n2_1" to 70.0,
        "n2_2" to 70.0,
        "bleed1" to true,
        "bleed2" to true,
        "engHyd1" to true,
        "engHyd2" to true,
        "elecHyd1" to true,
        "elecHyd2" to true,
        "gen1" to true,
        "gen2" to true,
        "gear" to "UP",
        "noseGearPos" to 0.0,
        "leftMainGearPos" to 0.0,
        "rightMainGearPos" to 0.0,
        "fltCtrlA" to true,
        "fltCtrlB" to true,
        "spoilerA" to true,
        "spoilerB" to true,
        "hydServicingConnected" to false,
        "hydGroundPowerConnected" to false,
        "groundPneumaticConnected" to false,
        "groundPneumaticPsi" to 0.0,
        "externalPowerConnected" to false,
        "parkingBrake" to false,
        "hydAPress" to 3000.0,
        "hydBPress" to 3000.0,
        "speedBrake" to "DOWN",
        "groundSpeedKt" to 160.0,
        "psFlightCasKt" to 160.0,
        "psFlightAltFt" to (if (altFt > 0) altFt else 5000.0),
        "psFlightTargetAltFt" to (if (altFt > 0) altFt else 5000.0),
        "psFlightTargetCasKt" to 160.0,
        "yokePitch" to 0.0,
        "yokeRoll" to 0.0,
        "rudderPedal" to 0.0,
        "tiller" to 0.0,
    )
}
