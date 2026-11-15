package com.oersted.trainer.sim

import com.oersted.trainer.sim.FlightPhases.Phase
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * Pure rules from src/lib/sim/flightPhases.ts (controller) and src/lib/sim/autoPhases.ts.
 *
 * Each function takes the values the web controller read from the sim, MIP, and phase stores as
 * arguments and returns the values it would have written. The imperative controller (timers,
 * patchSim/patchChanged, setPhase, the MIP mirror) is NOT ported yet: it needs the sim store, the
 * MIP panel store, and the phase store. See docs/HANDOFF.md.
 */
object FlightPhaseControl {
    private const val ROLL_ACCEL_KT_S = 7.2
    private const val ROLL_DRAG_PER_KT = 0.0165
    private const val ROLLOUT_DECEL_KT_S = 3.4

    /** JS Math.round for the values used here (round half up). */
    fun jsRound(x: Double): Double = floor(x + 0.5)

    /** TS cruiseCasKt: cruise CAS clamped to 200..300 kt. A zero or NaN cruise speed falls back to 430 (TS `|| 430`). */
    fun cruiseCasKt(cruiseSpeedKt: Double, altFt: Double): Double {
        val spd = if (cruiseSpeedKt == 0.0 || cruiseSpeedKt.isNaN()) 430.0 else cruiseSpeedKt
        return FlightPhases.clamp(FlightPhases.casFromTas(spd, altFt), 200.0, 300.0)
    }

    /** TS slewThrottle: move the lever at 0.3 of full travel per second, without overshooting. */
    fun slewThrottle(cur: Double, target: Double, dt: Double): Double {
        val step = 0.3 * dt
        return if (abs(target - cur) <= step) target else cur + sign(target - cur) * step
    }

    /** TS throttleFor: thrust setting for each phase. */
    fun throttleFor(phase: Phase, agl: Double = 0.0): Double = when (phase) {
        Phase.TAKEOFF -> if (agl >= FlightPhases.ACCEL_ALT_AGL_FT - 200) 0.88 else 1.0 // climb thrust once the acceleration altitude is reached
        Phase.CLIMB -> 0.88
        Phase.CRUISE -> 0.72
        Phase.DESCENT -> 0.3
        Phase.APPROACH -> 0.5
        Phase.LAND -> 0.45
        else -> 0.0
    }

    data class RollState(val gs: Double, val t: Double, val lever: Double)

    /**
     * One takeoff-roll tick (TS controllerTick, ground roll). [t] is the roll time before this tick; the
     * returned [RollState.t] is after it. The thrust follows the lever through a short lag, and the
     * integration runs in at most 0.1 s sub-steps so a late tick gives the same roll as a punctual one.
     * [n1Avg] is the average N1 of both engines (an un-started trainer uses a scripted spool-up).
     */
    fun takeoffRollStep(gs: Double, t: Double, dt: Double, n1Avg: Double): RollState {
        val tNew = t + dt
        val lever = min(1.0, tNew / 1.0)
        val enginesRunning = n1Avg > 25
        var g = gs
        val t0 = tNew - dt
        var rem = dt
        var k = 0
        while (rem > 1e-6 && k < 40) {
            val h = min(0.1, rem)
            rem -= h
            val tt = t0 + (dt - rem)
            val leverNow = min(1.0, tt / 1.0)
            val thrustFrac = if (enginesRunning) {
                FlightPhases.clamp(max((n1Avg - 32) / 60, leverNow * (1 - exp(-tt / 0.6))), 0.0, 1.0)
            } else {
                min(1.0, tt / 1.5)
            }
            g = min(FlightPhases.VR_KT + 5, g + max(0.0, thrustFrac * (ROLL_ACCEL_KT_S - ROLL_DRAG_PER_KT * g)) * h)
            k++
        }
        return RollState(g, tNew, lever)
    }

    data class RolloutState(val gs: Double, val stopped: Boolean)

    /** TS rollout: decelerate at 3.4 kt/s; stopped at 0.5 kt or below. */
    fun rolloutStep(gs: Double, dt: Double): RolloutState {
        val next = max(0.0, gs - ROLLOUT_DECEL_KT_S * dt)
        return RolloutState(next, next <= 0.5)
    }

