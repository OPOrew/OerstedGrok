package com.oersted.trainer.sim

import com.oersted.trainer.sim.AudioDsp.ExpEnvelope
import com.oersted.trainer.sim.AudioDsp.FilterType

/**
 * Sound builders for audio.ts, rendered to mono PCM for the software mixer. Each returns the rendered samples at [sampleRate].
 * The noise source is seeded per sound so renders are repeatable. Times and gains follow audio.ts.
 */
object AudioSounds {
    /** The cockpit switch / light click (audio.ts `click`): a 3200 Hz bandpass burst through a fast exponential envelope. */
    fun click(sampleRate: Double, seed: Long = 1L): DoubleArray {
        val len = (0.03 * sampleRate).toInt()
        val out = DoubleArray(len)
        val noise = AudioDsp.Noise(seed)
        val bp = AudioDsp.Biquad(FilterType.BANDPASS, sampleRate, 3200.0, 2.0)
        val env = ExpEnvelope(listOf(0.0 to 0.0001, 0.002 to 0.25, 0.02 to 0.0001))
        for (i in 0 until len) {
            val t = i / sampleRate
            out[i] = bp.process(noise.next()) * env.valueAt(t)
        }
        return out
    }

    /** Gain envelope of audio.ts env(): 0.0001 -> max(amp, 0.0002) over attack, then back to 0.0001 over decay. */
    private fun envelope(amp: Double, attack: Double, decay: Double): ExpEnvelope =
        ExpEnvelope(listOf(0.0 to 0.0001, attack to Math.max(amp, 0.0002), (attack + decay) to 0.0001))

    /** Length in samples of a burst that runs attack + decay + 0.05 s (the web stops it 0.05 s after the envelope). */
    private fun burstLength(sampleRate: Double, attack: Double, decay: Double): Int = Math.ceil((attack + decay + 0.05) * sampleRate).toInt()

    /** audio.ts toneBurst: an oscillator whose frequency ramps exponentially from f0 to f1 (floored at 1 Hz), under the env() gain. */
    fun toneBurst(sampleRate: Double, amp: Double, f0: Double, f1: Double, attack: Double, decay: Double, wave: AudioDsp.Wave = AudioDsp.Wave.SINE): DoubleArray {
        val len = burstLength(sampleRate, attack, decay)
        val out = DoubleArray(len)
        val osc = AudioDsp.Oscillator(wave, sampleRate)
        val env = envelope(amp, attack, decay)
        val span = attack + decay
        val f1c = Math.max(f1, 1.0)
        for (i in 0 until len) {
            val t = i / sampleRate
            val freq = if (t >= span) f1c else f0 * Math.pow(f1c / f0, t / span)
            out[i] = osc.next(freq) * env.valueAt(t)
        }
        return out
    }

    /** audio.ts noiseBurst: seeded noise through a biquad filter, under the env() gain. */
    fun noiseBurst(sampleRate: Double, amp: Double, attack: Double, decay: Double, type: FilterType, freq: Double, q: Double, seed: Long = 1L): DoubleArray {
        val len = burstLength(sampleRate, attack, decay)
        val out = DoubleArray(len)
        val noise = AudioDsp.Noise(seed)
        val filter = AudioDsp.Biquad(type, sampleRate, freq, q)
        val env = envelope(amp, attack, decay)
        for (i in 0 until len) {
            out[i] = filter.process(noise.next()) * env.valueAt(i / sampleRate)
        }
        return out
    }

    /** Mixes parts that each start at their own offset (seconds) into one mono buffer. */
    fun sum(parts: List<Pair<Double, DoubleArray>>, sampleRate: Double): DoubleArray {
        val len = parts.maxOfOrNull { (off, s) -> Math.ceil(off * sampleRate).toInt() + s.size } ?: 0
        val out = DoubleArray(len)
        for ((off, s) in parts) {
            val start = Math.ceil(off * sampleRate).toInt()
            for (i in s.indices) out[start + i] += s[i]
        }
        return out
    }

    /** audio.ts thump: a 120 -> 38 Hz body tone with a 320 Hz lowpassed noise knock. */
    fun thump(sampleRate: Double, amp: Double, seed: Long = 1L): DoubleArray = sum(
        listOf(
            0.0 to toneBurst(sampleRate, amp, 120.0, 38.0, 0.004, 0.24),
            0.0 to noiseBurst(sampleRate, amp * 0.7, 0.003, 0.09, FilterType.LOWPASS, 320.0, 0.7, seed),
        ),
        sampleRate,
    )

    /** audio.ts clunk: a short body tone, a 1500 Hz bandpass noise hit and two ring tones. */
    fun clunk(sampleRate: Double, amp: Double, seed: Long = 1L): DoubleArray = sum(
        listOf(
            0.0 to toneBurst(sampleRate, amp * 0.8, 170.0, 70.0, 0.002, 0.09),
            0.0 to noiseBurst(sampleRate, amp * 0.6, 0.001, 0.035, FilterType.BANDPASS, 1500.0, 2.0, seed),
            0.0 to toneBurst(sampleRate, amp * 0.17, 430.0, 420.0, 0.001, 0.08),
            0.0 to toneBurst(sampleRate, amp * 0.1, 1180.0, 1160.0, 0.001, 0.06),
        ),
        sampleRate,
    )

