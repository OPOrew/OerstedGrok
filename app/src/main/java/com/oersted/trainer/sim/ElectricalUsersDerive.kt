package com.oersted.trainer.sim

/**
 * Port of the electrical users list of src/lib/sim/derive.ts (ATA 24/26/29/30/21/52): for each electrical user,
 * the supply source, whether it is powered, its bus, and the detail text shown to the trainee.
 * Hydraulic-B electric pumps follow the TS bus rules (generator/tie/transfer relay with healthy three-phase).
 * Runs after AuxPowerDerive (standby, TR, battery charger), ElectricalDerive (ElecLocals) and FlightControlDerive.
 */
object ElectricalUsersDerive {
    private fun user(id: String, label: String, source: String, powered: Boolean, bus: String, detail: String) =
        ElectricalUserStatus().also {
            it.id = id; it.label = label; it.source = source; it.powered = powered; it.bus = bus; it.detail = detail
        }

    fun apply(s: SimState, d: Derived, e: ElecLocals) {
        // Recomputed inner locals (the TS keeps these local to derive()).
        val primaryHydraulicLoss = d.hydA < 1500.0 && d.hydB < 1500.0
        val stbyNeed = s.altFlapsArm || s.stbyHyd == "ON" || (s.stbyHyd == "AUTO" && primaryHydraulicLoss)
        val hydBBus1Electrical = (e.generator1BusPowered && e.g1Healthy) || e.tieSource1 || (e.transferRelay1Alt && e.g2Healthy)
        val hydBBus2Electrical = (e.generator2BusPowered && e.g2Healthy) || e.tieSource2 || (e.transferRelay2Alt && e.g1Healthy)
        val tr3InputAvailable = (e.generator2BusPowered && e.g2Healthy) || e.tieSource2
        val electricalSource1 = d.electricalSource1
        val electricalSource2 = d.electricalSource2
        val stbyHydElectricalSource = if (d.standbyAcPowered) {
            if (d.standbyAcSource == "STATIC INVERTER") "STATIC INVERTER"
            else if (electricalSource1 != "NONE") electricalSource1 else electricalSource2
        } else "NONE"

        val list = d.electricalUsers
        list.clear()
        list.add(user("hyd-elec-pump-1", "System B electric hydraulic pump 1", electricalSource1, d.hydBPump1On, "TRANSFER BUS 1",
            if (d.hydBPump1On) "Powered from $electricalSource1"
            else if (!s.elecHyd1) "SWITCH OFF"
            else if (!hydBBus1Electrical) {
                if (e.generator1BusPowered && !e.g1Healthy) "OPEN PHASE / UNHEALTHY 3φ on GEN 1"
                else if (e.transferRelay1Alt && s.elecHyd2) "XFER blocked — ELEC 2 still ON (load mgmt)"
                else "TRANSFER BUS 1 unavailable"
            } else "No reservoir head or quantity"))
        list.add(user("hyd-elec-pump-2", "System B electric hydraulic pump 2", electricalSource2, d.hydBPump2On, "TRANSFER BUS 2",
            if (d.hydBPump2On) "Powered from $electricalSource2"
            else if (!s.elecHyd2) "SWITCH OFF"
            else if (!hydBBus2Electrical) {
                if (e.generator2BusPowered && !e.g2Healthy) "OPEN PHASE / UNHEALTHY 3φ on GEN 2"
                else if (e.transferRelay2Alt && s.elecHyd1) "XFER blocked — ELEC 1 still ON (load mgmt)"
                else "TRANSFER BUS 2 unavailable"
            } else "No reservoir head or quantity"))
        val stbyPowered = d.standbyAcPowered && stbyNeed && s.hydSQty > 0.0
        list.add(user("stby-hyd-pump", "Standby hydraulic AC motor pump", stbyHydElectricalSource, stbyPowered, "STANDBY AC",
            if (stbyPowered) "AC motor energized — $stbyHydElectricalSource"
            else if (!stbyNeed) "Pump not demanded"
            else if (!d.standbyAcPowered) "No standby AC (check STANDBY POWER / static inverter)"
            else if (s.hydSQty <= 0.0) "No standby reservoir quantity"
            else "Unavailable"))
        list.add(user("te-flap-fpu", "TE flap alternate FPU electric motor",
            if (electricalSource2 != "NONE") electricalSource2 else electricalSource1, d.alternateFlapDrivePowered, "AC",
            if (d.alternateFlapDrivePowered) "Alternate electric drive powered" else "Alternate drive OFF"))
        list.add(user("apu-starter-battery", "APU starter",
            if (e.batteryPower) "BATTERY" else if (e.externalDcControl) "EXT DC" else "NONE",
            s.apuStarter && (e.batteryPower || e.externalDcControl), "28V DC CONTROL",
            if (s.apuStarter && (e.batteryPower || e.externalDcControl)) "ELECTRIC STARTER ENGAGED" else "Not engaged"))
        list.add(user("tr1", "TR 1",
            if (e.generator1BusPowered && e.g1Healthy) "TRANSFER BUS 1 / GEN 1"
            else if (e.tieSource1) (if (e.externalAc) "TRANSFER BUS 1 / GPU" else "TRANSFER BUS 1 / APU GEN")
            else if (e.transferRelay1Alt) "TRANSFER BUS 1 / GEN 2 XFER"
            else if (e.generator1BusPowered) "GEN 1 OPEN PHASE"
            else "NONE",
            d.tr1Powered, "DC BUS 1",
            if (d.tr1Powered) "TRANSFER BUS 1 AC input — feeds DC Bus 1"
            else if (s.faults.tr1Failure) "TR 1 FAILED"
            else "NO HEALTHY AC INPUT ON TRANSFER BUS 1"))
        list.add(user("tr2", "TR 2",
            if (e.generator2BusPowered && e.g2Healthy) "TRANSFER BUS 2 / GEN 2"
            else if (e.tieSource2) (if (e.externalAc) "TRANSFER BUS 2 / GPU" else "TRANSFER BUS 2 / APU GEN")
            else if (e.transferRelay2Alt) "TRANSFER BUS 2 / GEN 1 XFER"
            else if (e.generator2BusPowered) "GEN 2 OPEN PHASE"
            else "NONE",
            d.tr2Powered, "DC BUS 2",
            if (d.tr2Powered) "TRANSFER BUS 2 AC input — feeds DC Bus 2"
            else if (s.faults.tr2Failure) "TR 2 FAILED"
            else "NO HEALTHY AC INPUT ON TRANSFER BUS 2"))
        list.add(user("tr3", "TR 3",
            if (d.tr3Powered) (if (e.generator2BusPowered) "GENERATOR BUS 2" else if (e.externalAc) "EXTERNAL AC → BUS 2" else "APU GEN → BUS 2")
            else if (e.generator2BusPowered && !e.g2Healthy) "GEN 2 BUS (OPEN PHASE)"
            else "NONE",
            d.tr3Powered, "DC TIE / BACKUP",
            if (d.tr3Powered) "FED FROM GEN BUS 2 / VALID BUS 2 SOURCE — backup for DC Bus 1 and 2"
            else if (s.faults.tr3Failure) "TR 3 FAILED"
            else if (!tr3InputAvailable) "NO VALID INPUT FROM GENERATOR BUS 2 / BUS 2 SOURCE"
            else "NO TR 3 OUTPUT"))
        list.add(user("ac-28v-main-bus-2", "28V AC MAIN BUS NO. 2",
            if (d.ac28vMainBus2Powered) "115V AC MAIN BUS NO. 2 / TRANSFORMER" else "NONE",
            d.ac28vMainBus2Powered, "28V AC MAIN BUS 2",
            if (d.ac28vMainBus2Powered) "MAIN BUS 2 FEED AVAILABLE"
            else if (!d.acMainBus2Powered) "115V AC MAIN BUS 2 SOURCE LOST"
            else "28V AC BUS 2 FEED CB OPEN"))
        list.add(user("battery-charger", "Battery charger",
            if (d.batteryChargerPowered) (if (d.groundServiceBusPowered) "GROUND SERVICE BUS" else "NO. 2 MAIN / TRANSFER BUS") else "NONE",
            d.batteryChargerPowered, "28V DC CHARGE",
            if (d.batteryChargerPowered) "Charger input available" else "No ground-service or No. 2 bus feed"))
        list.add(user("static-inverter", "Static inverter",
            if (d.staticInverterPowered) "BATTERY" else "NONE",
            d.staticInverterPowered, "STANDBY AC",
            if (d.staticInverterPowered) "Inverting battery DC to standby AC"
            else if (s.faults.staticInverterFailure) "INVERTER FAILED"
            else "Not selected / battery unavailable"))
        list.add(user("recirc-fan", "Cabin recirculation / gasper fan",
            if (d.recircFanPowered) "AC BUS" else "NONE", d.recircFanPowered, "AC",
            if (d.recircFanPowered) "RECIRC FAN RUNNING — TRAINER FLOW CONTRIBUTION ACTIVE"
            else if (s.faults.recircFanFailure) "FAN FAILED"
            else if (!s.recirc) "SWITCH OFF"
            else "AC POWER UNAVAILABLE"))
        list.add(user("equip-cooling-supply", "Equipment cooling supply blower",
            if (d.equipmentCoolingSupplyPowered) "AC BUS" else "NONE", d.equipmentCoolingSupplyPowered, "AC",
            if (d.equipmentCoolingSupplyPowered) "SUPPLY ${s.equipSupply}"
            else if (s.faults.equipmentCoolingFailure) "BLOWER FAILED"
            else "SUPPLY ${s.equipSupply} / NO VALID POWER"))
        list.add(user("equip-cooling-exhaust", "Equipment cooling exhaust blower",
            if (d.equipmentCoolingExhaustPowered) "AC BUS" else "NONE", d.equipmentCoolingExhaustPowered, "AC",
            if (d.equipmentCoolingExhaustPowered) "EXHAUST ${s.equipExhaust}"
            else if (s.faults.equipmentCoolingFailure) "BLOWER FAILED"
            else "EXHAUST ${s.equipExhaust} / NO VALID POWER"))
        list.add(user("antiskid-controller", "Antiskid controller",
            if (d.dcPower) "28V DC" else "NONE", d.dcPower && d.antiskidOperational, "28V DC",
            if (d.antiskidOperational) "INBD + OUTBD CHANNELS AVAILABLE"
            else if (s.gear == "UP") "DISABLED — GEAR LEVER UP (RETRACTION)"
            else "ANTISKID FAULT / SWITCH OFF"))
        list.add(user("autobrake-controller", "Autobrake controller",
            if (d.dcPower) "28V DC" else "NONE",
            d.dcPower && s.autobrake > 0.0 && !s.faults.antiskidInop && !s.faults.antiskidInbdInop && !s.faults.antiskidOutbdInop,
            "28V DC",
            if (s.autobrake > 0.0) "SELECTED — CHANNEL AVAILABILITY SUBJECT TO -200 EFFECTIVITY" else "AUTOBRAKE OFF"))
    }
}
