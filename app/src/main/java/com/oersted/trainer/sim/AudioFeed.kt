package com.oersted.trainer.sim

import kotlin.math.max

/**
 * Port of the `useSimAudio({...})` argument built in routes/cockpit.tsx (lines 56-119), plus the marker-beacon gating of
 * components/panel/MarkerAudio.tsx and the switch-click rule of store.ts `patch`. Pure: no Android API.
 *
 * Kotlin differences, all forced by types: the web `gearLever` / `speedBrake` strings are compared only for change by audio.ts, so they
 * are passed as the string's hash code (Double); the sign-light strings are passed as strings (AudioEvents.Inputs.signs).
 */
object AudioFeed {

    /** What one audio tick needs from the sim. */
    data class Feed(
        val tick: AudioTicker.TickInputs,
        val engines: AudioTicker.EngineInputs,
        /** marker beacon to sound (null = none) */
        val marker: MarkerBeacons.MarkerId?,
    )

    /** Overhead-local fields the feed reads (web `o`). */
    data class Overhead(val hornCutout: Boolean = false, val machWarn: Int = 0, val stallTest: Int = 0)

    private fun count(vararg b: Boolean): Double = b.count { it }.toDouble()

    private fun id(s: String): Double = s.hashCode().toDouble()

    /** Cabin altitude warning horn (about 10 000 ft); silenced by ALT HORN CUTOUT. */
    fun cabinAltFt(s: SimState): Double = max(0.0, s.psFlightAltFt - max(0.0, s.cabinDiff) * 1600)

    fun build(
        s: SimState,
        d: Derived,
        ped: Pedestal.PedestalState,
        apWarn: Boolean,
        gpwsTest: Boolean,
        o: Overhead,
        markers: MarkerBeacons.MarkerState,
        navOn: Boolean,
        soundEnabled: Boolean = true,
    ): Feed {
        val fireTestRing = d.dcPower && ped.fireTest == Pedestal.FireTest.OVHT_FIRE
        val bellOff = ped.bellSilenced && !fireTestRing
        val cabinAltHorn = d.dcPower && cabinAltFt(s) >= 10000 && !o.hornCutout

        val extras = AudioEvents.Inputs(
            gear = listOf(s.noseGearPos, s.leftMainGearPos, s.rightMainGearPos),
            wheelBrake = listOf(
                s.wheelBrakePressures.li, s.wheelBrakePressures.lo, s.wheelBrakePressures.ri, s.wheelBrakePressures.ro,
            ),
            gearLever = id(s.gear),
            flapPos = s.flapPos,
            flapLever = s.flaps,
            lePos = s.lePos,
            spoilerPos = s.spoilerPos,
            speedBrake = id(s.speedBrake),
            apuDoor = s.apuDoor,
            hydPressure = listOf(s.hydAPress, s.hydBPress, s.hydSPress),
            hydPumps = count(d.hydAPump1On, d.hydAPump2On, d.hydBPump1On, d.hydBPump2On, d.stbyOn),
            elecPumps = d.hydBPump1On || d.hydBPump2On || d.stbyOn,
            wow = s.wow,
            groundSpeedKt = s.groundSpeedKt,
            overrun = s.rwyPhase == "OVERRUN",
            wheelSpeeds = listOf(s.wheelSpeeds.li, s.wheelSpeeds.lo, s.wheelSpeeds.ri, s.wheelSpeeds.ro),
            antiskid = d.antiskidOperational,
            parkingBrake = s.parkingBrake,
            n1 = listOf(s.n1_1, s.n1_2),
            reversers = listOf(d.thrustReverser1Active, d.thrustReverser2Active),
            relays = listOf(s.battery, s.gen1, s.gen2, s.apuGen, d.externalPowerContactorClosed, s.busTie, d.apuGenOnBus),
            startValve = listOf(d.startValveOpen1, d.startValveOpen2),
            fireHandles = listOf(s.engine1FirePulled, s.engine2FirePulled, s.apuFirePulled),
            bottles = listOf(s.fireBottleLDischarged, s.fireBottleRDischarged, s.apuBottleFired, s.cargoBottleDischarged),
            signs = listOf(s.ltSeatBelt, s.ltNoSmoking),
            packs = listOf(d.packLFlow, d.packRFlow),
            fuelPumps = count(d.fuelPump1On, d.fuelPump2On, d.fuelCenterPumpOn),
            acPower = d.acPower,
            dcPower = d.dcPower,
            gearHorn = d.gearWarningHorn,
            configOrCabinHorn = cabinAltHorn,
            apWarn = apWarn,
            // Stall stick-shaker: overhead STALL WARNING TEST (NO.1/NO.2) or psStallWarningTest; needs DC
            stallWarn = d.dcPower && (o.stallTest != 0 || s.psStallWarningTest),
            // Mach / airspeed warning test (P5)
            machWarn = (o.machWarn != 0 || s.psMachWarningTest) && d.dcPower,
            gpwsTest = gpwsTest && d.dcPower,
            // Mode 5 live path not yet driven; sample still plays during GPWS TEST vocab
            glideslopeWarn = false,
        )

        val tick = AudioTicker.TickInputs(
            startSwitchSelected = listOf(s.start1 != "OFF", s.start2 != "OFF"),
            lever = listOf(s.lever1, s.lever2),
            n2 = listOf(s.n2_1, s.n2_2),
            fireE1 = fireTestRing || (!bellOff && d.engine1FireWarn),
            fireE2 = fireTestRing || (!bellOff && d.engine2FireWarn),
            fireApu = fireTestRing || (!bellOff && d.apuFireWarn),
            fireOther = !bellOff && (d.wheelWellFireWarn || d.cargoFwdFireWarn || d.cargoAftFireWarn),
            apuRpm = s.apuRpm,
            apuStarter = s.apuStarter,
            extras = extras,
        )
        val engines = AudioTicker.EngineInputs(
            n1 = listOf(s.n1_1, s.n1_2),
            n2 = listOf(s.n2_1, s.n2_2),
            startValve = listOf(d.startValveOpen1, d.startValveOpen2),
        )
        // MarkerAudio.tsx: enabled && powered && navOn ? activeMarker(markers) : null
        val marker = if (soundEnabled && d.dcPower && navOn) MarkerBeacons.activeMarker(markers) else null
        return Feed(tick, engines, marker)
    }

