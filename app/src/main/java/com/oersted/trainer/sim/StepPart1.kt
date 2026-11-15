package com.oersted.trainer.sim

/**
 * Port of the first part of step() in src/lib/sim/derive.ts (TS ~1745-1905): fuel heat timers and
 * filter ice, heater outlet and drain/mast temperatures, generator drive oil, rain repellent, IRS
 * alignment, CVR timers, equipment bay temperature, oxygen, reverser latch, center pump relay, and the
 * trainer attitude model.
 *
 * Mutates [n] (the working copy of the state) in the same order as the TS. [d] is the derived view
 * taken at the top of the tick. The attitude block re-derives from [n] (TS `derive(n)`), as the TS does.
 */
internal object StepPart1 {
    /** TS `n.circuitBreakers[id]` truthiness: an absent entry counts as open. */
    fun cb(n: SimState, id: String): Boolean = n.circuitBreakers[id] == true

    fun approach(value: Double, target: Double, rate: Double, dt: Double): Double = SimMath.approach(value, target, rate, dt)

    fun apply(n: SimState, d: Derived, dt: Double) {
        val fuelAmbientC = ((n.ambientTempF ?: 59.0) - 32.0) * 5.0 / 9.0
        n.fuelTempC = approach(n.fuelTempC, fuelAmbientC, 0.008, dt)
        val fuelHeatTimerDt = dt * n.timeScale
        // ATA 75-12-01 WDM: ENG 1 heater/timer circuit follows DC Bus 1 and ENG 2 follows DC Bus 2.
        val fuelHeatControlAvailable1 = d.dcBus1Powered && cb(n, "fuel-heat-1")
        val fuelHeatControlAvailable2 = d.dcBus2Powered && cb(n, "fuel-heat-2")
        if (n.fuelHeat && fuelHeatControlAvailable1) {
            n.fuelHeatElapsedSec = minOf(60.0, n.fuelHeatElapsedSec + fuelHeatTimerDt)
            if (n.fuelHeatElapsedSec >= 60.0) n.fuelHeat = false
        } else if (!n.fuelHeat && n.fuelHeatElapsedSec < 60.0) {
            n.fuelHeatElapsedSec = 0.0
        } else if (!cb(n, "fuel-heat-1")) {
            n.fuelHeatElapsedSec = 0.0
        }
        if (n.fuelHeat2 && fuelHeatControlAvailable2) {
            n.fuelHeat2ElapsedSec = minOf(60.0, n.fuelHeat2ElapsedSec + fuelHeatTimerDt)
            if (n.fuelHeat2ElapsedSec >= 60.0) n.fuelHeat2 = false
        } else if (!n.fuelHeat2 && n.fuelHeat2ElapsedSec < 60.0) {
            n.fuelHeat2ElapsedSec = 0.0
        } else if (!cb(n, "fuel-heat-2")) {
            n.fuelHeat2ElapsedSec = 0.0
        }
        if (n.faults.fuelFilterIcingFault1 && !n.fuelFilterIceInjected1) {
            n.fuelFilterIceFraction1 = 1.0
            n.fuelFilterIceInjected1 = true
        } else if (!n.faults.fuelFilterIcingFault1) {
            n.fuelFilterIceInjected1 = false
            n.fuelFilterIceFraction1 = 0.0
        }
        if (n.faults.fuelFilterIcingFault2 && !n.fuelFilterIceInjected2) {
            n.fuelFilterIceFraction2 = 1.0
            n.fuelFilterIceInjected2 = true
        } else if (!n.faults.fuelFilterIcingFault2) {
            n.fuelFilterIceInjected2 = false
            n.fuelFilterIceFraction2 = 0.0
        }
        // Trainer approximation: linear ice clearance while the heater cycle runs (see derive.ts).
        if (d.fuelHeatValveOpen && d.eng1Running) n.fuelFilterIceFraction1 = maxOf(0.0, n.fuelFilterIceFraction1 - fuelHeatTimerDt / 60.0)
        if (d.fuelHeatValve2Open && d.eng2Running) n.fuelFilterIceFraction2 = maxOf(0.0, n.fuelFilterIceFraction2 - fuelHeatTimerDt / 60.0)
        val fuelHeater1Effective = d.fuelHeaterPowered && d.eng1Running
        val fuelHeater2Effective = d.fuelHeater2Powered && d.eng2Running
        val fuelHeater1OutletTarget = if (fuelHeater1Effective) maxOf(fuelAmbientC + 35.0, 45.0) else fuelAmbientC
        val fuelHeater2OutletTarget = if (fuelHeater2Effective) maxOf(fuelAmbientC + 35.0, 45.0) else fuelAmbientC
        n.fuelHeater1OutletTempC = approach(n.fuelHeater1OutletTempC, fuelHeater1OutletTarget, if (fuelHeater1Effective) 0.7 else 0.06, dt)
        n.fuelHeater2OutletTempC = approach(n.fuelHeater2OutletTempC, fuelHeater2OutletTarget, if (fuelHeater2Effective) 0.7 else 0.06, dt)

        // ATA 30-71 drain heaters and drain masts (trainer thermal rates).
        val drainAmbientC = ((n.ambientTempF ?: 59.0) - 32.0) * 5.0 / 9.0
        val toiletFwdOn = d.acMainBus2Powered && cb(n, "drain-heater") && !n.faults.drainHeaterFailure1
        val toiletAftOn = d.acMainBus2Powered && cb(n, "drain-heater") && !n.faults.drainHeaterFailure2
        val drainMastOn = d.drainMastAirPowered || d.drainMastGroundPowered
        n.drainHeater1TempC = approach(n.drainHeater1TempC, if (toiletFwdOn) maxOf(25.0, drainAmbientC + 20.0) else drainAmbientC, if (toiletFwdOn) 0.22 else 0.02, dt)
        n.drainHeater2TempC = approach(n.drainHeater2TempC, if (toiletAftOn) maxOf(25.0, drainAmbientC + 20.0) else drainAmbientC, if (toiletAftOn) 0.22 else 0.02, dt)
        n.drainMastForwardTempC = approach(
            n.drainMastForwardTempC,
            if (drainMastOn) maxOf(25.0, drainAmbientC + (if (d.drainMastAirPowered) 28.0 else 18.0)) else drainAmbientC,
            if (drainMastOn) 0.22 else 0.02, dt,
        )
        n.drainMastForwardLineTempC = approach(
            n.drainMastForwardLineTempC,
            if (drainMastOn) maxOf(25.0, drainAmbientC + (if (d.drainMastAirPowered) 24.0 else 14.0)) else drainAmbientC,
            if (drainMastOn) 0.22 else 0.02, dt,
        )
        n.drainMastAftTempC = approach(
            n.drainMastAftTempC,
            if (drainMastOn) maxOf(25.0, drainAmbientC + (if (d.drainMastAirPowered) 28.0 else 18.0)) else drainAmbientC,
            if (drainMastOn) 0.22 else 0.02, dt,
        )

        // ATA 24-11: simplified drive-oil pressure and temperature responses per side.
        for (side in 1..2) {
            val n2 = if (side == 1) n.n2_1 else n.n2_2
            val connected = if (side == 1) {
                !(n.gen1Disc && cb(n, "gen-drive-disc-1") && !n.faults.genDriveDisconnectFailure1)
            } else {
                !(n.gen2Disc && cb(n, "gen-drive-disc-2") && !n.faults.genDriveDisconnectFailure2)
            }
            val lowFault = if (side == 1) n.faults.genDriveOilPressureLow1 else n.faults.genDriveOilPressureLow2
            val hotFault = if (side == 1) n.faults.genDriveOilOverheat1 else n.faults.genDriveOilOverheat2
            val pressureTarget = if (n2 > 5 && connected && !lowFault) 60.0 else 0.0
            val tempTarget = when {
                hotFault -> 135.0
                n2 > 5 && connected -> 99.0
                else -> ((n.ambientTempF ?: 59.0) - 32.0) * 5.0 / 9.0
            }
            if (side == 1) {
                n.genDriveOilPressure1Psi = approach(n.genDriveOilPressure1Psi, pressureTarget, 80.0, dt)
                n.genDriveOilTemp1C = approach(n.genDriveOilTemp1C, tempTarget, if (hotFault) 2.4 else 0.25, dt)
            } else {
                n.genDriveOilPressure2Psi = approach(n.genDriveOilPressure2Psi, pressureTarget, 80.0, dt)
                n.genDriveOilTemp2C = approach(n.genDriveOilTemp2C, tempTarget, if (hotFault) 2.4 else 0.25, dt)
            }
        }

        // Momentary P5 controls are timers so repeated clicks are visible system events.
        n.rainRepelTimerL = if (cb(n, "rain-repellent-left")) maxOf(0.0, n.rainRepelTimerL - dt) else 0.0
        n.rainRepelTimerR = if (cb(n, "rain-repellent-right")) maxOf(0.0, n.rainRepelTimerR - dt) else 0.0
        if (n.irsLMode == "OFF") n.irsAlignL = 0.0
        else if (d.dcPower && cb(n, "irs-left") && n.irsLMode == "ALIGN") n.irsAlignL = minOf(1.0, n.irsAlignL + dt / 90.0)
        if (n.irsRMode == "OFF") n.irsAlignR = 0.0
        else if (d.dcPower && cb(n, "irs-right") && n.irsRMode == "ALIGN") n.irsAlignR = minOf(1.0, n.irsAlignR + dt / 90.0)
        if (n.irsLMode == "NAV" && n.irsAlignL >= 1.0 && d.dcPower && cb(n, "irs-left")) n.irsHeadingL = n.attitudeHeadingDeg
        if (n.irsRMode == "NAV" && n.irsAlignR >= 1.0 && d.dcPower && cb(n, "irs-right")) n.irsHeadingR = n.attitudeHeadingDeg
        if (n.cvrErase && d.dcPower && cb(n, "cvr") && n.wow && n.parkingBrake && !n.faults.cvrFailure) n.cvrMemoryErased = true
        if (d.dcPower && cb(n, "cvr") && !n.faults.cvrFailure && n.flightRecorderMode == "NORMAL") n.cvrElapsedSec += dt

        // Qualitative E/E bay thermal response: fans and NORMAL selections remove heat.
        val coolingNow = d.equipmentCoolingFlow
        val ambientC = ((n.ambientTempF ?: 75.0) - 32.0) * 5.0 / 9.0
        val equipmentBayTargetC = if (coolingNow > 0 && !n.faults.equipmentCoolingFailure) {
            ambientC + 12.0 + (1.0 - coolingNow) * 16.0
        } else {
            ambientC + 55.0
        }
        n.equipmentBayTempC = approach(n.equipmentBayTempC, equipmentBayTargetC, if (coolingNow > 0) 0.025 else 0.012, dt)

        // ATA 35 oxygen use/leakage (qualitative trainer behaviour).
        if (n.faults.oxygenSystemFailure) n.crewOxyPsi = maxOf(0.0, n.crewOxyPsi - 0.35 * dt)
        else if (n.crewOxyMode == "ON" && n.crewOxyPsi > 0) n.crewOxyPsi = maxOf(0.0, n.crewOxyPsi - 0.06 * dt)

        if (n.engine1FirePulled) n.cvrTest = false
        if (n.rev1 && !d.thrustReverser1Active) n.rev1 = false
        if (n.rev2 && !d.thrustReverser2Active) n.rev2 = false

        // ATA 28-22-0 post-SB 28A1228: center pump low-pressure caution at 10 s, relay cutout at 15 s.
        for (side in 1..2) {
            val switchOn = if (side == 1) n.fuelPumpCenter1 else n.fuelPumpCenter2
            val pumpPowered = if (side == 1) d.fuelCenterPump1On else d.fuelCenterPump2On
            if (!switchOn) {
                if (side == 1) { n.fuelCenterPump1RelayTripped = false; n.fuelCenterPump1LowPressureTimer = 0.0 }
                else { n.fuelCenterPump2RelayTripped = false; n.fuelCenterPump2LowPressureTimer = 0.0 }
            } else if (n.fuelCenterPumpAutoShutoffInstalled && !pumpPowered) {
                if (side == 1) {
                    n.fuelCenterPump1LowPressureTimer += dt
                    if (n.fuelCenterPump1LowPressureTimer >= 15.0) n.fuelCenterPump1RelayTripped = true
                } else {
                    n.fuelCenterPump2LowPressureTimer += dt
                    if (n.fuelCenterPump2LowPressureTimer >= 15.0) n.fuelCenterPump2RelayTripped = true
                }
            } else if (pumpPowered) {
                if (side == 1) n.fuelCenterPump1LowPressureTimer = 0.0 else n.fuelCenterPump2LowPressureTimer = 0.0
            }
        }

        // Trainer flight-attitude model (normalized rates, not AMM performance data).
        // TS re-derives here from the partially updated state.
        val af = SimDerive.computeAll(n)
        val rollCmd = af.controlSurfaces.aileronR
        val pitchCmd = af.controlSurfaces.elevator
        val speedFactor = maxOf(0.25, minOf(1.0, n.psFlightCasKt / 140.0))
        n.attitudeRollDeg = approach(n.attitudeRollDeg, rollCmd * 28.0, 16.0 * speedFactor, dt)
        val trimCutoutActive = (n.stabTrimCutoutA || n.stabTrimCutoutB) && !n.stabTrimOverride
        val trimPitchBias = if (trimCutoutActive) 0.0 else (n.stabTrimUnits - 5.0) * 0.08
        n.attitudePitchDeg = approach(n.attitudePitchDeg, pitchCmd * 12.0 + trimPitchBias, 5.5, dt)
        n.attitudeYawRate = approach(n.attitudeYawRate, af.controlSurfaces.rudder * 4.0 + n.attitudeRollDeg * 0.035, 2.5, dt)
        // ATA 22-15: yaw damper gating (training response).
        if (af.yawDamperEffective) n.attitudeYawRate *= 0.35
        n.attitudeHeadingDeg = (n.attitudeHeadingDeg + n.attitudeYawRate * dt + 360.0) % 360.0
    }
}
