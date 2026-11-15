package com.oersted.trainer.sim

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Small instrument readouts from MainInstrumentPanel.tsx (pure text and angle rules). */
object InstrumentReadouts {
    /** JS Math.round (half up), as the web uses for every displayed number. */
    fun jsRound(x: Double): Long = floor(x + 0.5).toLong()

    /** pad(n, w) (web): rounded and zero-padded to [w] digits; never truncated. */
    fun padZero(n: Double, w: Int): String = jsRound(n).toString().padStart(w, '0')

    /** Yaw damper indicator needle angle: the L / R test deflects it to -32 / +32 degrees, otherwise centred. */
    fun yawNeedleDeg(test: Int): Double = when (test) {
        1 -> -32.0
        2 -> 32.0
        else -> 0.0
    }

    /** Red LED fuel quantity text: blank without DC power, 888 during the lamp test, else the value (right-aligned to three). */
    fun qtyLedText(dcPower: Boolean, value: Double, testing: Boolean): String {
        if (!dcPower) return ""
        val shown = if (testing) 888L else jsRound(value)
        return shown.toString().padStart(3, ' ')
    }

    /** Standby airspeed strip (IAS): zero-padded to three digits and clamped to 0-999 when powered, "060" when not. */
    fun iasStripText(ias: Double, powered: Boolean): String =
        if (powered) padZero(max(0.0, min(999.0, ias)), 3) else "060"
}