    /** Marker tone gain (MarkerAudio.tsx): 0.12 while the keyed tone is ON, else 0. */
    fun markerGain(id: MarkerBeacons.MarkerId?, t: Double): Double =
        if (id != null && MarkerBeacons.markerKeyed(id, t)) 0.12 else 0.0

    // ── switch click (store.ts patch) ──────────────────────────────────────────

    /**
     * store.ts `patch` click rule, applied to a before / after pair: a control that changed counts as a switch when it is a boolean or a
     * string, or when both old and new are whole numbers 0..6 (small detents). Continuous values (non-integers) stay silent.
     * Compares every primitive / string field of [SimState]; nested objects (faults, maps, lists) are ignored.
     */
    fun isSwitchChange(before: SimState, after: SimState): Boolean {
        for (f in SimState::class.java.declaredFields) {
            if (java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
            val t = f.type
            val simple = t == java.lang.Boolean.TYPE || t == String::class.java || t == java.lang.Double.TYPE ||
                t == java.lang.Integer.TYPE
            if (!simple) continue
            f.isAccessible = true
            val a = f.get(before)
            val b = f.get(after)
            if (a == b) continue
            if (a is Boolean || a is String) return true
            if (a is Number && b is Number) {
                val x = a.toDouble()
                val y = b.toDouble()
                if (x == Math.floor(x) && y == Math.floor(y) && x in 0.0..6.0 && y in 0.0..6.0) return true
            }
        }
        return false
    }

    /** audio.ts playSwitchClick throttle: at most one click per 35 ms. */
    const val CLICK_THROTTLE_S = 0.035
}
