package com.oersted.trainer.sim

/**
 * ALT HOLD toggle, engage paddles and the pitch command knob offset (GlareshieldPanel.tsx: FdController, AltHold,
 * EngagePaddle). Pure. The FD mode number 1 is OFF on the web's MODE SEL knob.
 */
object GlareshieldLogic {
    const val FD_MODE_OFF = 1

    /** The ALT HOLD switch can be pressed to turn it off, or to turn it on when the FD is up and not OFF. */
    fun canToggleAltHold(altHoldOn: Boolean, fdUp: Boolean, fdMode: Int): Boolean =
        altHoldOn || (fdUp && fdMode != FD_MODE_OFF)

    /** The ALT HOLD lamp lights only while the switch is on and the power is on. */
    fun altHoldLampLit(on: Boolean, powered: Boolean): Boolean = on && powered

    /** Paddle rotation: a swing of -90 deg into the oval when engaged, flat (0) when disengaged. */
    fun paddleAngleDeg(engaged: Boolean): Double = if (engaged) -90.0 else 0.0

    /** A press on the paddle flips its state. */
    fun nextPaddle(engaged: Boolean): Boolean = !engaged

    /** The pitch command knob runs from -10 to +10 on the web's scale of 0..20 (value + 10), so the display offsets by 10. */
    fun pitchKnobValue(pitch: Double): Double = pitch + 10.0

    /** Inverse of [pitchKnobValue]: the knob reading back to a pitch command. */
    fun pitchFromKnob(knob: Double): Double = knob - 10.0

    /** Centre controller engage rule on the REQUESTED state: disengaging is always allowed; engaging needs the interlocks met. */
    fun canSetEngaged(requested: Boolean, interlocksMet: Boolean): Boolean = !requested || interlocksMet

    /** HDG SEL switch: engaged (2) and disengaged (1) toggle. */
    fun nextHdgSwitch(apHdgSw: Int): Int = if (apHdgSw == 2) 1 else 2

    /** Course / heading knob step: wraps into 0..360, unrounded, as the web's stepMod does. */
    fun stepHeading(value: Double, delta: Double): Double = ((value + delta) % 360 + 360) % 360

    /** INOP box left (FD or attitude failure, with power) and right (interlocks not met, with power). */
    fun inopLeftLit(powered: Boolean, fdFail: Boolean, attFail: Boolean): Boolean = powered && (fdFail || attFail)
    fun inopRightLit(powered: Boolean, interlocksMet: Boolean): Boolean = powered && !interlocksMet

    /** Centre controller marks: the AP NAV knob (MAN, VOR/LOC, AUTO APP, MAN G/S) and the AP PITCH knob (TURB, OFF, ALT HOLD). */
    val AP_NAV_ANGLES: List<Double> = listOf(0.0, 28.0, 55.0, 86.0)
    val AP_PITCH_ANGLES: List<Double> = listOf(-48.0, 0.0, 36.0)
}
