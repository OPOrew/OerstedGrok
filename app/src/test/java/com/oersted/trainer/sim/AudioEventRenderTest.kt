package com.oersted.trainer.sim

import com.oersted.trainer.sim.AudioDsp.FilterType
import com.oersted.trainer.sim.AudioEvents.Ev
import com.oersted.trainer.sim.AudioEvents.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Event to sample-buffer dispatch. */
class AudioEventRenderTest {
    private val sr = 22050.0

    @Test
    fun filterNamesMapToBiquadTypes() {
        assertEquals(FilterType.LOWPASS, AudioEventRender.filterType("lowpass"))
        assertEquals(FilterType.HIGHPASS, AudioEventRender.filterType("highpass"))
        assertEquals(FilterType.BANDPASS, AudioEventRender.filterType("bandpass"))
    }

    @Test
    fun everyKindRendersSomethingAndKeepsPanAndDelay() {
        val evs = listOf(
            Ev(Kind.CLUNK, 0.3, 0.3, 0.02),
            Ev(Kind.THUMP, -0.3, 0.5),
            Ev(Kind.WHOOSH, 0.2, 0.14, dur = 0.5, f0 = 4000.0, f1 = 1500.0, delay = 0.08, filter = "highpass", q = 0.5),
            Ev(Kind.NOISE_BURST, 0.0, 0.16, 0.04, attack = 0.001, decay = 0.015, filter = "bandpass", freq = 2400.0, q = 3.0),
            Ev(Kind.LOCK_THUNK, 0.1, 0.4),
            Ev(Kind.WHEEL_SPIN_DOWN, 0.1, delay = 0.1),
            Ev(Kind.TYRE_CHIRP, -0.2, 0.3),
        )
        for (e in evs) {
            val r = AudioEventRender.event(e, sr)
            assertTrue(e.kind.name, r.samples.isNotEmpty())
            assertTrue(e.kind.name, AudioDsp.rms(r.samples) > 0.0)
            assertEquals(e.pan, r.pan, 0.0)
            assertEquals(e.delay, r.delay, 0.0)
        }
    }

    @Test
    fun chimeIsCentredAndLongerForMoreNotes() {
        val one = AudioEventRender.event(Ev(Kind.CHIME, 0.7, notes = listOf(1175.0)), sr)
        val two = AudioEventRender.event(Ev(Kind.CHIME, 0.7, notes = listOf(1046.0, 784.0)), sr)
        assertEquals(0.0, one.pan, 0.0)
        assertTrue(two.samples.size > one.samples.size)
    }

    @Test
    fun bellStrikeAlternatesGongsAndKeepsTheStrikePan() {
        val a = AudioEventRender.bellStrike(sr, AudioTicker.BellStrike(1.0, 0, -0.08), 0.9, 0.5, 0.5)
        val b = AudioEventRender.bellStrike(sr, AudioTicker.BellStrike(1.15, 1, 0.08), 0.9, 0.5, 0.5)
        assertEquals(-0.08, a.pan, 0.0)
        assertEquals(0.08, b.pan, 0.0)
        assertEquals(0.1, a.delay, 1e-9)
        assertEquals(0.25, b.delay, 1e-9)
        assertTrue(AudioDsp.rms(a.samples) > 0.0)
        // a strike already in the past starts at once
        assertEquals(0.0, AudioEventRender.bellStrike(sr, AudioTicker.BellStrike(0.5, 2, -0.08), 0.9, 0.5, 0.5).delay, 0.0)
    }

    @Test
    fun clickAndLightOffFollowTheirPan() {
        val c = AudioEventRender.click(sr, AudioTicker.Click(0, -0.4, 0.0))
        assertEquals(-0.4, c.pan, 0.0)
        assertTrue(c.samples.isNotEmpty())
        val l = AudioEventRender.lightOff(sr, AudioTicker.LightOff(1, 0.4, 0.0))
        assertEquals(0.4, l.pan, 0.0)
        assertEquals((1.2 * sr).toInt(), l.samples.size)
    }
}
