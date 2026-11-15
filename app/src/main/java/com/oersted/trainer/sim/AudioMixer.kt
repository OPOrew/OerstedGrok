package com.oersted.trainer.sim

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Software real-time mixer for the Android audio layer (replaces the Web Audio graph of audio.ts). Pure Kotlin: the Android
 * AudioTrack thread (platform/AudioEngine.kt) just calls [render] for each block and writes the PCM.
 *
 * Threading: every voice is touched only inside [render] or inside a command run at the start of [render]. Other threads change
 * voices with [post], which queues a lambda; nothing else needs a lock. [time] is the mixer clock in seconds (frames rendered / rate)
 * and is what ApuVoice / TargetParam use as "now".
 */
class AudioMixer(val sampleRate: Int = 44100) {

    /** A source of mono audio panned in stereo. Adds into [l] / [r] for [n] frames. Returns false when finished (removed). */
    interface Voice {
        fun render(l: FloatArray, r: FloatArray, n: Int, mixer: AudioMixer): Boolean
    }

    private val voices = ArrayList<Voice>()
    private val commands = ConcurrentLinkedQueue<() -> Unit>()
    @Volatile
    private var frames = 0L

    /** Master gain 0..1 (web master GainNode, default 0.6). */
    @Volatile
    var master: Float = 0.6f

    /** Mixer clock, seconds. Read on the mixer thread (inside commands and voices). */
    val time: Double get() = frames.toDouble() / sampleRate

    /** Queues [cmd] to run on the mixer thread before the next block. */
    fun post(cmd: () -> Unit) {
        commands.add(cmd)
    }

    /** Adds a voice (call from a command or before the mixer thread starts). */
    fun add(v: Voice) {
        voices.add(v)
    }

    fun remove(v: Voice) {
        voices.remove(v)
    }

    fun voiceCount(): Int = voices.size

    private var lBuf = FloatArray(0)
    private var rBuf = FloatArray(0)

    /** Renders [n] stereo frames into [out] (interleaved 16-bit). Applies the master gain and hard-limits to +-1. */
    fun render(out: ShortArray, n: Int) {
        while (true) (commands.poll() ?: break).invoke()
        if (lBuf.size < n) {
            lBuf = FloatArray(n)
            rBuf = FloatArray(n)
        }
        java.util.Arrays.fill(lBuf, 0, n, 0f)
        java.util.Arrays.fill(rBuf, 0, n, 0f)
        val it = voices.iterator()
        while (it.hasNext()) if (!it.next().render(lBuf, rBuf, n, this)) it.remove()
        val g = master
        for (i in 0 until n) {
            out[2 * i] = toPcm(lBuf[i] * g)
            out[2 * i + 1] = toPcm(rBuf[i] * g)
        }
        frames += n
    }

    private fun toPcm(v: Float): Short {
        val c = if (v > 1f) 1f else if (v < -1f) -1f else v
        return (c * 32767f).toInt().toShort()
    }

    /** Smoothing step of a Web Audio setTargetAtTime over [dt] seconds with time constant [tau]. */
    private fun follow(cur: Float, target: Float, dt: Double, tau: Double): Float =
        if (tau <= 0.0) target else cur + (target - cur) * (1.0 - exp(-dt / tau)).toFloat()

    /** Equal-power pan gains (StereoPannerNode), as AudioDsp.equalPowerPan but in floats. */
    private fun panGains(pan: Double): Pair<Float, Float> {
        val (l, r) = AudioDsp.equalPowerPan(pan)
        return l.toFloat() to r.toFloat()
    }

    /** A pre-rendered mono sound played once, with a start [delay] in seconds. */
    inner class OneShot(private val buf: FloatArray, pan: Double, private val gain: Float, delay: Double = 0.0) : Voice {
        private val gl: Float
        private val gr: Float
        private var wait = (delay * sampleRate).toInt()
        private var pos = 0

        init {
            val p = panGains(pan)
            gl = p.first * gain
            gr = p.second * gain
        }

        override fun render(l: FloatArray, r: FloatArray, n: Int, mixer: AudioMixer): Boolean {
            var i = 0
            while (i < n && wait > 0) {
                i++
                wait--
            }
            while (i < n) {
                if (pos >= buf.size) return false
                val v = buf[pos++]
                l[i] += v * gl
                r[i] += v * gr
                i++
            }
            return pos < buf.size
        }
    }

