package com.oersted.trainer.sim

import kotlin.math.max
import kotlin.math.min

/**
 * Dial angle mappings from MainInstrumentPanel.tsx (pure; the Compose dials draw with these).
 * Angles are clockwise degrees from twelve o'clock, as the web SVG uses.
 */
object DialGeometry {
    private fun clamp(v: Double, lo: Double, hi: Double) = max(lo, min(hi, v))

    /** RA_PTS: radio altimeter scale, hundreds of feet -> angle. */
    private val RA_PTS = listOf(0.0 to 0.0, 5.0 to 180.0, 10.0 to 236.0, 15.0 to 268.0, 20.0 to 298.0, 25.0 to 328.0)

    /** raAng (web): piecewise-linear radio altimeter angle for [hundreds] (clamped to 0..25), 328 past the last point. */
    fun raAngleDeg(hundreds: Double): Double {
        val v = clamp(hundreds, 0.0, 25.0)
        for (i in 1 until RA_PTS.size) {
            val (k1, a1) = RA_PTS[i]
            val (k0, a0) = RA_PTS[i - 1]
            if (v <= k1) return a0 + ((v - k0) / (k1 - k0)) * (a1 - a0)
        }
        return 328.0
    }

    /** FLAP_PTS: flap unit positions and their scale angles (UP, 1, 2, 5, 10, 15, 25, 30, 40). */
    private val FLAP_PTS = listOf(
        Triple(0.0, 250.0, "UP"), Triple(1.0, 292.0, "1"), Triple(2.0, 336.0, "2"), Triple(5.0, 20.0, "5"),
        Triple(10.0, 62.0, "10"), Triple(15.0, 96.0, "15"), Triple(25.0, 134.0, "25"), Triple(30.0, 162.0, "30"), Triple(40.0, 192.0, "40"),
    )

    /** FlapDial (web): the needle angle for [units], interpolated on the non-linear scale; the scale wraps past 360. */
    fun flapAngleDeg(units: Double): Double {
        val v = clamp(units, 0.0, 40.0)
        var ang = 250.0
        for (i in 1 until FLAP_PTS.size) {
            val (u0, a0, _) = FLAP_PTS[i - 1]
            val (u1, a1raw, _) = FLAP_PTS[i]
            val a1 = if (a1raw < a0) a1raw + 360.0 else a1raw
            if (v <= u1) return a0 + ((v - u0) / (u1 - u0)) * (a1 - a0)
            ang = a1
        }
        return ang
    }
}
