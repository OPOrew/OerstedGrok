package com.oersted.trainer.sim

/**
 * Landing gear lever rules (GearSelectorLever in MainInstrumentPanel.tsx). Pure.
 * Positions are the web's GearLever values: "UP", "OFF", "DN" (the detents, top to bottom in the slot).
 */
object GearLeverLogic {
    val POSITIONS: List<String> = listOf("UP", "OFF", "DN")

    /** Detent height as a percentage of the slot, for UP / OFF / DN. */
    val SLOT_TOP_PERCENT: List<Int> = listOf(15, 50, 80)

    data class Selection(val gear: String, val locked: Boolean, val message: String?)

    /**
     * A request to move the lever to [next]. UP is inhibited on the ground unless the override is held: the lever
     * goes to OFF instead and the message says why. [wow] is weight-on-wheels.
     */
    fun select(next: String, wow: Boolean, override: Boolean): Selection {
        if (next == "UP" && wow && !override) {
            return Selection(
                gear = "OFF",
                locked = true,
                message = "GEAR SELECTOR LOCKED — UP inhibited on ground.",
            )
        }
        return Selection(gear = next, locked = false, message = null)
    }

    /** Tap on the lever cycles to the next detent: UP -> OFF -> DN -> UP. Unknown current position starts at UP. */
    fun nextFrom(current: String): String {
        val idx = POSITIONS.indexOf(current).coerceAtLeast(0)
        return POSITIONS[(idx + 1) % POSITIONS.size]
    }

    /** Index of the detent for [gear], 0 when unknown (the web's Math.max(0, indexOf)). */
    fun index(gear: String): Int = POSITIONS.indexOf(gear).coerceAtLeast(0)

    /** The lock-out tag under the lever is shown while on the ground. */
    fun showLockOutTag(wow: Boolean): Boolean = wow
}
