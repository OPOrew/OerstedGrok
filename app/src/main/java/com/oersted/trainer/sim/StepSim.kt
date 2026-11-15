package com.oersted.trainer.sim

import java.util.WeakHashMap

// Trainer runway (short-field 737-200 style) used by the Brakes tab scene. Values from derive.ts.
const val RUNWAY_LENGTH_M = 1800.0
const val RUNWAY_AIMPOINT_M = 300.0
const val RUNWAY_HALF_WIDTH_M = 22.9
const val PATCH_START_M = 400.0
const val PATCH_END_M = 1000.0

/**
 * Port of step() and its helpers from src/lib/sim/derive.ts (TS ~1745-2917).
 *
 * step() runs in four ordered parts (StepPart1 .. StepPart3) that mirror the TS body. Each part
 * mutates a deep copy of the input state, so the caller's state is never changed.
 *
 * Trainer inputs that the TS reads from a global rather than from the state are passed explicitly:
 *   liveWxIntensity: the live-weather precipitation intensity (TS globalThis.__oerstedLiveWxIntensity).
 *
 * Icing rate: the TS keeps `__iceRate` as an untyped property that survives from one tick to the
 * next, because each tick copies the previous state. SimState has no such field, so the rate is kept
 * here, keyed by the state object that step() returned. Replace this with a real field once
 * SimState is regenerated from a TS type that declares it.
 *
 * Dropped on purpose: `__wxDragPenalty` and `__wxTurbulence` are written by the TS but never read.
 */
object StepSim {
    private val iceRateByState = WeakHashMap<SimState, Double>()

    fun step(s: SimState, dt: Double, liveWxIntensity: Double = 0.0): SimState {
        val n = s.copy()
        val d = SimDerive.computeAll(n)

        // Part 1: fuel heat, heater and drain temperatures, generator drive oil, timers, equipment bay,
        // oxygen, reverser latch, center-pump relay, and the attitude model.
        StepPart1.apply(n, d, dt)

        // Part 2: fire pulls, wheel-well and cargo fire, scavenge, APU, engines and oil, fuel draw,
        // reservoirs and pressures, autobrake latches.
        StepPart2.firePulledBlocks(n)
        StepPart2.wheelWellCargoScavenge(n, d, dt)
        StepPart2.apu(n, d, dt)
        StepPart2.engines(n, d, dt)
        val p2 = StepPart2.fuelHydraulicsAndAutobrake(n, d, dt)

        // Part 3: brakes and rollout, gear, flaps and spoilers, leading edges, pack latches,
        // cabin temperature, icing, alpha vanes, service, pressurization, progressive profile.
        StepPart3.brakesAndRollout(n, p2.d2, p2.brakeState, dt)
        StepPart3.gear(n, dt)
        StepPart3.flapsSpoilersLeadingEdge(n, p2.d2, dt)
        val prevIce = iceRateByState[s]
        val iceRate = StepPart3.cabinIcingAndPressure(n, p2.d2, dt, liveWxIntensity, prevIce)
        if (iceRate != null) iceRateByState[n] = iceRate
        return n
    }

    /** TS controlLabel: display label for a control key; unknown keys become spaced upper case. */
    fun controlLabel(key: String): String =
        LABELS[key] ?: key.replace(Regex("([A-Z])")) { " " + it.value }.uppercase()

    /**
     * TS pushLog: prepends a log entry (max 100) unless the newest entry has the same text.
     * [nowMs] stands in for Date.now(), so callers and tests control the timestamp.
     */
    fun pushLog(
        s: SimState,
        text: String,
        tone: String,
        control: String? = null,
        before: String? = null,
        after: String? = null,
        nowMs: Double = System.currentTimeMillis().toDouble(),
    ): SimState {
        val last = s.log.firstOrNull()
        if (last != null && last.text == text) return s
        val entry = LogEntry().also {
            it.t = nowMs
            it.text = text
            it.tone = tone
            it.control = control
            it.before = before
            it.after = after
        }
        val next = s.copy()
        next.log = (listOf(entry) + s.log).take(100).toMutableList()
        return next
    }

    private val LABELS: Map<String, String> = mapOf(
        "battery" to "BATTERY", "apuGen" to "APU GEN", "groundPowerOn" to "GRD PWR", "gen1" to "GEN 1", "gen2" to "GEN 2", "busTie" to "BUS TRANSFER",
        "fltCtrlA" to "FLT CTRL A", "fltCtrlB" to "FLT CTRL B", "spoilerA" to "SPOILER A", "spoilerB" to "SPOILER B", "altFlapsArm" to "ALT FLAPS ARM", "altFlapsControl" to "ALT FLAPS",
        "engHyd1" to "ENG 1 HYD PUMP", "engHyd2" to "ENG 2 HYD PUMP", "elecHyd1" to "ELEC 1 HYD PUMP", "elecHyd2" to "ELEC 2 HYD PUMP", "stbyHyd" to "STANDBY HYD", "groundInterconnect" to "GROUND INTERCONNECT",
        "apuMaster" to "APU MASTER", "apuBleed" to "APU BLEED", "bleed1" to "BLEED 1", "bleed2" to "BLEED 2", "isolation" to "ISOLATION VALVE", "packL" to "L PACK", "packR" to "R PACK", "recirc" to "RECIRC", "fuelHeat" to "ENG 1 FUEL HEAT", "fuelHeat2" to "ENG 2 FUEL HEAT", "yawDamper" to "YAW DAMPER", "gen1Disc" to "GEN DRIVE 1 DISCONNECT", "gen2Disc" to "GEN DRIVE 2 DISCONNECT", "equipSupply" to "EQUIPMENT COOLING SUPPLY", "equipExhaust" to "EQUIPMENT COOLING EXHAUST", "passOxyMode" to "PASSENGER OXYGEN", "eltMode" to "ELT", "irsLMode" to "IRS L MODE", "irsRMode" to "IRS R MODE", "serviceInterphoneOn" to "SERVICE INTERPHONE", "cabinCallActive" to "CABIN CALL", "groundCrewCallActive" to "GROUND CREW CALL", "flightRecorderMode" to "FLIGHT RECORDER", "groundPneumaticConnected" to "GROUND AIR",
        "waterServiceConnected" to "WATER CART", "wasteServiceConnected" to "WASTE CART", "start1" to "ENG 1 START", "lever1" to "ENG 1 IDLE", "throttle1" to "ENG 1 THRUST", "start2" to "ENG 2 START", "lever2" to "ENG 2 IDLE", "throttle2" to "ENG 2 THRUST", "antiIce1" to "ANTI-ICE 1", "antiIce2" to "ANTI-ICE 2",
        "antiskidInbd" to "INBD ANTISKID", "antiskidOutbd" to "OUTBD ANTISKID", "autobrake" to "AUTOBRAKE", "gear" to "GEAR LEVER", "parkingBrake" to "PARKING BRAKE", "flaps" to "FLAP LEVER", "speedBrake" to "SPEED BRAKE", "groundSenseTest" to "GROUND SENSE TEST", "airborneTest" to "AIRBORNE TEST",
        "engine1FirePulled" to "ENG 1 FIRE", "engine2FirePulled" to "ENG 2 FIRE", "apuFirePulled" to "APU FIRE", "apuBottleFired" to "APU BOTTLE", "fireBottleLDischarged" to "ENG 1 BOTTLE", "fireBottleRDischarged" to "ENG 2 BOTTLE",
    )
}
