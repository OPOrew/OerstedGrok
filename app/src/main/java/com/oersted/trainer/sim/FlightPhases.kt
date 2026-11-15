package com.oersted.trainer.sim

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Port of the pure constants and helpers in src/lib/sim/flightPhases.ts. The phase store, the
 * controller tick, touchdown/rollout, and plan-to-controls patches depend on store.ts, flightNavigation,
 * planGuidance, and navReceiver. They are NOT ported yet (see docs/HANDOFF.md).
 */
object FlightPhases {
    /** TS Phase type. The labels match the web strings exactly, because the UI shows them. */
    enum class Phase(val label: String) {
        NO_PLAN("NO PLAN"), READY("READY"), TAKEOFF("TAKEOFF"), CLIMB("CLIMB"), CRUISE("CRUISE"),
        DESCENT("DESCENT"), APPROACH("APPROACH"), LAND("LAND"), ROLLOUT("ROLLOUT"), COMPLETE("COMPLETE"),
    }

    /** TS PHASE_BUTTONS. */
    val PHASE_BUTTONS: List<Phase> = listOf(Phase.TAKEOFF, Phase.CLIMB, Phase.CRUISE, Phase.DESCENT, Phase.APPROACH, Phase.LAND)

    /** TS FIELD_ELEV_FT: field elevation in feet by ICAO code. */
    val FIELD_ELEV_FT: Map<String, Double> = mapOf("RPLL" to 75.0, "WSSS" to 22.0, "WMKK" to 69.0)

    /** TS V_SPEEDS: trainer takeoff speeds (flaps 5, about 100,000 lb); not a performance calculation. */
    const val V1_KT = 128.0
    const val VR_KT = 136.0
    const val V2_KT = 141.0

    const val ACCEL_ALT_AGL_FT = 1500.0
    const val GEAR_UP_AGL_FT = 50.0 // gear up at a positive rate of climb, a few seconds after lift-off
    const val LATERAL_NAV_AGL_FT = 400.0
    const val TAKEOFF_CLIMB_STEP_FT = 450.0
    /** Climb rate at lift-off (fpm). Without it the altitude integrator pins the aircraft on the runway. */
    const val LIFTOFF_VS_FPM = 600.0
    const val LIFTOFF_MARGIN_KT = 5.0
    /** Trainer ISA deviation in deg C (hot day). */
    const val TRAINER_ISA_DEV_C = 15.0
    const val MIN_FLAP_RETRACT_AGL_FT = 800.0
    const val DEFAULT_DESCENT_TARGET_FT = 3000.0

    /** TS departureRunwayHeadingMag as nullable, for callers that need the value directly. */
    fun departureRunwayHeadingMagOrNull(plan: ActiveFlightPlan): Double? = FlightRoute.departureRunwayHeadingMag(plan)

    /** TS fieldElev: unknown ICAO codes read as sea level. */
    fun fieldElev(icao: String): Double = FIELD_ELEV_FT[icao] ?: 0.0

    /** TS PHASE_CONFIG: configuration the controller commands in each phase. */
    val PHASE_CONFIG: Map<Phase, String> = mapOf(
        Phase.NO_PLAN to "-",
        Phase.READY to "Gear DOWN · flaps 5 · speedbrake ARMED (parked, takeoff configuration)",
        Phase.TAKEOFF to "Flaps 5 · gear UP at positive rate (~50 ft) · speedbrake ARMED on the roll, then DOWN · clean-up at 1,500 ft",
        Phase.CLIMB to "Gear UP · flaps retract on the speed schedule (not below 800 ft) · speedbrake DOWN",
        Phase.CRUISE to "Gear UP · flaps 0 · speedbrake DOWN",
        Phase.DESCENT to "Gear UP · flaps 0 · speedbrake DOWN",
        Phase.APPROACH to "Flaps 0 / 1 / 5 by speed · gear DOWN once flaps 5 or within 15 NM of the runway · speedbrake DOWN",
        Phase.LAND to "Gear DOWN · flaps 15 → 30 → 40 · speedbrake ARMED",
        Phase.ROLLOUT to "Gear DOWN · flaps 40 · speedbrake UP (spoilers deployed)",
        Phase.COMPLETE to "Gear DOWN · stopped",
    )

    /** TS tasFromCas: true airspeed from calibrated, about 1.6 % per 1,000 ft. */
    fun tasFromCas(casKt: Double, altFt: Double): Double = casKt * (1 + (altFt / 1000) * 0.016)

    /** TS casFromTas: inverse of [tasFromCas]. */
    fun casFromTas(tasKt: Double, altFt: Double): Double = tasKt / (1 + (altFt / 1000) * 0.016)

    /** TS clamp. */
    fun clamp(v: Double, lo: Double, hi: Double): Double = max(lo, min(hi, v))

    data class ClimbPerformance(val weightLb: Double, val factor: Double)

    /**
     * TS climbPerformance: trainer gross weight estimated from the tank percentages (fuelTank1,
     * fuelTankCenter, fuelTank2, each 0..100), then a weight factor and a temperature factor.
     * Percentages outside 0..100 are clamped, as in the TS.
     */
    fun climbPerformance(tank1Pct: Double, tankCenterPct: Double, tank2Pct: Double): ClimbPerformance {
        val pct = { v: Double -> clamp(v, 0.0, 100.0) }
        val fuelFrac = (pct(tank1Pct) + pct(tankCenterPct) + pct(tank2Pct)) / 300
        // 82,000 lb dry + payload ... 120,000 lb full, rounded to 500 lb as in the TS.
        val weightLb = (((82000 + 38000 * fuelFrac) / 500).roundToLong() * 500).toDouble()
        val weightFactor = clamp(1 - (weightLb - 100000) / 130000, 0.8, 1.08)
        val tempFactor = 1 - 0.0027 * TRAINER_ISA_DEV_C // about 4 % less climb rate at ISA+15
        return ClimbPerformance(weightLb, weightFactor * tempFactor)
    }

    /**
     * TS descentTargetFt: the first vector fix's altitude, else 3,000 ft. [firstFixAltFt] is null when
     * the plan has no arrival fix or the fix has no altitude (TS fix.altitudeFt > 0 check).
     */
    fun descentTargetFt(firstFixAltFt: Double?): Double =
        if (firstFixAltFt != null && firstFixAltFt > 0) firstFixAltFt else DEFAULT_DESCENT_TARGET_FT
}