    /** audio.ts whoosh: noise through a filter whose frequency sweeps exponentially f0 -> f1 over dur, under env(amp, 0.3 dur, 0.7 dur). */
    fun whoosh(sampleRate: Double, amp: Double, dur: Double, f0: Double, f1: Double, type: FilterType = FilterType.BANDPASS, q: Double = 0.8, seed: Long = 1L): DoubleArray {
        val len = Math.ceil((dur + 0.1) * sampleRate).toInt()
        val out = DoubleArray(len)
        val noise = AudioDsp.Noise(seed)
        val filter = AudioDsp.Biquad(type, sampleRate, f0, q)
        val env = envelope(amp, dur * 0.3, dur * 0.7)
        for (i in 0 until len) {
            val t = i / sampleRate
            val freq = if (t >= dur) f1 else f0 * Math.pow(f1 / f0, t / dur)
            filter.retune(type, sampleRate, freq, q)
            out[i] = filter.process(noise.next()) * env.valueAt(t)
        }
        return out
    }

    /** audio.ts wheelSpinDown: a falling triangle tone with a falling whoosh. */
    fun wheelSpinDown(sampleRate: Double, seed: Long = 1L): DoubleArray = sum(
        listOf(
            0.0 to toneBurst(sampleRate, 0.03, 300.0, 55.0, 0.05, 1.6, AudioDsp.Wave.TRIANGLE),
            0.0 to whoosh(sampleRate, 0.05, 1.6, 700.0, 120.0, seed = seed),
        ),
        sampleRate,
    )

    /** audio.ts tyreChirp: a bandpassed noise chirp with a falling sawtooth. */
    fun tyreChirp(sampleRate: Double, amp: Double, seed: Long = 1L): DoubleArray = sum(
        listOf(
            0.0 to noiseBurst(sampleRate, amp, 0.004, 0.22, FilterType.BANDPASS, 1400.0, 6.0, seed),
            0.0 to toneBurst(sampleRate, amp * 0.25, 960.0, 800.0, 0.004, 0.18, AudioDsp.Wave.SAWTOOTH),
        ),
        sampleRate,
    )

    /** audio.ts lockThunk: a thump, then the clunk 12 ms later at 70 % of the level. */
    fun lockThunk(sampleRate: Double, amp: Double, seed: Long = 1L): DoubleArray = sum(
        listOf(
            0.0 to thump(sampleRate, amp, seed),
            0.012 to clunk(sampleRate, amp * 0.7, seed),
        ),
        sampleRate,
    )

    /** audio.ts bell pan: strikes alternate between the two gongs, panned ±0.08. */
    fun bellPan(k: Int): Double = if (k % 2 != 0) 0.08 else -0.08

    /**
     * audio.ts bell: one fire-bell strike. [base] is the gong frequency (BELL_BASE[k % 2] times detune), [amp] the per-strike level.
     * Six inharmonic partials with a slight downward drift, then the hammer click and knock. Partials above 12 kHz are dropped, as on the web.
     */
    fun bell(sampleRate: Double, base: Double, amp: Double): DoubleArray {
        // [frequency ratio, amplitude, decay seconds]
        val modes = listOf(
            Triple(1.0, 0.24, 0.36),
            Triple(1.004, 0.1, 0.3), // near-unison partner: slow shimmer / beating in the ring
            Triple(2.32, 0.15, 0.24),
            Triple(4.25, 0.1, 0.15),
            Triple(6.63, 0.06, 0.09),
            Triple(9.38, 0.035, 0.06),
        )
        val parts = mutableListOf<Pair<Double, DoubleArray>>()
        for ((r, a, d) in modes) {
            val f = base * r
            if (f > 12000) continue
            parts.add(0.0 to partial(sampleRate, f, a * amp, d))
        }
        // clapper hitting the gong: bright click + a dull knock of the hammer armature
        parts.add(0.0 to noiseBurst(sampleRate, 0.16 * amp, 0.0006, 0.011, FilterType.BANDPASS, 4200.0, 1.1))
        parts.add(0.0 to toneBurst(sampleRate, 0.07 * amp, 230.0, 120.0, 0.001, 0.03))
        return sum(parts, sampleRate)
    }

    /** One bell partial: a sine that drifts from f to 0.992 f over d seconds, with a 1.5 ms attack to a * amp. */
    private fun partial(sampleRate: Double, f: Double, peak: Double, d: Double): DoubleArray {
        val len = Math.ceil((d + 0.03) * sampleRate).toInt()
        val out = DoubleArray(len)
        val osc = AudioDsp.Oscillator(AudioDsp.Wave.SINE, sampleRate)
        val env = ExpEnvelope(listOf(0.0 to 0.0001, 0.0015 to Math.max(peak, 0.0002), d to 0.0001))
        for (i in 0 until len) {
            val t = i / sampleRate
            val freq = if (t >= d) f * 0.992 else f * Math.pow(0.992, t / d)
            out[i] = osc.next(freq) * env.valueAt(t)
        }
        return out
    }

