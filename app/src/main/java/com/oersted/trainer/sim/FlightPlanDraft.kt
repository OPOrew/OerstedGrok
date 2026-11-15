package com.oersted.trainer.sim

/**
 * The editable flight plan on the PLAN screen (pure). Defaults match DEFAULT_PLAN in flight-plan.tsx.
 * [toActive] mirrors the web's activeFrom: waypoints empty, time compression 15, procedures unverified.
 */
data class FlightPlanDraft(
    val callsign: String = "OERSTED 01",
    val departure: String = "RPLL",
    val destination: String = "WSSS",
    val alternate: String = "",
    val departureRunway: String = "06",
    val arrivalRunway: String = "",
    val cruiseAltitudeFt: Double = 25000.0,
    val cruiseSpeedKt: Double = 250.0,
    /** Route fixes (vector fixes from the vectored arrival, or user fixes once the editor supports them). */
    val waypoints: List<ActiveFlightWaypoint> = emptyList(),
    /** Vectored ILS arrival to WSSS RWY 02L; null when none is set. */
    val arrival: ActiveArrival? = null,
    /** Procedures (flight-plan.tsx Plan.sid / enrouteRoute / star / approach / procedureStatus). Defaults match DEFAULT_PLAN. */
    val sid: String = "",
    val enrouteRoute: String = "",
    val star: String = "",
    val approach: String = "",
    val procedureStatus: String = "unverified",
    /** Draft-only fields from the web Plan that are not in ActiveFlightPlan. */
    val routeText: String = "",
    val reserveFuelLb: Double = 3000.0,
    val cruiseTopOfClimbNm: Double = 30.0,
    val descentStartNm: Double = 180.0,
    val approachAltitudeFt: Double = 3000.0,
    /** Approach guidance ILS (not a route endpoint); null when none is chosen. */
    val ils: ActiveIls? = null,
    /** En-route time compression chosen on the PLAN screen (1, 5, 10, 15 or 20). Default 15, as DEFAULT_PLAN. */
    val timeCompression: Double = 15.0,
    /** Destination kind and the point the destination is plotted at (flight-plan.tsx destinationKind / destinationLat / destinationLon). Draft-only. */
    val destinationKind: String = "airport",
    val destinationLat: Double? = null,
    val destinationLon: Double? = null,
) {
    /**
     * TS vectorArrivalFor + the arrival setter: a vectored arrival to WSSS RWY 02L built from the point (fromLat, fromLon).
     * The VCT-A/B/C fixes are synthetic, not charted. Replaces any earlier vector fixes; keeps the other fixes.
     */
    fun withVectoredArrival(fromLat: Double, fromLon: Double): FlightPlanDraft {
        val scene = NavData.WSSS_ILS_02L
        val (vec, _) = NavData.buildVectorArrival(scene, fromLat, fromLon)
        val fixes = vec.map { ActiveFlightWaypoint(it.id, it.name, it.latitude, it.longitude, it.altitudeFt, it.speedKt) }
        val arrival = ActiveArrival(
            airport = "WSSS",
            runway = scene.runway,
            sceneId = scene.id,
            firstFixName = fixes.first().name,
            lastFixName = fixes.last().name,
            ilsFrequencyMHz = scene.frequencyMHz,
            courseMag = scene.courseMag,
        )
        return copy(
            destination = "WSSS",
            arrivalRunway = scene.runway,
            waypoints = waypoints.filter { !it.id.startsWith("VCT-") } + fixes,
            arrival = arrival,
        )
    }
    /** Null when the draft can be activated; otherwise the reason it cannot. */
    fun problem(): String? = when {
        callsign.isBlank() -> "Enter a callsign"
        departure.isBlank() || destination.isBlank() -> "Enter departure and destination"
        cruiseAltitudeFt <= 0 -> "Cruise altitude must be above zero"
        cruiseSpeedKt <= 0 -> "Cruise speed must be above zero"
        else -> null
    }

    fun toActive(activatedAt: String): ActiveFlightPlan = ActiveFlightPlan(
        version = 1,
        activatedAt = activatedAt,
        callsign = callsign,
        departure = departure,
        destination = destination,
        alternate = alternate,
        departureRunway = departureRunway,
        arrivalRunway = arrivalRunway,
        cruiseAltitudeFt = cruiseAltitudeFt,
        cruiseSpeedKt = cruiseSpeedKt,
        sid = sid,
        enrouteRoute = enrouteRoute,
        star = star,
        approach = approach,
        procedureStatus = procedureStatus,
        waypoints = waypoints,
        ils = ils,
        arrival = arrival,
        timeCompression = timeCompression,
    )
}
