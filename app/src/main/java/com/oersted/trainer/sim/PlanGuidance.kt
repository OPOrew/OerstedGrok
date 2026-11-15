package com.oersted.trainer.sim

import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.PI

/**
 * Port of src/lib/sim/planGuidance.ts ("PLAN NAV" trainer guidance). Pure functions only: the
 * simulator loop feeds in the geographic state and applies the result.
 * Lateral guidance is a heading command (the modeled SP-77 flies it like HDG SEL), vertical guidance
 * is an altitude target for ALT HOLD. Speed is ADVISORY only: a 737-200 has no autothrottle.
 */
data class GuidanceTarget(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeFt: Double,
    val speedKt: Double,
)

enum class VertMode { NONE, CLIMB, CRUISE, DESCENT, VECTOR }

data class GuidanceResult(
    val active: Boolean,
    /** true heading command, null when there is no usable heading */
    val headingTrueCmdDeg: Double?,
    /** heading command in the simulator's (magnetic) frame */
    val headingMagCmdDeg: Double?,
    val targetAltFt: Double?,
    val vertMode: VertMode,
    val speedAdvisoryKt: Double?,
    /** distance before the first vector fix at which the descent should start */
    val todDistanceNm: Double?,
    val distToTodNm: Double?,
    /** true once the aircraft is close enough to the intercept fix for the ILS to be tuned and armed */
    val ilsArm: Boolean,
    val inVectors: Boolean,
    val note: String,
)

val INACTIVE_GUIDANCE = GuidanceResult(
    active = false, headingTrueCmdDeg = null, headingMagCmdDeg = null, targetAltFt = null, vertMode = VertMode.NONE,
    speedAdvisoryKt = null, todDistanceNm = null, distToTodNm = null, ilsArm = false, inVectors = false, note = "NO PLAN GUIDANCE",
)

/** Plan fields the guidance reads (TS GuidanceInput.plan). */
data class GuidancePlan(val cruiseAltitudeFt: Double, val cruiseSpeedKt: Double, val arrival: ActiveArrival? = null)

data class GuidanceInput(
    val plan: GuidancePlan,
    val targets: List<GuidanceTarget>,
    val legIndex: Int,
    val latitude: Double,
    val longitude: Double,
    val altitudeFt: Double,
    val groundSpeedKt: Double,
    val desiredTrackDeg: Double?,
    val crossTrackNm: Double?,
    val bearingToNextDeg: Double?,
    val distanceToNextNm: Double?,
    /** east-positive magnetic variation at the aircraft */
    val magVarDeg: Double,
    /** distance to the arrival runway threshold, if known (for the final-approach speed advisory) */
    val distToThresholdNm: Double? = null,
)

object PlanGuidance {
    private const val R_NM = 3440.065

    private fun rad(n: Double) = n * PI / 180
    private fun clamp(v: Double, lo: Double, hi: Double) = max(lo, min(hi, v))

    fun wrap360(d: Double): Double = ((d % 360) + 360) % 360
    fun wrap180(d: Double): Double = ((((d + 180) % 360) + 360) % 360) - 180

    /** Great-circle distance in NM (haversine). */
    fun distNm(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val dLat = rad(bLat - aLat)
        val dLon = rad(bLon - aLon)
        val a = sin(dLat / 2).let { it * it } + cos(rad(aLat)) * cos(rad(bLat)) * sin(dLon / 2).let { it * it }
        return R_NM * 2 * atan2(sqrt(a), sqrt(max(0.0, 1 - a)))
    }

    /** True bearing from A to B, 0..360. */
    fun brgTrue(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val y = sin(rad(bLon - aLon)) * cos(rad(bLat))
        val x = cos(rad(aLat)) * sin(rad(bLat)) - sin(rad(aLat)) * cos(rad(bLat)) * cos(rad(bLon - aLon))
        return wrap360(atan2(y, x) * 180 / PI)
    }

    /** Distance before a waypoint at which to start a turn of [turnDeg] (fly-by), assuming a standard bank. */
    fun leadDistanceNm(groundSpeedKt: Double, turnDeg: Double, bankDeg: Double = 20.0): Double {
        val t = abs(wrap180(turnDeg))
        if (t < 5) return 0.0
        val radiusNm = (1.4565e-5 * groundSpeedKt * groundSpeedKt) / tan(rad(bankDeg))
        return clamp(radiusNm * tan(rad(min(t, 150.0) / 2)), 0.0, 8.0)
    }

    /**
     * Heading to fly (true): direct to the waypoint when far off track, else the leg track corrected
     * for cross-track error (capped at a 25 degree intercept).
     */
    fun lateralCommandTrue(desiredTrackDeg: Double?, crossTrackNm: Double?, bearingToNextDeg: Double?): Double? {
        if (desiredTrackDeg == null || crossTrackNm == null) return bearingToNextDeg
        if (abs(crossTrackNm) > 2 && bearingToNextDeg != null) return bearingToNextDeg
        // Positive cross-track = right of track, so steer left (subtract).
        return wrap360(desiredTrackDeg - clamp(crossTrackNm * 12, -25.0, 25.0))
    }

