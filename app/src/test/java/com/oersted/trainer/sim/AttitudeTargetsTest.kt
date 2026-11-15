package com.oersted.trainer.sim

import com.oersted.trainer.sim.AttitudeTargets.Inputs
import org.junit.Assert.assertEquals
import org.junit.Test

class AttitudeTargetsTest {
    private fun base(
        flying: Boolean = true, wow: Boolean = false, ias: Double = 250.0, turb: Boolean = false,
        yokePitch: Double = 0.0, yokeRoll: Double = 0.0, gamma: Double = 0.0, aoa: Double = 3.0,
        ailOn: Boolean = false, cwsR: Boolean = false, navHeld: Boolean = false,
        apHdgSw: Int = 0, refRoll: Double = 0.0, dyn: Boolean = false, elevOn: Boolean = false,
        cwsP: Boolean = false, refPitch: Double = 0.0,
    ) = Inputs(
        flying = flying, wow = wow, ias = ias, turb = turb, yokePitch = yokePitch, yokeRoll = yokeRoll,
        gamma = gamma, aoa = aoa, ailOn = ailOn, cwsR = cwsR, navHeld = navHeld,
        lateralBank = 0.0, lateralLim = 25.0, lateralRate = 8.0, planLat = false, planHdgErr = 0.0,
        apHdgSw = apHdgSw, selHdgErr = 0.0, hdgHold = false, refHdgErr = 0.0, refRoll = refRoll,
        dyn = dyn, elevOn = elevOn, cwsP = cwsP, gsCapAp = false, altSel = false, refPitch = refPitch,
        capturedGamma = 0.0,
    )

    @Test fun manualFlyingFollowsTheYoke() {
        val r = AttitudeTargets.compute(base(yokeRoll = 0.5, yokePitch = 0.5, gamma = 1.0), dt = 0.1)
        assertEquals(15.0, r.rollTarget, 1e-9)          // clamp(0.5 * 30)
        assertEquals(8.0, r.rollRate, 1e-9)
        assertEquals(1.0 + 3.0 + 4.0, r.pitchTarget, 1e-9) // gamma + aoa + yoke * 8
    }

    @Test fun onGroundPitchOnlyAboveRotateSpeed() {
        val slow = AttitudeTargets.compute(base(flying = false, wow = true, ias = 100.0, yokePitch = 1.0), dt = 0.1)
        assertEquals(0.0, slow.pitchTarget, 1e-9)
        val fast = AttitudeTargets.compute(base(flying = false, wow = true, ias = 150.0, yokePitch = 0.5), dt = 0.1)
        assertEquals(6.0, fast.pitchTarget, 1e-9)       // clamp(0.5 * 12, 0, 12)
    }

    @Test fun autopilotHeadingHoldUsesTheRefRoll() {
        val r = AttitudeTargets.compute(base(ailOn = true, apHdgSw = 0, refRoll = -4.0), dt = 0.1)
        assertEquals(-4.0, r.rollTarget, 1e-9)
        assertEquals(7.0, r.rollRate, 1e-9)            // roll-rate limit with the autopilot on
    }

    @Test fun hdgSelUsesTheHeadingErrorClampedToBankMax() {
        val r = AttitudeTargets.compute(base(ailOn = true, apHdgSw = 2).copy(selHdgErr = 30.0), dt = 0.1)
        assertEquals(25.0, r.rollTarget, 1e-9)         // 30 * 1.2 = 36, clamped to 25
        val turbulent = AttitudeTargets.compute(base(ailOn = true, apHdgSw = 2, turb = true).copy(selHdgErr = 30.0), dt = 0.1)
        assertEquals(8.0, turbulent.rollTarget, 1e-9)  // bank limited to 8 in turbulence
    }

    @Test fun navHeldUsesTheLateralLawAndItsRate() {
        val r = AttitudeTargets.compute(base(ailOn = true, navHeld = true).copy(lateralBank = 12.0, lateralLim = 15.0, lateralRate = 3.0), dt = 0.1)
        assertEquals(12.0, r.rollTarget, 1e-9)
        assertEquals(3.0, r.rollRate, 1e-9)
    }

    @Test fun dynamicElevatorMovesGammaTowardRefPitchMinusAoa() {
        // gamma 0, aoa 3, refPitch 5: target gamma 2, rate 1.5 deg/s over 1 s -> 1.5
        val r = AttitudeTargets.compute(base(dyn = true, elevOn = true, aoa = 3.0, refPitch = 5.0, gamma = 0.0), dt = 1.0)
        assertEquals(1.5, r.gamma, 1e-9)
        assertEquals(1.5 + 3.0, r.pitchTarget, 1e-9)
    }

    @Test fun dynamicYokeCommandsFlightPathRateWhenCwsPitch() {
        val r = AttitudeTargets.compute(base(dyn = true, elevOn = true, cwsP = true, yokePitch = 0.5, gamma = 1.0), dt = 2.0)
        assertEquals(1.0 + 0.5 * 3.0 * 2.0, r.gamma, 1e-9) // 1 + 3
    }

    @Test fun dynamicGammaIsClampedToFifteen() {
        val r = AttitudeTargets.compute(base(dyn = true, elevOn = true, cwsP = true, yokePitch = 1.0, gamma = 14.9), dt = 1.0)
        assertEquals(15.0, r.gamma, 1e-9)
    }
}
