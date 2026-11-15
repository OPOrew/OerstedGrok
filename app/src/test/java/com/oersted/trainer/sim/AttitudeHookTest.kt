package com.oersted.trainer.sim

import com.oersted.trainer.sim.AttitudeHook.State
import com.oersted.trainer.sim.AttitudeTargets.Inputs as TargetsIn
import com.oersted.trainer.sim.FlightDirector.Inputs as FdIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttitudeHookTest {
    private fun targets() = TargetsIn(
        flying = true, wow = false, ias = 250.0, turb = false, yokePitch = 0.0, yokeRoll = 0.0,
        gamma = 0.0, aoa = 0.0, ailOn = false, cwsR = false, navHeld = false, lateralBank = 0.0,
        lateralLim = 25.0, lateralRate = 4.0, planLat = false, planHdgErr = 0.0, apHdgSw = 1, selHdgErr = 0.0,
        hdgHold = false, refHdgErr = 0.0, refRoll = 0.0, dyn = false, elevOn = false, cwsP = false,
        gsCapAp = false, altSel = false, refPitch = 0.0, capturedGamma = 0.0,
    )

    private fun fd() = FdIn(
        power = false, fdMode = 2, fdUp = true, hdgBad = false, vorValid = false, locValid = false, captIsVor = false,
        lateralDev = 0.0, selCrs = 0.0, selHdg = 90.0, hdg = 90.0, roll = 0.0, pitch = 0.0, aoa = 0.0, flying = true,
        altFt = 10000.0, gsCapFd = false, gsErr = 0.0, altFd = 10000.0, fdAlt = false, fdPitch = 0.0,
    )

    private fun inputs(t: TargetsIn = targets()) = AttitudeHook.Inputs(dt = 1.0, tas = 250.0, simAltFt = 10000.0, targets = t, fd = fd())

    @Test fun levelFlightWithAutopilotOffHoldsAttitude() {
        val r = AttitudeHook.tick(State(MipLocal.Attitude(0.0, 0.0, 90.0), 0.0), inputs())
        assertEquals(0.0, r.attitude.pitch, 0.0)
        assertEquals(0.0, r.attitude.roll, 0.0)
        assertEquals(90.0, r.attitude.hdg, 0.0)
        assertFalse(r.writeAttitude)
        assertEquals(10000.0, r.navAltitudeFt, 0.0)
        assertTrue(r.psFlightVsFpm == null)
        assertFalse(r.fdCommand.show)
    }

    @Test fun yokeRollRateLimitsBankAndTurnsHeading() {
        val r = AttitudeHook.tick(
            State(MipLocal.Attitude(0.0, 0.0, 90.0), 0.0),
            inputs(targets().copy(yokeRoll = 0.5)),
        )
        // target bank 15 deg, rate 8 deg/s over 1 s -> 8 deg
        assertEquals(8.0, r.attitude.roll, 1e-9)
        val turn = 1091.0 * Math.tan(Math.toRadians(8.0)) / 250.0
        assertEquals(90.0 + turn, r.attitude.hdg, 1e-9)
        assertTrue(r.writeAttitude)
    }

    @Test fun dynamicClimbFollowsFlightPathAndVerticalSpeed() {
        val t = targets().copy(dyn = true, elevOn = true, refPitch = 3.0, aoa = 0.0)
        val r = AttitudeHook.tick(State(MipLocal.Attitude(0.0, 0.0, 90.0), 0.0), inputs(t))
        // gamma steps 1.5 deg/s toward refPitch - aoa = 3 deg over 1 s
        assertEquals(1.5, r.state.gamma, 1e-9)
        assertEquals(1.5, r.attitude.pitch, 1e-9)
        // vsNow = sin(1.5 deg) * 250 * 1.688 * 60 = 662.8 fpm -> sim field 663
        assertEquals(663.0, r.psFlightVsFpm!!, 0.0)
        assertEquals(10000.0 + 662.80 / 60.0, r.navAltitudeFt, 0.05)
    }
}
