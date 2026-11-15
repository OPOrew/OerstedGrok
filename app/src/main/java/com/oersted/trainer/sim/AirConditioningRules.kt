package com.oersted.trainer.sim

/**
 * Handler rules behind the Air Conditioning and Cabin Pressurization panels (routes/cockpit.tsx, ATA 21).
 * Each function returns the new state; the view keeps the layout, the live temperatures and the wiring drawings.
 * The web's `tempMode ?? "AUTO"` default is applied here, as it is in the panel.
 */
object AirConditioningRules {
    /** The four cabin temperature selector modes, in web order. */
    val TEMP_MODES: List<String> = listOf("AUTO", "MANUAL_COOL", "MANUAL_WARM", "OFF")

    /** The four pressurization modes, in web order. */
    val PRESS_MODES: List<String> = listOf("AUTO", "STBY", "MAN_AC", "MAN_DC")

    /** The selector mode with web defaults (a missing mode reads as AUTO). */
    fun tempMode(s: SimState, left: Boolean): String = (if (left) s.tempModeL else s.tempModeR) ?: "AUTO"

    private inline fun edited(s: SimState, edit: SimState.() -> Unit): SimState {
        val c = s.copy()
        c.edit()
        return c
    }

    /** Web PACK L / PACK R buttons. */
    fun togglePack(s: SimState, left: Boolean): SimState = edited(s) {
        if (left) packL = !s.packL else packR = !s.packR
    }

    /** Web PACK TRIP RESET buttons. */
    fun resetPackTrip(s: SimState, left: Boolean): SimState = edited(s) {
        if (left) packTripL = false else packTripR = false
    }

    /** Web P5-17 selector mode buttons. */
    fun setTempMode(s: SimState, left: Boolean, mode: String): SimState = edited(s) {
        if (left) tempModeL = mode else tempModeR = mode
    }

    /** Web selector button highlight: MANUAL is shown under MAN COOL. */
    fun modeButtonLit(mode: String, button: String): Boolean =
        mode == button || (button == "MANUAL_COOL" && mode == "MANUAL")

    /** Web AUTO SELECTOR slider: enabled only in AUTO. */
    fun selectorEnabled(s: SimState, left: Boolean): Boolean = tempMode(s, left) == "AUTO"

    /** Web AUTO SELECTOR slider: a value from 65 to 85 degrees F (the view clamps the slider). */
    fun setTempSelector(s: SimState, left: Boolean, valueF: Double): SimState = edited(s) {
        if (left) tempSelectL = valueF else tempSelectR = valueF
    }

    /** Web fine mix slider: enabled unless the selector is AUTO or OFF. Moving it puts the selector in MANUAL. */
    fun mixEnabled(s: SimState, left: Boolean): Boolean {
        val m = tempMode(s, left)
        return m != "AUTO" && m != "OFF"
    }

    fun setMix(s: SimState, left: Boolean, value: Double): SimState = edited(s) {
        if (left) {
            mixCabin = value
            tempModeL = "MANUAL"
        } else {
            mixPass = value
            tempModeR = "MANUAL"
        }
    }

    /** Web TEMP CONTROL FAULT selector. */
    fun setTempControlFault(s: SimState, left: Boolean, value: String): SimState = edited(s) {
        if (left) tempControlFaultL = value else tempControlFaultR = value
    }

    /** Web pressurization mode buttons. */
    fun setPressMode(s: SimState, mode: String): SimState = edited(s) { pressMode = mode }

    /** Web OUTFLOW VALVE buttons are enabled only in the manual modes. */
    fun outflowEnabled(s: SimState): Boolean = s.pressMode == "MAN_AC" || s.pressMode == "MAN_DC"

    /** Web OUTFLOW VALVE OPEN / CLOSE: 0.1 per press, held within 0 and 1. */
    fun stepOutflow(s: SimState, open: Boolean): SimState {
        if (!outflowEnabled(s)) return s
        return edited(s) { outflow = if (open) minOf(1.0, s.outflow + 0.1) else maxOf(0.0, s.outflow - 0.1) }
    }
}
