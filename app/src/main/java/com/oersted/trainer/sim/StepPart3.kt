package com.oersted.trainer.sim

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Port of the third part of step() in src/lib/sim/derive.ts (TS ~2470-2895): brake pressures and
 * antiskid, the landing / rollout trainer (friction, wheel slip, deceleration, directional dynamics),
 * gear and flap/LE/spoiler actuation, pack trips, cabin temperatures, icing and alpha vanes, ground
 * service, cabin pressurization, and the progressive flight profile.
 *
 * Mirrors the TS order. `d2` and `brakeState` are the mid-tick derived views taken in part 2.
 */
internal object StepPart3 {
    /** Four-wheel map in TS key order (li, lo, ri, ro). Used for transient per-wheel values. */
    class Quad(var li: Double = 0.0, var lo: Double = 0.0, var ri: Double = 0.0, var ro: Double = 0.0) {
        fun copy() = Quad(li, lo, ri, ro)
        fun mean() = (li + lo + ri + ro) / 4.0
        fun mapAll(f: (Double) -> Double) = Quad(f(li), f(lo), f(ri), f(ro))
    }

    /** Pressure in psi from the brake source for one wheel (TS rawPressures). */
    private fun wheelSource(hydPress: Double, fromSystem: String): String = if (hydPress > 1500) fromSystem else "NONE"

    private fun muFromSlip(muPeak: Double, slip: Double): Double {
        val s = maxOf(0.0, minOf(1.0, slip))
        // Peak around 0.13 slip; linear rise then linear fall to ~0.55 * peak at lock.
        if (s <= 0.13) return muPeak * (s / 0.13)
        return muPeak * (0.55 + 0.45 * (1 - (s - 0.13) / 0.87))
    }

    private fun approach(v: Double, t: Double, r: Double, dt: Double) = SimMath.approach(v, t, r, dt)

    private fun cb(n: SimState, id: String): Boolean = n.circuitBreakers[id] == true