    /**
     * Distance from the first vector fix at which to start descending from cruise: 1,500 fpm at the
     * mean of cruise and 250 kt groundspeed, plus 10 NM to slow down.
     */
    fun todDistanceNm(cruiseAltFt: Double, targetAltFt: Double, cruiseGsKt: Double): Double {
        val delta = max(0.0, cruiseAltFt - targetAltFt)
        val minutes = delta / 1500
        val meanGs = (max(cruiseGsKt, 250.0) + 250) / 2
        return (minutes * meanGs) / 60 + 10
    }

    /** Sum of great-circle legs from the aircraft through targets[fromIndex..toIndex]. */
    fun remainingRouteNm(latitude: Double, longitude: Double, targets: List<GuidanceTarget>, fromIndex: Int, toIndex: Int): Double {
        if (fromIndex > toIndex || fromIndex !in targets.indices) return 0.0
        var total = distNm(latitude, longitude, targets[fromIndex].latitude, targets[fromIndex].longitude)
        for (i in fromIndex until toIndex) {
            total += distNm(targets[i].latitude, targets[i].longitude, targets[i + 1].latitude, targets[i + 1].longitude)
        }
        return total
    }

    fun computeGuidance(input: GuidanceInput): GuidanceResult {
        val targets = input.targets
        val legIndex = input.legIndex
        val active = targets.getOrNull(legIndex) ?: return INACTIVE_GUIDANCE

        val arrival = input.plan.arrival
        val firstIdx = if (arrival != null) targets.indexOfFirst { it.name == arrival.firstFixName } else -1
        val lastIdx = if (arrival != null) targets.indexOfFirst { it.name == arrival.lastFixName } else -1
        // The leg TO the first vector fix still uses the cruise / top-of-descent logic; vectors begin once that fix is passed.
        val inVectors = firstIdx >= 0 && legIndex > firstIdx
        val cruise = input.plan.cruiseAltitudeFt

        var vertMode: VertMode
        var targetAlt: Double?
        var tod: Double? = null
        var distToTod: Double? = null
        val note: String

        if (inVectors) {
            vertMode = VertMode.VECTOR
            targetAlt = if (active.altitudeFt > 0) active.altitudeFt else null
            note = "VECTORS ${active.name}"
        } else if (firstIdx >= 0) {
            val approachAlt = if (targets[firstIdx].altitudeFt > 0) targets[firstIdx].altitudeFt else 5000.0
            tod = todDistanceNm(cruise, approachAlt, max(input.groundSpeedKt, input.plan.cruiseSpeedKt))
            val toFix = remainingRouteNm(input.latitude, input.longitude, targets, legIndex, firstIdx)
            distToTod = toFix - tod
            if (toFix <= tod) {
                vertMode = VertMode.DESCENT; targetAlt = approachAlt; note = "DESCENT to ${approachAlt.jsInt()} FT"
            } else if (input.altitudeFt < cruise - 300) {
                vertMode = VertMode.CLIMB; targetAlt = cruise; note = "CLIMB to ${cruise.jsInt()} FT"
            } else {
                vertMode = VertMode.CRUISE; targetAlt = cruise
                note = "CRUISE, TOD in ${fixed0(max(0.0, distToTod))} NM"
            }
        } else {
            vertMode = if (input.altitudeFt < cruise - 300) VertMode.CLIMB else VertMode.CRUISE
            targetAlt = cruise
            note = if (vertMode == VertMode.CLIMB) "CLIMB to ${cruise.jsInt()} FT" else "CRUISE"
        }

        // Speed advisory (pilot flies the throttles).
        val thr = input.distToThresholdNm
        val speed: Double? = when {
            inVectors && lastIdx >= 0 && legIndex >= lastIdx && thr != null ->
                if (thr > 8) 180.0 else if (thr > 4) 150.0 else null
            inVectors -> if (active.speedKt > 0) active.speedKt else 220.0
            else -> if (input.altitudeFt < 10000) 250.0 else input.plan.cruiseSpeedKt
        }

        val ilsArm = lastIdx >= 0 && (legIndex > lastIdx || (legIndex == lastIdx && (input.distanceToNextNm ?: 99.0) <= 6))
        val hdgTrue = lateralCommandTrue(input.desiredTrackDeg, input.crossTrackNm, input.bearingToNextDeg)
        return GuidanceResult(
            active = true,
            headingTrueCmdDeg = hdgTrue,
            headingMagCmdDeg = hdgTrue?.let { wrap360(it - input.magVarDeg) },
            targetAltFt = targetAlt,
            vertMode = vertMode,
            speedAdvisoryKt = speed,
            todDistanceNm = tod,
            distToTodNm = distToTod,
            ilsArm = ilsArm,
            inVectors = inVectors,
            note = note,
        )
    }

    /** JS toFixed(0) for non-negative values (round half up). */
    private fun fixed0(v: Double): String = String.format(Locale.ROOT, "%.0f", v)

    /** JS number-to-string for whole-number altitudes (e.g. 5000.0 -> "5000"). */
    private fun Double.jsInt(): String = if (this == Math.floor(this) && abs(this) < 1e15) toLong().toString() else toString()
}