    /** TS accelHold: level-off to accelerate after lift-off, until clean at 190 kt or above 1,800 ft AGL. */
    fun accelHold(phase: Phase, agl: Double, cas: Double, flapsNow: Double): Boolean =
        phase == Phase.CLIMB && agl < FlightPhases.ACCEL_ALT_AGL_FT + 300 && (flapsNow > 0 || cas < 190)

    data class Schedule(val targetCas: Double, val flaps: Double)

    /**
     * TS per-phase speed and flap schedule (the switch in controllerTick). [speedAdvisoryKt] is the PLAN NAV
     * advisory (null when none). [distThrNm] is the distance to the threshold.
     */
    fun schedule(
        phase: Phase,
        altFt: Double,
        agl: Double,
        cas: Double,
        flapsNow: Double,
        distThrNm: Double,
        cruiseSpeedKt: Double,
        speedAdvisoryKt: Double?,
    ): Schedule = when (phase) {
        Phase.TAKEOFF -> {
            if (agl >= FlightPhases.ACCEL_ALT_AGL_FT - 200) {
                Schedule(220.0, if (cas >= 180) 0.0 else if (cas >= 165) 1.0 else 5.0)
            } else {
                Schedule(FlightPhases.V2_KT + 15, flapsNow)
            }
        }
        Phase.CLIMB -> {
            val tc = if (altFt < 10000) 250.0 else min(cruiseCasKt(cruiseSpeedKt, altFt) + 5, 290.0)
            // never retract flaps close to the ground, even if the pilot pressed CLIMB early
            val f = if (agl >= FlightPhases.MIN_FLAP_RETRACT_AGL_FT) (if (cas >= 180) 0.0 else if (cas >= 165) 1.0 else flapsNow) else flapsNow
            Schedule(tc, f)
        }
        Phase.CRUISE -> Schedule(cruiseCasKt(cruiseSpeedKt, altFt), 0.0)
        Phase.DESCENT -> Schedule(if (altFt > 10000) 280.0 else 250.0, 0.0)
        Phase.APPROACH -> {
            val raw = speedAdvisoryKt ?: (if (altFt < 10000) 210.0 else 250.0)
            val tc = FlightPhases.clamp(raw, 140.0, 250.0)
            Schedule(tc, if (tc <= 190) 5.0 else if (tc <= 220) 1.0 else 0.0)
        }
        Phase.LAND -> {
            val tc = if (distThrNm > 8) 160.0 else if (distThrNm > 4) 150.0 else 142.0
            Schedule(tc, if (distThrNm > 12) 15.0 else if (distThrNm > 5) 30.0 else 40.0)
        }
        else -> Schedule(cas, flapsNow)
    }

    data class VerticalCommand(
        val vsCmd: Double,
        val targetAltFt: Double,
        /** psFlightVsFpm to patch (rounded to 10 fpm) */
        val psFlightVsFpm: Double,
        /** psFlightTargetAltFt to patch (rounded to 1 ft) */
        val psFlightTargetAltFt: Double,
    )

