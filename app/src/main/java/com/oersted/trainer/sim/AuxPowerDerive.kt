package com.oersted.trainer.sim

/**
 * Port of the aux-power section of src/lib/sim/derive.ts: drain heaters/masts (WDM CH30), alpha-vane
 * heat, standby power (AUTO/BAT), ground service, battery charger feed, 28VAC branch, electrical
 * source labels. Runs AFTER ElectricalDerive and ServicesDerive. Trainer values copied from TS.
 */
object AuxPowerDerive {
    fun apply(s: SimState, d: Derived, e: ElecLocals) {
        val cb = { id: String -> ElectricalDerive.cbClosed(s, id) }

        // WDM CH30 sheet 30-71-01: C233 drain heater <- 115V AC BUS NO.2; C700 drain-mast AIR <- 115V AC BUS NO.1;
        // C224 drain-mast GROUND <- 28V AC MAIN BUS NO.2. Do not substitute transfer-bus power for main-bus feeds.
        d.drainHeater1Powered = d.acMainBus2Powered && cb("drain-heater") && !s.faults.drainHeaterFailure1
        d.drainHeater2Powered = d.acMainBus2Powered && cb("drain-heater") && !s.faults.drainHeaterFailure2
        d.drainMastAirPowered = !s.wow && !e.externalAc && d.acMainBus1Powered && cb("drain-mast-air") && !s.faults.drainMastAirFailure
        d.drainMastGroundPowered = s.wow && d.ac28vMainBus2Powered && cb("drain-mast-ground") && !s.faults.drainMastGroundFailure
        d.drainMastForwardPowered = d.drainMastAirPowered || d.drainMastGroundPowered
        d.drainMastAftPowered = d.drainMastForwardPowered
        d.drainMastForwardLinePowered = d.drainMastForwardPowered
        d.drainHeater1Hot = d.drainHeater1Powered && s.drainHeater1TempC >= 20
        d.drainHeater2Hot = d.drainHeater2Powered && s.drainHeater2TempC >= 20

        // ATA 30-31 alpha-vane heaters (qualitative trainer state).
        d.alphaVaneHeatedL = d.acPower && cb("alpha-vane-left") && s.probeHeatAlphaL && !s.faults.alphaVaneHeatFailureL
        d.alphaVaneHeatedR = d.acPower && cb("alpha-vane-right") && s.probeHeatAlphaR && !s.faults.alphaVaneHeatFailureR
        d.doorPressureLeak = with(s.doorPositions) {
            listOf(fwdEntry, cargo, fwdCargo, fwdService, aftEntry, equip, aftCargo, aftService).any { it }
        }

        // Standby power: AUTO follows TRANSFER BUS 1 + DC BUS 1; otherwise battery/static inverter.
        val standbyAutoNormal = s.standbyPower == "AUTO" && s.battery && e.batteryPower && d.transferBus1Powered && d.dcBus1Powered
        val standbyAutoTransfer = s.standbyPower == "AUTO" && s.battery && e.batteryPower && !standbyAutoNormal
        d.standbyAutoTransfer = standbyAutoTransfer
        d.staticInverterPowered = e.batteryPower && !s.faults.staticInverterFailure && (s.standbyPower == "BAT" || standbyAutoTransfer)
        d.standbyAcPowered = standbyAutoNormal || d.staticInverterPowered
        d.standbyDcPowered = if (standbyAutoNormal) d.dcBus1Powered else d.staticInverterPowered
        d.standbyAcSource = if (standbyAutoNormal) "TRANSFER BUS 1" else if (d.staticInverterPowered) "STATIC INVERTER" else "NONE"
        d.standbyDcSource = if (standbyAutoNormal) "DC BUS 1" else if (d.staticInverterPowered) "BATTERY BUS" else "NONE"

        // Ground service: separate branch; GROUND POWER overrides the GROUND SERVICE switch.
        val groundServiceSource = e.groundPowerAvailable || e.generator1BusPowered || e.transferRelay1Alt || e.apuLeftSource
        d.groundServiceBusPowered = e.externalAcConnected || (s.groundServiceOn && !s.groundPowerOn && groundServiceSource)
        val no2MainBusChargerFeed = d.transferBus2Powered
        d.batteryChargerPowered = d.groundServiceBusPowered || no2MainBusChargerFeed

        // ATA 24: 28VAC single-phase branch derived from 115VAC through transformers.
        d.ac28vPowered = d.acPower

        d.electricalSource1 = if (e.generator1BusPowered) "GEN 1" else if (e.externalAc) "GPU" else if (e.apuLeftSource) "APU GEN" else if (e.transferRelay1Alt) "GEN 2 XFER" else "NONE"
        d.electricalSource2 = if (e.generator2BusPowered) "GEN 2" else if (e.externalAc) "GPU" else if (e.apuRightSource) "APU GEN" else if (e.transferRelay2Alt) "GEN 1 XFER" else "NONE"
    }
}