    /** Brakes, antiskid, rollout, deceleration and directional dynamics (TS ~2470-2655). */
    fun brakesAndRollout(n: SimState, d2: Derived, brakeState: Derived, dt: Double) {
        val kt2ms = 0.5144
        val rwyLen = RUNWAY_LENGTH_M
        val aimpoint = RUNWAY_AIMPOINT_M
        val halfWidth = RUNWAY_HALF_WIDTH_M
        val patchStart = PATCH_START_M
        val patchEnd = PATCH_END_M
        val rad = PI / 180.0
        val g = 28.0
        val wheelR = Quad(li = -2.2, lo = -3.0, ri = 2.2, ro = 3.0)
        val muPeakTable = mapOf("DRY" to 0.72, "WET" to 0.42, "ICY" to 0.18)

        // Automatic pressure and pilot pedal pressure compete at the shuttle valves.
        val autoPressure = if (brakeState.autobrakeActive) brakeState.autobrakePressure else 0.0
        val manualLeft = n.leftBrakePedal * 3000.0
        val manualRight = n.rightBrakePedal * 3000.0
        // Inboard brakes come from System A, outboard from System B.
        val srcLi = wheelSource(n.hydAPress, "A")
        val srcLo = wheelSource(n.hydBPress, "B")
        val srcRi = wheelSource(n.hydAPress, "A")
        val srcRo = wheelSource(n.hydBPress, "B")
        val rawPressures = Quad(
            li = if (srcLi == "A") maxOf(autoPressure, manualLeft) else 0.0,
            lo = if (srcLo == "B") maxOf(autoPressure, manualLeft) else 0.0,
            ri = if (srcRi == "A") maxOf(autoPressure, manualRight) else 0.0,
            ro = if (srcRo == "B") maxOf(autoPressure, manualRight) else 0.0,
        )

        // Landing / rollout trainer: approach -> touchdown -> rollout.
        if (n.rwyPhase == "APPROACH") {
            n.rwyPosM += n.groundSpeedKt * kt2ms * dt
            n.rwyAltFt = maxOf(0.0, (aimpoint - n.rwyPosM) * 0.172)
            n.airborneTest = true
            n.wow = false
            if (n.rwyPosM >= aimpoint) {
                n.rwyPhase = "ROLLOUT"
                n.airborneTest = false
                n.wow = true
                n.rwyAltFt = 0.0
                n.wheelSpeeds = SimStateWheelSpeeds().also { it.li = 0.0; it.lo = 0.0; it.ri = 0.0; it.ro = 0.0 }
                n.rwyNote = "TOUCHDOWN — wheels spinning up"
            }
        } else if (n.rwyPhase == "ROLLOUT" || n.rwyPhase == "OVERRUN") {
            n.rwyPosM += n.groundSpeedKt * kt2ms * cos(n.rwyTrackDeg * rad) * dt
        }
        val rolling = n.rwyPhase == "ROLLOUT" || n.rwyPhase == "OVERRUN"

        // Per-wheel peak surface friction: base surface, icy half-runway patch, grass off the edge.
        fun muPeakOf(lateralOffset: Double): Double {
            val wLat = n.rwyLatM + lateralOffset * cos(n.rwyHdgDeg * rad)
            val wPos = n.rwyPosM
            if (wPos > rwyLen || abs(wLat) > halfWidth) return 0.28 // grass / overrun
            var mu = muPeakTable[n.runway] ?: 0.72
            val inPatch = wPos >= patchStart && wPos <= patchEnd &&
                ((n.rwyPatch == "LEFT" && wLat < 0) || (n.rwyPatch == "RIGHT" && wLat > 0))
            if (inPatch) mu = minOf(mu, 0.12)
            return mu
        }
        val muPeakW = Quad(li = muPeakOf(wheelR.li), lo = muPeakOf(wheelR.lo), ri = muPeakOf(wheelR.ri), ro = muPeakOf(wheelR.ro))
        val offRunway = n.rwyPosM > rwyLen || abs(n.rwyLatM) > halfWidth + 2.5

        // Antiskid: pair locked-wheel protection, then per-channel modulation toward the friction peak.
        val nextWheelPressures = rawPressures.copy()
        fun wsOf(k: String): Double = when (k) {
            "li" -> n.wheelSpeeds.li
            "lo" -> n.wheelSpeeds.lo
            "ri" -> n.wheelSpeeds.ri
            else -> n.wheelSpeeds.ro
        }
        fun setPressure(k: String, v: Double) {
            when (k) {
                "li" -> nextWheelPressures.li = v
                "lo" -> nextWheelPressures.lo = v
                "ri" -> nextWheelPressures.ri = v
                else -> nextWheelPressures.ro = v
            }
        }
        fun pressureOf(k: String): Double = when (k) {
            "li" -> nextWheelPressures.li
            "lo" -> nextWheelPressures.lo
            "ri" -> nextWheelPressures.ri
            else -> nextWheelPressures.ro
        }
        fun pairLocked(slow: String, ref: Double): Boolean =
            n.groundSpeedKt > 25 && ref > 11 && wsOf(slow) > 0 && wsOf(slow) <= ref * 0.25
        fun pairProtection(a: String, b: String) {
            val ref = maxOf(wsOf(a), wsOf(b))
            val aLocked = pairLocked(a, ref)
            val bLocked = pairLocked(b, ref)
            if (aLocked && wsOf(a) < wsOf(b)) setPressure(a, 0.0)
            if (bLocked && wsOf(b) < wsOf(a)) setPressure(b, 0.0)
        }
        val keys = listOf("li", "lo", "ri", "ro")
        if (n.wow && n.groundSpeedKt > 11) {
            // Inboard and outboard channels protect their own wheel pairs.
            if (brakeState.antiskidInbdOperational) pairProtection("li", "ri")
            if (brakeState.antiskidOutbdOperational) pairProtection("lo", "ro")
            for (key in keys) {
                val channelOperational = if (key == "li" || key == "ri") brakeState.antiskidInbdOperational else brakeState.antiskidOutbdOperational
                if (!channelOperational) continue
                val wheel = wsOf(key)
                val ref = maxOf(n.groundSpeedKt, wheel)
                val slip = if (ref > 8) maxOf(0.0, 1 - wheel / ref) else 0.0
                // Begin relief near 10% slip; strong relief by ~28%.
                val modulation = maxOf(0.0, minOf(1.0, (slip - 0.10) / 0.18))
                setPressure(key, pressureOf(key) * (1 - modulation * 0.88))
            }
        }
        if (!n.wow) for (key in keys) setPressure(key, 0.0)
        n.wheelBrakePressures = SimStateWheelBrakePressures().also {
            it.li = nextWheelPressures.li
            it.lo = nextWheelPressures.lo
            it.ri = nextWheelPressures.ri
            it.ro = nextWheelPressures.ro
        }

        // Wheel slip: demand above what the surface gives drives the wheel toward lock.
        val nextWheelSpeeds = Quad(n.wheelSpeeds.li, n.wheelSpeeds.lo, n.wheelSpeeds.ri, n.wheelSpeeds.ro)
        fun nextWsOf(k: String): Double = when (k) {
            "li" -> nextWheelSpeeds.li
            "lo" -> nextWheelSpeeds.lo
            "ri" -> nextWheelSpeeds.ri
            else -> nextWheelSpeeds.ro
        }
        fun setNextWs(k: String, v: Double) {
            when (k) {
                "li" -> nextWheelSpeeds.li = v
                "lo" -> nextWheelSpeeds.lo = v
                "ri" -> nextWheelSpeeds.ri = v
                else -> nextWheelSpeeds.ro = v
            }
        }
        val wheelForce = Quad()
        fun setForce(k: String, v: Double) {
            when (k) {
                "li" -> wheelForce.li = v
                "lo" -> wheelForce.lo = v
                "ri" -> wheelForce.ri = v
                else -> wheelForce.ro = v
            }
        }
        fun muOf(k: String): Double = when (k) {
            "li" -> muPeakW.li
            "lo" -> muPeakW.lo
            "ri" -> muPeakW.ri
            else -> muPeakW.ro
        }
        var lockedCount = 0
        for (key in keys) {
            val frac = pressureOf(key) / 3000.0
            val demand = frac * g
            // Estimate current slip from the previous wheel speed, then evaluate available friction.
            val prevSlip = if (n.groundSpeedKt > 8) maxOf(0.0, 1 - wsOf(key) / n.groundSpeedKt) else 0.0
            val avail = muFromSlip(muOf(key), prevSlip) * g
            val excess = maxOf(0.0, demand - avail * 0.95) / maxOf(avail, 0.4)
            val slipTarget = minOf(1.0, frac * 0.06 + excess * 1.4)
            val targetWheelSpeed = if (n.wow) maxOf(0.0, n.groundSpeedKt * (1 - slipTarget)) else 0.0
            val rate = if (targetWheelSpeed < nextWsOf(key)) 160.0 else 95.0
            setNextWs(key, if (n.wow) approach(nextWsOf(key), targetWheelSpeed, rate, dt) else 0.0)
            val locked = n.wow && n.groundSpeedKt > 20 && nextWsOf(key) < n.groundSpeedKt * 0.25 && pressureOf(key) > 200
            if (locked) {
                lockedCount++
                val wear = n.tireWear
                when (key) {
                    "li" -> wear.li = minOf(1.0, wear.li + dt * 0.06)
                    "lo" -> wear.lo = minOf(1.0, wear.lo + dt * 0.06)
                    "ri" -> wear.ri = minOf(1.0, wear.ri + dt * 0.06)
                    else -> wear.ro = minOf(1.0, wear.ro + dt * 0.06)
                }
            }
            val slipNow = if (n.groundSpeedKt > 8) maxOf(0.0, 1 - nextWsOf(key) / n.groundSpeedKt) else 0.0
            val muNow = muFromSlip(muOf(key), slipNow)
            setForce(key, minOf(demand, muNow * g * (if (locked) 0.55 else 1.0)))
        }
        n.wheelSpeeds = SimStateWheelSpeeds().also {
            it.li = nextWheelSpeeds.li
            it.lo = nextWheelSpeeds.lo
            it.ri = nextWheelSpeeds.ri
            it.ro = nextWheelSpeeds.ro
        }

        // Deceleration: brake command, capped by the friction available under each wheel.
        val meanFrac = nextWheelPressures.mean() / 3000.0
        val hydraulicBrakeAvailable = n.hydAPress > 1500 || n.hydBPress > 1500
        val preReliefMean = rawPressures.mean() / 3000.0
        val relief = if (preReliefMean > 0.01) minOf(1.0, meanFrac / preReliefMean) else 1.0
        val autoDecel = if (brakeState.autobrakeActive) brakeState.targetDecel * relief else 0.0
        val pedalDecel = meanFrac * g
        val peakCap = muPeakW.mapAll { it * g }.mean()
        val capMean = if (wheelForce.mean() > 0) {
            maxOf(wheelForce.mean() * (if (lockedCount != 0) 0.75 * (1 - lockedCount / 8.0) else 1.0), 0.0)
        } else {
            peakCap
        }
        var commandedDecel = if (hydraulicBrakeAvailable) maxOf(autoDecel, pedalDecel) else 0.0
        commandedDecel = minOf(commandedDecel, maxOf(capMean, peakCap * 0.3))
        // Reverse thrust is trainer-normalized, not an AMM performance value.
        val reverseThrustDecel = (if (d2.thrustReverser1Active) 2.5 else 0.0) + (if (d2.thrustReverser2Active) 2.5 else 0.0)
        val drag = if (rolling && n.wow) {
            0.35 + (if (n.speedBrake == "UP" || n.speedBrake == "ARMED") 0.9 else 0.0) + reverseThrustDecel + (if (offRunway) 2.5 else 0.0)
        } else {
            0.0
        }
        n.actualDecel = approach(n.actualDecel, commandedDecel, 18.0, dt)
        n.groundSpeedKt = maxOf(0.0, n.groundSpeedKt - (n.actualDecel + drag) * dt)

        // Directional dynamics: differential braking, rudder, nose-wheel steering, and tyre side force.
        if (rolling && n.wow) {
            val gs = n.groundSpeedKt
            val gsMs = gs * kt2ms
            val spd = minOf(1.0, gs / 12.0)
            val meanF = maxOf(wheelForce.mean(), 0.01)
            val scaleF = minOf(1.6, n.actualDecel / meanF)
            val fl = ((wheelForce.li + wheelForce.lo) / 2.0) * scaleF
            val fr = ((wheelForce.ri + wheelForce.ro) / 2.0) * scaleF
            // Cornering grip: friction remaining after slip (locked wheels give none).
            var grip = 0.0
            for (k in keys) {
                val slip = if (gs > 8) maxOf(0.0, 1 - wsOf(k) / gs) else 0.0
                grip += (muFromSlip(muOf(k), slip) * (1 - minOf(1.0, slip) * 0.85)) / 4.0
            }
            grip = maxOf(0.0, minOf(1.0, grip))
            val rudderAuth = brakeState.controlSurfaces.rudder
            val aero = minOf(1.5, (gs / 90.0) * (gs / 90.0))
            val nws = if (brakeState.noseWheelSteeringAvailable) brakeState.noseWheelSteeringAngle else 0.0
            val beta = n.rwyHdgDeg - n.rwyTrackDeg // side-slip
            val yawAcc = 0.95 * (fr - fl) * spd + // differential braking yaws toward the less-braked side
                7.0 * rudderAuth * aero + // aerodynamic rudder (needs speed)
                0.9 * nws * (0.35 + 0.65 * grip) * minOf(1.0, gs / 6.0) - // nose-wheel steering
                1.1 * beta * (0.15 + grip) * minOf(1.5, gs / 50.0) - // weathercock restoring moment
                1.6 * n.rwyYawRate
            n.rwyYawRate += yawAcc * dt
            n.rwyHdgDeg += n.rwyYawRate * dt
            // The track follows the heading through tyre side force.
            n.rwyTrackDeg += beta * 0.9 * grip * minOf(1.0, gs / 25.0) * dt
            n.rwyLatM += gsMs * sin(n.rwyTrackDeg * rad) * dt
            if (gs < 1) n.rwyYawRate *= 0.9
        }

        if (rolling) {
            if (n.rwyPhase == "ROLLOUT" && offRunway && n.groundSpeedKt > 1) {
                n.rwyPhase = "OVERRUN"
                n.rwyNote = if (n.rwyPosM > rwyLen) "RUNWAY OVERRUN" else "RUNWAY EXCURSION — ${if (n.rwyLatM < 0) "LEFT" else "RIGHT"}"
            }
            if (n.groundSpeedKt <= 1) {
                n.groundSpeedKt = 0.0
                n.rwyYawRate = 0.0
                if (n.rwyPhase == "ROLLOUT") {
                    n.rwyPhase = "STOPPED"
                    n.rwyNote = "STOPPED — ${maxOf(0L, floor(rwyLen - n.rwyPosM + 0.5).toLong())} m of runway remaining · " +
                        "${fixed0(abs(n.rwyLatM))} m ${if (n.rwyLatM < 0) "left" else "right"} of centreline"
                } else if (n.rwyPhase == "OVERRUN") {
                    n.rwyPhase = "STOPPED"
                    n.rwyNote = if (n.rwyPosM > rwyLen) {
                        "STOPPED ${floor(n.rwyPosM - rwyLen + 0.5).toLong()} m BEYOND RUNWAY END"
                    } else {
                        "STOPPED OFF THE RUNWAY — ${fixed0(abs(n.rwyLatM))} m ${if (n.rwyLatM < 0) "LEFT" else "RIGHT"}"
                    }
                }
            }
        }
    }

