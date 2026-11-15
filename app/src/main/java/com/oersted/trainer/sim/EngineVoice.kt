package com.oersted.trainer.sim

import com.oersted.trainer.sim.AudioDsp.FilterType
import com.oersted.trainer.sim.AudioDsp.Wave
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One engine voice (audio.ts engVoices): the JT8D synth layers (fan tone, compressor whine, jet roar with slow turbulence, body rumble, starter air
 * rush) plus the recorded idle and power loops, which go through a low shelf (180 Hz, 9 dB + 5 dB while starting) and a high shelf
 * (3 kHz, 3 dB) as on the web. The voice is panned 1.8 times the engine's own offset, so each engine sits well out to its side, and gated by
 * a bus that stays open 1.5 s after the engine stops. Targets come from [EngineSynthTargets] and [EngineSampleLayer].
 * Not ported: the shared convolution reverb and the 190 / 270 ms echo send of the web (a soft room effect, far too slow to run as a
 * time-domain convolution on a phone).
 * [update] and [attach] run on the mixer thread (AudioMixer.post).
 */
class EngineVoice(private val sr: Int, val index: Int, seed: Long = 1L) : AudioMixer.Voice {
    private val detune = if (index == 0) 0.992 else 1.008
    private val gl: Double
    private val gr: Double

    init {
        val p = AudioDsp.equalPowerPan(((if (index == 0) -0.4 else 0.4) * 1.8).coerceIn(-1.0, 1.0))
        gl = p.first
        gr = p.second
    }

    private var idle: AudioMixer.SampleLoop? = null
    private var run: AudioMixer.SampleLoop? = null
    private val bus = FxBank.Bus()

    // synth layers
    private val fanA = AudioDsp.Oscillator(Wave.TRIANGLE, sr.toDouble())
    private val fanB = AudioDsp.Oscillator(Wave.SINE, sr.toDouble())
    private val fanLp = AudioDsp.Biquad(FilterType.LOWPASS, sr.toDouble(), 1800.0, 1.0)
    private val cmpA = AudioDsp.Oscillator(Wave.SINE, sr.toDouble())
    private val cmpB = AudioDsp.Oscillator(Wave.SINE, sr.toDouble())
    private val cmpLp = AudioDsp.Biquad(FilterType.LOWPASS, sr.toDouble(), 4800.0, 1.0)
    private val roarNoise = AudioDsp.Noise(seed)
    private val roarLp = AudioDsp.Biquad(FilterType.LOWPASS, sr.toDouble(), 300.0, 0.7)
    private val bodyNoise = AudioDsp.Noise(seed + 1)
    private val bodyBp = AudioDsp.Biquad(FilterType.BANDPASS, sr.toDouble(), 180.0, 0.9)
    private val rushNoise = AudioDsp.Noise(seed + 2)
    private val rushHp = AudioDsp.Biquad(FilterType.HIGHPASS, sr.toDouble(), 1400.0, 1.0)
    private var lfoPhase = 0.0
    private val lfoHz = 0.31 + index * 0.13

    // live parameters (web ramp(): setTargetAtTime with the constants of audio.ts update())
    private val fanF = FxBank.Param(70.0)
    private val fanBF = FxBank.Param(140.0)
    private val fanG = FxBank.Param(0.0)
    private val cmpF = FxBank.Param(700.0)
    private val cmpBF = FxBank.Param(1000.0)
    private val cmpG = FxBank.Param(0.0)
    private val roarF = FxBank.Param(300.0)
    private val roarG = FxBank.Param(0.0)
    private val lfoD = FxBank.Param(0.0)
    private val bodyF = FxBank.Param(180.0)
    private val bodyG = FxBank.Param(0.0)
    private val rushG = FxBank.Param(0.0)
    private val lowDb = FxBank.Param(9.0)

    // recorded layer shelves
    private val lowShelf = Shelf(low = true, sr = sr, freq = 180.0, gainDb = 9.0)
    private val highShelf = Shelf(low = false, sr = sr, freq = 3000.0, gainDb = 3.0)
    private var lastLowDb = 9.0
    private var tmpL = FloatArray(0)
    private var tmpR = FloatArray(0)