    /** audio.ts chime: each note is a tone plus its octave-ish partner (2.01x), notes spaced by [gap] seconds. */
    fun chime(sampleRate: Double, notes: List<Double>, gap: Double = 0.2): DoubleArray {
        val parts = mutableListOf<Pair<Double, DoubleArray>>()
        notes.forEachIndexed { i, f ->
            val t = i * gap
            parts.add(t to toneBurst(sampleRate, 0.17, f, f, 0.005, 0.75))
            parts.add(t to toneBurst(sampleRate, 0.05, f * 2.01, f * 2.01, 0.005, 0.45))
        }
        return sum(parts, sampleRate)
    }

    /**
     * audio.ts loopBuffer: a one-second mono loop filled by [fill], then peak-normalised to 1.
     * Random draws come from a seeded noise source (the web uses Math.random), so renders are repeatable.
     */
    private fun loopBuffer(sampleRate: Double, fill: (DoubleArray, Double, () -> Double) -> Unit, seed: Long): DoubleArray {
        val sr = sampleRate
        val d = DoubleArray(sampleRate.toInt())
        val noise = AudioDsp.Noise(seed)
        // Math.random() in [0, 1) and the [-1, 1) form used by the web clacks and rattles
        val rnd01: () -> Double = { (noise.next() + 1) / 2 }
        fill(d, sr, rnd01)
        var pk = 0.0
        for (v in d) pk = Math.max(pk, Math.abs(v))
        if (pk > 0) for (i in d.indices) d[i] /= pk
        return d
    }

    /**
     * audio.ts shakerLoop: a stick-shaker motor. Each revolution is a heavy thump as the eccentric weight swings round,
     * a smaller rebound, and loose linkage rattle. [revs] revolutions per second.
     */
    fun shakerLoop(sampleRate: Double, revs: Int, seed: Long = 1L): DoubleArray = loopBuffer(sampleRate, { d, sr, rnd ->
        val noiseSrc = AudioDsp.Noise(seed + 17)
        fun add(at: Int, k: Int, v: Double) { val i = (at + k) % d.size; d[i] += v }
        fun thump(at: Int, amp: Double, f: Double, decay: Double) {
            val len = Math.floor(decay * 4 * sr).toInt()
            for (k in 0 until len) {
                val t = k / sr
                add(at, k, amp * Math.sin(2 * Math.PI * (f + f * 0.45 * Math.exp(-t / 0.008)) * t) * Math.exp(-t / decay))
            }
        }
        fun rattle(at: Int, amp: Double, decay: Double) {
            val len = Math.floor(decay * 5 * sr).toInt()
            for (k in 0 until len) add(at, k, amp * noiseSrc.next() * Math.exp(-(k / sr) / decay))
        }
        val per = sr / revs
        for (n in 0 until revs) {
            val at = Math.floor(n * per).toInt()
            val amp = 0.8 + rnd() * 0.4
            thump(at, amp, 92.0, 0.02)
            rattle(at, amp * 0.55, 0.0035)
            val re = at + Math.floor(per * (0.4 + rnd() * 0.1)).toInt() // weight rebounding off its stop
            thump(re, amp * 0.42, 150.0, 0.012)
            rattle(re, amp * 0.28, 0.003)
            for (j in 0 until 2) rattle(at + Math.floor(per * (0.12 + rnd() * 0.75)).toInt(), 0.1 + rnd() * 0.12, 0.002) // loose linkage
        }
    }, seed)

    /** audio.ts clackLoop: the Mach / airspeed warning clacker, a relay hammer with a small armature bounce. [hz] clacks per second. */
    fun clackLoop(sampleRate: Double, hz: Int, seed: Long = 1L): DoubleArray = loopBuffer(sampleRate, { d, sr, _ ->
        val noiseSrc = AudioDsp.Noise(seed + 31)
        val per = sr / hz
        for (n in 0 until hz) {
            val at = Math.floor(n * per).toInt()
            val amp = if (n % 2 != 0) 0.88 else 1.0
            fun hit(off: Double, a: Double) {
                val len = Math.floor(0.03 * sr).toInt()
                val start = at + Math.floor(off * sr).toInt()
                for (k in 0 until len) {
                    val t = k / sr
                    val v = noiseSrc.next() * Math.exp(-t / 0.0045) + 0.7 * Math.sin(2 * Math.PI * 210 * t) * Math.exp(-t / 0.011)
                    d[(start + k) % d.size] += a * v
                }
            }
            hit(0.0, amp)
            hit(0.0065, amp * 0.45) // bounce
        }
    }, seed)
}
