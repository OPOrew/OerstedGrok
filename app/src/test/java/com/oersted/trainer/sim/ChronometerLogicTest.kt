package com.oersted.trainer.sim

import com.oersted.trainer.sim.ChronometerLogic.Flag
import com.oersted.trainer.sim.ChronometerLogic.State
import org.junit.Assert.assertEquals
import org.junit.Test

class ChronometerLogicTest {
    @Test fun handsAtTwelveOclockAreZero() {
        val h = ChronometerLogic.hands(0, 0, 0)
        assertEquals(0.0, h.hourDeg, 0.0)
        assertEquals(0.0, h.minuteDeg, 0.0)
        assertEquals(0.0, h.secondDeg, 0.0)
    }

    @Test fun handsUseTheTwelveHourFaceAndSmoothMinuteAndHourMotion() {
        // 15:30:30 -> hour 3 (90) + 30 min * 0.5 = 105; minute 30*6 + 30*0.1 = 183; second 180
        val h = ChronometerLogic.hands(15, 30, 30)
        assertEquals(105.0, h.hourDeg, 1e-9)
        assertEquals(183.0, h.minuteDeg, 1e-9)
        assertEquals(180.0, h.secondDeg, 1e-9)
    }

    @Test fun elapsedTextIsMinutesAndSecondsWithMinutesModulo100() {
        assertEquals("00:00", ChronometerLogic.elapsedText(0))
        assertEquals("01:05", ChronometerLogic.elapsedText(65))
        assertEquals("00:00", ChronometerLogic.elapsedText(6000))
    }

    @Test fun flagShowsRunningStoppedOrReset() {
        assertEquals(Flag.RUNNING, ChronometerLogic.flag(State(running = true, elapsed = 0)))
        assertEquals(Flag.STOPPED, ChronometerLogic.flag(State(running = false, elapsed = 12)))
        assertEquals(Flag.RESET, ChronometerLogic.flag(State(running = false, elapsed = 0)))
    }

    @Test fun tickCountsOnlyWhileRunning() {
        assertEquals(State(true, 1), ChronometerLogic.tick(State(true, 0)))
        assertEquals(State(false, 5), ChronometerLogic.tick(State(false, 5)))
    }

    @Test fun toggleRunsAndStopsAndResetZeroes() {
        assertEquals(State(true, 7), ChronometerLogic.toggle(State(false, 7)))
        assertEquals(State(false, 7), ChronometerLogic.toggle(State(true, 7)))
        assertEquals(State(false, 0), ChronometerLogic.reset())
    }
}
