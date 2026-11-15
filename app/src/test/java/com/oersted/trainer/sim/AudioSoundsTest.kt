package com.oersted.trainer.sim

import com.oersted.trainer.sim.AudioDsp.ExpEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSoundsTest {
    private val sr = 48000.0

    @Test fun exponentialEnvelopeIsGeometricBetweenPoints() {
        // halfway (in time) between 0.0001 and 1.0 is their geometric mean, 0.01
        val e = ExpEnvelope(listOf(0.0 to 0.0001, 1.0 to 1.0))
        assertEquals(0.01, e.valueAt(0.5), 1e-9)
        assertEquals(1.0, e.valueAt(1.0), 1e-9)
    }

    @Test fun equalPowerPanPutsFullSignalOnOneSideAtTheEdges() {
        val (lL, lR) = AudioDsp.equalPowerPan(-1.0)
        assertEquals(1.0, lL, 1e-9)
        assertEquals(0.0, lR, 1e-9)
        val (cL, cR) = AudioDsp.equalPowerPan(0.0)
        assertEquals(Math.sqrt(0.5), cL, 1e-9)
        assertEquals(Math.sqrt(0.5), cR, 1e-9)
    }

    @Test fun mixerPlacesASoundAtItsStartTimeAndPan() {
        val m = AudioDsp.StereoMixer(sr, 1.0)
        m.add(doubleArrayOf(1.0), atSec = 0.1, pan = -1.0, gain = 1.0)
        assertEquals(1.0, m.left[4800], 1e-9)
        assertEquals(0.0, m.right[4800], 1e-9)
        assertEquals(0.0, m.left[0], 0.0)
    }

    @Test fun clickIsAShortBurstThatDecaysQuickly() {
        val c = AudioSounds.click(sr)
        val early = c.copyOfRange(0, (0.005 * sr).toInt()).maxOf { Math.abs(it) }
        val late = c.copyOfRange((0.015 * sr).toInt(), c.size).maxOf { Math.abs(it) }
        assertTrue(early > 0.0)
        assertTrue(late < early * 0.2)
    }
}
