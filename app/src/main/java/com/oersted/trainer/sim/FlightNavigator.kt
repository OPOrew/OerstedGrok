package com.oersted.trainer.sim

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.asin
import kotlin.math.atan2

/**
 * Port of the state machine in src/lib/sim/flightNavigation.ts: tracks the aircraft's geographic
 * position from simulated groundspeed and heading, sequences waypoints, and computes PLAN NAV guidance.
 *
 * Platform side effects are injected: [clock] stands in for new Date().toISOString(), [restore] for the
 * persisted state read at startup, and [onPublish] for the once-per-second localStorage write and the
 * change event. The plan is passed to [advance] and [reset] (the web read it from localStorage).
 */
class FlightNavigator(
    private val clock: () -> String = { "" },
    private val restore: () -> FlightNavigationState? = { null },
    private val onPublish: (FlightNavigationState) -> Unit = {},
) {
    private var live: FlightNavigationState? = null
    private var liveGuidance: GuidanceResult = INACTIVE_GUIDANCE
    private var liveArrival: ActiveArrival? = null
    private var elapsedSincePublish = 0.0

    /** TS getLiveFlightNavigation. */
    fun liveState(): FlightNavigationState? = live
    /** TS getLiveFlightGuidance. */
    fun liveGuidance(): GuidanceResult = liveGuidance
    /** TS getLiveArrival. */
    fun liveArrival(): ActiveArrival? = liveArrival
    /** TS readFlightNavigationState: the live state, else the restored one. */
    fun readState(): FlightNavigationState? = live ?: restore()

    private fun writeState(state: FlightNavigationState) {
        live = state
        onPublish(state)
    }

    /** TS buildState. The telemetry defaults to zeros, as in the web. */
    fun buildState(plan: ActiveFlightPlan, telemetry: NavigationTelemetry? = null): FlightNavigationState {
        val origin = FlightRoute.departurePoint(plan)
        val latitude = origin?.lat ?: 0.0
        val longitude = origin?.lon ?: 0.0
        val targets = FlightRoute.targetList(plan)
        val first = targets.firstOrNull()
        val dest = targets.lastOrNull()
        val magVar = NavData.magVarAt(latitude)
        val distanceToNextNm = if (first != null && origin != null) FlightNavigation.distanceNm(latitude, longitude, first.latitude, first.longitude) else null
        val guidance = FlightRoute.routeGuidance(latitude, longitude, (telemetry?.headingDeg ?: 0.0) + magVar, plan, 0, targets)
        return FlightNavigationState(
            planActivatedAt = plan.activatedAt,
            latitude = latitude,
            longitude = longitude,
            headingDeg = telemetry?.headingDeg ?: 0.0,
            altitudeFt = telemetry?.altitudeFt ?: 0.0,
            groundSpeedKt = telemetry?.groundSpeedKt ?: 0.0,
            activeLegIndex = 0,
            activeLegName = first?.name ?: plan.destination,
            distanceToNextNm = distanceToNextNm,
            bearingToNextDeg = if (first != null && origin != null) FlightNavigation.bearingDeg(latitude, longitude, first.latitude, first.longitude) else null,
            desiredTrackDeg = guidance.desiredTrackDeg,
            crossTrackNm = guidance.crossTrackNm,
            trackErrorDeg = guidance.trackErrorDeg,
            distanceToDestinationNm = if (dest != null && origin != null) FlightNavigation.distanceNm(latitude, longitude, dest.latitude, dest.longitude) else null,
            phase = FlightPhase.PARKED,
            positionMode = if (origin != null) PositionMode.INITIALIZED_AT_DEPARTURE else PositionMode.NO_GEOGRAPHIC_REFERENCE,
            updatedAt = clock(),
            magVarDeg = magVar,
        )
    }

    /** TS resetFlightNavigationForPlan: rebuilds the state for a newly loaded plan and publishes it. */
    fun reset(plan: ActiveFlightPlan, telemetry: NavigationTelemetry? = null): FlightNavigationState {
        val state = buildState(plan, telemetry)
        liveGuidance = INACTIVE_GUIDANCE
        writeState(state)
        return state
    }

    /**
     * TS advanceFlightNavigation. Call once per simulator tick with the active plan (null when none is
     * loaded). Moves the aircraft along the route at groundspeed times the time compression, in sub-steps
     * so it never jumps a waypoint, and sequences waypoints with the turn-anticipation distance.
     */
    fun advance(plan: ActiveFlightPlan?, telemetry: NavigationTelemetry) {
        if (!telemetry.dt.isFinite() || telemetry.dt <= 0) return
        if (plan == null) {
            liveGuidance = INACTIVE_GUIDANCE
            liveArrival = null
            return
        }
        liveArrival = plan.arrival
        // TS: `live && live.planActivatedAt === plan.activatedAt ? live : readState()`, then rebuild on a mismatch.
        val picked: FlightNavigationState? = if (live?.planActivatedAt == plan.activatedAt) live else readState()
        var state: FlightNavigationState = if (picked == null || picked.planActivatedAt != plan.activatedAt) buildState(plan, telemetry) else picked

        val origin = FlightRoute.departurePoint(plan)
        val targets = FlightRoute.targetList(plan)
        val destTarget = targets.lastOrNull()
        if (origin == null || state.positionMode == PositionMode.NO_GEOGRAPHIC_REFERENCE) {
            state = state.copy(positionMode = PositionMode.NO_GEOGRAPHIC_REFERENCE, phase = FlightPhase.NO_PLAN, updatedAt = clock())
            live = state
            liveGuidance = INACTIVE_GUIDANCE
            elapsedSincePublish += telemetry.dt
            if (elapsedSincePublish >= 1) {
                elapsedSincePublish = 0.0
                writeState(state)
            }
            return
        }

        var latitude = state.latitude
        var longitude = state.longitude
        val speed = max(0.0, telemetry.groundSpeedKt)
        val dt = min(telemetry.dt, 1.0)
        val magVar = NavData.magVarAt(latitude)
        val trueHeading = PlanGuidance.wrap360(telemetry.headingDeg + magVar)
        val maxCompression = plan.timeCompression ?: FlightNavigation.DEFAULT_TIME_COMPRESSION
        val compression = FlightNavigation.enrouteCompression(
            maxCompression,
            telemetry.wow,
            telemetry.altitudeFt,
            plan.cruiseAltitudeFt,
            if (liveGuidance.todDistanceNm != null) liveGuidance.distToTodNm else null,
            state.distanceToDestinationNm,
        )
        var legIndex = min(state.activeLegIndex, max(0, targets.size - 1))
        var target: GuidanceTarget? = targets.getOrNull(legIndex)
        var range: Double? = target?.let { FlightNavigation.distanceNm(latitude, longitude, it.latitude, it.longitude) }

        // Move in short sub-steps so that, even when compressed, the aircraft never jumps over a waypoint.
        val totalNm = if (speed > 1) (speed * dt / 3600) * compression else 0.0
        var lastMoveBearing = trueHeading
        val steps = if (totalNm > 0) min(400.0, max(1.0, ceil(totalNm / 0.3))).toInt() else 1
        for (i in 0 until steps) {
            if (totalNm > 0) {
                // At 1x the aircraft flies its heading. As compression rises it is steered along the route instead,
                // blended smoothly so there is no jump when compression starts or ends.
                val w = if (maxCompression > 1) ((compression - 1) / max(1.0, maxCompression - 1)).coerceIn(0.0, 1.0) else 0.0
                val cur = target
                val toTarget = if (cur != null) FlightNavigation.bearingDeg(latitude, longitude, cur.latitude, cur.longitude) else trueHeading
                val moveBearing = if (w > 0) FlightNavigation.blendDeg(trueHeading, toTarget, w) else trueHeading
                lastMoveBearing = moveBearing
                var stepNm = totalNm / steps
                val r = range
                if (compression > 1.01 && r != null) stepNm = min(stepNm, r) // do not overshoot the active waypoint when compressed
                val angularDistance = stepNm / 3440.065
                val bearing = rad(moveBearing)
                val lat1 = rad(latitude)
                val lon1 = rad(longitude)
                val lat2 = asin(sin(lat1) * cos(angularDistance) + cos(lat1) * sin(angularDistance) * cos(bearing))
                val lon2 = lon1 + atan2(sin(bearing) * sin(angularDistance) * cos(lat1), cos(angularDistance) - sin(lat1) * sin(lat2))
                latitude = lat2 * 180 / PI
                longitude = ((lon2 * 180 / PI + 540) % 360) - 180
                range = target?.let { FlightNavigation.distanceNm(latitude, longitude, it.latitude, it.longitude) }
            }
            // Fly-by sequencing: switch to the next waypoint at the turn-anticipation distance (minimum 0.7 NM).
            while (target != null && range != null && legIndex < targets.size - 1) {
                val tgt = target
                val rng = range
                val start = FlightRoute.legStart(plan, targets, legIndex)
                val next = targets[legIndex + 1]
                val inbound = if (start != null) FlightNavigation.bearingDeg(start.lat, start.lon, tgt.latitude, tgt.longitude)
                    else FlightNavigation.bearingDeg(latitude, longitude, tgt.latitude, tgt.longitude)
                val outbound = FlightNavigation.bearingDeg(tgt.latitude, tgt.longitude, next.latitude, next.longitude)
                val threshold = max(0.7, PlanGuidance.leadDistanceNm(max(speed, 120.0), outbound - inbound))
                if (rng > threshold) break
                legIndex += 1
                target = targets.getOrNull(legIndex)
                range = target?.let { FlightNavigation.distanceNm(latitude, longitude, it.latitude, it.longitude) }
            }
        }

        val distanceToDestinationNm = destTarget?.let { FlightNavigation.distanceNm(latitude, longitude, it.latitude, it.longitude) }
        val route = FlightRoute.routeGuidance(latitude, longitude, trueHeading, plan, legIndex, targets)
        val cur = target
        val bearingToNext = cur?.let { FlightNavigation.bearingDeg(latitude, longitude, it.latitude, it.longitude) }
        var phase = FlightPhase.PARKED
        val dToDest = distanceToDestinationNm
        if (telemetry.wow) phase = if (speed > 1) FlightPhase.GROUND_MOVEMENT else FlightPhase.PARKED
        else if (dToDest != null && dToDest <= 60) phase = FlightPhase.DESCENT_APPROACH
        else if (telemetry.altitudeFt < plan.cruiseAltitudeFt - 1500) phase = FlightPhase.CLIMB
        else if (telemetry.altitudeFt >= plan.cruiseAltitudeFt - 1500) phase = FlightPhase.CRUISE
        if (dToDest != null && dToDest < 2) phase = FlightPhase.DESTINATION_AREA

        val guidance = PlanGuidance.computeGuidance(
            GuidanceInput(
                plan = GuidancePlan(plan.cruiseAltitudeFt, plan.cruiseSpeedKt, plan.arrival),
                targets = targets,
                legIndex = legIndex,
                latitude = latitude,
                longitude = longitude,
                altitudeFt = telemetry.altitudeFt,
                groundSpeedKt = speed,
                desiredTrackDeg = route.desiredTrackDeg,
                crossTrackNm = route.crossTrackNm,
                bearingToNextDeg = bearingToNext,
                distanceToNextNm = range,
                magVarDeg = magVar,
                distToThresholdNm = if (FlightRoute.arrivalScene(plan) != null) distanceToDestinationNm else null,
            ),
        )
        liveGuidance = guidance

        state = state.copy(
            latitude = latitude,
            longitude = longitude,
            headingDeg = ((telemetry.headingDeg % 360) + 360) % 360,
            altitudeFt = telemetry.altitudeFt,
            groundSpeedKt = speed,
            activeLegIndex = legIndex,
            activeLegName = cur?.name ?: plan.destination,
            distanceToNextNm = range,
            bearingToNextDeg = bearingToNext,
            desiredTrackDeg = route.desiredTrackDeg,
            crossTrackNm = route.crossTrackNm,
            trackErrorDeg = route.trackErrorDeg,
            distanceToDestinationNm = distanceToDestinationNm,
            phase = phase,
            positionMode = if (speed > 1) PositionMode.MOVING_WITH_SIMULATED_AIRCRAFT else state.positionMode,
            updatedAt = clock(),
            timeCompression = jsRound1(compression),
            trackTrueDeg = jsRound1(lastMoveBearing),
            magVarDeg = magVar,
            guidanceNote = guidance.note,
            guidanceVertMode = guidance.vertMode,
            guidanceTargetAltFt = guidance.targetAltFt,
            guidanceSpeedKt = guidance.speedAdvisoryKt,
            guidanceHeadingMagDeg = guidance.headingMagCmdDeg,
            guidanceTodNm = guidance.todDistanceNm,
            guidanceDistToTodNm = guidance.distToTodNm,
        )
        live = state
        elapsedSincePublish += telemetry.dt
        if (elapsedSincePublish >= 1 || state.planActivatedAt != plan.activatedAt) {
            elapsedSincePublish = 0.0
            writeState(state)
        }
    }

    private fun rad(n: Double) = n * PI / 180

    /** JS Math.round(x * 10) / 10 (round half up). */
    private fun jsRound1(x: Double): Double = floor(x * 10 + 0.5) / 10
}

