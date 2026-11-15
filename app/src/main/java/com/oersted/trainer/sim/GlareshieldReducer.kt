package com.oersted.trainer.sim

import com.oersted.trainer.sim.MipLocal.MipState

/**
 * State changes made by the glareshield controls (GlareshieldPanel.tsx handlers). Pure: each takes the MIP state and returns the
 * new state. The captain's controller writes the fd* fields; the first officer's writes the fd*2 fields ([second] = true).
 */
object GlareshieldReducer {
    fun setFdMode(s: MipState, second: Boolean, mode: Int): MipState =
        if (second) s.copy(fdMode2 = mode) else s.copy(fdMode = mode)

    /** ALT HOLD toggle: applied only when [GlareshieldLogic.canToggleAltHold] allows it for the controller's own mode. */
    fun setFdAltHold(s: MipState, second: Boolean, on: Boolean, fdUp: Boolean): MipState {
        val mode = if (second) s.fdMode2 else s.fdMode
        val current = if (second) s.fdAlt2 else s.fdAlt
        if (!GlareshieldLogic.canToggleAltHold(current, fdUp, mode)) return s
        return if (second) s.copy(fdAlt2 = on) else s.copy(fdAlt = on)
    }

    fun setFdPitch(s: MipState, second: Boolean, pitch: Double): MipState =
        if (second) s.copy(fdPitch2 = pitch) else s.copy(fdPitch = pitch)

    fun setApNav(s: MipState, nav: Int): MipState = s.copy(apNav = nav)

    fun setApPitch(s: MipState, pitch: Int): MipState = s.copy(apPitch = pitch)

    /** Course knob step: the selected course wraps into 0..360. */
    fun stepSelCrs(s: MipState, delta: Double): MipState = s.copy(selCrs = GlareshieldLogic.stepHeading(s.selCrs, delta))

    /** Heading knob step: the selected heading wraps into 0..360. */
    fun stepSelHdg(s: MipState, delta: Double): MipState = s.copy(selHdg = GlareshieldLogic.stepHeading(s.selHdg, delta))

    /** HDG SEL switch toggles between 2 (engaged) and 1 (centre). */
    fun toggleHdgSwitch(s: MipState): MipState = s.copy(apHdgSw = GlareshieldLogic.nextHdgSwitch(s.apHdgSw))

    /** AIL or ELEV engage paddle: engaging is refused without the interlocks; disengaging always works. */
    fun setEngage(s: MipState, aileron: Boolean, engaged: Boolean, interlocksMet: Boolean): MipState {
        if (!GlareshieldLogic.canSetEngaged(engaged, interlocksMet)) return s
        return if (aileron) s.copy(apAil = engaged) else s.copy(apElev = engaged)
    }

    /** A/P DISC warning light: press to reset. */
    fun resetApDisc(s: MipState): MipState = s.copy(apWarn = false)
}
