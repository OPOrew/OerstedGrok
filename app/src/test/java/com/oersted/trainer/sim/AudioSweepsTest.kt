package com.oersted.trainer.sim

import com.oersted.trainer.sim.AudioDsp.FilterType
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSweepsTest {
    private val sr = 48000.0

    private fun rmsRange(x: DoubleArray, fromSec: Double, toSec: Double): Double {
        val a = (fromSec * sr).toInt()
        val b = minOf((toSec * sr).toInt(), x.size)
        return AudioDsp.rms(x.copyOfRange(a, b))
    }

    @Test fun retunedBiquadKeepsItsStateAndChangesResponse() {
        val f = AudioDsp.Biquad(FilterType.LOWPASS, sr, 200.0, 0.7)
        repeat(1000) { f.process(1.0) }
        f.retune(FilterType.LOWPASS, sr, 8000.0, 0.7)
        // a 4 kHz-cutoff lowpass lets DC through at unity once settled
        var y = 0.0
        repeat(5000) { y = f.process(1.0) }
        assertTrue(Math.abs(y - 1.0) < 1e-3)
    }

    @Test fun whooshLengthIsDurationPlusTail() {
        val x = AudioSounds.whoosh(sr, 0.05, 1.6, 700.0, 120.0)
        // 1.6 + 0.1 s
        assertTrue(x.size in 81598..81602)
    }

    @Test fun whooshRisesThenFalls() {
        val x = AudioSounds.whoosh(sr, 0.5, 1.0, 1200.0, 300.0, seed = 9L)
        // attack is 30 % of the duration, so the middle is louder than the tail
        assertTrue(rmsRange(x, 0.25, 0.5) > rmsRange(x, 0.9, 1.0))
    }

    @Test fun wheelSpinDownAndTyreChirpAndLockThunkRender() {
        assertTrue(AudioSounds.wheelSpinDown(sr).size > 0)
        assertTrue(AudioDsp.rms(AudioSounds.tyreChirp(sr, 0.5)) > 0.0)
        val lock = AudioSounds.lockThunk(sr, 0.5)
        // the thump (0.294 s) is the longest part; the clunk starts 12 ms in and ends well before that
        assertTrue(lock.size in 14110..14114)
    }
}
