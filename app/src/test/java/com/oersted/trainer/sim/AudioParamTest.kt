package com.oersted.trainer.sim

import com.oersted.trainer.sim.AudioDsp.TargetParam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioParamTest {
    @Test fun setTargetApproachesWithTheTimeConstant() {
        val p = TargetParam(0.0)
        p.setTargetAtTime(1.0, 0.0, 0.1)
        // one time constant: 1 - e^-1
        assertEquals(1.0 - Math.exp(-1.0), p.valueAt(0.1), 1e-9)
        assertEquals(0.0, p.valueAt(0.0), 0.0)
        assertEquals(1.0, p.valueAt(10.0), 1e-6)
    }

    @Test fun retargetingStartsFromTheValueReachedSoFar() {
        val p = TargetParam(0.0)
        p.setTargetAtTime(1.0, 0.0, 0.1)
        val atSecondEvent = p.valueAt(0.2)
        p.setTargetAtTime(0.0, 0.2, 0.1)
        assertEquals(atSecondEvent, p.valueAt(0.2), 1e-12)
        assertEquals(atSecondEvent * Math.exp(-1.0), p.valueAt(0.3), 1e-9)
    }

    @Test fun setValueHoldsUntilTheNextEvent() {
        val p = TargetParam(0.0)
        p.setValueAtTime(0.5, 1.0)
        assertEquals(0.0, p.valueAt(0.5), 0.0)
        assertEquals(0.5, p.valueAt(2.0), 0.0)
    }

    @Test fun manyLiveUpdatesDoNotGrowOrRecurse() {
        val p = TargetParam(0.0)
        var t = 0.0
        repeat(10000) { i ->
            p.setTargetAtTime(if (i % 2 == 0) 1.0 else 0.0, t, 0.05)
            t += 0.1
        }
        val v = p.valueAt(t)
        assertTrue(v >= 0.0 && v <= 1.0)
    }
}
