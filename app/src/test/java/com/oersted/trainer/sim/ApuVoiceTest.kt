package com.oersted.trainer.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApuVoiceTest {
    private val sr = 48000.0

    private fun run(v: ApuVoice, seconds: Double, startSec: Double): DoubleArray {
        val out = DoubleArray((seconds * sr).toInt())
        v.render(out, startSec)
        return out
    }

    @Test fun silentWhenTheApuIsOffAndNotStarting() {
        val v = ApuVoice(sr)
        assertFalse(v.update(0.0, starterOn = false, nowSec = 0.0))
        val out = run(v, 1.0, 0.0)
        assertEquals(0.0, AudioDsp.rms(out), 0.0)
    }

    @Test fun whinesWhenRunningAndTheBusIsOn() {
        val v = ApuVoice(sr)
        assertTrue(v.update(80.0, starterOn = false, nowSec = 0.0))
        val out = run(v, 1.0, 0.0)
        assertTrue(AudioDsp.rms(out) > 0.0)
    }

    @Test fun startingIsAudibleEvenAtZeroSpeed() {
        val v = ApuVoice(sr)
        assertTrue(v.update(0.0, starterOn = true, nowSec = 0.0))
        val out = run(v, 1.0, 0.0)
        assertTrue(AudioDsp.rms(out) > 0.0)
    }

    @Test fun louderAtHigherSpeed() {
        val slow = ApuVoice(sr, seed = 2L)
        slow.update(30.0, starterOn = false, nowSec = 0.0)
        val fast = ApuVoice(sr, seed = 2L)
        fast.update(100.0, starterOn = false, nowSec = 0.0)
        val a = run(slow, 2.0, 0.0)
        val b = run(fast, 2.0, 0.0)
        assertTrue(AudioDsp.rms(b) > AudioDsp.rms(a))
    }
}
