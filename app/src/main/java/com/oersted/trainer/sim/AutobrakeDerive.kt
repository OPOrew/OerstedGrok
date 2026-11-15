package com.oersted.trainer.sim

/**
 * Port of the antiskid and autobrake section of src/lib/sim/derive.ts (ATA 32).
 * Trainer thresholds copied verbatim from the TS: autobrake test timer 2.5 s, hydraulic ready 1500 psi,
 * landing on-ramp 60 kt, RTO 88 kt, thrust idle 0.05, pedal override 0.067, pedal disarm 20 kt,
 * pressure command 1000/2000/3000 psi for MIN/MED/MAX.
 * Runs after ElectricalDerive (needs d.dcPower) and GearDerive, as in the TS derive().
 */
object AutobrakeDerive {
    fun apply(s: SimState, d: Derived) {
        val dcPower = d.dcPower

        // Inboard channel is the arming interlock (AMM ATA 32-09-100); gear-lever UP microswitches
        // disable antiskid regardless of the channel switches (AMM 32-42-0).
        val gearLeverRetract = s.gear == "UP"
        val inbdOperational = s.antiskidInbd && ElectricalDerive.cbClosed(s, "antiskid-inbd") &&
            !s.faults.antiskidInop && !s.faults.antiskidInbdInop && !gearLeverRetract
        val outbdOperational = s.antiskidOutbd && ElectricalDerive.cbClosed(s, "antiskid-outbd") &&
            !s.faults.antiskidInop && !s.faults.antiskidOutbdInop && !gearLeverRetract
        val antiskidOperational = inbdOperational && outbdOperational

        val powerAvailable = dcPower && ElectricalDerive.cbClosed(s, "autobrake")
        val hydraulicReady = s.hydAPress > 1500.0 && s.hydBPress > 1500.0
        val testPassed = s.autobrake > 0.0 && s.autobrakeTestTimer >= 2.5 &&
            powerAvailable && inbdOperational && hydraulicReady
        val landingSelected = s.autobrake >= 1.0 && s.autobrake <= 3.0
        val rtoSelected = s.autobrake == 4.0
        val armed = testPassed &&
            ((landingSelected && !s.autobrakeLandingDisarmed && s.speedBrake == "ARMED") ||
                (rtoSelected && s.autobrakeRtoArmed))

        // Forward thrust levers advanced out of idle inhibit or drop landing autobrake (ATA 32-43-0/32-43-01).
        val throttlesIdle = s.throttle1 <= 0.05 && s.throttle2 <= 0.05
        val landingThrustDisarm = landingSelected && s.wow &&
            (s.groundSpeedKt > 60.0 || s.autobrakeLandingActivated) && testPassed && !throttlesIdle
        // Automatic braking starts at roughly 60-65 kt, then stays active through the rollout (ATA 32-42-0).
        val landingActive = armed && landingSelected && s.wow &&
            (s.groundSpeedKt > 60.0 || s.autobrakeLandingActivated) && throttlesIdle
        val rtoActive = armed && rtoSelected && s.wow &&
            s.throttle1 <= 0.05 && s.throttle2 <= 0.05 && s.groundSpeedKt > 88.0 && s.autobrakeRtoArmed

        // Pilot pedal braking overrides (disarms) the autobrake.
        val pedalOverride = s.leftBrakePedal > 0.067 || s.rightBrakePedal > 0.067
        val active = (landingActive || rtoActive) && !pedalOverride
        val disarm = s.autobrake > 0.0 && (
            !powerAvailable ||
                !inbdOperational ||
                s.autobrakeLandingDisarmed ||
                !hydraulicReady ||
                (landingSelected && s.speedBrake != "ARMED" && !active) ||
                (active && !throttlesIdle) ||
                landingThrustDisarm ||
                (pedalOverride && s.wow && s.groundSpeedKt > 20.0 && (landingActive || rtoActive))
            )

        // MIN/MED/MAX are deceleration selections; the simulator uses a proportional command.
        val pressure = if (!active) 0.0 else when (s.autobrake) {
            1.0 -> 1000.0
            2.0 -> 2000.0
            else -> 3000.0
        }
        val modulating = active && (inbdOperational || outbdOperational)

        d.antiskidInbdOperational = inbdOperational
        d.antiskidOutbdOperational = outbdOperational
        d.antiskidOperational = antiskidOperational
        d.autobrakeTestPassed = testPassed
        d.autobrakeArmed = armed
        d.autobrakeActive = active
        d.autobrakeDisarm = disarm
        d.autobrakePressure = pressure
        d.antiskidModulating = modulating
    }
}
