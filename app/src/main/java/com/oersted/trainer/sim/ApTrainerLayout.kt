package com.oersted.trainer.sim

import kotlin.math.max

/**
 * Autopilot trainer view layout (ApTrainerView.tsx): the scroll offset that centres a design-width area in the viewport.
 * The design width is 3060 px; the areas are centred at the design x positions in [JUMP].
 */
object ApTrainerLayout {
    const val DESIGN_W = 3060.0

    /** horizontal centres (design px) of the areas to jump to */
    val JUMP: Map<String, Double> = mapOf("capt" to 640.0, "ap" to 1530.0, "fo" to 2560.0)

    /** The zoom levels offered by the size buttons. */
    val SCALES: List<Double> = listOf(0.35, 0.5, 0.75, 1.0)

    /** web scrollTo left: max(0, centre * scale - clientWidth / 2). */
    fun scrollLeft(area: String, scale: Double, viewportWidthPx: Double): Double {
        val centre = JUMP[area] ?: return 0.0
        return max(0.0, centre * scale - viewportWidthPx / 2.0)
    }

    /** The view opens on the captain's HSI / RMI for navigation, and on the glareshield A/P controls otherwise. */
    fun openingArea(focus: String): String = if (focus == "nav") "capt" else "ap"
}