    /** Attaches the recorded idle and power loops (pan 0; this voice pans the sum). */
    fun attach(idle: AudioMixer.SampleLoop, run: AudioMixer.SampleLoop) {
        this.idle = idle
        this.run = run
    }

    /** web update(): sets every target from the acoustic spool. [valve] is the start / bleed valve; [snap] adopts the targets at once. */
    fun update(spool: EngineSpoolAudio.AcousticSpool, valve: Boolean, now: Double, snap: Boolean = false) {
        val smp = idle != null && run != null
        val t = EngineSynthTargets.compute(spool, valve, detune, smp)
        bus.set(t.busOn, now)
        fanF.set(FxTargets.Ramp(t.fanFreq, 0.12)); fanBF.set(FxTargets.Ramp(t.fanBFreq, 0.12)); fanG.set(FxTargets.Ramp(t.fanGain, 0.2))
        cmpF.set(FxTargets.Ramp(t.cmpFreq, 0.1)); cmpBF.set(FxTargets.Ramp(t.cmpBFreq, 0.1)); cmpG.set(FxTargets.Ramp(t.cmpGain, 0.2))
        roarF.set(FxTargets.Ramp(t.roarLpFreq, 0.15)); roarG.set(FxTargets.Ramp(t.roarGain, 0.25)); lfoD.set(FxTargets.Ramp(t.lfoDepth, 0.25))
        bodyF.set(FxTargets.Ramp(t.bodyFreq, 0.2)); bodyG.set(FxTargets.Ramp(t.bodyGain, 0.3))
        rushG.set(FxTargets.Ramp(t.rushGain, 0.15))
        lowDb.set(FxTargets.Ramp(t.lowShelfGain, 0.2))
        val i = idle
        val r = run
        if (i != null && r != null) {
            val eff = (maxOf(spool.n1, spool.n2 * 0.5) / 100).coerceIn(0.0, 1.0)
            val m = EngineSampleLayer.mix(eff, spool.jet, detune)
            i.gainTau = 0.12; r.gainTau = 0.12
            i.rateTau = 0.14; r.rateTau = 0.14
            i.targetGain = m.idleGain.toFloat(); r.targetGain = m.runGain.toFloat()
            i.targetRate = m.idleRate.toFloat(); r.targetRate = m.runRate.toFloat()
            if (snap) {
                i.snap()
                r.snap()
            }
        }
        if (snap) for (p in listOf(fanF, fanBF, fanG, cmpF, cmpBF, cmpG, roarF, roarG, lfoD, bodyF, bodyG, rushG, lowDb)) p.snap()
    }

    /** True while the bus is open. */
    fun active(): Boolean = bus.active


