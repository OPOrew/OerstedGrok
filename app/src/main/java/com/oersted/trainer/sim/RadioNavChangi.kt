package com.oersted.trainer.sim

import com.oersted.trainer.sim.NavaidsChangi.Navaid
import com.oersted.trainer.sim.NavaidsChangi.NavaidKind
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.abs
import kotlin.math.PI

/**
 * Port of radionav-changi.ts: radio-navigation solution from the Changi navaid catalogue.
 * WSSS_RUNWAYS and the re-exports in the TS file are not ported here (the Kotlin table lives in WsssRunways).
 */
object RadioNavChangi {
    data class NavSolution(
        val valid: Boolean,
        val navaid: Navaid?,
        val distanceNm: Double,
        val bearingTrueDeg: Double,
        /** VOR radial FROM station (magnetic) */
        val radialFrom: Double,
        val toFlag: Boolean,
        /** positive = fly right */
        val locDevDeg: Double,
        /** positive = above GS */
        val gsDevDeg: Double,
        val locValid: Boolean,
        val gsValid: Boolean,
        val vorValid: Boolean,
        val adfValid: Boolean,
        val ident: String,
        val frequency: Double,
    )

    private fun empty(frequency: Double) = NavSolution(
        valid = false,
        navaid = null,
        distanceNm = 0.0,
        bearingTrueDeg = 0.0,
        radialFrom = 0.0,
        toFlag = true,
        locDevDeg = 0.0,
        gsDevDeg = 0.0,
        locValid = false,
        gsValid = false,
        vorValid = false,
        adfValid = false,
        ident = "----",
        frequency = frequency,
    )

    /** Local angDiff with this file's own formula (same as the TS in radionav-changi.ts). */
    private fun angDiff(a: Double, b: Double): Double = ((a - b + 540) % 360) - 180

    /**
     * TS computeChangiRadioNav. [mode] is "VOR", "ILS" or "NDB" and selects the lookup kind.
     * Returns valid guidance only when the station exists, is available, and the aircraft is inside its service volume.
     */
    fun computeChangiRadioNav(
        acLat: Double,
        acLon: Double,
        acAltFt: Double,
        tunedFreq: Double,
        mode: String,
        selectedCourseMag: Double = 0.0,
    ): NavSolution {
        val navaid = findChangiNavaid(tunedFreq, mode) ?: return empty(tunedFreq)

        val dist = distNm(acLat, acLon, navaid.lat, navaid.lon)
        if (dist > navaid.rangeNm || dist < 0.05) {
            return empty(navaid.frequency).copy(navaid = navaid, ident = navaid.ident, distanceNm = dist)
        }

        val brgTrue = bearingTrue(acLat, acLon, navaid.lat, navaid.lon)
        val magVar = navaid.magVarDeg
        val brgMag = (brgTrue - magVar + 360) % 360

        val base = NavSolution(
            valid = true,
            navaid = navaid,
            distanceNm = dist,
            bearingTrueDeg = brgTrue,
            radialFrom = 0.0,
            toFlag = true,
            locDevDeg = 0.0,
            gsDevDeg = 0.0,
            locValid = false,
            gsValid = false,
            vorValid = false,
            adfValid = false,
            ident = navaid.ident,
            frequency = navaid.frequency,
        )

        if (navaid.kind == NavaidKind.NDB) {
            // instrument converts this to a relative bearing
            return base.copy(adfValid = true, radialFrom = brgMag)
        }

        if (navaid.kind == NavaidKind.VOR || navaid.kind == NavaidKind.VOR_DME) {
            val radialFrom = (brgMag + 180) % 360
            val diff = angDiff(selectedCourseMag, radialFrom)
            val toFlag = abs(diff) <= 90
            return base.copy(
                vorValid = true,
                radialFrom = radialFrom,
                toFlag = toFlag,
                locDevDeg = if (toFlag) -diff else diff,
            )
        }

        if (navaid.kind == NavaidKind.ILS || navaid.kind == NavaidKind.LOC) {
            val courseMag = navaid.courseMag ?: 0.0
            val courseTrue = courseMag + magVar
            // bearing of the aircraft FROM the localizer antenna
            val brgFromAnt = bearingTrue(navaid.lat, navaid.lon, acLat, acLon)
            val locDev = angDiff(brgFromAnt, (courseTrue + 180) % 360)
            val locValid = abs(locDev) < 35 && dist < 25
            var sol = base.copy(locValid = locValid, locDevDeg = locDev, radialFrom = courseMag)

            val gs = navaid.gsDeg
            if (gs != null && gs != 0.0 && locValid) {
                val heightAbove = max(0.0, acAltFt - (navaid.elevFt ?: 20.0))
                val gsAngle = atan2(heightAbove, dist * 6076.12) * 180 / PI
                sol = sol.copy(
                    gsDevDeg = gsAngle - gs,
                    gsValid = dist < 12 && abs(locDev) < 8 && gsAngle < 8 && gsAngle > 0.5,
                )
            }
            return sol
        }

        return empty(tunedFreq)
    }

    /** TS pedestalFreqToMHz: the pedestal stores kHz-style integers (109950 = 109.95 MHz). */
    fun pedestalFreqToMHz(raw: Double): Double = raw / 1000
}
