package com.oersted.trainer.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The software mixer: one-shots, gapless smoothed loops, tones, command queue, master gain and limiting. */
class AudioMixerTest {
    private val rate = 1000

    private fun block(m: AudioMixer, n: Int): ShortArray {
        val out = ShortArray(n * 2)
        m.render(out, n)
        return out
    }

    @Test
    fun oneShotPlaysOnceThenIsRemoved() {
        val m = AudioMixer(rate).apply { master = 1f }
        m.add(m.OneShot(floatArrayOf(0.5f, 0.5f, 0.5f), 0.0, 1f))
        val out = block(m, 8)
        assertTrue(out[0] > 0)
        assertEquals(0, out[2 * 3].toInt())
        assertEquals(0, m.voiceCount())
    }

    @Test
    fun oneShotDelayAndEqualPowerPan() {
        val m = AudioMixer(rate).apply { master = 1f }
        m.add(m.OneShot(floatArrayOf(1f), -1.0, 1f, delay = 0.002))
        val out = block(m, 6)
        assertEquals(0, out[0].toInt())
        assertEquals(0, out[2].toInt())
        // frame 2: hard left, so the right channel is silent
        assertTrue(out[4] > 30000)
        assertEquals(0, out[5].toInt())
    }

    @Test
    fun masterGainScalesAndTheOutputIsLimited() {
        val m = AudioMixer(rate).apply { master = 0.5f }
        m.add(m.OneShot(floatArrayOf(1f), 0.0, 1f))
        m.add(m.OneShot(floatArrayOf(1f), 0.0, 1f))
        val out = block(m, 2)
        // two unit sources at centre: 2 * 0.7071 * 0.5 = 0.707 -> not clipped
        assertEquals(0.7071 * 32767, out[0].toDouble(), 40.0)
        val loud = AudioMixer(rate).apply { master = 1f }
        loud.add(loud.OneShot(floatArrayOf(1f), 0.0, 4f))
        assertEquals(32767, block(loud, 1)[0].toInt())
    }

    @Test
    fun loopWrapsWithoutAGapAndHonoursRate() {
        val m = AudioMixer(rate).apply { master = 1f }
        val loop = m.SampleLoop(floatArrayOf(0f, 1f, 0f, 1f), rate, 0.0)
        loop.targetGain = 1f
        loop.snap()
        m.add(loop)
        val l = FloatArray(8)
        val r = FloatArray(8)
        loop.render(l, r, 8, m)
        // continuous: samples alternate 0,1,0,1,... across the wrap, never silent for two in a row
        for (i in 0 until 7) assertTrue(l[i] != l[i + 1])
        // double rate: steps two samples at a time over a 4-sample loop
        val fast = m.SampleLoop(floatArrayOf(0f, 1f, 0f, 1f), rate, -1.0)
        fast.targetGain = 1f
        fast.targetRate = 2f
        fast.snap()
        val l2 = FloatArray(4)
        val r2 = FloatArray(4)
        fast.render(l2, r2, 4, m)
        // every second source sample: all zeros at 2x (0,2,0,2 -> 0)
        for (v in l2) assertEquals(0f, v, 1e-6f)
    }

    @Test
    fun loopGainRampsTowardTheTarget() {
        val m = AudioMixer(rate)
        val loop = m.SampleLoop(floatArrayOf(1f, 1f, 1f, 1f), rate)
        loop.gainTau = 0.05
        loop.targetGain = 1f
        val l = FloatArray(10)
        val r = FloatArray(10)
        loop.render(l, r, 10, m)
        // dt = 10 ms, tau = 50 ms: 1 - exp(-0.2) = 0.1813
        assertEquals(0.1813f, loop.currentGain, 1e-3f)
        loop.snap()
        assertEquals(1f, loop.currentGain, 0f)
    }

    @Test
    fun silentLoopKeepsRunningWithoutOutput() {
        val m = AudioMixer(rate)
        val loop = m.SampleLoop(floatArrayOf(1f, 1f), rate)
        val l = FloatArray(4)
        val r = FloatArray(4)
        assertTrue(loop.render(l, r, 4, m))
        assertEquals(0f, l[0], 0f)
    }

    @Test
    fun toneFollowsKeyingAndStaysSilentWhenOff() {
        val m = AudioMixer(rate)
        val tone = m.Tone(100.0, tau = 0.001)
        val l = FloatArray(20)
        val r = FloatArray(20)
        tone.render(l, r, 20, m)
        assertEquals(0f, l[3], 0f)
        tone.targetGain = 0.12f
        tone.render(l, r, 20, m)
        var peak = 0f
        for (v in l) if (Math.abs(v) > peak) peak = Math.abs(v)
        assertTrue(peak > 0.05f && peak <= 0.12f + 1e-4f)
    }

    @Test
    fun commandsRunOnTheNextBlockInOrder() {
        val m = AudioMixer(rate)
        val seen = ArrayList<Int>()
        m.post { seen.add(1) }
        m.post { seen.add(2) }
        assertTrue(seen.isEmpty())
        block(m, 4)
        assertEquals(listOf(1, 2), seen)
        block(m, 4)
        assertEquals(2, seen.size)
    }

    @Test
    fun clockAdvancesByFramesRendered() {
        val m = AudioMixer(rate)
        assertEquals(0.0, m.time, 0.0)
        block(m, 250)
        assertEquals(0.25, m.time, 1e-12)
    }

    @Test
    fun streamVoiceReceivesTheBlockStartTimeAndCanBeSwitchedOff() {
        val m = AudioMixer(rate).apply { master = 1f }
        val starts = ArrayList<Double>()
        val s = m.Stream({ out, t ->
            starts.add(t)
            out[0] = 0.5
        })
        m.add(s)
        block(m, 10)
        block(m, 10)
        assertEquals(listOf(0.0, 0.01), starts)
        s.on = false
        block(m, 10)
        assertEquals(2, starts.size)
        assertFalse(m.voiceCount() == 0)
    }
}
