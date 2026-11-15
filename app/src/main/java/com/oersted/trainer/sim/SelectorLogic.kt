package com.oersted.trainer.sim

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Small control and readout rules from MainInstrumentPanel.tsx: the three-position selector and the EPR counter. */
object SelectorLogic {
    /** Knob pointer angle for a three-position selector: 42 degrees per detent, centred on position 1. */
    fun knobAngleDeg(value: Int): Double = (value - 1) * 42.0

    /**
     * Pressing the knob steps it: a press on the left half of the knob moves one position down, the right half one up,
     * clamped to 0..2 (as the web's onPointerDown).
     */
    fun stepFromPress(value: Int, pressLeftOfCentre: Boolean): Int =
        max(0, min(2, value + if (pressLeftOfCentre) -1 else 1))

    /** EPR counter text: two decimals when powered, null (no text, the amber bar shows) when not. */
    fun eprText(value: Double, powered: Boolean): String? =
        if (powered) String.format(Locale.ROOT, "%.2f", value) else null
}
