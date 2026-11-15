package com.oersted.trainer.sim

/** GENERATED from src/lib/sim/store.ts (toggleFault labels) and SimTypes.kt Faults. Do not hand-edit. */
object FaultTable {
    val LABELS: Map<String, String> = mapOf(
        "apuDoorJam" to "APU inlet door jam",
        "hydALeak" to "Hydraulic System A leak",
        "packOverheat" to "Pack overheat (L)",
        "packOverheatR" to "Pack overheat (R)",
        "bleedOverheat1" to "Bleed overheat (engine 1)",
        "bleedOverheat2" to "Bleed overheat (engine 2)",
        "apuFireDetected" to "APU fire detected",
        "apuOverspeed" to "APU overspeed shutdown",
        "apuLowOilPressure" to "APU low oil pressure",
        "apuHighOilTemperature" to "APU high oil temperature",
        "engineFire1" to "Engine 1 fire detected",
        "engineFire2" to "Engine 2 fire detected",
        "hydBLeak" to "Hydraulic System B leak",
        "tr1Failure" to "TR 1 failure",
        "tr2Failure" to "TR 2 failure",
        "generator1Failure" to "Generator 1 failure",
        "generator2Failure" to "Generator 2 failure",
        "transferRelay1Failure" to "Transfer relay 1 failure",
        "transferRelay2Failure" to "Transfer relay 2 failure",
        "btb1Failure" to "BTB 1 failure",
        "btb2Failure" to "BTB 2 failure",
        "apuGeneratorBreakerFailure" to "APU generator breaker failure",
        "externalPowerContactorFailure" to "External power contactor failure",
        "openPhase1" to "Generator 1 open phase",
        "openPhase2" to "Generator 2 open phase",
        "tr3Failure" to "TR 3 failure",
        "batteryFailure" to "Battery failure",
        "staticInverterFailure" to "Static inverter failure",
        "teFlapHydMotorFail" to "TE flap hydraulic motor failure",
        "flapAsymmetry" to "Trailing-edge flap asymmetry",
        "antiskidInop" to "Antiskid control fault",
        "generator1OverFrequency" to "Generator 1 overfrequency",
        "generator1UnderFrequency" to "Generator 1 underfrequency",
        "generator1OverVoltage" to "Generator 1 overvoltage",
        "generator1UnderVoltage" to "Generator 1 undervoltage",
        "generator1OverCurrent" to "Generator 1 overcurrent",
        "generator1Differential" to "Generator 1 differential protection",
        "generator2OverFrequency" to "Generator 2 overfrequency",
        "generator2UnderFrequency" to "Generator 2 underfrequency",
        "generator2OverVoltage" to "Generator 2 overvoltage",
        "generator2UnderVoltage" to "Generator 2 undervoltage",
        "generator2OverCurrent" to "Generator 2 overcurrent",
        "generator2Differential" to "Generator 2 differential protection",
        "fuelTank1Leak" to "Fuel tank 1 leak",
        "fuelTank2Leak" to "Fuel tank 2 leak",
        "fuelCenterLeak" to "Center fuel tank leak",
        "fuelBoostPump1Fail" to "All tank 1 boost pumps failure",
        "fuelBoostPump2Fail" to "All tank 2 boost pumps failure",
        "fuelCenterPumpFail" to "Both center boost pumps failure",
        "fuelTank1FwdPumpFail" to "Tank 1 forward boost pump failure",
        "fuelTank1AftPumpFail" to "Tank 1 aft boost pump failure",
        "fuelTank2FwdPumpFail" to "Tank 2 forward boost pump failure",
        "fuelTank2AftPumpFail" to "Tank 2 aft boost pump failure",
        "fuelCenterPump1Fail" to "Center left boost pump failure",
        "fuelCenterPump2Fail" to "Center right boost pump failure",
        "fuelCrossfeedValveFail" to "Fuel crossfeed valve failure",
        "fuelFilterIcingFault1" to "Engine 1 fuel-filter icing event",
        "fuelFilterIcingFault2" to "Engine 2 fuel-filter icing event",
        "antiskidInbdInop" to "Inboard antiskid channel failure",
        "antiskidOutbdInop" to "Outboard antiskid channel failure",
        "oilChip1" to "Engine 1 oil chip detector",
        "oilChip2" to "Engine 2 oil chip detector",
        "oilLow1" to "Engine 1 low oil pressure",
        "oilLow2" to "Engine 2 low oil pressure",
        "genDriveDisconnectFailure1" to "Engine 1 generator-drive disconnect solenoid failure",
        "genDriveDisconnectFailure2" to "Engine 2 generator-drive disconnect solenoid failure",
        "genDriveOilPressureLow1" to "Engine 1 generator-drive oil pressure low",
        "genDriveOilPressureLow2" to "Engine 2 generator-drive oil pressure low",
        "genDriveOilOverheat1" to "Engine 1 generator-drive oil overheat",
        "genDriveOilOverheat2" to "Engine 2 generator-drive oil overheat",
        "yawDamperFailure" to "Yaw damper failure",
        "equipmentCoolingFailure" to "Equipment-cooling blower failure",
        "oxygenSystemFailure" to "Oxygen system failure",
        "doorWarningFailure" to "Door-warning circuit failure",
        "recircFanFailure" to "Cabin recirculation/gasper fan failure",
        "mixValveJamL" to "Left air-mix valve jam",
        "mixValveJamR" to "Right air-mix valve jam",
        "cvrFailure" to "Cockpit voice recorder failure",
        "rainRepellentFailure" to "Rain-repellent system failure",
        "fuelHeatFailure" to "Engine 1 fuel-heater valve/system failure",
        "fuelHeatFailure2" to "Engine 2 fuel-heater valve/system failure",
        "windowHeatFailureL" to "Left windshield heat failure",
        "windowHeatFailureR" to "Right windshield heat failure",
        "igniter1LeftFailure" to "Engine 1 left igniter/exciter failure",
        "igniter1RightFailure" to "Engine 1 right igniter/exciter failure",
        "igniter2LeftFailure" to "Engine 2 left igniter/exciter failure",
        "igniter2RightFailure" to "Engine 2 right igniter/exciter failure",
        "drainHeaterFailure1" to "Forward toilet drain heater failure",
        "drainHeaterFailure2" to "Aft toilet drain heater failure",
        "drainMastAirFailure" to "Drain-mast air-mode circuit failure",
        "drainMastGroundFailure" to "Drain-mast ground-mode circuit failure",
        "alphaVaneHeatFailureL" to "Left alpha-vane heater failure",
        "alphaVaneHeatFailureR" to "Right alpha-vane heater failure",
    )