enum class FlightPhase(val label: String) {
    NO_PLAN("NO PLAN"),
    PARKED("PARKED"),
    GROUND_MOVEMENT("GROUND MOVEMENT"),
    CLIMB("CLIMB"),
    CRUISE("CRUISE"),
    DESCENT_APPROACH("DESCENT / APPROACH"),
    DESTINATION_AREA("DESTINATION AREA"),
}

enum class PositionMode(val label: String) {
    INITIALIZED_AT_DEPARTURE("INITIALIZED AT DEPARTURE"),
    MOVING_WITH_SIMULATED_AIRCRAFT("MOVING WITH SIMULATED AIRCRAFT"),
    NO_GEOGRAPHIC_REFERENCE("NO GEOGRAPHIC REFERENCE"),
}

/** Per-tick aircraft telemetry the navigator needs (TS NavigationTelemetry). */
data class NavigationTelemetry(
    val dt: Double,
    val groundSpeedKt: Double,
    val headingDeg: Double,
    val altitudeFt: Double,
    val wow: Boolean,
)

/** TS FlightNavigationState (version 1). */
data class FlightNavigationState(
    val version: Int = 1,
    val planActivatedAt: String,
    val latitude: Double,
    val longitude: Double,
    /** Simulator heading (magnetic), degrees. */
    val headingDeg: Double,
    val altitudeFt: Double,
    val groundSpeedKt: Double,
    val activeLegIndex: Int,
    val activeLegName: String,
    val distanceToNextNm: Double?,
    /** True bearing to the active waypoint. */
    val bearingToNextDeg: Double?,
    /** Desired true track from the start of the active leg to its target. */
    val desiredTrackDeg: Double?,
    /** Signed cross-track error in NM; positive means right of the active leg. */
    val crossTrackNm: Double?,
    /** Wrapped difference between desired track and current true heading, degrees. */
    val trackErrorDeg: Double?,
    val distanceToDestinationNm: Double?,
    val phase: FlightPhase,
    val positionMode: PositionMode,
    val updatedAt: String,
    /** East-positive magnetic variation at the aircraft (magnetic = true - variation). */
    val magVarDeg: Double? = null,
    /** True direction the aircraft is actually moving along. */
    val trackTrueDeg: Double? = null,
    /** Current en-route position-time multiplier (1 = real time). */
    val timeCompression: Double? = null,
    /** PLAN NAV guidance summary for the planner display. */
    val guidanceNote: String? = null,
    val guidanceVertMode: VertMode? = null,
    val guidanceTargetAltFt: Double? = null,
    val guidanceSpeedKt: Double? = null,
    val guidanceHeadingMagDeg: Double? = null,
    val guidanceTodNm: Double? = null,
    val guidanceDistToTodNm: Double? = null,
)
