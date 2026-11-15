package com.oersted.trainer.sim

/**
 * Port of the services section of src/lib/sim/derive.ts: recirc fan, fuel heaters and filter icing,
 * equipment cooling, yaw damper, oxygen, doors, CVR, interphone, ELT, rain repellent, generator-drive
 * indications. Must run AFTER ElectricalDerive.apply (uses its outputs and locals).
 * Trainer thresholds are copied verbatim from the TS; they are not Boeing limits.
 */
object ServicesDerive {
    fun apply(s: SimState, d: Derived, e: ElecLocals) {
        val cb = { id: String -> ElectricalDerive.cbClosed(s, id) }
        val eng1Running = d.eng1Running
        val eng2Running = d.eng2Running
        val dcPower = d.dcPower
        val acPower = d.acPower
        val indicationPower = e.indicationPower

        // ATA 21: recirc fan on AC power (trainer airflow value).
        d.recircFanPowered = s.recirc && acPower && cb("recirc-fan") && !s.faults.recircFanFailure
        d.recircAirflow = if (d.recircFanPowered) 0.18 else 0.0

        // ATA 75-12-01: ENG 1 heater on DC BUS-1, ENG 2 heater on DC BUS-2 (independent feeds).
        val fuelHeatControlAvailable1 = d.dcBus1Powered && cb("fuel-heat-1")
        val fuelHeatControlAvailable2 = d.dcBus2Powered && cb("fuel-heat-2")
        d.fuelHeaterPowered = fuelHeatControlAvailable1 && s.fuelHeat && s.fuelHeatElapsedSec < 60 && !s.faults.fuelHeatFailure
        d.fuelHeater2Powered = fuelHeatControlAvailable2 && s.fuelHeat2 && s.fuelHeat2ElapsedSec < 60 && !s.faults.fuelHeatFailure2
        d.fuelHeatValveOpen = d.fuelHeaterPowered
        d.fuelHeatValve2Open = d.fuelHeater2Powered

        // AMM ATA 73-14-0: DP switch closes at 4.4–5.8 psi; 5.1 psi is a trainer trip point.
        d.fuelFilterDeltaP1Psi = if (eng1Running) 3.0 + 2.5 * maxOf(0.0, minOf(1.0, s.fuelFilterIceFraction1)) else 0.0
        d.fuelFilterDeltaP2Psi = if (eng2Running) 3.0 + 2.5 * maxOf(0.0, minOf(1.0, s.fuelFilterIceFraction2)) else 0.0
        d.fuelFilterIcing1 = indicationPower && eng1Running && d.fuelFilterDeltaP1Psi >= 5.1
        d.fuelFilterIcing2 = indicationPower && eng2Running && d.fuelFilterDeltaP2Psi >= 5.1

        // WDM CH24/CH28: fuel temperature indication is fed from 28V AC MAIN BUS NO.2.
        d.fuelTempIndicationPowered = d.ac28vMainBus2Powered && cb("fuel-temp-indication")
        d.fuelTempLow = d.fuelTempIndicationPowered && s.fuelTempC < 0

        d.equipmentCoolingSupplyPowered = acPower && cb("equipment-cooling-supply") && !s.faults.equipmentCoolingFailure
        d.equipmentCoolingExhaustPowered = acPower && cb("equipment-cooling-exhaust") && !s.faults.equipmentCoolingFailure
        val supplyCoolingFactor = if (s.equipSupply == "NORMAL") 1.0 else 0.65
        val exhaustCoolingFactor = if (s.equipExhaust == "NORMAL") 1.0 else 0.65
        d.equipmentCoolingFlow = (if (d.equipmentCoolingSupplyPowered) 0.5 * supplyCoolingFactor else 0.0) +
            (if (d.equipmentCoolingExhaustPowered) 0.5 * exhaustCoolingFactor else 0.0)
        d.equipmentCoolingOverheat = indicationPower && (s.faults.equipmentCoolingFailure || s.equipmentBayTempC >= 45)

        d.yawDamperEffective = s.yawDamper && dcPower && cb("yaw-damper") && s.hydBPress >= 800 && !s.faults.yawDamperFailure

        // Cabin altitude estimate (trainer): flight altitude minus 1600 ft per psi of differential.
        val estimatedCabinAltitudeFt = maxOf(0.0, s.psFlightAltFt - maxOf(0.0, s.cabinDiff) * 1600)
        d.oxygenPressureIndicated = dcPower && cb("crew-oxygen-indication") && !s.faults.oxygenSystemFailure
        d.passengerOxygenDeployed = !s.faults.oxygenSystemFailure && cb("passenger-oxygen-control") &&
            (s.passOxyMode == "ON" || (s.passOxyAutoDeploy && estimatedCabinAltitudeFt >= 14000))

        val doorOpen = with(s.doorPositions) {
            listOf(fwdEntry, cargo, fwdCargo, fwdService, aftEntry, equip, aftCargo, aftService).any { it }
        }
        d.doorWarning = indicationPower && cb("door-warning") && !s.faults.doorWarningFailure && doorOpen

        d.cvrPowered = dcPower && cb("cvr") && !s.faults.cvrFailure
        d.cvrOff = !d.cvrPowered
        d.serviceInterphoneEffective = s.serviceInterphoneOn && dcPower && cb("service-interphone")
        d.serviceCallEffective = (s.cabinCallActive || s.groundCrewCallActive) && dcPower && cb("service-interphone")
        d.eltTransmitting = cb("elt") && (s.eltMode == "ON" ||
            (s.eltMode == "ARM" && (s.rwyPhase == "OVERRUN" || (s.rwyPhase == "STOPPED" && kotlin.math.abs(s.rwyYawRate) > 18))))
        d.rainRepellentLActive = indicationPower && cb("rain-repellent-left") && s.rainRepelTimerL > 0 && !s.faults.rainRepellentFailure
        d.rainRepellentRActive = indicationPower && cb("rain-repellent-right") && s.rainRepelTimerR > 0 && !s.faults.rainRepellentFailure

        // Generator-drive CSD indications (ATA 24-11). Thresholds are trainer values.
        d.generatorDrive1LowOilPressure = indicationPower && cb("gen-drive-indication-1") && e.eng1AccessoryDriven &&
            (s.genDriveOilPressure1Psi < 15 || s.faults.genDriveOilPressureLow1 || e.gen1DriveDisconnected)
        d.generatorDrive2LowOilPressure = indicationPower && cb("gen-drive-indication-2") && e.eng2AccessoryDriven &&
            (s.genDriveOilPressure2Psi < 15 || s.faults.genDriveOilPressureLow2 || e.gen2DriveDisconnected)
        d.generatorDrive1HighOilTemp = indicationPower && cb("gen-drive-indication-1") && (s.genDriveOilTemp1C >= 120 || s.faults.genDriveOilOverheat1)
        d.generatorDrive2HighOilTemp = indicationPower && cb("gen-drive-indication-2") && (s.genDriveOilTemp2C >= 120 || s.faults.genDriveOilOverheat2)
    }
}
