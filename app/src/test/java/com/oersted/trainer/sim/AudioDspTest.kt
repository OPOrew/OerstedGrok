package com.oersted.trainer.sim

import com.oersted.trainer.sim.AudioDsp.Envelope
import com.oersted.trainer.sim.AudioDsp.FilterType
import com.oersted.trainer.sim.AudioDsp.Wave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDspTest {
    private val sr = 48000.0

    @Test fun lowpassPassesDcAndHighpassBlocksIt() {
        val lp = AudioDsp.Biquad(FilterType.LOWPASS, sr, 1000.0, 0.7)
        val hp = AudioDsp.Biquad(FilterType.HIGHPASS, sr, 1000.0, 0.7)
        var yl = 0.0
        var yh = 0.0
        repeat(20000) { yl = lp.process(1.0); yh = hp.process(1.0) }
        assertEquals(1.0, yl, 1e-6)
        assertEquals(0.0, yh, 1e-6)
    }

    @Test fun bandpassPassesItsCentreFrequencyAtUnityGain() {
        val bp = AudioDsp.Biquad(FilterType.BANDPASS, sr, 1200.0, 2.0)
        val osc = AudioDsp.Oscillator(Wave.SINE, sr)
        val input = DoubleArray(96000)
        val out = DoubleArray(96000)
        for (i in input.indices) {
            input[i] = osc.next(1200.0)
            out[i] = bp.process(input[i])
        }
        val tail = 48000 until 96000
        val inRms = AudioDsp.rms(input.copyOfRange(tail.first, tail.last + 1))
        val outRms = AudioDsp.rms(out.copyOfRange(tail.first, tail.last + 1))
        assertEquals(inRms, outRms, inRms * 0.03)
    }

    @Test fun sineStartsAtZeroAndSquareAtPlusOne() {
        assertEquals(0.0, AudioDsp.Oscillator(Wave.SINE, sr).next(100.0), 1e-9)
        assertEquals(1.0, AudioDsp.Oscillator(Wave.SQUARE, sr).next(100.0), 0.0)
        assertEquals(-1.0, AudioDsp.Oscillator(Wave.SAWTOOTH, sr).next(100.0), 0.0)
        assertEquals(-1.0, AudioDsp.Oscillator(Wave.TRIANGLE, sr).next(100.0), 0.0)
    }

    @Test fun sineFrequencyGivesTheRightNumberOfCycles() {
        val osc = AudioDsp.Oscillator(Wave.SINE, sr)
        var prev = 0.0
        var upward = 0
        for (i in 0 until 4800) {
            val v = osc.next(100.0)
            if (prev < 0 && v >= 0) upward++
            prev = v
        }
        // 100 Hz over 0.1 s is 10 cycles
        assertTrue(upward in 9..10)
    }

    @Test fun noiseIsRepeatableBoundedAndCentred() {
        val a = AudioDsp.Noise(7L)
        val b = AudioDsp.Noise(7L)
        var sum = 0.0
        repeat(10000) {
            val x = a.next()
            assertEquals(x, b.next(), 0.0)
            assertTrue(x >= -1.0 && x <= 1.0)
            sum += x
        }
        assertTrue(kotlin.math.abs(sum / 10000) < 0.05)
    }

    @Test fun envelopeRampsLinearlyAndHoldsItsEnds() {
        val e = Envelope(listOf(0.0 to 0.0, 0.1 to 1.0, 0.5 to 0.0))
        assertEquals(0.5, e.valueAt(0.05), 1e-9)
        assertEquals(1.0, e.valueAt(0.1), 1e-9)
        assertEquals(0.5, e.valueAt(0.3), 1e-9)
        assertEquals(0.0, e.valueAt(1.0), 1e-9)
        assertEquals(0.0, e.valueAt(-1.0), 1e-9)
    }
}