    fun get(f: Faults, key: String): Boolean = when (key) {
        "apuDoorJam" -> f.apuDoorJam
        "hydALeak" -> f.hydALeak
        "packOverheat" -> f.packOverheat
        "packOverheatR" -> f.packOverheatR
        "bleedOverheat1" -> f.bleedOverheat1
        "bleedOverheat2" -> f.bleedOverheat2
        "apuFireDetected" -> f.apuFireDetected
        "apuOverspeed" -> f.apuOverspeed
        "apuLowOilPressure" -> f.apuLowOilPressure
        "apuHighOilTemperature" -> f.apuHighOilTemperature
        "engineFire1" -> f.engineFire1
        "engineFire2" -> f.engineFire2
        "hydBLeak" -> f.hydBLeak
        "tr1Failure" -> f.tr1Failure
        "tr2Failure" -> f.tr2Failure
        "generator1Failure" -> f.generator1Failure
        "generator2Failure" -> f.generator2Failure
        "transferRelay1Failure" -> f.transferRelay1Failure
        "transferRelay2Failure" -> f.transferRelay2Failure
        "btb1Failure" -> f.btb1Failure
        "btb2Failure" -> f.btb2Failure
        "apuGeneratorBreakerFailure" -> f.apuGeneratorBreakerFailure
        "externalPowerContactorFailure" -> f.externalPowerContactorFailure
        "openPhase1" -> f.openPhase1
        "openPhase2" -> f.openPhase2
        "tr3Failure" -> f.tr3Failure
        "batteryFailure" -> f.batteryFailure
        "staticInverterFailure" -> f.staticInverterFailure
        "teFlapHydMotorFail" -> f.teFlapHydMotorFail
        "flapAsymmetry" -> f.flapAsymmetry
        "antiskidInop" -> f.antiskidInop
        "generator1OverFrequency" -> f.generator1OverFrequency
        "generator1UnderFrequency" -> f.generator1UnderFrequency
        "generator1OverVoltage" -> f.generator1OverVoltage
        "generator1UnderVoltage" -> f.generator1UnderVoltage
        "generator1OverCurrent" -> f.generator1OverCurrent
        "generator1Differential" -> f.generator1Differential
        "generator2OverFrequency" -> f.generator2OverFrequency
        "generator2UnderFrequency" -> f.generator2UnderFrequency
        "generator2OverVoltage" -> f.generator2OverVoltage
        "generator2UnderVoltage" -> f.generator2UnderVoltage
        "generator2OverCurrent" -> f.generator2OverCurrent
        "generator2Differential" -> f.generator2Differential
        "fuelTank1Leak" -> f.fuelTank1Leak
        "fuelTank2Leak" -> f.fuelTank2Leak
        "fuelCenterLeak" -> f.fuelCenterLeak
        "fuelBoostPump1Fail" -> f.fuelBoostPump1Fail
        "fuelBoostPump2Fail" -> f.fuelBoostPump2Fail
        "fuelCenterPumpFail" -> f.fuelCenterPumpFail
        "fuelTank1FwdPumpFail" -> f.fuelTank1FwdPumpFail
        "fuelTank1AftPumpFail" -> f.fuelTank1AftPumpFail
        "fuelTank2FwdPumpFail" -> f.fuelTank2FwdPumpFail
        "fuelTank2AftPumpFail" -> f.fuelTank2AftPumpFail
        "fuelCenterPump1Fail" -> f.fuelCenterPump1Fail
        "fuelCenterPump2Fail" -> f.fuelCenterPump2Fail
        "fuelCrossfeedValveFail" -> f.fuelCrossfeedValveFail
        "fuelFilterIcingFault1" -> f.fuelFilterIcingFault1
        "fuelFilterIcingFault2" -> f.fuelFilterIcingFault2
        "antiskidInbdInop" -> f.antiskidInbdInop
        "antiskidOutbdInop" -> f.antiskidOutbdInop
        "oilChip1" -> f.oilChip1
        "oilChip2" -> f.oilChip2
        "oilLow1" -> f.oilLow1
        "oilLow2" -> f.oilLow2
        "genDriveDisconnectFailure1" -> f.genDriveDisconnectFailure1
        "genDriveDisconnectFailure2" -> f.genDriveDisconnectFailure2
        "genDriveOilPressureLow1" -> f.genDriveOilPressureLow1
        "genDriveOilPressureLow2" -> f.genDriveOilPressureLow2
        "genDriveOilOverheat1" -> f.genDriveOilOverheat1
        "genDriveOilOverheat2" -> f.genDriveOilOverheat2
        "yawDamperFailure" -> f.yawDamperFailure
        "equipmentCoolingFailure" -> f.equipmentCoolingFailure
        "oxygenSystemFailure" -> f.oxygenSystemFailure
        "doorWarningFailure" -> f.doorWarningFailure
        "recircFanFailure" -> f.recircFanFailure
        "mixValveJamL" -> f.mixValveJamL
        "mixValveJamR" -> f.mixValveJamR
        "cvrFailure" -> f.cvrFailure
        "rainRepellentFailure" -> f.rainRepellentFailure
        "fuelHeatFailure" -> f.fuelHeatFailure
        "fuelHeatFailure2" -> f.fuelHeatFailure2
        "windowHeatFailureL" -> f.windowHeatFailureL
        "windowHeatFailureR" -> f.windowHeatFailureR
        "igniter1LeftFailure" -> f.igniter1LeftFailure
        "igniter1RightFailure" -> f.igniter1RightFailure
        "igniter2LeftFailure" -> f.igniter2LeftFailure
        "igniter2RightFailure" -> f.igniter2RightFailure
        "drainHeaterFailure1" -> f.drainHeaterFailure1
        "drainHeaterFailure2" -> f.drainHeaterFailure2
        "drainMastAirFailure" -> f.drainMastAirFailure
        "drainMastGroundFailure" -> f.drainMastGroundFailure
        "alphaVaneHeatFailureL" -> f.alphaVaneHeatFailureL
        "alphaVaneHeatFailureR" -> f.alphaVaneHeatFailureR
        else -> throw IllegalArgumentException("Unknown fault: $key")
    }

