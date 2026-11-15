package com.oersted.trainer.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioBellTest {
    private val sr = 48000.0

    private fun rmsRange(x: DoubleArray, fromSec: Double, toSec: Double): Double {
        val a = (fromSec * sr).toInt()
        val b = minOf((toSec * sr).toInt(), x.size)
        return AudioDsp.rms(x.copyOfRange(a, b))
    }

    @Test fun bellPansAlternateBetweenTheGongs() {
        assertEquals(-0.08, AudioSounds.bellPan(0), 0.0)
        assertEquals(0.08, AudioSounds.bellPan(1), 0.0)
        assertEquals(-0.08, AudioSounds.bellPan(2), 0.0)
    }

    @Test fun bellStrikeRingsOutInUnderHalfASecond() {
        val x = AudioSounds.bell(sr, 1170.0, 1.0)
        // the longest partial runs 0.36 + 0.03 s
        assertTrue(x.size in 18718..18724)
        assertTrue(rmsRange(x, 0.0, 0.05) > rmsRange(x, 0.3, 0.35))
    }

    @Test fun chimeSpacesItsNotesByTheGap() {
        val x = AudioSounds.chime(sr, listOf(440.0, 880.0), gap = 0.2)
        // second note starts at 0.2 s and its tone lasts 0.005 + 0.75 + 0.05 = 0.805 s, so the render ends near 1.005 s
        assertTrue(x.size in 48236..48244)
        assertTrue(rmsRange(x, 0.0, 0.1) > 0.0)
    }
}