    /** JS toFixed(0) for non-negative values (round half up). */
    private fun fixed0(v: Double): String = String.format(Locale.ROOT, "%.0f", v)

    /** Gear and wheel-on-ground logic (TS ~2655-2700). */
    fun gear(n: SimState, dt: Double) {
        val gearCommandUpLocked = n.wow && n.gear == "UP" && !n.gearLeverOverride
        if (gearCommandUpLocked) n.gear = "OFF"
        if (n.gearLeverOverride && !n.wow) n.gearLeverOverride = false
        val groundAPressureAvailable = n.groundInterconnect && n.wow && n.hydBPress > 1500
        val gearHydraulicAvailable = n.hydAPress > 1500 || groundAPressureAvailable
        val gearTarget: Double? = when (n.gear) {
            "UP" -> 0.0
            "DN" -> 1.0
            else -> null
        }
        val gearRate = if (gearHydraulicAvailable) 0.45 else 0.0

        // Manual extension releases one uplock at a time and runs that gear to DOWN/LOCKED.
        val manualRate = 4.0
        val manualNoseReleased = n.manualReleasedNose
        val manualLeftReleased = n.manualReleasedLeftMain
        val manualRightReleased = n.manualReleasedRightMain

        n.noseGearPos = when {
            manualNoseReleased -> approach(n.noseGearPos, 1.0, manualRate, dt)
            n.landingGearFault == "NOSE" -> n.noseGearPos
            gearTarget == null -> n.noseGearPos
            else -> approach(n.noseGearPos, gearTarget, gearRate, dt)
        }
        n.leftMainGearPos = when {
            manualLeftReleased -> approach(n.leftMainGearPos, 1.0, manualRate, dt)
            n.landingGearFault == "LEFT_MAIN" -> n.leftMainGearPos
            gearTarget == null -> n.leftMainGearPos
            else -> approach(n.leftMainGearPos, gearTarget, gearRate, dt)
        }
        n.rightMainGearPos = when {
            manualRightReleased -> approach(n.rightMainGearPos, 1.0, manualRate, dt)
            n.landingGearFault == "RIGHT_MAIN" -> n.rightMainGearPos
            gearTarget == null -> n.rightMainGearPos
            else -> approach(n.rightMainGearPos, gearTarget, gearRate, dt)
        }

        if (manualNoseReleased && n.noseGearPos >= 0.999) {
            n.noseGearPos = 1.0
            n.manualReleasedNose = false
            n.manualExtendNose = false
        }
        if (manualLeftReleased && n.leftMainGearPos >= 0.999) {
            n.leftMainGearPos = 1.0
            n.manualReleasedLeftMain = false
            n.manualExtendLeftMain = false
        }
        if (manualRightReleased && n.rightMainGearPos >= 0.999) {
            n.rightMainGearPos = 1.0
            n.manualReleasedRightMain = false
            n.manualExtendRightMain = false
        }

        // A position fault clears once the affected gear reaches DOWN/LOCKED.
        if (n.landingGearFault == "NOSE" && n.noseGearPos >= 0.999) n.landingGearFault = "NONE"
        else if (n.landingGearFault == "LEFT_MAIN" && n.leftMainGearPos >= 0.999) n.landingGearFault = "NONE"
        else if (n.landingGearFault == "RIGHT_MAIN" && n.rightMainGearPos >= 0.999) n.landingGearFault = "NONE"

        // Legacy aggregate position: mean of the three physical gear positions.
        n.gearPos = (n.noseGearPos + n.leftMainGearPos + n.rightMainGearPos) / 3.0

        // Weight-on-wheels follows the physical gear position, with explicit ground/air test overrides.
        if (n.groundSenseTest) n.wow = true
        else if (n.airborneTest) n.wow = false
        else if (n.noseGearPos >= 0.98 && n.leftMainGearPos >= 0.98 && n.rightMainGearPos >= 0.98) n.wow = true
        else if (n.noseGearPos <= 0.02 && n.leftMainGearPos <= 0.02 && n.rightMainGearPos <= 0.02) n.wow = false
    }

