package com.oersted.trainer.sim

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Port of dmeModel.ts (ATA 34-55, DME interrogator training model).
 * Slant-range geometry, search/track, lock memory, display lag. Not pulse-pair RF.
 */
object DmeModel {
    /** DmeMode: 0 STBY, 1 NORM, 2 ORIDE. */
    const val MODE_STBY = 0
    const val MODE_NORM = 1
    const val MODE_ORIDE = 2

    /**
     * null displayNm = dashes (STBY / searching / lost).
     * memoryT = seconds remaining of memory hold after signal loss.
     * lagNm = lagged display value for 1 s smoothing.
     */
    data class DmeChannelState(
        val displayNm: Double?,
        val searching: Boolean,
        val locked: Boolean,
        val lockT: Double,
        val memoryT: Double,
        val lagNm: Double?,
    )

    private const val SEARCH_SEC_NEAR = 1.2
    private const val SEARCH_SEC_FAR = 4.0
    private const val NORM_MAX_NM = 200.0
    private const val ORIDE_MAX_NM = 300.0
    private const val MEMORY_SEC = 12.0
    private const val LAG_TAU = 0.85 // ~1 s display lag

    /** Convert horizontal range + height difference to slant range (nm). */
    fun slantRangeNm(horizNm: Double, deltaAltFt: Double): Double {
        val deltaNm = deltaAltFt / 6076.12
        return sqrt(horizNm * horizNm + deltaNm * deltaNm)
    }

    /** Longer search at long range. */
    private fun searchTimeForRange(nm: Double): Double =
        SEARCH_SEC_NEAR + (SEARCH_SEC_FAR - SEARCH_SEC_NEAR) * min(1.0, nm / 150)

    /** JS Math.round: floor(x + 0.5). */
    private fun jsRound(x: Double): Double = floor(x + 0.5)

    /**
     * TS stepDmeChannel.
     * [geomNm] is the horizontal range in nm, or null if there is no station.
     * [deltaAltFt] is aircraft minus station elevation (ft), used for the slant range.
     */
    fun stepDmeChannel(
        prev: DmeChannelState,
        mode: Int,
        test: Boolean,
        powered: Boolean,
        geomNm: Double?,
        deltaAltFt: Double = 0.0,
        dtSec: Double,
    ): DmeChannelState {
        if (!powered || mode == MODE_STBY) {
            return DmeChannelState(null, searching = false, locked = false, lockT = 0.0, memoryT = 0.0, lagNm = null)
        }
        if (test) {
            return DmeChannelState(0.0, searching = false, locked = true, lockT = 2.0, memoryT = 0.0, lagNm = 0.0)
        }

        val maxR = if (mode == MODE_ORIDE) ORIDE_MAX_NM else NORM_MAX_NM
        val horizOk = geomNm != null && geomNm.isFinite() && geomNm >= 0 && geomNm <= maxR
        val slant = if (horizOk) slantRangeNm(geomNm!!, deltaAltFt) else null
        val inRange = slant != null && slant <= maxR

        // Signal lost while previously locked: memory hold
        if (!inRange) {
            if (prev.locked || prev.memoryT > 0) {
                val mem = (if (prev.memoryT > 0) prev.memoryT else MEMORY_SEC) - dtSec
                if (mem > 0) {
                    return DmeChannelState(
                        displayNm = prev.displayNm,
                        searching = false,
                        locked = false,
                        lockT = 0.0,
                        memoryT = mem,
                        lagNm = prev.lagNm ?: prev.displayNm,
                    )
                }
            }
            return DmeChannelState(null, searching = true, locked = false, lockT = 0.0, memoryT = 0.0, lagNm = null)
        }

        val need = searchTimeForRange(slant!!)
        val lockT = if (prev.locked) need else prev.lockT + dtSec
        if (!prev.locked && lockT < need) {
            return DmeChannelState(null, searching = true, locked = false, lockT = lockT, memoryT = 0.0, lagNm = null)
        }

        val raw = jsRound(slant * 10) / 10
        // lag toward raw
        val lagPrev = prev.lagNm ?: raw
        val alpha = 1 - exp(-dtSec / LAG_TAU)
        val lagNm = lagPrev + (raw - lagPrev) * alpha
        val displayNm = jsRound(lagNm * 10) / 10

        return DmeChannelState(
            displayNm = displayNm,
            searching = false,
            locked = true,
            lockT = need,
            memoryT = 0.0,
            lagNm = lagNm,
        )
    }

    val IDLE_DME = DmeChannelState(null, searching = false, locked = false, lockT = 0.0, memoryT = 0.0, lagNm = null)
}
