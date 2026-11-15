package com.oersted.trainer.sim

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * Pure rules of components/panel/PedestalLevers.tsx: the FLAP lever and SPEED BRAKE lever on the centre pedestal.
 * Both are dragged (or tapped on a detent) and snap to the nearest gate. The Compose views are in ui/pedestal/PedestalLevers.kt.
 */
object PedestalLeversRules {
    /** web PAD: space above the first gate and below the last */
    const val PAD = 18.0

    /** web DetentGate `y(i)`: the gate's distance from the top of the track */
    fun gateY(i: Int, n: Int, height: Double): Double = PAD + (i * (height - 2 * PAD)) / max(1, n - 1)

    /** web DetentGate `pick`: the gate nearest the pointer ([relY] from the top of the track); null when it is the current one */
    fun pickGate(relY: Double, n: Int, height: Double, index: Int): Int? {
        var best = 0
        for (i in 1 until n) if (abs(gateY(i, n, height) - relY) < abs(gateY(best, n, height) - relY)) best = i
        return if (best != index) best else null
    }

    /** web flap detents, top to bottom */
    val FLAP_VALUES: List<Double> = listOf(0.0, 1.0, 2.0, 5.0, 10.0, 15.0, 25.0, 30.0, 40.0)

    /** web FlapLeverQuadrant `idx`: the detent nearest the lever setting (the first one on a tie) */
    fun flapIndex(flaps: Double): Int {
        var best = 0
        for (i in FLAP_VALUES.indices) if (abs(FLAP_VALUES[i] - flaps) < abs(FLAP_VALUES[best] - flaps)) best = i
        return best
    }

    /** web "LEVER UP" / "LEVER 15" text of a detent */
    fun flapLeverText(value: Double): String = if (value == 0.0) "UP" else value.toInt().toString()

    /** web log note of a flap selection */
    fun flapNote(value: Double): String = "FLAP LEVER ${flapLeverText(value)}"

    /** web "TE n" text: the trailing edge position rounded as JS Math.round */
    fun trailingEdgeText(flapPos: Double): String = floor(flapPos + 0.5).toInt().toString()

    /** web SpeedBrake values and legends, top of the track first */
    val SPEED_BRAKE_VALUES: List<String> = listOf("DOWN", "ARMED", "FLIGHT", "UP")
    val SPEED_BRAKE_NAMES: List<String> = listOf("DOWN", "ARM", "FLT DET", "UP")

    fun speedBrakeIndex(speedBrake: String): Int = max(0, SPEED_BRAKE_VALUES.indexOf(speedBrake))

    /** web log note of a speed brake selection */
    fun speedBrakeNote(value: String): String = "SPEED BRAKE $value"

    /** web status chip text under the speed brake lever */
    fun speedBrakeStatus(speedBrake: String): String = when (speedBrake) {
        "ARMED" -> "ARMED"
        "FLIGHT" -> "FLIGHT DETENT"
        else -> speedBrake
    }

    enum class StatusTone { OK, NEUTRAL, CAUTION }

    /** web status chip colours: ARMED ok, DOWN neutral, anything else caution */
    fun speedBrakeTone(speedBrake: String): StatusTone = when (speedBrake) {
        "ARMED" -> StatusTone.OK
        "DOWN" -> StatusTone.NEUTRAL
        else -> StatusTone.CAUTION
    }
}
