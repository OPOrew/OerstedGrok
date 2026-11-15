package com.oersted.trainer.sim

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Software-mixer DSP primitives for the synthesised cockpit sounds (audio.ts). Pure Kotlin, so they run and are tested locally.
 * Design decision: sounds are rendered into PCM by a software mixer rather than AudioTrack voices; this matches the Web Audio graph
 * (gain, biquad filters, oscillators, noise) and can be checked without a device.
 */
object AudioDsp {
    /** Web Audio BiquadFilterNode types used by audio.ts. */
    enum class FilterType { LOWPASS, HIGHPASS, BANDPASS }

    /** Direct-form-I biquad coefficients, normalised by a0 (RBJ cookbook, as in the Web Audio spec). */
    class Biquad(type: FilterType, sampleRate: Double, freq: Double, q: Double) {
        private var b0 = 0.0
        private var b1 = 0.0
        private var b2 = 0.0
        private var a1 = 0.0
        private var a2 = 0.0
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        init {
            retune(type, sampleRate, freq, q)
        }

        /** Recomputes the coefficients in place, keeping the filter state (a swept filter, as Web Audio does when frequency changes). */
        fun retune(type: FilterType, sampleRate: Double, freq: Double, q: Double) {
            val w0 = 2 * PI * freq / sampleRate
            val alpha = sin(w0) / (2 * q)
            val c = cos(w0)
            val (nb0, nb1, nb2) = when (type) {
                FilterType.LOWPASS -> Triple((1 - c) / 2, 1 - c, (1 - c) / 2)
                FilterType.HIGHPASS -> Triple((1 + c) / 2, -(1 + c), (1 + c) / 2)
                FilterType.BANDPASS -> Triple(alpha, 0.0, -alpha)
            }
            val a0 = 1 + alpha
            b0 = nb0 / a0
            b1 = nb1 / a0
            b2 = nb2 / a0
            a1 = (-2 * c) / a0
            a2 = (1 - alpha) / a0
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

    /** Oscillator waveforms (OscillatorType in Web Audio). */
    enum class Wave { SINE, SQUARE, TRIANGLE, SAWTOOTH }

    /** Phase-accumulating oscillator returning values in [-1, 1]. */
    class Oscillator(private val wave: Wave, private val sampleRate: Double) {
        private var phase = 0.0

        fun next(freqHz: Double): Double {
            val v = when (wave) {
                Wave.SINE -> sin(2 * PI * phase)
                Wave.SQUARE -> if (phase < 0.5) 1.0 else -1.0
                Wave.TRIANGLE -> 1 - 4 * kotlin.math.abs(phase - 0.5)
                Wave.SAWTOOTH -> 2 * phase - 1
            }
            phase += freqHz / sampleRate
            phase -= kotlin.math.floor(phase)
            return v
        }
    }

    /** Deterministic white noise (a 32-bit LCG) so renders are repeatable and testable. */
    class Noise(seed: Long = 1L) {
        private var state = seed and 0xFFFFFFFFL

        fun next(): Double {
            state = (state * 1664525L + 1013904223L) and 0xFFFFFFFFL
            return state / 2147483648.0 - 1.0
        }
    }

    /**
     * Gain envelope with Web Audio ramp semantics: linear ramps between (time, value) points, holding the last value after the end.
     * Points must be in ascending time order.
     */
    class Envelope(private val points: List<Pair<Double, Double>>) {
        init {
            require(points.isNotEmpty()) { "envelope needs at least one point" }
            require(points.zipWithNext().all { (a, b) -> b.first >= a.first }) { "envelope times must not decrease" }
        }

        fun valueAt(t: Double): Double {
            if (t <= points.first().first) return points.first().second
            for (i in 0 until points.size - 1) {
                val (t0, v0) = points[i]
                val (t1, v1) = points[i + 1]
                if (t < t1) {
                    if (t1 == t0) return v1
                    return v0 + (v1 - v0) * (t - t0) / (t1 - t0)
                }
            }
            return points.last().second
        }
    }

    /**
     * Gain envelope with Web Audio exponentialRampToValueAtTime semantics: v(t) = v0 * (v1 / v0) ^ ((t - t0) / (t1 - t0)).
     * Used by audio.ts for almost every gain. Values must be positive (the web uses 0.0001 as the floor).
     */
    class ExpEnvelope(private val points: List<Pair<Double, Double>>) {
        init {
            require(points.isNotEmpty()) { "envelope needs at least one point" }
            require(points.all { it.second > 0.0 }) { "exponential ramps need positive values" }
            require(points.zipWithNext().all { (a, b) -> b.first >= a.first }) { "envelope times must not decrease" }
        }

        fun valueAt(t: Double): Double {
            if (t <= points.first().first) return points.first().second
            for (i in 0 until points.size - 1) {
                val (t0, v0) = points[i]
                val (t1, v1) = points[i + 1]
                if (t < t1) {
                    if (t1 == t0) return v1
                    return v0 * Math.pow(v1 / v0, (t - t0) / (t1 - t0))
                }
            }
            return points.last().second
        }
    }

    /**
     * Web Audio AudioParam with setValueAtTime and setTargetAtTime (first-order approach with time constant tau).
     * Events must be given in ascending time order. audio.ts uses setTargetAtTime for most of its live parameters.
     */
    class TargetParam(initial: Double) {
        /**
         * One scheduled event: a held value (isTarget false) or an approach to [target] with time constant [tau] (isTarget true).
         * [startV] is the value the approach starts from, captured when the event is added, so evaluation never recurses.
         */
        private class Event(val t: Double, val isTarget: Boolean, val v: Double, val target: Double, val tau: Double, val startV: Double)

        private val events = mutableListOf(Event(Double.NEGATIVE_INFINITY, false, initial, initial, 0.0, initial))

        /** History kept for queries; older events are dropped so a live parameter does not grow without limit. */
        private val maxHistory = 64

        /** Holds [v] from time [t] on. */
        fun setValueAtTime(v: Double, t: Double) = add(Event(t, false, v, v, 0.0, v))

        /** Approaches [target] from the value it has at [t], with time constant [tau] seconds. */
        fun setTargetAtTime(target: Double, t: Double, tau: Double) = add(Event(t, true, 0.0, target, tau, valueAt(t)))

        private fun add(e: Event) {
            events.add(e)
            if (events.size > maxHistory) events.removeAt(0)
        }

        /** Value at [t], from the latest event at or before it. */
        fun valueAt(t: Double): Double {
            var i = events.size - 1
            while (i > 0 && events[i].t > t) i--
            val e = events[i]
            if (!e.isTarget) return e.v
            if (e.tau <= 0.0) return e.target
            return e.target + (e.startV - e.target) * Math.exp(-(t - e.t) / e.tau)
        }
    }

    /** Equal-power stereo pan (Web Audio StereoPannerNode): returns (left gain, right gain) for pan in [-1, 1]. */
    fun equalPowerPan(pan: Double): Pair<Double, Double> {
        val x = (pan.coerceIn(-1.0, 1.0) + 1) / 2
        return cos(PI / 2 * x) to sin(PI / 2 * x)
    }

    /** Stereo accumulation buffer for the software mixer. Times are seconds from the start of the render. */
    class StereoMixer(private val sampleRate: Double, seconds: Double) {
        val left = DoubleArray((seconds * sampleRate).toInt())
        val right = DoubleArray((seconds * sampleRate).toInt())

        /** Adds a mono sound starting at [atSec], scaled by [gain] and panned by [pan]. Samples past the end are dropped. */
        fun add(samples: DoubleArray, atSec: Double, pan: Double, gain: Double) {
            val (gl, gr) = equalPowerPan(pan)
            val start = (atSec * sampleRate).toInt()
            for (i in samples.indices) {
                val n = start + i
                if (n < 0 || n >= left.size) continue
                left[n] += samples[i] * gain * gl
                right[n] += samples[i] * gain * gr
            }
        }
    }

    /** Root-mean-square of a buffer; used by the tests to check level and by the mixer for metering. */
    fun rms(samples: DoubleArray): Double {
        if (samples.isEmpty()) return 0.0
        var s = 0.0
        for (v in samples) s += v * v
        return sqrt(s / samples.size)
    }
}