    fun set(f: Faults, key: String, value: Boolean) {
        when (key) {
            "apuDoorJam" -> f.apuDoorJam = value
            "hydALeak" -> f.hydALeak = value
            "packOverheat" -> f.packOverheat = value
            "packOverheatR" -> f.packOverheatR = value
            "bleedOverheat1" -> f.bleedOverheat1 = value
            "bleedOverheat2" -> f.bleedOverheat2 = value
            "apuFireDetected" -> f.apuFireDetected = value
            "apuOverspeed" -> f.apuOverspeed = value
            "apuLowOilPressure" -> f.apuLowOilPressure = value
            "apuHighOilTemperature" -> f.apuHighOilTemperature = value
            "engineFire1" -> f.engineFire1 = value
            "engineFire2" -> f.engineFire2 = value
            "hydBLeak" -> f.hydBLeak = value
            "tr1Failure" -> f.tr1Failure = value
            "tr2Failure" -> f.tr2Failure = value
            "generator1Failure" -> f.generator1Failure = value
            "generator2Failure" -> f.generator2Failure = value
            "transferRelay1Failure" -> f.transferRelay1Failure = value
            "transferRelay2Failure" -> f.transferRelay2Failure = value
            "btb1Failure" -> f.btb1Failure = value
            "btb2Failure" -> f.btb2Failure = value
            "apuGeneratorBreakerFailure" -> f.apuGeneratorBreakerFailure = value
            "externalPowerContactorFailure" -> f.externalPowerContactorFailure = value
            "openPhase1" -> f.openPhase1 = value
            "openPhase2" -> f.openPhase2 = value
            "tr3Failure" -> f.tr3Failure = value
            "batteryFailure" -> f.batteryFailure = value
            "staticInverterFailure" -> f.staticInverterFailure = value
            "teFlapHydMotorFail" -> f.teFlapHydMotorFail = value
            "flapAsymmetry" -> f.flapAsymmetry = value
            "antiskidInop" -> f.antiskidInop = value
            "generator1OverFrequency" -> f.generator1OverFrequency = value
            "generator1UnderFrequency" -> f.generator1UnderFrequency = value
            "generator1OverVoltage" -> f.generator1OverVoltage = value
            "generator1UnderVoltage" -> f.generator1UnderVoltage = value
            "generator1OverCurrent" -> f.generator1OverCurrent = value
            "generator1Differential" -> f.generator1Differential = value
            "generator2OverFrequency" -> f.generator2OverFrequency = value
            "generator2UnderFrequency" -> f.generator2UnderFrequency = value
            "generator2OverVoltage" -> f.generator2OverVoltage = value
            "generator2UnderVoltage" -> f.generator2UnderVoltage = value
            "generator2OverCurrent" -> f.generator2OverCurrent = value
            "generator2Differential" -> f.generator2Differential = value
            "fuelTank1Leak" -> f.fuelTank1Leak = value
            "fuelTank2Leak" -> f.fuelTank2Leak = value
            "fuelCenterLeak" -> f.fuelCenterLeak = value
            "fuelBoostPump1Fail" -> f.fuelBoostPump1Fail = value
            "fuelBoostPump2Fail" -> f.fuelBoostPump2Fail = value
            "fuelCenterPumpFail" -> f.fuelCenterPumpFail = value
            "fuelTank1FwdPumpFail" -> f.fuelTank1FwdPumpFail = value
            "fuelTank1AftPumpFail" -> f.fuelTank1AftPumpFail = value
            "fuelTank2FwdPumpFail" -> f.fuelTank2FwdPumpFail = value
            "fuelTank2AftPumpFail" -> f.fuelTank2AftPumpFail = value
            "fuelCenterPump1Fail" -> f.fuelCenterPump1Fail = value
            "fuelCenterPump2Fail" -> f.fuelCenterPump2Fail = value
            "fuelCrossfeedValveFail" -> f.fuelCrossfeedValveFail = value
            "fuelFilterIcingFault1" -> f.fuelFilterIcingFault1 = value
            "fuelFilterIcingFault2" -> f.fuelFilterIcingFault2 = value
            "antiskidInbdInop" -> f.antiskidInbdInop = value
            "antiskidOutbdInop" -> f.antiskidOutbdInop = value
            "oilChip1" -> f.oilChip1 = value
            "oilChip2" -> f.oilChip2 = value
            "oilLow1" -> f.oilLow1 = value
            "oilLow2" -> f.oilLow2 = value
            "genDriveDisconnectFailure1" -> f.genDriveDisconnectFailure1 = value
            "genDriveDisconnectFailure2" -> f.genDriveDisconnectFailure2 = value
            "genDriveOilPressureLow1" -> f.genDriveOilPressureLow1 = value
            "genDriveOilPressureLow2" -> f.genDriveOilPressureLow2 = value
            "genDriveOilOverheat1" -> f.genDriveOilOverheat1 = value
            "genDriveOilOverheat2" -> f.genDriveOilOverheat2 = value
            "yawDamperFailure" -> f.yawDamperFailure = value
            "equipmentCoolingFailure" -> f.equipmentCoolingFailure = value
            "oxygenSystemFailure" -> f.oxygenSystemFailure = value
            "doorWarningFailure" -> f.doorWarningFailure = value
            "recircFanFailure" -> f.recircFanFailure = value
            "mixValveJamL" -> f.mixValveJamL = value
            "mixValveJamR" -> f.mixValveJamR = value
            "cvrFailure" -> f.cvrFailure = value
            "rainRepellentFailure" -> f.rainRepellentFailure = value
            "fuelHeatFailure" -> f.fuelHeatFailure = value
            "fuelHeatFailure2" -> f.fuelHeatFailure2 = value
            "windowHeatFailureL" -> f.windowHeatFailureL = value
            "windowHeatFailureR" -> f.windowHeatFailureR = value
            "igniter1LeftFailure" -> f.igniter1LeftFailure = value
            "igniter1RightFailure" -> f.igniter1RightFailure = value
            "igniter2LeftFailure" -> f.igniter2LeftFailure = value
            "igniter2RightFailure" -> f.igniter2RightFailure = value
            "drainHeaterFailure1" -> f.drainHeaterFailure1 = value
            "drainHeaterFailure2" -> f.drainHeaterFailure2 = value
            "drainMastAirFailure" -> f.drainMastAirFailure = value
            "drainMastGroundFailure" -> f.drainMastGroundFailure = value
            "alphaVaneHeatFailureL" -> f.alphaVaneHeatFailureL = value
            "alphaVaneHeatFailureR" -> f.alphaVaneHeatFailureR = value
            else -> throw IllegalArgumentException("Unknown fault: $key")
        }
    }
}
