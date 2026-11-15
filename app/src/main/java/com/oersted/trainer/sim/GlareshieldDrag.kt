package com.oersted.trainer.sim

import kotlin.math.abs

/**
 * Drag rules of the glareshield controls (GlareshieldPanel.tsx: CourseKnob, HdgButton). Pure.
 * Each step size is the web's: 6 degrees of knob rotation per course step, 3 px of drag per heading step.
 */
object GlareshieldDrag {
    /** Degrees of knob rotation per course step. */
    const val COURSE_STEP_DEG = 6.0

    /** Pixels of drag per heading step. */
    const val HDG_STEP_PX = 3.0

    /** Drag beyond this many pixels counts as a drag, not a tap (sticky for the rest of the press). */
    const val HDG_MOVED_PX = 3.0

    /** The web's displayed heading: normalised to 0..360 and rounded, zero-padded to three digits. */
    fun headingText(v: Double): String = InstrumentReadouts.padZero(((v % 360) + 360) % 360, 3)

    /** Angle change between two knob angles, wrapped into -180..180 (CourseKnob pointer move). */
    fun wrapAngleDelta(angleDeg: Double, lastDeg: Double): Double {
        var d = angleDeg - lastDeg
        if (d > 180) d -= 360
        if (d < -180) d += 360
        return d
    }

    /**
     * Course knob: add the rotation to the accumulator and take whole steps (trunc, as the web does). Returns the steps and
     * the leftover accumulator.
     */
    fun courseSteps(acc: Double, deltaDeg: Double): Pair<Int, Double> {
        val total = acc + deltaDeg
        val s = (total / COURSE_STEP_DEG).toInt() // toInt truncates toward zero, as Math.trunc does
        return s to total - s * COURSE_STEP_DEG
    }

    /** HDG SEL button drag state: the last pointer Y, the accumulated pixels and whether the press has become a drag. */
    data class HdgDrag(val y: Double, val acc: Double, val moved: Boolean) {
        companion object {
            fun start(y: Double) = HdgDrag(y = y, acc = 0.0, moved = false)
        }
    }

    /**
     * One pointer move. Upward drag gives positive steps (the web's dy = last Y - Y). Steps are only taken once the press
     * has become a drag. Returns the new state and the steps to apply.
     */
    fun hdgMove(state: HdgDrag, y: Double): Pair<HdgDrag, Int> {
        val dy = state.y - y
        val acc = state.acc + dy
        val moved = state.moved || abs(acc) > HDG_MOVED_PX
        val steps = (acc / HDG_STEP_PX).toInt()
        if (steps != 0 && moved) {
                return HdgDrag(y, acc - steps * HDG_STEP_PX, moved) to steps
        }
        return HdgDrag(y, acc, moved) to 0
    }

    /** A release with no drag is a tap, which toggles the button. */
    fun isTap(state: HdgDrag): Boolean = !state.moved
}