    /** Flaps, spoilers, leading edges, and the pack trip latches; returns the flap target for later use. */
    fun flapsSpoilersLeadingEdge(n: SimState, d2: Derived, dt: Double) {
        if (!n.altFlapsArm) n.altFlapsControl = "OFF"
        val alternateFlapsCommanded = d2.alternateFlapTakeover && n.altFlapsControl != "OFF"
        val flapTarget = if (alternateFlapsCommanded) {
            if (n.altFlapsControl == "DN") 40.0 else 0.0
        } else {
            n.flaps
        }
        val normalFlapDriveAvailable = d2.teFlapHydraulicDriveAvailable ||
            (n.hydGroundPowerConnected && n.groundInterconnect && n.wow && n.parkingBrake)
        val alternateFlapDriveAvailable = alternateFlapsCommanded && d2.alternateFlapDrivePowered
        if (normalFlapDriveAvailable || alternateFlapDriveAvailable) {
            val rate = if (alternateFlapDriveAvailable && !normalFlapDriveAvailable) 0.08 else 0.45
            n.flapPos = approach(n.flapPos, flapTarget, rate, dt)
        }

        n.spoilerPos = approach(
            n.spoilerPos,
            if (n.speedBrake == "UP" && n.wow) 1.0 else if (n.speedBrake == "FLIGHT") 0.45 else 0.0,
            if (n.hydAPress > 1200 || n.hydBPress > 1200) 1.4 else 0.0,
            dt,
        )

        // Leading edges: retracted, intermediate, extended, from the TE flap position.
        val leDriveAvailable = d2.leDriveAvailable
        val normalizedFlapTarget = maxOf(0.0, minOf(40.0, flapTarget)) / 40.0
        val altFlapsHoldLeadingEdge = d2.alternateFlapTakeover && n.altFlapsControl != "DN"
        val leTarget = when {
            altFlapsHoldLeadingEdge -> n.lePos
            normalizedFlapTarget <= 0.02 -> 0.0
            normalizedFlapTarget < 0.28 -> 0.5
            else -> 1.0
        }
        if (leDriveAvailable) n.lePos = approach(n.lePos, leTarget, 0.35, dt)

        if (n.faults.packOverheat || n.tempControlFaultL == "PACK_OVERHEAT") n.packTripL = true
        if (n.faults.packOverheatR || n.tempControlFaultR == "PACK_OVERHEAT") n.packTripR = true
        if (n.faults.bleedOverheat1) n.bleedTrip1 = true
        if (n.faults.bleedOverheat2) n.bleedTrip2 = true
    }

