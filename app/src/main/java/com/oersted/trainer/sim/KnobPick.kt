package com.oersted.trainer.sim

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * Detent knob rules (GlareshieldPanel.tsx: Knob). Pure. A knob has marks at fixed angles (degrees, 0 = up, clockwise).
 * A pointer press picks the nearest mark; ties keep the lower index, as the web's strict "<" does.
 */
object KnobPick {
    /** Pointer angle about the knob centre, in degrees: dx = x - centre x, dy = y - centre y (screen y grows downwards). */
    fun pointerAngleDeg(dx: Double, dy: Double): Double = Math.toDegrees(atan2(dx, -dy))

    /** Index of the mark nearest to [angleDeg]. The first mark wins a tie. */
    fun nearestMark(angleDeg: Double, markAngles: List<Double>): Int {
        var best = 0
        for (i in 1 until markAngles.size) {
            if (abs(markAngles[i] - angleDeg) < abs(markAngles[best] - angleDeg)) best = i
        }
        return best
    }

    /** A press changes the value only when the nearest mark is a different one. */
    fun pressedValue(current: Int, angleDeg: Double, markAngles: List<Double>): Int? {
        val best = nearestMark(angleDeg, markAngles)
        return if (best != current) best else null
    }

    /** Keyboard step, clamped to the mark range. */
    fun stepped(value: Int, delta: Int, count: Int): Int = max(0, min(count - 1, value + delta))

    /** Pointer angle the knob pointer shows: the mark at the value, clamped into range; 0 when the list is empty. */
    fun pointerAngleFor(value: Int, markAngles: List<Double>): Double =
        markAngles.getOrNull(max(0, min(markAngles.size - 1, value))) ?: 0.0

    /** The FD mode knob marks (GA, OFF, HDG, VOR/LOC, AUTO APP, MAN GS) and the pitch command knob's 21 marks. */
    val FD_MODE_ANGLES: List<Double> = listOf(-125.0, -86.0, -52.0, -22.0, 6.0, 28.0)
    val PITCH_CMD_ANGLES: List<Double> = List(21) { i -> -105.0 + (i / 20.0) * 210.0 }
}
