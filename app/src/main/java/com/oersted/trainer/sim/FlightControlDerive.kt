package com.oersted.trainer.sim

import kotlin.math.abs
import kotlin.math.sign

/**
 * Port of the standby rudder, alternate flaps, control-surface mixer and adverse-yaw cue of src/lib/sim/derive.ts
 * (ATA 27/29/32). Trainer values copied from the TS: standby pressure 1800 psi, system A/B 1500 psi,
 * alternate flap limits 39.99 / 0.01 degrees, adverse-yaw weights 0.55 / 0.45, rudder cross-correction 0.85.
 * Control-surface authorities are 1 (the manual gives no numeric reduced authority; the TS uses normalized travel).
 * Runs after HydraulicsDerive (hydA, hydB, hydS, flight-control availability) and ElectricalDerive (acPower, dcPower).
 * The hydraulic and electrical "users" arrays are a separate, later section.
 */
object FlightControlDerive {
    private fun clamp(v: Double, lo: Double, hi: Double) = maxOf(lo, minOf(hi, v))

    fun apply(s: SimState, d: Derived) {
        val hydA = d.hydA
        val hydB = d.hydB
        val hydS = d.hydS
        val dcPower = d.dcPower
        val acPower = d.acPower
        val fcA = d.flightControlAAvailable
        val fcB = d.flightControlBAvailable

        // Standby rudder actuator: STBY HYD ON, or AUTO when both primary systems are low.
        val primaryHydraulicLoss = hydA < 1500.0 && hydB < 1500.0
        val standbyRudderActuatorDemand = s.stbyHyd == "ON" || (s.stbyHyd == "AUTO" && primaryHydraulicLoss)
        val standbyRudderPowered = hydS > 1800.0 && standbyRudderActuatorDemand
        d.standbyRudderPowered = standbyRudderPowered

        // ALT FLAPS ARM: electric TE drive bypass, standby pressure for LE DN only.
        val alternateFlapArmed = s.altFlapsArm
        val alternateFlapTakeover = alternateFlapArmed && dcPower
        d.alternateFlapArmed = alternateFlapArmed
        d.alternateFlapTakeover = alternateFlapTakeover
        val alternateFlapLimitReached = (s.altFlapsControl == "DN" && s.flapPos >= 39.99) ||
            (s.altFlapsControl == "UP" && s.flapPos <= 0.01)
        val teFlapAsymmetryShutdown = s.faults.flapAsymmetry
        val alternateFlapDrivePowered = alternateFlapTakeover && s.altFlapsControl != "OFF" &&
            acPower && !alternateFlapLimitReached && !teFlapAsymmetryShutdown
        d.alternateFlapDrivePowered = alternateFlapDrivePowered
        d.teFlapHydraulicDriveAvailable = hydA > 1500.0 && !alternateFlapTakeover &&
            !s.faults.teFlapHydMotorFail && !teFlapAsymmetryShutdown
        val leSystemAAvailable = hydA > 1500.0 && !alternateFlapTakeover
        val leStandbyAvailable = alternateFlapTakeover && s.altFlapsControl == "DN" && hydS > 1800.0
        d.leDriveAvailable = leSystemAAvailable || leStandbyAvailable

        // Aileron / elevator authority is 1 whenever a source exists; the TS keeps the same normalized value otherwise.
        val commandedRoll = s.yokeRoll
        val commandedPitch = s.yokePitch
        val aileronL = clamp(-commandedRoll, -1.0, 1.0)
        val aileronR = clamp(commandedRoll, -1.0, 1.0)
        val rudderAuthority = if (fcA || fcB || standbyRudderPowered) 1.0 else 0.0

        // Flight spoilers: 2/7 are System B, 3/6 are System A; each panel is source-gated.
        val spoilerBase = clamp(s.spoilerPos, 0.0, 1.0)
        val spoiler3 = if (fcA && s.spoilerA) clamp(spoilerBase + maxOf(aileronL, 0.0) * (1 - spoilerBase), 0.0, 1.0) else 0.0
        val spoiler2 = if (fcB && s.spoilerB) clamp(spoilerBase + maxOf(aileronL, 0.0) * (1 - spoilerBase), 0.0, 1.0) else 0.0
        val spoiler6 = if (fcA && s.spoilerA) clamp(spoilerBase + maxOf(aileronR, 0.0) * (1 - spoilerBase), 0.0, 1.0) else 0.0
        val spoiler7 = if (fcB && s.spoilerB) clamp(spoilerBase + maxOf(aileronR, 0.0) * (1 - spoilerBase), 0.0, 1.0) else 0.0
        val spoilerL = maxOf(spoiler2, spoiler3)
        val spoilerR = maxOf(spoiler6, spoiler7)
        val rudder = clamp(s.rudderPedal * rudderAuthority, -1.0, 1.0)

        val cs = d.controlSurfaces
        cs.elevator = clamp(commandedPitch, -1.0, 1.0)
        cs.aileronL = aileronL
        cs.aileronR = aileronR
        cs.rudder = rudder
        cs.spoiler = spoilerBase
        cs.spoilerL = spoilerL
        cs.spoilerR = spoilerR
        cs.spoiler2 = spoiler2
        cs.spoiler3 = spoiler3
        cs.spoiler6 = spoiler6
        cs.spoiler7 = spoiler7

        // Adverse-yaw training cue: a visualization, not a flight-dynamics prediction.
        val rollMagnitude = abs(commandedRoll)
        val rollDirection = if (commandedRoll > 0.0) 1 else if (commandedRoll < 0.0) -1 else 0
        val spoilerDifferential = abs(spoilerR - spoilerL)
        val rawAdverseYaw = clamp(rollMagnitude * 0.55 + spoilerDifferential * 0.45, 0.0, 1.0)
        val rudderSign = sign(rudder).toInt()
        val rudderCorrection = if (rollDirection != 0 && rudderSign == rollDirection) minOf(abs(rudder) * 0.85, rawAdverseYaw) else 0.0
        val adverseYawCue = if (s.adverseYawScenario) clamp(rawAdverseYaw - rudderCorrection, 0.0, 1.0) else 0.0
        d.adverseYawCue = adverseYawCue
        d.adverseYawDirection = if (adverseYawCue > 0.03) (if (rollDirection > 0) "LEFT" else "RIGHT") else "NONE"
        d.adverseYawCorrected = s.adverseYawScenario && rollMagnitude > 0.15 && rollDirection != 0 &&
            rudderSign == rollDirection && abs(rudder) >= 0.28 && adverseYawCue < 0.18
    }
}
