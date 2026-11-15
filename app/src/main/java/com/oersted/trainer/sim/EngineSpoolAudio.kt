package com.oersted.trainer.sim

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Port of engineSpoolAudio.ts: acoustic spool model for the engine sound (pure maths, no audio API).
 * Each rotor is a velocity-limited, critically smoothed follower, so the sound has inertia and never steps.
 * Spool-down is lazier than spool-up, and the jet roar lags the rotors going up and drops sooner coming down.
 */
object EngineSpoolAudio {
    /** Rotor tuning: tau (rounded finish, s), max (max rate, %/s), vTau (velocity filter, s). */
    data class RotorTune(val tau: Double, val max: Double, val vTau: Double)

    // Idle to takeoff N1 (about 32 to 92 %) takes roughly 6-8 s once the acoustic lag is added to the sim's own spool.
    val N1_UP = RotorTune(tau = 1.0, max = 10.5, vTau = 0.6)
    val N1_DOWN = RotorTune(tau = 1.3, max = 8.5, vTau = 0.75)
    val N2_UP = RotorTune(tau = 0.9, max = 7.0, vTau = 0.5)
    val N2_DOWN = RotorTune(tau = 1.2, max = 6.0, vTau = 0.65)

    /** Jet roar follows thrust (0..1) a little behind the rotors going up, and drops off sooner coming down. */
    const val JET_UP_TAU = 1.1
    const val JET_DOWN_TAU = 0.7

    data class AcousticSpool(
        /** smoothed fan speed, % */
        val n1: Double,
        /** smoothed core speed, % */
        val n2: Double,
        /** %/s */
        val v1: Double,
        /** %/s */
        val v2: Double,
        /** 0..1 thrust above idle as the exhaust sees it */
        val jet: Double,
        val ready: Boolean,
    )

    fun newAcousticSpool(): AcousticSpool = AcousticSpool(n1 = 0.0, n2 = 0.0, v1 = 0.0, v2 = 0.0, jet = 0.0, ready = false)

    /** One rotor step: returns (position, velocity). Never overshoots the target. */
    private fun follow(x: Double, v: Double, target: Double, h: Double, up: RotorTune, down: RotorTune): Pair<Double, Double> {
        val err = target - x
        val c = if (err >= 0) up else down
        val want = max(-down.max, min(up.max, err / c.tau))
        // when the target is hit from above or below, the same filter kills the velocity
        val a = 1 - exp(-h / c.vTau)
        var nv = v + (want - v) * a
        var nx = x + nv * h
        // never overshoot the target (no ringing): clamp and kill the velocity
        if ((target - x) * (target - nx) <= 0) {
            nx = target
            nv = 0.0
        }
        return Pair(nx, nv)
    }

    /** Thrust above idle, 0..1, from fan speed (idle N1 about 32 %, takeoff about 92 to 100 %). */
    fun thrustFromN1(n1: Double): Double = max(0.0, min(1.0, (n1 - 32) / 60))

    /**
     * Advance the model by [dt] seconds toward the sim's current N1 / N2 (dt is clamped to 0..1 s).
     * [snap] jumps straight to the targets: the first update, or sound just switched on while the engine runs.
     */
    fun stepAcousticSpool(s: AcousticSpool, n1Target: Double, n2Target: Double, dt: Double, snap: Boolean = false): AcousticSpool {
        if (snap || !s.ready) {
            return AcousticSpool(n1 = n1Target, n2 = n2Target, v1 = 0.0, v2 = 0.0, jet = thrustFromN1(n1Target), ready = true)
        }
        var n1 = s.n1
        var n2 = s.n2
        var v1 = s.v1
        var v2 = s.v2
        var jet = s.jet
        var rem = max(0.0, min(dt, 1.0))
        while (rem > 1e-6) {
            val h = min(0.02, rem)
            rem -= h
            val r1 = follow(n1, v1, n1Target, h, N1_UP, N1_DOWN)
            n1 = r1.first
            v1 = r1.second
            val r2 = follow(n2, v2, n2Target, h, N2_UP, N2_DOWN)
            n2 = r2.first
            v2 = r2.second
            val jt = thrustFromN1(n1)
            val tau = if (jt >= jet) JET_UP_TAU else JET_DOWN_TAU
            jet += (jt - jet) * (1 - exp(-h / tau))
        }
        return AcousticSpool(n1 = n1, n2 = n2, v1 = v1, v2 = v2, jet = jet, ready = true)
    }
}
