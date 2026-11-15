package com.oersted.trainer.sim

import com.oersted.trainer.sim.AdfBearing.NavPos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdfBearingTest {
    private val ba = NavPos(16.52 - 20.0 / 60, 121.25) // 20 NM south of BA (BA range is 25 NM), so the bearing to BA is due north

    @Test fun unpoweredOrFailedGivesNothing() {
        assertTrue(AdfBearing.computeAdfBearings(0.0, false, false, ba, 339.0, 272.0).brg1 == null)
        assertTrue(AdfBearing.computeAdfBearings(0.0, true, true, ba, 339.0, 272.0).brg1 == null)
    }

    @Test fun testModeGivesFixedRelativeBearing() {
        val r = AdfBearing.computeAdfBearings(90.0, true, false, ba, 339.0, 272.0, mode1 = 3, mode2 = 0)
        assertEquals(45.0, r.brg1!!, 1e-9)
        assertTrue(r.brg2 == null)
    }

    @Test fun adfModeGivesRelativeBearingToNdb() {
        // Heading 0 (north), station due north: relative bearing should be near 0.
        val r = AdfBearing.computeAdfBearings(0.0, true, false, ba, 339.0, 272.0, mode1 = 2, mode2 = 0)
        assertTrue(r.brg1 != null)
        assertTrue(Math.abs(r.brg1!!) < 1.0)
        assertTrue(r.dist1Nm != null && r.dist1Nm!! > 19.5 && r.dist1Nm!! < 20.5)
    }

    @Test fun outOfRangeGivesNoBearing() {
        val far = NavPos(0.0, 100.0)
        val r = AdfBearing.computeAdfBearings(0.0, true, false, far, 339.0, 272.0, mode1 = 2, mode2 = 0)
        assertTrue(r.brg1 == null)
    }

    @Test fun missingPositionGivesNoBearing() {
        val r = AdfBearing.computeAdfBearings(0.0, true, false, null, 339.0, 272.0, mode1 = 2, mode2 = 0)
        assertTrue(r.brg1 == null)
    }

    @Test fun unknownFrequencyGivesNoBearing() {
        val r = AdfBearing.computeAdfBearings(0.0, true, false, ba, 1000.0, 272.0, mode1 = 2, mode2 = 0)
        assertTrue(r.brg1 == null)
    }

    @Test fun navaidLookupAndDistance() {
        assertTrue(findChangiNavaid(110.9, "ILS")?.ident == "ICW")
        assertTrue(findChangiNavaid(339.0, "NDB")?.ident == "BA")
        assertTrue(findChangiNavaid(245.0, "NDB") == null) // legacy NDB is unavailable
        assertEquals(60.04, distNm(0.0, 0.0, 1.0, 0.0), 0.01)
        assertEquals(90.0, bearingTrue(0.0, 0.0, 0.0, 1.0), 1e-6)
    }
}
