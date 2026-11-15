package com.oersted.trainer.sim

import com.oersted.trainer.sim.DmeModel.DmeChannelState
import com.oersted.trainer.sim.DmeModel.IDLE_DME
import com.oersted.trainer.sim.DmeModel.stepDmeChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DmeModelTest {
    @Test fun standbyOrUnpoweredIsBlank() {
        val s = stepDmeChannel(IDLE_DME, DmeModel.MODE_STBY, false, true, 50.0, 0.0, 0.5)
        assertTrue(s.displayNm == null && !s.locked && !s.searching)
        val u = stepDmeChannel(IDLE_DME, DmeModel.MODE_NORM, false, false, 50.0, 0.0, 0.5)
        assertTrue(u.displayNm == null)
    }

    @Test fun testModeShowsZeroLocked() {
        val s = stepDmeChannel(IDLE_DME, DmeModel.MODE_NORM, true, true, null, 0.0, 0.5)
        assertEquals(0.0, s.displayNm!!, 1e-9)
        assertTrue(s.locked)
    }

    @Test fun slantRangeIncludesHeight() {
        assertEquals(5.0, DmeModel.slantRangeNm(3.0, 4 * 6076.12), 1e-9)
    }

    @Test fun searchesThenLocksAndShowsRange() {
        var s = IDLE_DME
        // 50 NM: search time is 1.2 + 2.8 * 50/150 = 2.133 s
        s = stepDmeChannel(s, DmeModel.MODE_NORM, false, true, 50.0, 0.0, 0.1)
        assertTrue(s.searching && s.displayNm == null)
        repeat(5) { s = stepDmeChannel(s, DmeModel.MODE_NORM, false, true, 50.0, 0.0, 0.5) }
        assertTrue(s.locked)
        assertEquals(50.0, s.displayNm!!, 0.05)
    }

    @Test fun outOfRangeStartsSearch() {
        val s = stepDmeChannel(IDLE_DME, DmeModel.MODE_NORM, false, true, 250.0, 0.0, 0.5)
        assertTrue(s.searching && s.displayNm == null)
    }

    @Test fun overrideReachesFurtherThanNormal() {
        val prev = DmeChannelState(250.0, searching = false, locked = true, lockT = 2.0, memoryT = 0.0, lagNm = 250.0)
        val ride = stepDmeChannel(prev, DmeModel.MODE_ORIDE, false, true, 250.0, 0.0, 0.1)
        assertTrue(ride.locked)
        assertEquals(250.0, ride.displayNm!!, 0.05)
        val norm = stepDmeChannel(prev, DmeModel.MODE_NORM, false, true, 250.0, 0.0, 0.1)
        assertTrue(!norm.locked)
        assertTrue(norm.memoryT > 11.8 && norm.memoryT < 12.0)
    }

    @Test fun memoryHoldsThenExpires() {
        val locked = DmeChannelState(40.0, searching = false, locked = true, lockT = 2.0, memoryT = 0.0, lagNm = 40.0)
        val held = stepDmeChannel(locked, DmeModel.MODE_NORM, false, true, null, 0.0, 1.0)
        assertEquals(40.0, held.displayNm!!, 1e-9)
        assertEquals(11.0, held.memoryT, 1e-9)
        var s = held
        repeat(12) { s = stepDmeChannel(s, DmeModel.MODE_NORM, false, true, null, 0.0, 1.0) }
        assertTrue(s.displayNm == null && s.searching)
    }
}
