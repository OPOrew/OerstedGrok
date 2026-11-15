package com.oersted.trainer.sim

import org.junit.Assert.assertEquals
import org.junit.Test

class DialGeometryTest {
    @Test fun radioAltimeterPassesItsKnownPoints() {
        assertEquals(0.0, DialGeometry.raAngleDeg(0.0), 1e-9)
        assertEquals(236.0, DialGeometry.raAngleDeg(10.0), 1e-9)
        assertEquals(328.0, DialGeometry.raAngleDeg(25.0), 1e-9)
    }

    @Test fun radioAltimeterInterpolatesBetweenPoints() {
        // halfway between 10 (236) and 15 (268)
        assertEquals(252.0, DialGeometry.raAngleDeg(12.5), 1e-9)
    }

    @Test fun radioAltimeterClampsOutOfRange() {
        assertEquals(0.0, DialGeometry.raAngleDeg(-3.0), 1e-9)
        assertEquals(328.0, DialGeometry.raAngleDeg(40.0), 1e-9)
    }

    @Test fun flapsUpIsTheStartAndFortyIsTheEnd() {
        assertEquals(250.0, DialGeometry.flapAngleDeg(0.0), 1e-9)
        assertEquals(192.0, DialGeometry.flapAngleDeg(40.0), 1e-9)
    }

    @Test fun flapsWrapThroughNorthOnTheWayToTheFirstSetting() {
        // from UP (250) to 1 (292): no wrap, midpoint 1.5 -> 271
        assertEquals(271.0, DialGeometry.flapAngleDeg(0.5), 1e-9)
        // from 2 (336) to 5 (20) the scale crosses north (20 becomes 380): midpoint 3.5 -> 336 + 0.5 * 44 = 358
        assertEquals(358.0, DialGeometry.flapAngleDeg(3.5), 1e-9)
    }
}