    override fun render(l: FloatArray, r: FloatArray, n: Int, mixer: AudioMixer): Boolean {
        bus.update(mixer.time)
        val dtBlock = n.toDouble() / sr
        if (!bus.active) {
            for (p in listOf(fanF, fanBF, fanG, cmpF, cmpBF, cmpG, roarF, roarG, lfoD, bodyF, bodyG, rushG, lowDb)) p.step(dtBlock)
            return true
        }
        // recorded layer for the whole block (mono, centred), then shelves, then the synth on top
        if (tmpL.size < n) {
            tmpL = FloatArray(n)
            tmpR = FloatArray(n)
        }
        val i = idle
        val rr = run
        val haveSmp = i != null && rr != null
        if (haveSmp) {
            java.util.Arrays.fill(tmpL, 0, n, 0f)
            java.util.Arrays.fill(tmpR, 0, n, 0f)
            i!!.render(tmpL, tmpR, n, mixer)
            rr!!.render(tmpL, tmpR, n, mixer)
        }
        var off = 0
        while (off < n) {
            val m = minOf(CHUNK, n - off)
            val dt = m.toDouble() / sr
            val ff = fanF.step(dt); val fbf = fanBF.step(dt); val fg = fanG.step(dt)
            val cf = cmpF.step(dt); val cbf = cmpBF.step(dt); val cg = cmpG.step(dt)
            val rf = roarF.step(dt); val rg = roarG.step(dt); val ld = lfoD.step(dt)
            val bf = bodyF.step(dt); val bg = bodyG.step(dt)
            val sg = rushG.step(dt)
            val ldb = lowDb.step(dt)
            if (Math.abs(ldb - lastLowDb) > 0.05) {
                lowShelf.set(ldb)
                lastLowDb = ldb
            }
            roarLp.retune(FilterType.LOWPASS, sr.toDouble(), rf.coerceIn(20.0, sr * 0.45), 0.7)
            bodyBp.retune(FilterType.BANDPASS, sr.toDouble(), bf.coerceIn(20.0, sr * 0.45), 0.9)
            val turb = sin(lfoPhase) * ld
            lfoPhase = (lfoPhase + 2 * PI * lfoHz * dt) % (2 * PI)
            for (k in 0 until m) {
                var v = 0.0
                if (fg > 1e-7) v += fanLp.process(fanA.next(ff) + 0.45 * fanB.next(fbf)) * fg
                if (cg > 1e-7) v += cmpLp.process(cmpA.next(cf) + 0.5 * cmpB.next(cbf)) * cg
                if (rg > 1e-7 || ld > 1e-7) v += roarLp.process(roarNoise.next()) * (rg + turb)
                if (bg > 1e-7) v += bodyBp.process(bodyNoise.next()) * bg
                if (sg > 1e-7) v += rushHp.process(rushNoise.next()) * sg
                if (haveSmp) v += highShelf.process(lowShelf.process(tmpL[off + k] * SQRT2))
                l[off + k] += (v * gl).toFloat()
                r[off + k] += (v * gr).toFloat()
            }
            off += m
        }
        return true
    }

    /** RBJ shelving biquad (Web Audio lowshelf / highshelf, slope 1). */
    class Shelf(private val low: Boolean, private val sr: Int, private val freq: Double, gainDb: Double) {
        private var b0 = 1.0
        private var b1 = 0.0
        private var b2 = 0.0
        private var a1 = 0.0
        private var a2 = 0.0
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        init {
            set(gainDb)
        }

        fun set(gainDb: Double) {
            val a = 10.0.pow(gainDb / 40)
            val w0 = 2 * PI * freq / sr
            val cw = cos(w0)
            val alpha = sin(w0) / 2 * sqrt(2.0)
            val sa = 2 * sqrt(a) * alpha
            val nb0: Double
            val nb1: Double
            val nb2: Double
            val na0: Double
            val na1: Double
            val na2: Double
            if (low) {
                nb0 = a * ((a + 1) - (a - 1) * cw + sa)
                nb1 = 2 * a * ((a - 1) - (a + 1) * cw)
                nb2 = a * ((a + 1) - (a - 1) * cw - sa)
                na0 = (a + 1) + (a - 1) * cw + sa
                na1 = -2 * ((a - 1) + (a + 1) * cw)
                na2 = (a + 1) + (a - 1) * cw - sa
            } else {
                nb0 = a * ((a + 1) + (a - 1) * cw + sa)
                nb1 = -2 * a * ((a - 1) + (a + 1) * cw)
                nb2 = a * ((a + 1) + (a - 1) * cw - sa)
                na0 = (a + 1) - (a - 1) * cw + sa
                na1 = 2 * ((a - 1) - (a + 1) * cw)
                na2 = (a + 1) - (a - 1) * cw - sa
            }
            b0 = nb0 / na0
            b1 = nb1 / na0
            b2 = nb2 / na0
            a1 = na1 / na0
            a2 = na2 / na0
        }

        fun process(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            return y
        }
    }

    companion object {
        const val CHUNK = 64
        private const val SQRT2 = 1.4142135623730951
    }
}
