package com.oersted.trainer.sim

import com.oersted.trainer.sim.AudioDsp.FilterType
import com.oersted.trainer.sim.AudioDsp.Oscillator
import com.oersted.trainer.sim.AudioDsp.TargetParam
import com.oersted.trainer.sim.AudioDsp.Wave

/**
 * APU voice (audio.ts, the APU block of build()): a quiet tail-mounted turbine, a soft whine (sawtooth + triangle at 1.5x through a
 * 1700 Hz lowpass), a bandpassed noise hiss through a 1500 Hz lowpass, and a starter buzz (sawtooth through a 420 Hz lowpass).
 * Streaming: call [update] when the APU speed or starter changes, then [render] for each block. Live parameters use [TargetParam],
 * as the web's setTargetAtTime does. [update] returns whether the APU bus should be audible (the caller switches the bus).
 */
class ApuVoice(private val sampleRate: Double, seed: Long = 1L) {
    private val whineF = TargetParam(220.0)
    private val whineG = TargetParam(0.0)
    private val noiseF = TargetParam(250.0)
    private val noiseG = TargetParam(0.0)
    private val starterF = TargetParam(55.0)
    private val starterG = TargetParam(0.0)

    private val whine = Oscillator(Wave.SAWTOOTH, sampleRate)
    private val whine2 = Oscillator(Wave.TRIANGLE, sampleRate)
    private val whineLp = AudioDsp.Biquad(FilterType.LOWPASS, sampleRate, 1700.0, 1.0)
    private val noise = AudioDsp.Noise(seed)
    private val noiseBp = AudioDsp.Biquad(FilterType.BANDPASS, sampleRate, 250.0, 0.6)
    private val noiseLp = AudioDsp.Biquad(FilterType.LOWPASS, sampleRate, 1500.0, 1.0)
    private val starter = Oscillator(Wave.SAWTOOTH, sampleRate)
    private val starterLp = AudioDsp.Biquad(FilterType.LOWPASS, sampleRate, 420.0, 1.0)

    /** Samples between band-pass retunes: the centre frequency moves slowly, so this is not audible. */
    private val retuneEvery = 32

    /**
     * Sets the targets for the current APU speed (percent N1-like, 0-100) and starter state, at time [nowSec].
     * Returns true when the APU bus should be connected (running or starting).
     */
    fun update(rpm: Double, starterOn: Boolean, nowSec: Double): Boolean {
        val r = (rpm / 100.0).coerceIn(0.0, 1.0)
        val on = r > 0.02
        val f = 220 + r * 1000
        whineF.setTargetAtTime(f, nowSec, 0.08)
        whineG.setTargetAtTime(if (on) 0.004 + 0.014 * r else 0.0, nowSec, 0.15)
        noiseF.setTargetAtTime(250 + r * 700, nowSec, 0.1)
        noiseG.setTargetAtTime(if (on) 0.004 + 0.03 * Math.pow(r, 1.2) else 0.0, nowSec, 0.2)
        starterF.setTargetAtTime(55 + r * 90, nowSec, 0.1)
        starterG.setTargetAtTime(if (starterOn) 0.035 * (1 - r * 0.6) else 0.0, nowSec, 0.1)
        return on || starterOn
    }

    /** Renders [out].size mono samples starting at [startSec]. Accumulates into [out]. */
    fun render(out: DoubleArray, startSec: Double) {
        for (i in out.indices) {
            val t = startSec + i / sampleRate
            if (i % retuneEvery == 0) noiseBp.retune(FilterType.BANDPASS, sampleRate, noiseF.valueAt(t), 0.6)
            val wf = whineF.valueAt(t)
            val whineSum = whineLp.process(whine.next(wf) + whine2.next(wf * 1.5))
            val noiseSum = noiseLp.process(noiseBp.process(noise.next()))
            val starterSum = starterLp.process(starter.next(starterF.valueAt(t)))
            out[i] += whineSum * whineG.valueAt(t) + noiseSum * noiseG.valueAt(t) + starterSum * starterG.valueAt(t)
        }
    }
}
