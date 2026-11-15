package com.oersted.trainer.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Switch click and light-off rumble: length, level and shape against the audio.ts envelopes. */
class AudioSwitchSoundsTest {
    private val sr = 44100.0

    private fun peak(a: DoubleArray, from: Double, to: Double): Double {
        var p = 0.0
        for (i in (from * sr).toInt() until minOf(a.size, (to * sr).toInt())) p = maxOf(p, Math.abs(a[i]))
        return p
    }

    @Test
    fun clickPitchSpansTheWebRange() {
        assertEquals(0.92, AudioSwitchSounds.clickPitch(0.0), 1e-12)
        assertEquals(1.08, AudioSwitchSounds.clickPitch(1.0), 1e-12)
    }

    @Test
    fun clickHasADownClickThenAQuieterRelease() {
        val c = AudioSwitchSounds.switchClick(sr)
        assertEquals(Math.ceil(0.067 * sr).toInt(), c.size)
        val down = peak(c, 0.0, 0.02)
        val release = peak(c, 0.024, 0.045)
        assertTrue(down > 0.05)
        assertTrue(release > 0.005)
        assertTrue(down > release)
        // it dies away: nothing left at the end
        assertTrue(peak(c, 0.062, 0.067) < 0.01)
    }

    @Test
    fun clickIsRepeatableAndPitchChangesIt() {
        val a = AudioSwitchSounds.switchClick(sr, 1.0, 7L)
        val b = AudioSwitchSounds.switchClick(sr, 1.0, 7L)
        assertTrue(a.contentEquals(b))
        val c = AudioSwitchSounds.switchClick(sr, 1.08, 7L)
        assertTrue(!a.contentEquals(c))
    }

    @Test
    fun lightOffSwellsThenFades() {
        val r = AudioSwitchSounds.lightOff(sr)
        assertEquals((1.2 * sr).toInt(), r.size)
        val start = peak(r, 0.0, 0.01)
        val mid = peak(r, 0.1, 0.25)
        val end = peak(r, 1.15, 1.2)
        assertTrue(start < mid)
        assertTrue(mid > 0.02)
        assertTrue(end < mid * 0.1)
    }
}
