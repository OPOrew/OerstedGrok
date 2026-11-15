package com.oersted.trainer.sim

import com.oersted.trainer.sim.AudioDsp.ExpEnvelope
import com.oersted.trainer.sim.AudioDsp.FilterType

/**
 * Two sounds of audio.ts that AudioSounds does not carry: the mechanical switch click (`playSwitchClick`) and the engine light-off
 * rumble (`lightOff`). Mono renders, same conventions as AudioSounds (seeded noise, exponential envelopes).
 */
object AudioSwitchSounds {
    /** playSwitchClick pitch spread: 0.92 + random * 0.16. */
    fun clickPitch(random: Double): Double = 0.92 + random * 0.16

    /**
     * playSwitchClick: a down-click (amp 0.4, 2800 Hz) and a release 22 ms later (amp 0.2, 3600 Hz). Each tick is a Q 3 bandpass noise burst
     * (1.5 ms attack, 14 ms decay, 30 ms long) over a sine body thump (190 x pitch down to 90 Hz in 30 ms, amp x 0.5, 45 ms long).
     */
    fun switchClick(sampleRate: Double, pitch: Double = 1.0, seed: Long = 1L): DoubleArray {
        val len = Math.ceil((0.022 + 0.045) * sampleRate).toInt()
        val out = DoubleArray(len)
        tick(out, sampleRate, 0.0, 0.4, 2800.0, pitch, seed)
        tick(out, sampleRate, 0.022, 0.2, 3600.0, pitch, seed + 1)
        return out
    }

    private fun tick(out: DoubleArray, sr: Double, at: Double, amp: Double, freq: Double, pitch: Double, seed: Long) {
        val start = Math.ceil(at * sr).toInt()
        val noise = AudioDsp.Noise(seed)
        val bp = AudioDsp.Biquad(FilterType.BANDPASS, sr, freq * pitch, 3.0)
        val env = ExpEnvelope(listOf(0.0 to 0.0001, 0.0015 to amp, 0.014 to 0.0001))
        val nLen = (0.03 * sr).toInt()
        for (i in 0 until nLen) {
            if (start + i >= out.size) break
            out[start + i] += bp.process(noise.next()) * env.valueAt(i / sr)
        }
        val osc = AudioDsp.Oscillator(AudioDsp.Wave.SINE, sr)
        val oenv = ExpEnvelope(listOf(0.0 to 0.0001, 0.002 to amp * 0.5, 0.035 to 0.0001))
        val f0 = 190 * pitch
        val oLen = (0.045 * sr).toInt()
        for (i in 0 until oLen) {
            if (start + i >= out.size) break
            val t = i / sr
            val f = if (t >= 0.03) 90.0 else f0 * Math.pow(90.0 / f0, t / 0.03)
            out[start + i] += osc.next(f) * oenv.valueAt(t)
        }
    }

    /**
     * lightOff: looped noise through a lowpass swept 180 to 700 Hz over 0.6 s (exponential), gain 0.0001 to 0.35 at 0.12 s and back to
     * 0.0001 at 1.1 s; 1.2 s long. The caller pans it.
     */
    fun lightOff(sampleRate: Double, seed: Long = 1L): DoubleArray {
        val len = (1.2 * sampleRate).toInt()
        val out = DoubleArray(len)
        val noise = AudioDsp.Noise(seed)
        val lp = AudioDsp.Biquad(FilterType.LOWPASS, sampleRate, 180.0, 1.0)
        val env = ExpEnvelope(listOf(0.0 to 0.0001, 0.12 to 0.35, 1.1 to 0.0001))
        for (i in 0 until len) {
            val t = i / sampleRate
            if (i % 32 == 0) {
                val f = if (t >= 0.6) 700.0 else 180.0 * Math.pow(700.0 / 180.0, t / 0.6)
                lp.retune(FilterType.LOWPASS, sampleRate, f, 1.0)
            }
            out[i] = lp.process(noise.next()) * env.valueAt(t)
        }
        return out
    }
}
