package com.oersted.trainer.sim


/**
 * Port of the route lookups in src/lib/sim/flightNavigation.ts: arrivalScene, departurePoint,
 * departureRunwayHeadingMag, targetList, planRoutePoints, legStart, and the routeGuidance wrapper.
 * The plan is passed in (the web read it from localStorage).
 */
object FlightRoute {
    data class LatLon(val lat: Double, val lon: Double)

    enum class RoutePointKind { DEP, WPT, DEST }

    data class RoutePoint(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val altitudeFt: Double,
        val speedKt: Double,
        val kind: RoutePointKind,
    )

    /** TS AIRPORTS: aerodrome reference point for every airport in AIRPORT_DATA. */
    val AIRPORTS: Map<String, LatLon> = NavData.AIRPORT_DATA.values.associate { it.icao to LatLon(it.arpLat, it.arpLon) }

    /** ILS scene for a plan's vectored arrival, if any. */
    fun arrivalScene(plan: ActiveFlightPlan): NavData.IlsScene? =
        if (NavData.isWsssArrivalSceneId(plan.arrival?.sceneId)) NavData.WSSS_ILS_02L else null

    /** Where the aircraft starts: the departure runway threshold when known, else the aerodrome reference point. */
    fun departurePoint(plan: ActiveFlightPlan): LatLon? {
        val ap = NavData.AIRPORT_DATA[plan.departure]
        val rwy = ap?.runways?.get(plan.departureRunway)
        if (rwy != null) return LatLon(rwy.thrLat, rwy.thrLon)
        return AIRPORTS[plan.departure]
    }

    /** Magnetic heading for the departure runway, or null when the runway is not in the data. */
    fun departureRunwayHeadingMag(plan: ActiveFlightPlan): Double? {
        val ap = NavData.AIRPORT_DATA[plan.departure] ?: return null
        val rwy = ap.runways[plan.departureRunway] ?: return null
        return PlanGuidance.wrap360(rwy.trueBrgDeg - ap.magVarDeg)
    }

    /** Valid waypoints in flying order, then the destination (the arrival runway threshold for a vectored ILS). */
    fun targetList(plan: ActiveFlightPlan): List<GuidanceTarget> {
        val valid = plan.waypoints
            .filter { it.latitude.isFinite() && it.longitude.isFinite() && kotlin.math.abs(it.latitude) <= 90 && kotlin.math.abs(it.longitude) <= 180 }
            .map { GuidanceTarget(it.name, it.latitude, it.longitude, it.altitudeFt, it.speedKt) }
        val scene = arrivalScene(plan)
        if (scene != null) {
            return valid + GuidanceTarget("${plan.destination} ${scene.runway}", scene.thrLat, scene.thrLon, 0.0, 0.0)
        }
        val dest = AIRPORTS[plan.destination] ?: return valid
        return valid + GuidanceTarget(plan.destination, dest.lat, dest.lon, 0.0, 0.0)
    }

    /** Departure point, every navigable waypoint and the destination, in flying order, for route displays. */
    fun planRoutePoints(plan: ActiveFlightPlan): List<RoutePoint> {
        val out = mutableListOf<RoutePoint>()
        val dep = departurePoint(plan)
        if (dep != null) out.add(RoutePoint(plan.departure, dep.lat, dep.lon, 0.0, 0.0, RoutePointKind.DEP))
        val targets = targetList(plan)
        val hasDest = arrivalScene(plan) != null || AIRPORTS[plan.destination] != null
        targets.forEachIndexed { i, t ->
            val isDest = i == targets.size - 1 && hasDest
            out.add(RoutePoint(t.name, t.latitude, t.longitude, t.altitudeFt, t.speedKt, if (isDest) RoutePointKind.DEST else RoutePointKind.WPT))
        }
        return out
    }

    /** Start of the leg that ends at targets[legIndex]: the previous target, or the departure point for leg 0. */
    fun legStart(plan: ActiveFlightPlan, targets: List<GuidanceTarget>, legIndex: Int): LatLon? =
        if (legIndex > 0) targets.getOrNull(legIndex - 1)?.let { LatLon(it.latitude, it.longitude) } else departurePoint(plan)

    /** TS routeGuidance: cross-track and track error for the leg that ends at targets[legIndex]. */
    fun routeGuidance(
        latitude: Double,
        longitude: Double,
        trueHeadingDeg: Double,
        plan: ActiveFlightPlan,
        legIndex: Int,
        targets: List<GuidanceTarget> = targetList(plan),
    ): FlightNavigation.RouteGuidance {
        val target = targets.getOrNull(legIndex)
        val previous = legStart(plan, targets, legIndex)
        return FlightNavigation.routeGuidance(
            latitude, longitude, trueHeadingDeg,
            previous?.lat, previous?.lon, target?.latitude, target?.longitude,
        )
    }

}
