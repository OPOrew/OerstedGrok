package com.oersted.trainer.sim

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of src/lib/sim/markerBeacons.ts (ILS marker beacons). Training geometry only: the positions and
 * coverage below are synthetic values for the simulated ILS scenes, NOT chart data.
 */
object MarkerBeacons {
    enum class MarkerId(val key: String) { OM("om"), MM("mm"), IM("im") }

    data class MarkerSpec(
        val id: MarkerId,
        val name: String,
        val lamp: String,
        val color: String,
        val distNm: Double,
        val halfNm: Double,
        val hz: Double,
        val morse: String,
    )

    /** Typical ILS siting: OM ~4-7 NM, MM ~3,500 ft, IM ~1,000 ft before the threshold. */
    val MARKERS: List<MarkerSpec> = listOf(
        MarkerSpec(MarkerId.OM, "Outer marker", "OUTER", "#4a8dff", 4.6, 0.30, 400.0, "dashes · 2 per second"),
        MarkerSpec(MarkerId.MM, "Middle marker", "MIDDLE", "#ffb02e", 0.58, 0.13, 1300.0, "alternating dot-dash"),
        MarkerSpec(MarkerId.IM, "Inner marker", "AIRWAYS", "#f2f4f6", 0.16, 0.07, 3000.0, "dots · 6 per second"),
    )

    data class MarkerState(val om: Boolean, val mm: Boolean, val im: Boolean)

    val NO_MARKERS = MarkerState(om = false, mm = false, im = false)

    data class Pos(val x: Double, val y: Double)

    /**
     * TS markerCoverage: which markers the receiver hears. The cone widens with height, and the HIGH
     * sensitivity position widens it further. Above 3,500 ft AGL, or below 50 ft under the field, nothing is received.
     */
    fun markerCoverage(sceneId: RadioNav.NavSceneId?, pos: Pos, altFt: Double, high: Boolean): MarkerState {
        val g = RadioNav.sceneGeom(sceneId)
        val c = g.crs * PI / 180
        val along = pos.x * sin(c) + pos.y * cos(c) // +ve past the threshold
        val cross = pos.x * cos(c) - pos.y * sin(c)
        val dist = -along // NM before the threshold
        val agl = altFt - g.fieldElevFt
        if (agl > 3500 || agl < -50) return NO_MARKERS
        val sens = if (high) 1.5 else 1.0
        val widen = 1 + maxOf(0.0, agl) / 1800
        fun inCone(m: MarkerSpec) = abs(dist - m.distNm) <= m.halfNm * widen * sens && abs(cross) <= 0.45 * widen * sens
        val (om, mm, im) = MARKERS.map { inCone(it) }
        return MarkerState(om, mm, im)
    }

    /** TS markerKeyed: is the keyed tone / lamp ON at time t (seconds)? OM 2 dashes/s, MM dot-dash, IM 6 dots/s. */
    fun markerKeyed(id: MarkerId, t: Double): Boolean = when (id) {
        MarkerId.OM -> jsMod(t, 0.5) < 0.36
        MarkerId.IM -> jsMod(t, 1.0 / 6) < 0.07
        MarkerId.MM -> {
            val p = jsMod(t, 0.63) // dot, gap, dash, gap
            p < 0.1 || (p >= 0.2 && p < 0.5)
        }
    }

    /** JS % (result takes the sign of the dividend). */
    private fun jsMod(a: Double, b: Double): Double = a % b

    /** TS activeMarker: the marker to show, inner first. */
    fun activeMarker(s: MarkerState): MarkerId? = when {
        s.im -> MarkerId.IM
        s.mm -> MarkerId.MM
        s.om -> MarkerId.OM
        else -> null
    }

    fun markerSpec(id: MarkerId): MarkerSpec = MARKERS.first { it.id == id }
}

/** TS maintenanceInterlocks: the AMM ATA 24-11 generator-drive reset rule (ground only, N2 at or below 5 %). */
object MaintenanceInterlocks {
    fun canResetGeneratorDrive(weightOnWheels: Boolean, engineN2Percent: Double): Boolean =
        weightOnWheels && engineN2Percent.isFinite() && engineN2Percent <= 5
}

/** TS airportCoords: local airport reference table for the planner and its station map. */
object AirportCoords {
    data class Coord(val lat: Double, val lon: Double, val label: String)

    val AIRPORT_COORDS: Map<String, Coord> = mapOf(
        "RPLL" to Coord(14.5086, 121.0198, "Manila / NAIA"),
        "WSSS" to Coord(1.3502, 103.9940, "Singapore Changi"),
        "WMKK" to Coord(2.7456, 101.7072, "Kuala Lumpur"),
    )
}
