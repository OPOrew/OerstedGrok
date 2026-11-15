package com.oersted.trainer.sim

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Waypoint editing rules on the PLAN screen (routes/flight-plan.tsx: addSelectedNavaid, addWaypoint, setHeadingBugForNextLeg).
 * Pure: each function returns the new draft or the reason it was refused, so the caller sets the notice text.
 * Simplification: the web keeps a `source` tag on each waypoint ("user", "charted", "vector"); the Kotlin waypoint type has no
 * such field yet, so the source is returned alongside the draft instead of being stored on it.
 */
object FlightPlanEdits {
    sealed class Result {
        data class Refused(val reason: String) : Result()
        data class Added(val draft: FlightPlanDraft, val source: String) : Result()
    }

    /** JS Number(text) || 0, for the altitude and speed boxes. Blank or unparseable gives 0. */
    private fun jsNumberOrZero(text: String): Double {
        val t = text.trim()
        if (t.isEmpty()) return 0.0
        val v = t.toDoubleOrNull() ?: return 0.0
        return if (v.isNaN()) 0.0 else v
    }

    private fun clamp(v: Double, lo: Double, hi: Double) = max(lo, min(hi, v))

    /** Altitude box: 0 to 45000 ft. */
    fun altitudeFromText(text: String): Double = clamp(jsNumberOrZero(text), 0.0, 45000.0)

    /** Speed box: 0 to 400 kt. */
    fun speedFromText(text: String): Double = clamp(jsNumberOrZero(text), 0.0, 400.0)

    /** Latitude -90..90 and longitude -180..180, both required (addWaypoint). Null when either is blank or out of range. */
    fun parseCoordinates(latText: String, lonText: String): Pair<Double, Double>? {
        if (latText.trim().isEmpty() || lonText.trim().isEmpty()) return null
        val lat = latText.trim().toDoubleOrNull() ?: return null
        val lon = lonText.trim().toDoubleOrNull() ?: return null
        if (lat.isNaN() || lon.isNaN() || lat.isInfinite() || lon.isInfinite()) return null
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null
        return lat to lon
    }

    /** addWaypoint: a typed fix with its own coordinates. [id] is supplied by the caller (the web uses a timestamp). */
    fun addUserWaypoint(
        draft: FlightPlanDraft, rawName: String, latText: String, lonText: String,
        altText: String, speedText: String, id: String,
    ): Result {
        val name = rawName.trim().uppercase()
        if (name.isEmpty()) return Result.Refused("Enter a waypoint identifier before adding it.")
        val (lat, lon) = parseCoordinates(latText, lonText)
            ?: return Result.Refused("Enter valid waypoint latitude (-90 to 90) and longitude (-180 to 180).")
        if (draft.waypoints.any { it.name == name }) return Result.Refused("$name is already in the route.")
        val wp = ActiveFlightWaypoint(id, name, lat, lon, altitudeFromText(altText), speedFromText(speedText))
        return Result.Added(draft.copy(waypoints = draft.waypoints + wp), source = "user")
    }

    /**
     * addSelectedNavaid: a catalogue VOR / NDB / ILS added as an ordered intermediate fix. Refused when the same id or
     * ident is already in the route. The source is "charted" for CAAS AIP entries, otherwise "user", as on the web.
     */
    fun addNavaid(draft: FlightPlanDraft, station: NavaidsChangi.Navaid, altText: String, speedText: String): Result {
        if (draft.waypoints.any { it.id == station.id || it.name == station.ident }) {
            return Result.Refused("${station.ident} is already in the route.")
        }
        val wp = ActiveFlightWaypoint(
            station.id, station.ident, station.lat, station.lon,
            altitudeFromText(altText), speedFromText(speedText),
        )
        val source = if (station.source.contains("CAAS AIP")) "charted" else "user"
        return Result.Added(draft.copy(waypoints = draft.waypoints + wp), source = source)
    }

    /**
     * setHeadingBugForNextLeg: the magnetic heading for the heading bug, from the true bearing to the next waypoint.
     * JS: ((Math.round(bearing - magVar) % 360) + 360) % 360, with Math.round as floor(x + 0.5).
     */
    fun headingBugTarget(bearingDeg: Double, magVarDeg: Double): Int {
        val rounded = floor(bearingDeg - magVarDeg + 0.5)
        val m = ((rounded % 360) + 360) % 360
        return m.toInt()
    }

    /** The en-route time compression choices on the PLAN screen (flight-plan.tsx select). */
    val TIME_COMPRESSION_OPTIONS: List<Double> = listOf(1.0, 5.0, 10.0, 15.0, 20.0)

    /** Picks a compression from the select; any other value leaves the draft unchanged. */
    fun withTimeCompression(draft: FlightPlanDraft, value: Double): FlightPlanDraft =
        if (value in TIME_COMPRESSION_OPTIONS) draft.copy(timeCompression = value) else draft

    /** The distance boxes (top of climb, descent start): 0 to 1500 NM, blank is 0. */
    fun nmFromText(text: String): Double = clamp(jsNumberOrZero(text), 0.0, 1500.0)
}
