package com.oersted.trainer.sim

import com.oersted.trainer.sim.AutopilotModes.Inputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutopilotModesTest {
    private fun base() = Inputs(
        interlocksOk = true, apAil = true, apElev = true, apNav = 0, apPitch = 1, apHdgSw = 1, fdMode = 2, fdAlt = false,
        flying = true, altFail = false, attBad = false, fdFail = false, power = true, yokeRoll = 0.0, yokePitch = 0.0,
        captIsVor = false, vorValid = false, locValid = false, gsValid = false, gsCapAp = false, gsErr = 0.0, gsCapFd = false,
    )

    @Test fun axesEngageOnlyWithInterlocks() {
        val r = AutopilotModes.evaluate(base())
        assertTrue(r.ailOn && r.elevOn)
        val lost = AutopilotModes.evaluate(base().copy(interlocksOk = false))
        assertFalse(lost.ailOn || lost.elevOn)
        assertTrue(lost.disconnect)
        assertFalse(lost.apAil)
        assertFalse(lost.apElev)
    }

    @Test fun turbulenceNeedsElevatorAndTurbSelection() {
        assertTrue(AutopilotModes.evaluate(base().copy(apPitch = 0)).turb)
        assertFalse(AutopilotModes.evaluate(base().copy(apPitch = 1)).turb)
    }

    @Test fun cwsDetentIsEightHundredthsOfAYokeUnit() {
        assertTrue(AutopilotModes.evaluate(base().copy(yokeRoll = 0.2)).cwsR)
        assertFalse(AutopilotModes.evaluate(base().copy(yokeRoll = 0.05)).cwsR)
    }

    @Test fun navDropsToManWhenBeamIsLost() {
        // NAV 2 (LOC) with no localizer: not held, so the NAV knob returns to MAN (0)
        val r = AutopilotModes.evaluate(base().copy(apNav = 2, locValid = false))
        assertFalse(r.navHeld)
        assertEquals(0, r.apNav)
    }

    @Test fun navHeldWithAValidLocalizer() {
        val r = AutopilotModes.evaluate(base().copy(apNav = 2, locValid = true))
        assertTrue(r.navHeld)
        assertEquals(2, r.apNav)
    }

    @Test fun goAroundDropsNavHeld() {
        val r = AutopilotModes.evaluate(base().copy(apNav = 2, locValid = true, fdMode = 0, flying = true))
        assertTrue(r.ga)
        assertFalse(r.navHeld)
    }

    @Test fun hdgSelFallsBackToHoldWhenAileronsDisengage() {
        val r = AutopilotModes.evaluate(base().copy(apAil = false, apHdgSw = 2))
        assertEquals(1, r.apHdgSw)
    }

    @Test fun altHoldReleasesOnGlideSlopeCapture() {
        val r = AutopilotModes.evaluate(base().copy(apPitch = 2, gsCapAp = true))
        assertEquals(1, r.apPitch)
        val held = AutopilotModes.evaluate(base().copy(apPitch = 2, gsCapAp = false))
        assertEquals(2, held.apPitch)
    }

    @Test fun fdAltitudeHoldClearsWhenFlightDirectorIsDown() {
        val r = AutopilotModes.evaluate(base().copy(fdAlt = true, fdFail = true))
        assertFalse(r.fdAlt)
    }

    @Test fun fdGlideCaptureNeedsAValidGlideSignal() {
        val r = AutopilotModes.evaluate(base().copy(fdMode = 5, gsValid = true, gsErr = 0.0, fdAlt = true))
        assertTrue(r.gsCapFd)
        assertFalse(r.fdAlt)
        val none = AutopilotModes.evaluate(base().copy(fdMode = 5, gsValid = false))
        assertFalse(none.gsCapFd)
    }

    @Test fun fdCaptureOfMode4NeedsCloseGlideError() {
        val far = AutopilotModes.evaluate(base().copy(fdMode = 4, gsValid = true, gsErr = 0.5))
        assertFalse(far.gsCapFd)
        val near = AutopilotModes.evaluate(base().copy(fdMode = 4, gsValid = true, gsErr = 0.1))
        assertTrue(near.gsCapFd)
    }
}