    /**
     * A gapless looping sample with smoothed gain and playback rate (the web's BufferSource loop + GainNode + playbackRate with
     * setTargetAtTime). [rate] 1.0 plays at the file's own pitch. Never finishes; remove it explicitly.
     */
    inner class SampleLoop(
        private val pcm: FloatArray,
        private val srcRate: Int,
        private val pan: Double = 0.0,
    ) : Voice {
        @Volatile
        var targetGain = 0f
        @Volatile
        var targetRate = 1f
        var gainTau = 0.1
        var rateTau = 0.2
        private var gain = 0f
        private var rate = 1f
        private var pos = 0.0
        private val gl: Float
        private val gr: Float

        init {
            val p = panGains(pan)
            gl = p.first
            gr = p.second
        }

        /** Moves the play position to [fraction] (0..1) of the loop; each engine starts at its own point (audio.ts src.start(0, random)). */
        fun startAt(fraction: Double) {
            pos = fraction.coerceIn(0.0, 0.999999) * pcm.size
        }

        /** Jumps straight to the targets (no ramp). */
        fun snap() {
            gain = targetGain
            rate = targetRate
        }

        val currentGain: Float get() = gain
        val currentRate: Float get() = rate

        override fun render(l: FloatArray, r: FloatArray, n: Int, mixer: AudioMixer): Boolean {
            if (pcm.size < 2) return true
            val dt = n.toDouble() / sampleRate
            gain = follow(gain, targetGain, dt, gainTau)
            rate = follow(rate, targetRate, dt, rateTau)
            // silent: keep the clock running so loops stay in step, but skip the work
            if (gain < 1e-5f && targetGain < 1e-5f) {
                pos = (pos + n * (srcRate.toDouble() / sampleRate) * rate) % pcm.size
                return true
            }
            val step = (srcRate.toDouble() / sampleRate) * rate
            val len = pcm.size
            for (i in 0 until n) {
                val i0 = pos.toInt()
                val f = (pos - i0).toFloat()
                val a = pcm[i0]
                val b = pcm[if (i0 + 1 >= len) 0 else i0 + 1]
                val v = (a + (b - a) * f) * gain
                l[i] += v * gl
                r[i] += v * gr
                pos += step
                if (pos >= len) pos -= len
            }
            return true
        }
    }

    /** A sine tone with a smoothed gain (the marker beacon oscillator: gain.setTargetAtTime(g, t, 0.004)). */
    inner class Tone(@Volatile var freq: Double, private val tau: Double = 0.004) : Voice {
        @Volatile
        var targetGain = 0f
        private var gain = 0f
        private var phase = 0.0

        override fun render(l: FloatArray, r: FloatArray, n: Int, mixer: AudioMixer): Boolean {
            val dt = n.toDouble() / sampleRate
            gain = follow(gain, targetGain, dt, tau)
            if (gain < 1e-5f && targetGain < 1e-5f) return true
            val w = 2 * PI * freq / sampleRate
            for (i in 0 until n) {
                val v = sin(phase).toFloat() * gain
                l[i] += v
                r[i] += v
                phase += w
                if (phase > 2 * PI) phase -= 2 * PI
            }
            return true
        }
    }

    /** Wraps a streaming mono renderer (ApuVoice, synthetic loops): [fill] adds [n] samples starting at [startSec] into the buffer. */
    inner class Stream(private val fill: (out: DoubleArray, startSec: Double) -> Unit, pan: Double = 0.0) : Voice {
        @Volatile
        var on = true
        private var tmp = DoubleArray(0)
        private val gl: Float
        private val gr: Float

        init {
            val p = panGains(pan)
            gl = p.first
            gr = p.second
        }

        override fun render(l: FloatArray, r: FloatArray, n: Int, mixer: AudioMixer): Boolean {
            if (!on) return true
            if (tmp.size != n) tmp = DoubleArray(n)
            java.util.Arrays.fill(tmp, 0.0)
            fill(tmp, mixer.time)
            for (i in 0 until n) {
                val v = tmp[i].toFloat()
                l[i] += v * gl
                r[i] += v * gr
            }
            return true
        }
    }

    companion object {
        /** Mono Double render (AudioSounds) to the mixer's Float format. */
        fun toFloat(d: DoubleArray): FloatArray = FloatArray(d.size) { d[it].toFloat() }
    }
}
