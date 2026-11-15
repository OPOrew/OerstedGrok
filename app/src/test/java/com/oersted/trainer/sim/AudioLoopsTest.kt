package com.oersted.trainer.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioLoopsTest {
    private val sr = 48000.0

    private fun peak(x: DoubleArray): Double = x.maxOf { Math.abs(it) }

    @Test fun shakerLoopIsOneSecondPeakNormalised() {
        val x = AudioSounds.shakerLoop(sr, 17, seed = 2L)
        assertEquals(48000, x.size)
        assertEquals(1.0, peak(x), 1e-9)
        assertTrue(AudioDsp.rms(x) > 0.0)
    }

    @Test fun clackLoopIsOneSecondPeakNormalised() {
        val x = AudioSounds.clackLoop(sr, 8, seed = 3L)
        assertEquals(48000, x.size)
        assertEquals(1.0, peak(x), 1e-9)
    }

    @Test fun loopsAreRepeatableForAGivenSeed() {
        val a = AudioSounds.clackLoop(sr, 8, seed = 4L)
        val b = AudioSounds.clackLoop(sr, 8, seed = 4L)
        for (i in a.indices) assertEquals(a[i], b[i], 0.0)
    }
}
