package com.oersted.trainer.sim

import com.oersted.trainer.sim.AutoFlight.AfPlant
import com.oersted.trainer.sim.AutoFlight.GsIn
import com.oersted.trainer.sim.AutoFlight.GsPhase
import com.oersted.trainer.sim.AutoFlight.Light
import com.oersted.trainer.sim.AutoFlight.LatIn
import com.oersted.trainer.sim.AutoFlight.LatSub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoFlightTest {
    private val allOk = AfPlant(pw = true, hydB = true, attOk = true, hdgOk = true)

    @Test fun angDiffWrapsBothWays() {
        assertEquals(20.0, AutoFlight.angDiff(10.0, 350.0), 1e-9)
        assertEquals(-20.0, AutoFlight.angDiff(350.0, 10.0), 1e-9)
    }

    @Test fun interlocksNeedAllFourAndReportFirstFailure() {
        assertTrue(AutoFlight.afInterlocks(allOk))
        assertTrue(AutoFlight.afDisconnectReason(allOk) == null)
        val noHyd = allOk.copy(hydB = false)
        assertTrue(!AutoFlight.afInterlocks(noHyd))
        assertTrue(AutoFlight.afDisconnectReason(noHyd) == "hydraulic system B pressure low")
        assertTrue(AutoFlight.afDisconnectReason(allOk.copy(pw = false, attOk = false)) == "DC power lost")
    }

    @Test fun stepLateralCapturesThenReachesOnCourse() {
        val near = LatIn(active = true, kind = "LOC", dev = 0.5, bank = 0.0, crsErr = 0.0, overStation = false)
        assertTrue(AutoFlight.stepLateral(LatSub.OFF, near) == LatSub.CAPTURE)
        val far = near.copy(dev = 2.0)
        assertTrue(AutoFlight.stepLateral(LatSub.OFF, far) == LatSub.INTERCEPT)
        assertTrue(AutoFlight.stepLateral(LatSub.CAPTURE, near.copy(bank = 2.0, crsErr = 5.0)) == LatSub.ON_COURSE)
        assertTrue(AutoFlight.stepLateral(LatSub.CAPTURE, near.copy(bank = 10.0, crsErr = 5.0)) == LatSub.CAPTURE)
        assertTrue(AutoFlight.stepLateral(LatSub.ON_COURSE, near.copy(active = false)) == LatSub.OFF)
    }

    @Test fun vorOverStationSubmode() {
        val vor = LatIn(active = true, kind = "VOR", dev = 0.0, bank = 0.0, crsErr = 0.0, overStation = true)
        assertTrue(AutoFlight.stepLateral(LatSub.ON_COURSE, vor) == LatSub.OVER_STATION)
        assertTrue(AutoFlight.stepLateral(LatSub.ON_COURSE, vor.copy(overStation = false)) == LatSub.ON_COURSE)
    }

    @Test fun zoneOfConfusionHasMinimumRadius() {
        assertTrue(AutoFlight.inZoneOfConfusion(0.1, 0.0))
        assertTrue(AutoFlight.inZoneOfConfusion(1.0, 6076.12))
        assertTrue(!AutoFlight.inZoneOfConfusion(2.0, 6076.12))
    }

    @Test fun onCourseBankIsCappedAtEightDegrees() {
        val cmd = AutoFlight.lateralLaw(LatSub.ON_COURSE, "LOC", 0.0, 0.0, 0.0, 0.0)
        assertEquals(8.0, cmd.lim, 1e-9)
        assertTrue(cmd.bank <= 8.0 && cmd.bank >= -8.0)
    }

    @Test fun glideStepCapturesThenTracks() {
        val i = GsIn(want = true, manual = true, locCaptured = false, gsErr = 0.0)
        val s0 = AutoFlight.stepGlide(GsPhase.OFF, 0.0, i, 0.1)
        assertTrue(s0.phase == GsPhase.CAPTURE)
        val s1 = AutoFlight.stepGlide(GsPhase.CAPTURE, 8.0, i, 4.0)
        assertTrue(s1.phase == GsPhase.TRACK)
        assertEquals(12.0, s1.capT, 1e-9)
        assertTrue(AutoFlight.stepGlide(GsPhase.TRACK, 0.0, i.copy(want = false), 1.0).phase == GsPhase.OFF)
    }

    @Test fun glideSlopeCommandIsThreeDegreesOnBeam() {
        assertEquals(-3.0, AutoFlight.gsGamma(0.0), 1e-9)
    }

    @Test fun glideGainEasesOnAndFloorsLow() {
        assertEquals(0.25, AutoFlight.glideGain(20.0, 0.0), 1e-9)
        assertEquals(1.0, AutoFlight.glideGain(20.0, 9999.0), 1e-9)
        assertEquals(0.0, AutoFlight.glideGain(10.0, 9999.0), 1e-9)
    }

    @Test fun altAcquireHoldsInsideBand() {
        val hold = AutoFlight.altAcquire(0.0, 250.0)
        assertEquals(0.0, hold.vsFpm, 1e-9)
        assertTrue(hold.phase == "HOLD")
        val acq = AutoFlight.altAcquire(1000.0, 250.0)
        assertEquals(1500.0, acq.vsFpm, 1e-9)
        assertTrue(acq.phase == "ACQ")
        assertEquals(1800.0, AutoFlight.altAcquire(5000.0, 250.0).vsFpm, 1e-9)
    }

    @Test fun nextLightLatchesGreen() {
        assertTrue(AutoFlight.nextLight(Light.OFF, selected = true, captured = false) == Light.AMBER)
        assertTrue(AutoFlight.nextLight(Light.AMBER, selected = true, captured = true) == Light.GREEN)
        assertTrue(AutoFlight.nextLight(Light.GREEN, selected = true, captured = false) == Light.GREEN)
        assertTrue(AutoFlight.nextLight(Light.GREEN, selected = false, captured = true) == Light.OFF)
    }

    @Test fun goAroundOnlyWhileFlying() {
        assertTrue(AutoFlight.goAroundSelected(0, flying = true))
        assertTrue(!AutoFlight.goAroundSelected(0, flying = false))
        assertTrue(!AutoFlight.goAroundSelected(2, flying = true))
    }
}
