package com.oersted.trainer.sim

/**
 * One tick of the useAttitude hook (MainInstrumentPanel.tsx, ~1064–1145), composed from the pure pieces already ported:
 * attitude targets (AttitudeTargets), rate-limited integration (AttitudeKinematics), the dynamic altitude and write gate
 * (AttitudeOutputs), and the flight-director V-pointers (FlightDirector).
 *
 * The web order is kept: targets use the previous gamma, integration uses the new targets, the flight director sees the
 * newly integrated pitch, roll and heading, and the altitude uses the gamma updated by the targets.
 * Inputs that come from the upstream autopilot, plan and radio sections are supplied by the caller. Not verified against the web yet.
 */
object AttitudeHook {
    data class Inputs(
        val dt: Double,
        /** TAS, knots */
        val tas: Double,
        /** simulated altitude, feet */
        val simAltFt: Double,
        /** attitude targets input; its gamma is overwritten by the state gamma below */
        val targets: AttitudeTargets.Inputs,
        /** flight director input template; roll, pitch, heading and altitude are replaced by the integrated values */
        val fd: FlightDirector.Inputs,
    )

    /** Persistent hook state across ticks (the web `r` ref fields this step uses). */
    data class State(val attitude: MipLocal.Attitude, val gamma: Double)

    data class Result(
        val state: State,
        /** attitude to write to MIP, only when writeAttitude */
        val attitude: MipLocal.Attitude,
        val writeAttitude: Boolean,
        val navAltitudeFt: Double,
        /** SimStore psFlightVsFpm, null means no write this tick */
        val psFlightVsFpm: Double?,
        val fdCommand: FlightDirector.Command,
    )

    fun tick(state: State, p: Inputs): Result {
        val targetsIn = p.targets.copy(gamma = state.gamma)
        val t = AttitudeTargets.compute(targetsIn, p.dt)

        val a = state.attitude
        val next = AttitudeKinematics.integrate(
            a = a,
            pitchTarget = t.pitchTarget,
            rollTarget = t.rollTarget,
            rollRate = t.rollRate,
            tas = p.tas,
            flying = p.targets.flying,
            turb = p.targets.turb,
            dt = p.dt,
        )

        val alt = AttitudeOutputs.altitude(p.targets.dyn, t.gamma, p.tas, p.simAltFt, p.dt)

        val fd = FlightDirector.compute(
            p.fd.copy(roll = next.roll, pitch = next.pitch, hdg = next.hdg, altFt = p.simAltFt),
        )

        return Result(
            state = State(attitude = next, gamma = t.gamma),
            attitude = next,
            writeAttitude = AttitudeOutputs.shouldWriteAttitude(a, next),
            navAltitudeFt = alt.navAltitudeFt,
            psFlightVsFpm = alt.psFlightVsFpm,
            fdCommand = fd,
        )
    }
}
