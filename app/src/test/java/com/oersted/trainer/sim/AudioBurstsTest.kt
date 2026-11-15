package com.oersted.trainer.sim

import com.oersted.trainer.sim.AudioDsp.FilterType
import com.oersted.trainer.sim.AudioDsp.Wave
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioBurstsTest {
    private val sr = 48000.0

    private fun rmsRange(x: DoubleArray, fromSec: Double, toSec: Double): Double {
        val a = (fromSec * sr).toInt()
        val b = minOf((toSec * sr).toInt(), x.size)
        return AudioDsp.rms(x.copyOfRange(a, b))
    }

    @Test fun toneBurstStaysInsideItsDurationAndDecays() {
        // attack 0.01 + decay 0.2 + 0.05 tail = 0.26 s
        val x = AudioSounds.toneBurst(sr, 0.5, 800.0, 800.0, 0.01, 0.2, Wave.SINE)
        assertTrue(x.size in 12478..12482)
        assertTrue(rmsRange(x, 0.0, 0.05) > rmsRange(x, 0.15, 0.25))
    }

    @Test fun noiseBurstIsLowpassedAndDecays() {
        val x = AudioSounds.noiseBurst(sr, 0.5, 0.003, 0.09, FilterType.LOWPASS, 320.0, 0.7, seed = 3L)
        assertTrue(AudioDsp.rms(x) > 0.0)
        assertTrue(rmsRange(x, 0.0, 0.03) > rmsRange(x, 0.1, 0.14))
    }

    @Test fun thumpIsAKnockThatEndsAfterAbout0_29Seconds() {
        val x = AudioSounds.thump(sr, 0.5)
        // tone 0.004 + 0.24 + 0.05 = 0.294 s is the longer part
        assertTrue(x.size in 14110..14114)
        assertTrue(rmsRange(x, 0.0, 0.02) > rmsRange(x, 0.15, 0.29))
    }

    @Test fun clunkSumsItsPartsAtTheSameStart() {
        val x = AudioSounds.clunk(sr, 0.5, seed = 5L)
        // longest part is the 0.002 + 0.09 + 0.05 = 0.142 s body tone
        assertTrue(x.size in 6814..6818)
        assertTrue(AudioDsp.rms(x) > 0.0)
    }
}
