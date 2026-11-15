package com.oersted.trainer.sim

import org.junit.Assert.assertEquals
import org.junit.Test

class AttitudeKinematicsTest {
    @Test fun stepMovesAtMostRateTimesDt() {
        assertEquals(2.0, AttitudeKinematics.step(0.0, 10.0, 2.0, 1.0), 1e-9)
        assertEquals(-2.0, AttitudeKinematics.step(0.0, -10.0, 2.0, 1.0), 1e-9)
    }

    @Test fun stepReachesTheTargetWithoutOvershoot() {
        assertEquals(1.5, AttitudeKinematics.step(0.0, 1.5, 2.0, 1.0), 1e-9)
        assertEquals(2.0, AttitudeKinematics.step(0.0, 3.0, 2.0, 1.0), 1e-9)
        assertEquals(5.0, AttitudeKinematics.step(5.0, 5.0, 2.0, 1.0), 1e-9)
    }

    @Test fun pitchRateFollowsTurbulence() {
        assertEquals(1.2, AttitudeKinematics.pitchRate(true), 1e-9)
        assertEquals(2.0, AttitudeKinematics.pitchRate(false), 1e-9)
    }

    @Test fun bankedTurnAt250ktGivesTheStandardRate() {
        // 25 deg bank at 250 kt: 1091 * tan(25) / 250 = 2.035 deg/s, over 1 s
        val a = MipLocal.Attitude(pitch = 0.0, roll = 25.0, hdg = 90.0)
        val r = AttitudeKinematics.integrate(a, pitchTarget = 0.0, rollTarget = 25.0, rollRate = 8.0, tas = 250.0, flying = true, turb = false, dt = 1.0)
        assertEquals(25.0, r.roll, 1e-9)
        assertEquals(92.035, r.hdg, 0.001)
    }

    @Test fun notFlyingKeepsTheHeading() {
        val a = MipLocal.Attitude(pitch = 0.0, roll = 0.0, hdg = 90.0)
        val r = AttitudeKinematics.integrate(a, 0.0, 25.0, 8.0, 250.0, flying = false, turb = false, dt = 1.0)
        assertEquals(90.0, r.hdg, 1e-9)
    }

    @Test fun headingWrapsAcrossNorth() {
        val a = MipLocal.Attitude(pitch = 0.0, roll = 25.0, hdg = 359.0)
        val r = AttitudeKinematics.integrate(a, 0.0, 25.0, 8.0, 250.0, flying = true, turb = false, dt = 1.0)
        assertEquals(1.035, r.hdg, 0.001)
    }
}
