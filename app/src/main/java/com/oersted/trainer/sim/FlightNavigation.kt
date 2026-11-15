package com.oersted.trainer.sim

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Port of the pure geometry and time-compression helpers in src/lib/sim/flightNavigation.ts.
 *
 * Not ported yet (they need navData.ts: AIRPORT_DATA, magVarAt, WSSS_ILS_02L, and the plan lookup):
 * arrivalScene, departurePoint, departureRunwayHeadingMag, targetList, planRoutePoints, legStart,
 * and the advance/reset state machine. Those take their inputs from the caller here.
 */
object FlightNavigation {
    const val DEFAULT_TIME_COMPRESSION = 15.0
    private const val EARTH_RADIUS_NM = 3440.065

    private fun rad(n: Double) = n * PI / 180
    private fun degOf(n: Double) = (n * 180 / PI + 360) % 360
    private fun wrap360(d: Double) = ((d % 360) + 360) % 360
    private fun smoothstep(x: Double): Double {
        val t = max(0.0, min(1.0, x))
        return t * t * (3 - 2 * t)
    }

    /**
     * TS enrouteCompression: smooth position-time multiplier from 1 to [max], rising only while cruising.
     *  - altitude gate: 1x until 4,000 ft below cruise altitude, full once within 1,000 ft of it
     *  - distance gate: full while more than 60 NM before top of descent (or, with no TOD, more than
     *    160 NM from the destination), back to 1x 10 NM / 110 NM out
     */
    fun enrouteCompression(
        max: Double,
        wow: Boolean,
        altitudeFt: Double,
        cruiseAltitudeFt: Double,
        distToTodNm: Double?,
        distToDestNm: Double?,
    ): Double {
        if (!(max > 1) || wow) return 1.0
        val altGate = smoothstep((altitudeFt - (cruiseAltitudeFt - 4000)) / 3000)
        val remaining = distToTodNm ?: distToDestNm?.let { it - 100 }
        val distGate = if (remaining == null) 0.0 else smoothstep((remaining - 10) / 50)
        return 1 + (max - 1) * altGate * distGate
    }

    /** TS blendDeg: heading [w] of the way from [a] to [b] along the shorter arc. */
    fun blendDeg(a: Double, b: Double, w: Double): Double = wrap360(a + ((((b - a) + 540) % 360) - 180) * w)

    /** Great-circle distance in NM (haversine), as in flightNavigation.ts (not the planGuidance copy). */
    fun distanceNm(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val dLat = rad(bLat - aLat)
        val dLon = rad(bLon - aLon)
        val a = sin(dLat / 2).let { it * it } + cos(rad(aLat)) * cos(rad(bLat)) * sin(dLon / 2).let { it * it }
        return EARTH_RADIUS_NM * 2 * atan2(sqrt(a), sqrt(max(0.0, 1 - a)))
    }

    /** True bearing from A to B in degrees (0..360). */
    fun bearingDeg(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val y = sin(rad(bLon - aLon)) * cos(rad(bLat))
        val x = cos(rad(aLat)) * sin(rad(bLat)) - sin(rad(aLat)) * cos(rad(bLat)) * cos(rad(bLon - aLon))
        return degOf(atan2(y, x))
    }

    data class RouteGuidance(val desiredTrackDeg: Double?, val crossTrackNm: Double?, val trackErrorDeg: Double?)

    /**
     * TS routeGuidance for one leg. [previousLat]/[previousLon] is the leg start and [targetLat]/[targetLon]
     * the leg end (the caller resolves them from the plan; null when the leg is missing).
     * Positive cross-track is right of track. The angular distance is already in radians (NM / Earth
     * radius), so it is NOT converted again (the TS comment records that bug).
     */
    fun routeGuidance(
        latitude: Double,
        longitude: Double,
        trueHeadingDeg: Double,
        previousLat: Double?,
        previousLon: Double?,
        targetLat: Double?,
        targetLon: Double?,
    ): RouteGuidance {
        if (previousLat == null || previousLon == null || targetLat == null || targetLon == null) {
            return RouteGuidance(null, null, null)
        }
        val desiredTrackDeg = bearingDeg(previousLat, previousLon, targetLat, targetLon)
        val startToAircraftNm = distanceNm(previousLat, previousLon, latitude, longitude)
        val bearingStartToAircraft = bearingDeg(previousLat, previousLon, latitude, longitude)
        val angle = startToAircraftNm / EARTH_RADIUS_NM
        val delta = rad((((bearingStartToAircraft - desiredTrackDeg) + 540) % 360) - 180)
        val crossTrackNm = asin(max(-1.0, min(1.0, sin(angle) * sin(delta)))) * EARTH_RADIUS_NM
        val rawTrackError = desiredTrackDeg - trueHeadingDeg
        val trackErrorDeg = ((rawTrackError + 540) % 360) - 180
        return RouteGuidance(desiredTrackDeg, crossTrackNm, trackErrorDeg)
    }

}