    /**
     * Cabin temperature control, icing, alpha vanes, ground service, pressurization and the
     * progressive flight profile. [iceRatePrev] is the icing rate carried from the previous tick
     * (TS dynamic property `__iceRate`); returns the updated rate.
     */
    fun cabinIcingAndPressure(n: SimState, d2: Derived, dt: Double, liveWxIntensity: Double, iceRatePrev: Double?): Double? {
        val acNow = SimDerive.computeAll(n)
        val ambientF = n.ambientTempF ?: 75.0
        if (acNow.packLPackOverheatRelay || acNow.packLPackTrip250) n.packTripL = true
        if (acNow.packRPackOverheatRelay || acNow.packRPackTrip250) n.packTripR = true

        val leftSupply = acNow.controlCabinSupplyTempF ?: 0.0
        val rightSupply = acNow.passengerCabinSupplyTempF ?: 0.0
        val leftCabinTarget = n.tempSelectL ?: 75.0
        val rightCabinTarget = n.tempSelectR ?: 75.0
        val leftCabinAir = if (leftSupply > 0) leftSupply else ambientF
        val rightCabinAir = if (rightSupply > 0) rightSupply else ambientF
        val recircDamping = if (acNow.recircAirflow > 0) 0.78 else 1.0
        var cabinL = approach(n.controlCabinTempF ?: ambientF, leftCabinAir, 0.22 * recircDamping, dt)
        var cabinR = approach(n.passengerCabinTempF ?: ambientF, rightCabinAir, 0.18 * recircDamping, dt)
        if (n.tempModeL == "AUTO") cabinL = approach(cabinL, leftCabinTarget, 0.015, dt)
        if (n.tempModeR == "AUTO") cabinR = approach(cabinR, rightCabinTarget, 0.012, dt)
        n.controlCabinTempF = cabinL
        n.passengerCabinTempF = cabinR

        // Live-weather icing: at altitude in precipitation the aircraft accumulates ice; heat counters it.
        val altFt = n.psFlightAltFt
        val inIcingBand = altFt > 4000 && altFt < 28000
        val highSpeed = n.psFlightCasKt > 250
        var iceRate = iceRatePrev
        if (liveWxIntensity > 0.2 && inIcingBand) {
            n.icingConditions = true
            val rateBoost = if (highSpeed) 1.6 else 1.0
            iceRate = 0.012 * rateBoost * minOf(1.0, liveWxIntensity * 2)
        } else if (liveWxIntensity < 0.08) {
            n.icingConditions = false
            iceRate = 0.0
        }

        // ATA 30-31 alpha-vane icing (qualitative trainer rates).
        for (side in listOf("L", "R")) {
            val heated = if (side == "L") acNow.alphaVaneHeatedL else acNow.alphaVaneHeatedR
            // TS `__iceRate || 0.012`: an absent or zero rate falls back to 0.012.
            val rate = if (iceRate == null || iceRate == 0.0) 0.012 else iceRate
            if (side == "L") {
                if (n.icingConditions && !heated) n.alphaVaneIceL = minOf(1.0, n.alphaVaneIceL + rate * dt)
                else if (heated) n.alphaVaneIceL = maxOf(0.0, n.alphaVaneIceL - 0.08 * dt)
                else n.alphaVaneIceL = maxOf(0.0, n.alphaVaneIceL - 0.018 * dt)
            } else {
                if (n.icingConditions && !heated) n.alphaVaneIceR = minOf(1.0, n.alphaVaneIceR + rate * dt)
                else if (heated) n.alphaVaneIceR = maxOf(0.0, n.alphaVaneIceR - 0.08 * dt)
                else n.alphaVaneIceR = maxOf(0.0, n.alphaVaneIceR - 0.018 * dt)
            }
        }

        // ATA 38 servicing is ground-only.
        if (n.wow && n.waterServiceConnected) n.potableWater = minOf(100.0, n.potableWater + 8 * dt)
        if (n.wow && n.wasteServiceConnected) n.wasteLevel = maxOf(0.0, n.wasteLevel - 10 * dt)

        // Cabin pressurization: pack output is the inflow side; the outflow valve is the exhaust side.
        val supply = d2.cabinSupplyFlow
        val autoPress = n.pressMode == "AUTO" || n.pressMode == "STBY"
        val doorOpen = doorsAnyOpen(n.doorPositions)
        val targetDiff = if (n.wow || supply <= 0.01 || doorOpen) 0.0 else 7.4
        if (autoPress) {
            val pressureError = targetDiff - n.cabinDiff
            val controlBias = pressureError * 0.06
            val flowBalanceValve = supply / 1.05
            n.outflow = approach(
                n.outflow,
                if (doorOpen) 0.98 else maxOf(0.02, minOf(0.98, flowBalanceValve - controlBias)),
                0.70, dt,
            )
        }
        val exhaustCapacity = maxOf(0.0, minOf(1.0, n.outflow * 1.05))
        val netPressure = supply - exhaustCapacity
        val targetFromFlow = if (n.wow || doorOpen) 0.0 else maxOf(0.0, minOf(8.3, n.cabinDiff + netPressure * 2.8))
        n.cabinDiff = approach(n.cabinDiff, targetFromFlow, if (doorOpen) 2.5 else 0.95, dt)
        if (n.cabinDiff > 8.3) {
            n.cabinDiff = approach(n.cabinDiff, 8.3, 2.5, dt)
            n.outflow = maxOf(n.outflow, 0.82)
        }
        if (n.wow && n.cabinDiff > 0.05) n.cabinDiff = approach(n.cabinDiff, 0.0, 1.5, dt)

        progressiveFlightProfile(n, dt)
        return iceRate
    }

