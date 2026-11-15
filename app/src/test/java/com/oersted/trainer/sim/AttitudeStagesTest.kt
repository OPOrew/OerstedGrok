package com.oersted.trainer.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure helpers of RadioStage and AutopilotStage (the web useAttitude steps). */
class AttitudeStagesTest {
    @Test fun navTransferForcesNavOneOrTwoOtherwiseKeepsSelector() {
        assertEquals(0, RadioStage.effectiveNavSel("BOTH ON 1", 2))
        assertEquals(2, RadioStage.effectiveNavSel("BOTH ON 2", 1))
        assertEquals(1, RadioStage.effectiveNavSel("NORMAL", 1))
    }

    @Test fun efiTransferSelectsOneSideOrNone() {
        assertEquals(0, RadioStage.efiSide("BOTH ON L"))
        assertEquals(2, RadioStage.efiSide("BOTH ON R"))
        assertNull(RadioStage.efiSide("NORMAL"))
    }

    @Test fun ilsTestFrequencyNeedsOddHundredthsInTheIlsBand() {
        assertTrue(RadioStage.isIlsTestFrequency(108.11))
        assertFalse(RadioStage.isIlsTestFrequency(108.10))
        assertTrue(RadioStage.isIlsTestFrequency(109.95))
        assertFalse(RadioStage.isIlsTestFrequency(107.11))
    }

    @Test fun trueAirspeedRisesWithAltitude() {
        assertEquals(100.0, AutopilotStage.trueAirspeed(100.0, 0.0), 1e-9)
        assertEquals(116.0, AutopilotStage.trueAirspeed(100.0, 10000.0), 1e-9)
    }
}
