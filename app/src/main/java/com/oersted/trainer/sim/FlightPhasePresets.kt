package com.oersted.trainer.sim

/**
 * Port of the pure patch builders in flightPhases.ts: the parked-on-ground preset written by loadPlanIntoControls
 * (groundReadyPatch), the sim patch written by parkAtDeparture, and the MIP PLAN NAV arming patch shared by
 * loadPlanIntoControls and parkAtDeparture.
 *
 * The web code writes these with useSim.setState / useMip.setState / patchSim. Here they are returned as maps so the
 * stateful layer (not yet ported) can apply them to the sim and MIP holders. Values keep the web types: strings for
 * enum-like fields, Double for numbers, Boolean for flags.
 */
object FlightPhasePresets {

    /** Speed brake and flap values from the READY preset. */
    const val READY_FLAPS = 5.0

    /**
     * web groundReadyPatch(plan): explicit parked-on-ground preset. Engine/APU state is deliberately NOT included,
     * so loading a route never touches the start sequence.
     */
    fun groundReadyPatch(fieldElevFt: Double): Map<String, Any> = mapOf(
        "battery" to true,
        "wow" to true,
        "airborneTest" to false,
        "groundSenseTest" to false,
        "gear" to "DN",
        "noseGearPos" to 1.0,
        "leftMainGearPos" to 1.0,
        "rightMainGearPos" to 1.0,
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
        "flaps" to READY_FLAPS,
        "speedBrake" to "ARMED",
        // takeoff brake configuration: both anti-skid channels ON, autobrake on RTO
        "antiskidInbd" to true,
        "antiskidOutbd" to true,
        "autobrake" to 4.0,
        "throttle1" to 0.0,
        "throttle2" to 0.0,
        "groundSpeedKt" to 0.0,
        "psFlightCasKt" to 0.0,
        "psFlightTargetCasKt" to 0.0,
        "psGroundRoll" to false,
        "psFlightVsFpm" to 0.0,
        "psFlightAltFt" to fieldElevFt,
        "psFlightTargetAltFt" to fieldElevFt,
        "psFlightProgressive" to true,
        "yokePitch" to 0.0,
        "yokeRoll" to 0.0,
        "rudderPedal" to 0.0,
        "tiller" to 0.0,
        "rwyPhase" to "IDLE",
        "rwyPosM" to -1800.0,
        "rwyAltFt" to 0.0,
        "rwyLatM" to 0.0,
        "rwyHdgDeg" to 0.0,
        "rwyTrackDeg" to 0.0,
        "rwyYawRate" to 0.0,
        "rwyNote" to "",
        "wheelSpeeds" to wheels(),
        "wheelBrakePressures" to wheels(),
        "actualDecel" to 0.0,
    )

    /**
     * web parkAtDeparture(): the physical parked state written directly to the sim (a subset of the ground preset,
     * without the systems and brake-configuration keys). Enforced even after an airborne or landing exercise.
     */
    fun parkSimPatch(fieldElevFt: Double): Map<String, Any> = mapOf(
        "wow" to true,
        "airborneTest" to false,
        "groundSenseTest" to false,
        "gear" to "DN",
        "noseGearPos" to 1.0,
        "leftMainGearPos" to 1.0,
        "rightMainGearPos" to 1.0,
        "groundSpeedKt" to 0.0,
        "psFlightCasKt" to 0.0,
        "psFlightTargetCasKt" to 0.0,
        "psFlightVsFpm" to 0.0,
        "psGroundRoll" to false,
        "psFlightAltFt" to fieldElevFt,
        "psFlightTargetAltFt" to fieldElevFt,
        "psFlightProgressive" to true,
        "rwyPhase" to "IDLE",
        "rwyPosM" to -1800.0,
        "rwyAltFt" to 0.0,
        "rwyLatM" to 0.0,
        "rwyHdgDeg" to 0.0,
        "rwyTrackDeg" to 0.0,
        "rwyYawRate" to 0.0,
        "rwyNote" to "",
        "wheelSpeeds" to wheels(),
        "wheelBrakePressures" to wheels(),
        "actualDecel" to 0.0,
    )

    /**
     * MIP patch that arms PLAN NAV and waits for TAKEOFF: autopilot axes off, navResetN incremented.
     * When the departure runway heading is known, the HDG SEL bug and course are set to its rounded value and the
     * attitude heading request is bumped so the flight director turns to it.
     */
    fun planNavArmPatch(
        navResetN: Int,
        attHdgReqN: Int,
        rwyHdgMag: Double?,
    ): Map<String, Any> {
        val m = mutableMapOf<String, Any>(
            "planNav" to true,
            "planNavStatus" to "ARMED - WAITING FOR TAKEOFF",
            "apAil" to false,
            "apElev" to false,
            "apNav" to 0.0,
            "apHdgSw" to 0.0,
            "navResetN" to (navResetN + 1).toDouble(),
        )
        if (rwyHdgMag != null) {
            val h = FlightPhaseControl.jsRound(rwyHdgMag)
            m["selHdg"] = h
            m["selCrs"] = h
            m["attHdgReq"] = mapOf("n" to (attHdgReqN + 1).toDouble(), "hdg" to rwyHdgMag)
        }
        return m
    }

    private fun wheels(): Map<String, Double> = mapOf("li" to 0.0, "lo" to 0.0, "ri" to 0.0, "ro" to 0.0)
}
