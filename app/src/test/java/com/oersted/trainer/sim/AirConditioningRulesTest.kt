package com.oersted.trainer.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AirConditioningRulesTest {
    private val rules = AirConditioningRules

    @Test fun packsAndTripResetsAreIndependent() {
        val s = initialSimState()
        assertTrue(rules.togglePack(s, left = true).packL)
        assertFalse(rules.togglePack(s, left = true).packR)
        val tripped = initialSimState().apply { packTripL = true; packTripR = true }
        assertFalse(rules.resetPackTrip(tripped, left = true).packTripL)
        assertTrue(rules.resetPackTrip(tripped, left = true).packTripR)
    }

    @Test fun missingTempModeReadsAsAuto() {
        val s = initialSimState()
        s.tempModeL = null
        assertEquals("AUTO", rules.tempMode(s, left = true))
        assertTrue(rules.selectorEnabled(s, left = true))
        assertFalse(rules.mixEnabled(s, left = true))
    }

    @Test fun manualCoolHighlightsForManual() {
        assertTrue(rules.modeButtonLit("MANUAL", "MANUAL_COOL"))
        assertFalse(rules.modeButtonLit("MANUAL", "MANUAL_WARM"))
    }

    @Test fun movingTheMixPutsTheSelectorInManual() {
        val s = initialSimState()
        s.tempModeR = "MANUAL_WARM"
        assertTrue(rules.mixEnabled(s, left = false))
        val m = rules.setMix(s, left = false, value = 0.7)
        assertEquals(0.7, m.mixPass, 0.0)
        assertEquals("MANUAL", m.tempModeR)
    }

    @Test fun selectorValueAndFaultSet() {
        val s = initialSimState()
        assertEquals(72.0, rules.setTempSelector(s, left = true, valueF = 72.0).tempSelectL!!, 0.0)
        assertEquals("STUCK_COLD", rules.setTempControlFault(s, left = false, value = "STUCK_COLD").tempControlFaultR)
    }

    @Test fun outflowStepsAndClamps() {
        val s = initialSimState()
        s.pressMode = "MAN_AC"
        s.outflow = 0.95
        assertEquals(1.0, rules.stepOutflow(s, open = true).outflow, 0.0)
        s.outflow = 0.05
        assertEquals(0.0, rules.stepOutflow(s, open = false).outflow, 0.0)
        s.pressMode = "AUTO"
        assertTrue(rules.stepOutflow(s, open = true) === s)
    }

    @Test fun pressModesSet() {
        assertEquals("MAN_DC", rules.setPressMode(initialSimState(), "MAN_DC").pressMode)
        assertEquals(4, rules.PRESS_MODES.size)
        assertEquals(4, rules.TEMP_MODES.size)
    }
}
