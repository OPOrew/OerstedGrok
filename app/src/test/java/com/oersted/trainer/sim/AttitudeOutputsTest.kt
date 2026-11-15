package com.oersted.trainer.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttitudeOutputsTest {
    @Test fun notDynamicLeavesAltitudeAndWritesNoVerticalSpeed() {
        val r = AttitudeOutputs.altitude(dyn = false, gammaDeg = 3.0, tasKt = 250.0, simAltFt = 10000.0, dt = 1.0)
        assertEquals(10000.0, r.navAltitudeFt, 0.0)
        assertTrue(r.psFlightVsFpm == null)
    }

    @Test fun levelFlightHoldsAltitudeAndRoundsZeroVerticalSpeed() {
        val r = AttitudeOutputs.altitude(dyn = true, gammaDeg = 0.0, tasKt = 250.0, simAltFt = 10000.0, dt = 1.0)
        assertEquals(10000.0, r.navAltitudeFt, 0.0)
        assertEquals(0.0, r.psFlightVsFpm!!, 0.0)
    }

    @Test fun climbAddsVerticalSpeedOverDt() {
        // vsNow = sin(3 deg) * 250 kt * 1.688 * 60 = 1325.15 fpm, rounded to 1325 for the sim field
        val r = AttitudeOutputs.altitude(dyn = true, gammaDeg = 3.0, tasKt = 250.0, simAltFt = 10000.0, dt = 1.0)
        assertEquals(1325.0, r.psFlightVsFpm!!, 0.0)
        assertEquals(10000.0 + 1325.15 / 60.0, r.navAltitudeFt, 0.05)
    }

    @Test fun altitudeNeverGoesBelowZero() {
        val r = AttitudeOutputs.altitude(dyn = true, gammaDeg = -10.0, tasKt = 250.0, simAltFt = 0.0, dt = 1.0)
        assertEquals(0.0, r.navAltitudeFt, 0.0)
    }

    @Test fun smallAttitudeChangeIsNotWritten() {
        val prev = MipLocal.Attitude(pitch = 2.0, roll = -1.0, hdg = 90.0)
        assertFalse(AttitudeOutputs.shouldWriteAttitude(prev, MipLocal.Attitude(2.01, -1.01, 90.01)))
    }

    @Test fun anyAxisAtThresholdIsWritten() {
        val prev = MipLocal.Attitude(pitch = 2.0, roll = -1.0, hdg = 90.0)
        assertTrue(AttitudeOutputs.shouldWriteAttitude(prev, MipLocal.Attitude(2.03, -1.0, 90.0)))
        assertTrue(AttitudeOutputs.shouldWriteAttitude(prev, MipLocal.Attitude(2.0, -1.0, 90.5)))
    }

    @Test fun headingGateComparesWithoutWrapping() {
        // matches the web: |hdg - a.hdg| is not wrapped, so a 359.99 -> 0.01 change is written
        val prev = MipLocal.Attitude(pitch = 0.0, roll = 0.0, hdg = 359.99)
        assertTrue(AttitudeOutputs.shouldWriteAttitude(prev, MipLocal.Attitude(0.0, 0.0, 0.01)))
    }
}