    /**
     * TS vertical-speed command. [guideTargetAltFt] is the PLAN NAV target (null uses the current altitude).
     * [perfFactor] is climbPerformance(...).factor. [vsCmd] is the commanded vertical speed carried from the
     * previous tick and [dt] the elapsed time in seconds. The command ramps at 300 fpm/s for takeoff and
     * climb and 700 fpm/s otherwise. On LAND it flies a 3 degree path to the threshold.
     */
    fun verticalCommand(
        phase: Phase,
        altFt: Double,
        elevFt: Double,
        toDestElevFt: Double,
        distThrNm: Double,
        gsKt: Double,
        guideTargetAltFt: Double?,
        perfFactor: Double,
        accelHold: Boolean,
        vsCmd: Double,
        dt: Double,
    ): VerticalCommand {
        var tgtAlt = guideTargetAltFt ?: altFt
        // TAKEOFF flies its own slow climb: never depend on the PLAN NAV guidance being active.
        if (phase == Phase.TAKEOFF) {
            tgtAlt = min(elevFt + FlightPhases.ACCEL_ALT_AGL_FT, max(altFt, elevFt) + FlightPhases.TAKEOFF_CLIMB_STEP_FT)
        }
        if (accelHold) tgtAlt = min(tgtAlt, elevFt + FlightPhases.ACCEL_ALT_AGL_FT) // level off to accelerate and clean up
        val wanted: Double
        if (phase == Phase.LAND) {
            val pathAlt = toDestElevFt + 40 + max(0.0, distThrNm) * 318 // 3 degree glide path, aim point ~40 ft above the threshold
            val gsFpm = max(100.0, gsKt) * 5.3 // vertical speed that holds the 3 degree path
            wanted = FlightPhases.clamp(-gsFpm + (pathAlt - altFt) * 6, -1500.0, 1000.0)
            tgtAlt = if (wanted >= 0) altFt + 200 else toDestElevFt
        } else {
            // 737-200 (JT8D): about 2,000-2,500 fpm at 250 kt low down, falling with altitude; the first climb after lift-off is gentler.
            val climbMax = (if (phase == Phase.TAKEOFF) 1800.0 else FlightPhases.clamp(2500 - altFt * 0.05, 800.0, 2500.0)) * perfFactor
            val descMax = if (phase == Phase.APPROACH) 1500.0 else 2000.0
            wanted = FlightPhases.clamp((tgtAlt - altFt) * 6, -descMax, climbMax) // tapers to zero within ~300-400 ft of the target
        }
        val vsRamp = if (phase == Phase.TAKEOFF || phase == Phase.CLIMB) 300.0 else 700.0 // fpm per second
        var vs = vsCmd + FlightPhases.clamp(wanted - vsCmd, -vsRamp * dt, vsRamp * dt)
        if (phase != Phase.LAND && abs(tgtAlt - altFt) < 2) vs = 0.0
        return VerticalCommand(vs, tgtAlt, jsRound(vs / 10) * 10, jsRound(tgtAlt))
    }

    data class StartGate(val ok: Boolean, val reason: String)

    /** TS canStart: whether a phase button may be pressed now. */
    fun canStart(p: Phase, hasPlan: Boolean, phase: Phase, wow: Boolean, hasArrivalScene: Boolean): StartGate {
        if (!hasPlan || phase == Phase.NO_PLAN) return StartGate(false, "Activate a flight plan first")
        if (phase == Phase.READY && p != Phase.TAKEOFF && wow) return StartGate(false, "Start with TAKEOFF")
        if (p == Phase.TAKEOFF) {
            return if (wow && (phase == Phase.READY || phase == Phase.COMPLETE)) StartGate(true, "")
            else StartGate(false, if (wow) "Load the plan first" else "Already airborne")
        }
        if (wow) return StartGate(false, "Not airborne")
        if (phase == Phase.ROLLOUT || phase == Phase.COMPLETE) return StartGate(false, "Flight is over")
        if (p == Phase.LAND && !hasArrivalScene) return StartGate(false, "Auto-land needs the vectored ILS arrival (WSSS RWY 02L)")
        return StartGate(true, "")
    }

    /** TS totalRouteDistanceNm: departure, every waypoint and the destination, in NM. */
    fun totalRouteDistanceNm(plan: ActiveFlightPlan): Double {
        val pts = FlightRoute.planRoutePoints(plan)
        var total = 0.0
        for (i in 1 until pts.size) {
            total += FlightNavigation.distanceNm(pts[i - 1].latitude, pts[i - 1].longitude, pts[i].latitude, pts[i].longitude)
        }
        return total
    }

    /** Inputs AUTO PHASES reads (TS AutoCtx). */
    data class AutoContext(
        val agl: Double,
        val altFt: Double,
        val distThrNm: Double,
        val descentTgtFt: Double,
        val gearUp: Boolean,
        val locCaptured: Boolean,
        val inVectors: Boolean,
        val todReached: Boolean,
    )

