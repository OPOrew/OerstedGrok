package com.oersted.trainer.sim

import java.util.Locale

/**
 * Procedure apply rules on the PLAN screen (routes/flight-plan.tsx: applySid, applyVectorArrival). Pure: each returns the
 * new draft plus the notice text, or the reason it was refused. Waypoints: SID fixes are "SID-" ids and vector fixes are
 * "VCT-" ids (the web tags them by source "charted" / "vector").
 */
object FlightPlanProcedures {
    sealed class Result {
        data class Refused(val reason: String) : Result()
        data class Applied(val draft: FlightPlanDraft, val notice: String) : Result()
    }

    /** applySid: the RPLL SID for the departure runway; replaces earlier SID fixes and keeps the rest of the route. */
    fun applySid(draft: FlightPlanDraft, base: String, rwy: String): Result {
        val name = NavData.sidProcedureName(base, rwy)
        val def = NavData.RPLL_SIDS[base]
        if (name == null || def == null) {
            return Result.Refused(
                "No SID data for $base on RWY ${rwy.ifEmpty { "?" }}. Published data is encoded for RWY 06, 24 and 13.",
            )
        }
        val sidFixes = NavData.buildSidWaypoints(base).map {
            ActiveFlightWaypoint(it.id, it.name, it.latitude, it.longitude, it.altitudeFt, it.speedKt)
        }
        val remarks = if (def.remarks.isNullOrEmpty()) "" else ". ${def.remarks}"
        val sid = "$name (RWY $rwy: ${def.initial[rwy]}; track out MIA R${def.radial}) - ${NavData.Editions.RPLL_SID}$remarks"
        val next = draft.copy(
            sid = sid,
            waypoints = sidFixes + draft.waypoints.filter { !it.id.startsWith("SID-") },
        )
        return Result.Applied(
            next,
            "$name applied with published fix coordinates. ${NavData.RPLL_SID_NOTES} Check against the current chart.",
        )
    }

    /**
     * applyVectorArrival: the vectored ILS 02L arrival at WSSS. The vectors start from the last route fix, or from the
     * departure airport when the route has none; refused when neither gives a point.
     */
    fun applyVectorArrival(draft: FlightPlanDraft): Result {
        val base = draft.waypoints.filter { !it.id.startsWith("VCT-") }
        val from = base.lastOrNull()
        val dep = NavData.AIRPORT_DATA.values.firstOrNull { it.icao == draft.departure }
        val lat = from?.latitude ?: dep?.arpLat
        val lon = from?.longitude ?: dep?.arpLon
        if (lat == null || lon == null) {
            return Result.Refused("Add at least one waypoint first so the vector pattern can be placed on the correct side of the final course.")
        }
        val scene = NavData.WSSS_ILS_02L
        val vectored = draft.withVectoredArrival(lat, lon).copy(
            destinationKind = "airport",
            destinationLat = scene.thrLat,
            destinationLon = scene.thrLon,
            approachAltitudeFt = 3500.0,
            star = "NONE - radar vectors VCT-A / VCT-B / VCT-C (current WSSS STARs are RNAV GNSS)",
            approach = "ILS/DME RWY 02L (ICW ${String.format(Locale.ROOT, "%.1f", scene.frequencyMHz)}) - ${NavData.Editions.WSSS_ILS_02L}",
            ils = FlightPlanIls.icw() ?: draft.ils,
        )
        return Result.Applied(
            vectored,
            "Vectored ILS arrival to WSSS RWY 02L added (VCT-A downwind, VCT-B base, VCT-C intercept). These fixes are synthetic, not charted.",
        )
    }
}
