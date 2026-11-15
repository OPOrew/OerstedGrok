package com.oersted.trainer.sim

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * Port of lrra.ts (ATA 34-33, dual radio altimeter training model).
 * Dual channels, antenna offset, self-test heights, excessive-rate blank, DH lights.
 */
object Lrra {
    data class LrraChannel(
        val powered: Boolean,
        val valid: Boolean,
        val heightFt: Double?,
        val dhFt: Double,
        val dhLight: Boolean,
        val testing: Boolean,
    )

    data class LrraResult(val ch1: LrraChannel, val ch2: LrraChannel)

    private const val LRRA_MAX_FT = 2500.0
    /** gear/antenna above ground when parked */
    private const val ANTENNA_OFFSET_FT = 7.0
    /** Self-test drives indicators to fixed heights (AMM-style cue). */
    private const val TEST_HEIGHT_FT = 100.0

    /** JS Math.round: floor(x + 0.5). */
    private fun jsRound(x: Double): Double = floor(x + 0.5)

    /**
     * TS resolveLrra. [vsFpm] is vertical speed in ft/min; excessive rate (|vs| > 6000) blanks the radio altitude.
     */
    fun resolveLrra(
        aglFt: Double?,
        vsFpm: Double = 0.0,
        dcPower: Boolean,
        cb1: Boolean,
        cb2: Boolean,
        fail1: Boolean,
        fail2: Boolean,
        dh1: Double,
        dh2: Double,
        test1: Boolean = false,
        test2: Boolean = false,
    ): LrraResult {
        val rateBlank = abs(vsFpm) > 6000 // excessive rate inhibit

        fun mk(cb: Boolean, fail: Boolean, dh: Double, test: Boolean, biasFt: Double): LrraChannel {
            val powered = dcPower && cb
            if (test && powered && !fail) {
                return LrraChannel(
                    powered = powered,
                    valid = true,
                    heightFt = TEST_HEIGHT_FT,
                    dhFt = dh,
                    dhLight = TEST_HEIGHT_FT <= dh,
                    testing = true,
                )
            }
            val corrected = if (aglFt != null && aglFt.isFinite()) max(0.0, aglFt - ANTENNA_OFFSET_FT + biasFt) else null
            val inRange = corrected != null && corrected >= 0 && corrected <= LRRA_MAX_FT && !rateBlank
            val valid = powered && !fail && inRange
            val heightFt = if (valid) jsRound(corrected!!) else null
            return LrraChannel(
                powered = powered,
                valid = valid,
                heightFt = heightFt,
                dhFt = dh,
                dhLight = valid && heightFt != null && heightFt <= dh,
                testing = false,
            )
        }

        return LrraResult(
            ch1 = mk(cb1, fail1, dh1, test1, 0.0),
            // slight dual-channel bias
            ch2 = mk(cb2, fail2, dh2, test2, 3.0),
        )
    }
}