    /** TS autoNext: the phase AUTO PHASES hands over to, or null to stay in the current phase. */
    fun autoNext(phase: Phase, cruiseAltFt: Double, c: AutoContext): Phase? = when (phase) {
        Phase.TAKEOFF -> if (c.agl >= FlightPhases.ACCEL_ALT_AGL_FT - 150 && c.gearUp) Phase.CLIMB else null
        Phase.CLIMB -> if (abs(c.altFt - cruiseAltFt) < 150) Phase.CRUISE else null
        Phase.CRUISE -> if (c.todReached) Phase.DESCENT else null
        Phase.DESCENT -> if (c.inVectors || c.altFt - c.descentTgtFt < 400) Phase.APPROACH else null
        Phase.APPROACH -> if (c.locCaptured && c.distThrNm < 14) Phase.LAND else null
        else -> null
    }

    /** TS descentTargetFt for a plan: the first vector fix's altitude, else 3,000 ft. */
    fun descentTargetForPlan(plan: ActiveFlightPlan): Double {
        val a = plan.arrival
        val fix = if (a != null) plan.waypoints.firstOrNull { it.name == a.firstFixName } else null
        return FlightPhases.descentTargetFt(fix?.altitudeFt)
    }

    /** JS number-to-string for the whole-number altitudes used in the notes (e.g. 1575.0 -> "1575"). */
    private fun ft(v: Double): String = if (v == floor(v) && abs(v) < 1e15) v.toLong().toString() else v.toString()

    /**
     * TS applyPhaseToGuidance: PLAN NAV only does what the pressed phase allows. Returns the guidance the
     * controller should use for [phase]. Inactive guidance or no plan returns [g] unchanged.
     */
    fun applyPhaseToGuidance(g: GuidanceResult, phase: Phase, altFt: Double, plan: ActiveFlightPlan?): GuidanceResult {
        if (!g.active || plan == null) return g
        return when (phase) {
            Phase.NO_PLAN, Phase.READY, Phase.ROLLOUT, Phase.COMPLETE ->
                INACTIVE_GUIDANCE.copy(note = if (phase == Phase.READY) "READY - press TAKEOFF" else "NO PLAN GUIDANCE")
            Phase.TAKEOFF -> {
                val elev = FlightPhases.fieldElev(plan.departure)
                val rwy = FlightPhases.departureRunwayHeadingMagOrNull(plan)
                val straight = rwy != null && altFt - elev < FlightPhases.LATERAL_NAV_AGL_FT
                // Slow initial climb: a target only ~450 ft above the aircraft, capped at 1,500 ft AGL.
                val stepTarget = min(elev + FlightPhases.ACCEL_ALT_AGL_FT, max(altFt, elev) + FlightPhases.TAKEOFF_CLIMB_STEP_FT)
                val base = g.copy(
                    targetAltFt = stepTarget, vertMode = VertMode.CLIMB, ilsArm = false, inVectors = false,
                    speedAdvisoryKt = FlightPhases.V2_KT + 15,
                    note = "TAKEOFF - slow climb to ${ft(elev + FlightPhases.ACCEL_ALT_AGL_FT)} FT",
                )
                if (straight) base.copy(headingMagCmdDeg = rwy, headingTrueCmdDeg = null) else base
            }
            Phase.CLIMB -> g.copy(
                targetAltFt = plan.cruiseAltitudeFt, vertMode = VertMode.CLIMB, ilsArm = false, inVectors = false,
                note = "CLIMB to ${ft(plan.cruiseAltitudeFt)} FT",
            )
            Phase.CRUISE -> {
                val level = if (abs(altFt - plan.cruiseAltitudeFt) < 500) plan.cruiseAltitudeFt else jsRound(altFt / 100) * 100
                g.copy(targetAltFt = level, vertMode = VertMode.CRUISE, ilsArm = false, inVectors = false, note = "CRUISE ${ft(level)} FT")
            }
            Phase.DESCENT -> {
                val tgt = descentTargetForPlan(plan)
                g.copy(targetAltFt = tgt, vertMode = VertMode.DESCENT, ilsArm = false, note = "DESCENT to ${ft(tgt)} FT")
            }
            Phase.APPROACH, Phase.LAND -> {
                if (g.inVectors || phase == Phase.LAND) g
                else {
                    val tgt = descentTargetForPlan(plan)
                    g.copy(targetAltFt = tgt, vertMode = VertMode.DESCENT, ilsArm = false, note = "APPROACH - descend to ${ft(tgt)} FT")
                }
            }
        }
    }
}
