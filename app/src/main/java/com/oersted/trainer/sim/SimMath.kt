package com.oersted.trainer.sim

/** Ported from src/lib/sim/derive.ts (approach, spool). Keep numerically identical to the TS. */
object SimMath {
    fun approach(value: Double, target: Double, rate: Double, dt: Double): Double = when {
        value < target -> minOf(target, value + rate * dt)
        value > target -> maxOf(target, value - rate * dt)
        else -> value
    }

    data class Rate(val k: Double, val max: Double, val min: Double)

    /** Turbine spool model: proportional approach with rate limits. */
    fun spool(value: Double, target: Double, dt: Double, accel: Rate, decel: Rate): Double {
        val err = target - value
        if (kotlin.math.abs(err) < 1e-4) return target
        val c = if (err > 0) accel else decel
        val rate = minOf(c.max, maxOf(c.min, kotlin.math.abs(err) * c.k))
        val stepSize = rate * dt
        return if (kotlin.math.abs(err) <= stepSize) target else value + kotlin.math.sign(err) * stepSize
    }

    val N1_ACCEL = Rate(0.75, 15.0, 3.0)
    val N1_ACCEL_TO = Rate(1.3, 26.0, 6.0)
    val N1_DECEL = Rate(0.6, 13.0, 2.5)
    val N2_ACCEL = Rate(0.85, 9.0, 2.5)
    val N2_DECEL = Rate(0.65, 8.0, 2.0)
}