    /** True when any door position is open (TS Object.values(doorPositions).some(Boolean)). */
    private fun doorsAnyOpen(p: SimStateDoorPositions): Boolean =
        p.fwdEntry || p.cargo || p.fwdCargo || p.fwdService || p.aftEntry || p.equip || p.aftCargo || p.aftService

    /** ATA 34 progressive altitude and CAS toward the trainer targets (TS ~2905 block). */
    private fun progressiveFlightProfile(n: SimState, dt: Double) {
        if (!((n.psTrainerEnabled || (!n.wow && n.psFlightCasKt > 0)) && n.psFlightProgressive)) return
        val targetAlt = if (n.psFlightTargetAltFt.isFinite()) n.psFlightTargetAltFt else n.psFlightAltFt
        val targetCas = if (n.psFlightTargetCasKt.isFinite()) n.psFlightTargetCasKt else n.psFlightCasKt
        val vsFpm = if (n.psFlightVsFpm.isFinite()) n.psFlightVsFpm else 0.0

        // Integrate altitude toward the target at the current vertical speed (fpm -> ft/s).
        if (abs(targetAlt - n.psFlightAltFt) > 1) {
            val dir = kotlin.math.sign(targetAlt - n.psFlightAltFt)
            val rateFps = abs(vsFpm) / 60.0
            val stepFt = dir * rateFps * dt
            val nextAlt = n.psFlightAltFt + stepFt
            if ((dir > 0 && nextAlt >= targetAlt) || (dir < 0 && nextAlt <= targetAlt)) {
                n.psFlightAltFt = targetAlt
                if (abs(vsFpm) > 0) n.psFlightVsFpm = 0.0 // level off when the target is reached
            } else {
                n.psFlightAltFt = nextAlt
            }
        } else {
            n.psFlightAltFt = targetAlt
            if (abs(vsFpm) > 0 && abs(targetAlt - n.psFlightAltFt) <= 1) n.psFlightVsFpm = 0.0
        }

        // Gentle CAS transition toward the target airspeed; the gain follows the thrust produced.
        if (abs(targetCas - n.psFlightCasKt) > 0.5) {
            val casDir = kotlin.math.sign(targetCas - n.psFlightCasKt)
            val thrustFrac = maxOf(0.0, minOf(1.0, ((n.n1_1 + n.n1_2) / 2.0 - 32.0) / 60.0))
            val casRate = if (casDir > 0) 2.5 + 3.5 * thrustFrac else 2.5
            val nextCas = n.psFlightCasKt + casDir * casRate * dt
            n.psFlightCasKt = if ((casDir > 0 && nextCas >= targetCas) || (casDir < 0 && nextCas <= targetCas)) targetCas else nextCas
        } else {
            n.psFlightCasKt = targetCas
        }
    }
}
