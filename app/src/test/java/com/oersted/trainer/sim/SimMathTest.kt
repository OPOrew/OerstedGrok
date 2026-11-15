package com.oersted.trainer.sim

import org.junit.Assert.assertEquals
import org.junit.Test

class SimMathTest {
    @Test fun approachClampsToTarget() {
        assertEquals(1.0, SimMath.approach(0.0, 1.0, 10.0, 1.0), 1e-9)
        assertEquals(0.0, SimMath.approach(5.0, 0.0, 10.0, 1.0), 1e-9)
        assertEquals(3.0, SimMath.approach(3.0, 3.0, 1.0, 1.0), 1e-9)
    }
    @Test fun spoolReachesTargetAndRespectsRateCap() {
        val r = SimMath.spool(0.0, 100.0, 0.1, SimMath.N1_ACCEL, SimMath.N1_DECEL)
        assertEquals(0.1 * 15.0, r, 1e-9) // capped at max=15 %/s
        assertEquals(50.0, SimMath.spool(49.9999, 50.0, 0.1, SimMath.N1_ACCEL, SimMath.N1_DECEL), 0.0)
    }
}
