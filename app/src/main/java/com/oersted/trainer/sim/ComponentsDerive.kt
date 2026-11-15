package com.oersted.trainer.sim

import java.util.Locale

/**
 * Port of the component status list of src/lib/sim/derive.ts (ATA 21-73): one entry per panel component with its
 * active flag, circuit-breaker reference and detail text. Detail text formats match the TS: Math.round for whole
 * numbers and toFixed(n) for decimals. Runs last among the derived sections because it reads most of them.
 */
object ComponentsDerive {
    private fun c(id: String, label: String, active: Boolean, cb: String, detail: String) =
        ComponentStatus().also { it.id = id; it.label = label; it.active = active; it.cb = cb; it.detail = detail }

    private fun r(x: Double): String = Math.round(x).toString()
    private fun tf(x: Double, n: Int): String = String.format(Locale.ROOT, "%.${n}f", x)

    fun apply(s: SimState, d: Derived, e: ElecLocals) {
        val hydA = d.hydA
        val hydB = d.hydB
        val hydS = d.hydS
        val dcPower = d.dcPower
        val indicationPower = e.indicationPower
        val startValveOpen1 = d.startValveOpen1
        val startValveOpen2 = d.startValveOpen2
        val fcA = d.flightControlAAvailable
        val fcB = d.flightControlBAvailable
        val gearDown = d.gearDown
        val gearUp = d.gearUp
        val gearLeverRetract = s.gear == "UP"

        // TS locals recomputed here (not stored on Derived).
        val crossfeedOpen = dcPower && s.fuelCrossfeed && !s.faults.fuelCrossfeedValveFail
        val fuelCenterSelected = s.fuelPumpCenter1 || s.fuelPumpCenter2
        val hydBBus1Electrical = (e.generator1BusPowered && e.g1Healthy) || e.tieSource1 || (e.transferRelay1Alt && e.g2Healthy)
        val hydBBus2Electrical = (e.generator2BusPowered && e.g2Healthy) || e.tieSource2 || (e.transferRelay2Alt && e.g1Healthy)
        val noseWheelSteeringAvailable = gearDown && s.wow && hydA > 1500.0
        // groundAPress is folded into hydA (hydA = max(hydA, groundAPress)), so hydA > 1500 covers it.

        val lo = s.wheelBrakePressures
        val brakeLi = if (s.hydAPress > 1500.0) "A" else "NONE"
        val brakeRi = if (s.hydAPress > 1500.0) "A" else "NONE"
        val brakeLo = if (s.hydBPress > 1500.0) "B" else "NONE"
        val brakeRo = if (s.hydBPress > 1500.0) "B" else "NONE"
        val autobrakeLabels = listOf("", "MIN", "MED", "MAX")

        val drainMastAir = d.drainMastAirPowered
        val drainMastGround = d.drainMastGroundPowered
        fun mastSource(): String = if (drainMastAir) "AIR / 115V AC" else if (drainMastGround) "GROUND / 28V AC" else "OFF / NO BUS"
        fun heaterDetail(temp: Double, fault: Boolean, powered: Boolean): String =
            "${r(temp)}°C · ${if (fault) "HEATER FAULT" else if (powered) "115V AC POWERED" else "OFF / NO BUS"}"

        val list = d.components
        list.clear()
        list.add(c("gen1-drive", "GENERATOR DRIVE / CSD 1", e.eng1AccessoryDriven && !e.gen1DriveDisconnected, "P5-5 / ATA 24-11",
            if (e.gen1DriveDisconnected) "DISCONNECTED BY SOLENOID"
            else if (s.faults.genDriveDisconnectFailure1 && s.gen1Disc) "DISCONNECT SOLENOID FAILED — DRIVE STILL CONNECTED"
            else "DRIVE CONNECTED"))
        list.add(c("gen2-drive", "GENERATOR DRIVE / CSD 2", e.eng2AccessoryDriven && !e.gen2DriveDisconnected, "P5-5 / ATA 24-11",
            if (e.gen2DriveDisconnected) "DISCONNECTED BY SOLENOID"
            else if (s.faults.genDriveDisconnectFailure2 && s.gen2Disc) "DISCONNECT SOLENOID FAILED — DRIVE STILL CONNECTED"
            else "DRIVE CONNECTED"))
        list.add(c("cabin-recirc-fan", "CABIN RECIRCULATION FAN", d.recircFanPowered, "ATA 21-24 / 21-26",
            if (d.recircFanPowered) "FLOW CONTRIBUTION ${r(d.recircAirflow * 100)}%"
            else if (s.faults.recircFanFailure) "FAILED" else "OFF / UNPOWERED"))
        list.add(c("engine-fuel-filter-icing", "ENGINE FUEL FILTER ICING DETECTION", d.fuelFilterIcing1 || d.fuelFilterIcing2,
            "ATA 73-14-0 / engine fuel-heat interface",
            "ENG 1 ΔP ${tf(d.fuelFilterDeltaP1Psi, 1)} PSI ${if (d.fuelFilterIcing1) "ICING LIGHT ON" else "LIGHT OFF"} · " +
                "ENG 2 ΔP ${tf(d.fuelFilterDeltaP2Psi, 1)} PSI ${if (d.fuelFilterIcing2) "ICING LIGHT ON" else "LIGHT OFF"}"))
        list.add(c("equipment-cooling", "EQUIPMENT COOLING BLOWERS",
            d.equipmentCoolingSupplyPowered || d.equipmentCoolingExhaustPowered, "ATA 21-58",
            if (d.equipmentCoolingOverheat) "E/E BAY OVERHEAT / BLOWER FAULT — ${r(s.equipmentBayTempC)}°C"
            else "SUPPLY ${s.equipSupply} / EXHAUST ${s.equipExhaust} / FLOW ${r(d.equipmentCoolingFlow * 100)}%"))
        list.add(c("crew-oxygen", "CREW OXYGEN PRESSURE INDICATION", d.oxygenPressureIndicated && s.crewOxyPsi > 0.0, "ATA 35-21",
            if (s.faults.oxygenSystemFailure) "OXYGEN SYSTEM FAILURE" else "${r(s.crewOxyPsi)} PSI"))
        list.add(c("toilet-drain-fwd", "FORWARD TOILET DRAIN HEATER", d.drainHeater1Powered, "P18 DRAIN HEATER / ATA 30-71",
            heaterDetail(s.drainHeater1TempC, s.faults.drainHeaterFailure1, d.drainHeater1Powered)))
        list.add(c("toilet-drain-aft", "AFT TOILET DRAIN HEATER", d.drainHeater2Powered, "P18 DRAIN HEATER / ATA 30-71",
            heaterDetail(s.drainHeater2TempC, s.faults.drainHeaterFailure2, d.drainHeater2Powered)))
        list.add(c("drain-mast-fwd", "FORWARD DRAIN MAST HEATER", d.drainMastForwardPowered, "P18 DRAIN MAST AIR/GROUND / ATA 30-71",
            "${r(s.drainMastForwardTempC)}°C · ${mastSource()}"))
        list.add(c("drain-mast-fwd-line", "FORWARD DRAIN MAST LINE HEATER", d.drainMastForwardLinePowered, "P18 DRAIN MAST AIR/GROUND / ATA 30-71",
            "${r(s.drainMastForwardLineTempC)}°C · ${mastSource()}"))
        list.add(c("drain-mast-aft", "AFT DRAIN MAST HEATER", d.drainMastAftPowered, "P18 DRAIN MAST AIR/GROUND / ATA 30-71",
            "${r(s.drainMastAftTempC)}°C · ${mastSource()}"))
        list.add(c("alpha-vane-left", "LEFT ALPHA VANE HEATER", d.alphaVaneHeatedL, "ATA 30-31",
            "${r(s.alphaVaneIceL * 100)}% TRAINER ICE · ${if (s.faults.alphaVaneHeatFailureL) "HEATER FAILED" else if (d.alphaVaneHeatedL) "HEATED" else "OFF / UNPOWERED"}"))
        list.add(c("alpha-vane-right", "RIGHT ALPHA VANE HEATER", d.alphaVaneHeatedR, "ATA 30-31",
            "${r(s.alphaVaneIceR * 100)}% TRAINER ICE · ${if (s.faults.alphaVaneHeatFailureR) "HEATER FAILED" else if (d.alphaVaneHeatedR) "HEATED" else "OFF / UNPOWERED"}"))
        list.add(c("passenger-oxygen", "PASSENGER OXYGEN CONTROL", d.passengerOxygenDeployed, "ATA 35-21",
            if (d.passengerOxygenDeployed) "PASSENGER OXYGEN DEPLOYED / ON" else "NORMAL / STOWED"))
        list.add(c("door-warning", "DOOR WARNING", d.doorWarning, "ATA 52-71",
            if (d.doorWarning) "ONE OR MORE DOORS OPEN" else if (s.faults.doorWarningFailure) "WARNING CIRCUIT FAILED" else "ALL MONITORED DOORS CLOSED"))
        list.add(c("cvr", "COCKPIT VOICE RECORDER", d.cvrPowered && s.flightRecorderMode == "NORMAL", "ATA 23-71",
            if (s.faults.cvrFailure) "CVR FAILED"
            else if (d.cvrPowered) (if (s.cvrMemoryErased) "POWERED — MEMORY ERASED" else "POWERED — RECORDING STATE")
            else "OFF / UNPOWERED"))
        list.add(c("service-interphone", "SERVICE INTERPHONE / CALL", d.serviceInterphoneEffective || d.serviceCallEffective, "ATA 23-41/42",
            if (d.serviceInterphoneEffective) "INTERPHONE ON / DC POWERED"
            else if (d.serviceCallEffective) "CALL ACTIVE" else "OFF / UNPOWERED / CB OPEN"))
        list.add(c("eng1-start-cb", "ENGINE START VALVE 1 CB", dcPower, "P6-2", "28V DC control circuit"))
        list.add(c("eng1-starter-valve", "ENGINE 1 STARTER VALVE", startValveOpen1, "ENGINE START VALVES",
            if (startValveOpen1) "OPEN — pneumatic starter supplied" else "CLOSED"))
        list.add(c("eng1-starter", "ENGINE 1 PNEUMATIC STARTER", startValveOpen1 && s.n2_1 < 40.0, "ENGINE START VALVES",
            if (startValveOpen1) "MOToring N2" else "OFF"))
        list.add(c("eng1-ignition", "ENGINE 1 IGNITION EXCITER", d.ignition1On, "START IGNITION / LOW ENERGY IGNITION",
            if (d.ignition1On) "${s.start1} · ENERGIZED" else "${s.start1} · OFF"))
        list.add(c("eng2-start-cb", "ENGINE START VALVE 2 CB", dcPower, "P6-2", "28V DC control circuit"))
        list.add(c("eng2-starter-valve", "ENGINE 2 STARTER VALVE", startValveOpen2, "ENGINE START VALVES",
            if (startValveOpen2) "OPEN — pneumatic starter supplied" else "CLOSED"))
        list.add(c("eng2-starter", "ENGINE 2 PNEUMATIC STARTER", startValveOpen2 && s.n2_2 < 40.0, "ENGINE START VALVES",
            if (startValveOpen2) "MOToring N2" else "OFF"))
        list.add(c("eng2-ignition", "ENGINE 2 IGNITION EXCITER", d.ignition2On, "START IGNITION / LOW ENERGY IGNITION",
            if (d.ignition2On) "${s.start2} · ENERGIZED" else "${s.start2} · OFF"))
        list.add(c("fuel-crossfeed-valve", "FUEL CROSSFEED VALVE", crossfeedOpen, "FUEL CROSSFEED",
            if (s.faults.fuelCrossfeedValveFail) "FAILED — VALVE UNAVAILABLE"
            else if (crossfeedOpen) "OPEN — ${d.fuelFeedSource1} / ${d.fuelFeedSource2}" else "CLOSED"))
        list.add(c("fuel-boost-pump-set", "FUEL BOOST PUMP SET", d.fuelPump1On || d.fuelPump2On || d.fuelCenterPumpOn, "FUEL BOOST PUMPS",
            "T1 ${if (d.fuelPump1On) "PRESS" else "LOW PRESSURE"} · " +
                "CTR ${if (d.fuelCenterPumpOn) "PRESS" else if (fuelCenterSelected) "LOW PRESSURE" else "OFF"} · " +
                "T2 ${if (d.fuelPump2On) "PRESS" else "LOW PRESSURE"}"))
        list.add(c("fire-module-p8", "FIRE PROTECTION MODULE P8-1", d.fireWarn, "FIRE PROTECTION",
            if (d.fireWarn) "WARNING INPUT ACTIVE" else "MONITORING"))
        list.add(c("wheelwell-fire-det", "WHEEL-WELL OVERHEAT DETECTION", d.wheelWellFireWarn, "WHEEL WELL FIRE",
            if (d.wheelWellFireWarn) "OVERHEAT/FIRE WARNING" else "NORMAL"))
        list.add(c("cargo-fwd-fire-det", "FWD CARGO SMOKE DETECTION", d.cargoFwdFireWarn, "CARGO FIRE FWD DET",
            if (d.cargoFwdFireWarn) "SMOKE WARNING" else "NORMAL"))
        list.add(c("cargo-aft-fire-det", "AFT CARGO SMOKE DETECTION", d.cargoAftFireWarn, "CARGO FIRE AFT DET",
            if (d.cargoAftFireWarn) "SMOKE WARNING" else "NORMAL"))
        val bottlesUsed = s.fireBottleLDischarged || s.fireBottleRDischarged
        list.add(c("engine-fire-bottles", "ENGINE FIRE EXTINGUISHER BOTTLES", bottlesUsed, "FIRE EXT",
            if (bottlesUsed) "DISCHARGED" else "ARMED / AVAILABLE"))
        val qty = d.fuelQuantityIndicationPowered
        list.add(c("fuel-tank-1", "NO. 1 FUEL TANK", qty && s.fuelTank1 > 0.5, "FUEL QTY",
            if (qty) "${tf(s.fuelTank1, 1)}% · ${if (d.fuelLowPressure1) "LOW PRESSURE" else "FEED AVAILABLE"}" else "NO INDICATION — NO DC POWER"))
        list.add(c("fuel-center-tank", "CENTER FUEL TANK", qty && s.fuelTankCenter > 0.5, "FUEL QTY",
            if (qty) "${tf(s.fuelTankCenter, 1)}% · ${if (d.fuelCenterLowPressure) "LOW PRESSURE" else "FEED AVAILABLE"}" +
                (if (d.fuelScavengeActive) " · SCAVENGE ACTIVE" else "")
            else "NO INDICATION — NO DC POWER"))
        list.add(c("fuel-tank-2", "NO. 2 FUEL TANK", qty && s.fuelTank2 > 0.5, "FUEL QTY",
            if (qty) "${tf(s.fuelTank2, 1)}% · ${if (d.fuelLowPressure2) "LOW PRESSURE" else "FEED AVAILABLE"}" else "NO INDICATION — NO DC POWER"))
        list.add(c("engine-1-fuel-feed", "ENGINE 1 FUEL FEED", d.fuelFeedPressure1 > 0.5, "ENG 1 FUEL SHUTOFF",
            "${tf(d.fuelFeedPressure1, 0)} PSI · ${d.fuelFeedSource1} · VALVE ${if (d.fuelShutoff1Open) "OPEN" else "CLOSED"}"))
        list.add(c("engine-2-fuel-feed", "ENGINE 2 FUEL FEED", d.fuelFeedPressure2 > 0.5, "ENG 2 FUEL SHUTOFF",
            "${tf(d.fuelFeedPressure2, 0)} PSI · ${d.fuelFeedSource2} · VALVE ${if (d.fuelShutoff2Open) "OPEN" else "CLOSED"}"))
        list.add(c("engine-1-oil", "ENGINE 1 OIL SYSTEM", s.oilPsi1 > 30.0, "OIL TEMP & PRESS",
            "${tf(s.oilPsi1, 0)} PSI · USABLE QTY ${tf(s.oilQty1, 1)}%" +
                (if (s.faults.oilLow1 || s.oilPsi1 < 30.0) " · LOW PRESSURE" else "") +
                (if (s.faults.oilChip1) " · CHIP DETECTED" else "")))
        list.add(c("engine-2-oil", "ENGINE 2 OIL SYSTEM", s.oilPsi2 > 30.0, "OIL TEMP & PRESS",
            "${tf(s.oilPsi2, 0)} PSI · USABLE QTY ${tf(s.oilQty2, 1)}%" +
                (if (s.faults.oilLow2 || s.oilPsi2 < 30.0) " · LOW PRESSURE" else "") +
                (if (s.faults.oilChip2) " · CHIP DETECTED" else "")))
        list.add(c("apu-fuel-valve", "APU FUEL SHUTOFF VALVE", s.apuFuelValve > 0.95, "APU CONT", "${r(s.apuFuelValve * 100)}% OPEN"))
        list.add(c("apu-inlet-door", "APU INLET DOOR", s.apuDoor > 0.95, "APU CONT", "${r(s.apuDoor * 100)}% OPEN"))
        list.add(c("apu-starter", "APU STARTER", s.apuStarter, "APU CONT", if (s.apuStarter) "ENGAGED" else "OFF"))
        list.add(c("apu-bleed-valve", "APU BLEED AIR VALVE", s.apuBleedValve > 0.7, "APU CONT", if (s.apuBleedValve > 0.7) "OPEN" else "CLOSED"))
        list.add(c("stby-pump", "STANDBY HYDRAULIC PUMP", hydS > 500.0, "STBY HYD CONTROL",
            if (hydS > 500.0) "PRESSURIZED — STANDBY AC POWER" else "OFF / NO STANDBY AC OR NO DEMAND"))
        list.add(c("stby-rudder", "STANDBY RUDDER POWER", d.standbyRudderPowered, "STBY HYD CONTROL",
            if (d.standbyRudderPowered) "AVAILABLE" else "NO STANDBY PRESSURE"))
        list.add(c("alt-flap-te", "TE ALTERNATE ELECTRIC DRIVE", d.alternateFlapDrivePowered, "ALT FLAPS",
            if (d.alternateFlapDrivePowered) "115V AC ELECTRIC MOTOR DRIVING FPU" else "OFF / NOT COMMANDED"))
        list.add(c("te-bypass", "TE FLAP BYPASS VALVE", d.alternateFlapTakeover, "ALT FLAPS",
            if (d.alternateFlapTakeover) "BYPASS — HYDRAULIC MOTOR ISOLATED FOR ELECTRIC FPU DRIVE" else "NORMAL HYDRAULIC PATH"))
        list.add(c("te-hyd-motor", "TE FLAP HYDRAULIC MOTOR", d.teFlapHydraulicDriveAvailable, "TE FLAP DRIVE",
            if (s.faults.teFlapHydMotorFail) "FAILED — ALTERNATE DRIVE REQUIRED"
            else if (d.teFlapHydraulicDriveAvailable) "SYSTEM A HYDRAULIC DRIVE AVAILABLE" else "BYPASSED"))
        list.add(c("flt-ctrl-a", "FLIGHT CONTROL A SHUTOFF", fcA, "FLT CTRL A", if (fcA) "SYSTEM A USERS SUPPLIED" else "OFF / LOW PRESSURE"))
        list.add(c("flt-ctrl-b", "FLIGHT CONTROL B SHUTOFF", fcB, "FLT CTRL B", if (fcB) "SYSTEM B USERS SUPPLIED" else "OFF / LOW PRESSURE"))
        list.add(c("spoiler-a", "SPOILER A SHUTOFF", fcA && s.spoilerA, "SPOILER A",
            if (fcA && s.spoilerA) "INBOARD / GROUND SPOILER PATH AVAILABLE" else "OFF"))
        list.add(c("spoiler-b", "SPOILER B SHUTOFF", fcB && s.spoilerB, "SPOILER B",
            if (fcB && s.spoilerB) "OUTBOARD SPOILER PATH AVAILABLE" else "OFF"))
        list.add(c("hyd-a", "HYDRAULIC SYSTEM A", hydA > 1500.0, "HYD CONTROL", "${r(hydA)} PSI"))
        list.add(c("hyd-b", "HYDRAULIC SYSTEM B", hydB > 1500.0, "HYD CONTROL", "${r(hydB)} PSI"))
        val edp1 = e.eng1AccessoryDriven && s.engHyd1 && !s.engine1FirePulled
        list.add(c("hyd-a-edp-1", "SYSTEM A ENGINE 1 PUMP", edp1, "ENG 1 HYD PUMP",
            if (edp1) "ACCESSORY ROTATING / PRESSURE SOURCE AVAILABLE" else "OFF / ACCESSORY NOT ROTATING"))
        val edp2 = e.eng2AccessoryDriven && s.engHyd2 && !s.engine2FirePulled
        list.add(c("hyd-a-edp-2", "SYSTEM A ENGINE 2 PUMP", edp2, "ENG 2 HYD PUMP",
            if (edp2) "ACCESSORY ROTATING / PRESSURE SOURCE AVAILABLE" else "OFF / ACCESSORY NOT ROTATING"))
        list.add(c("hyd-b-elec-1", "SYSTEM B ELECTRIC PUMP 1", d.hydBPump1On, "B ELEC HYD PUMP 1",
            if (d.hydBPump1On) "3000 PSI SOURCE — ${d.electricalSource1}"
            else if (!s.elecHyd1) "SWITCH OFF"
            else if (!hydBBus1Electrical) "NO HEALTHY AC ON TRANSFER BUS 1" else "NO RESERVOIR HEAD / QTY"))
        list.add(c("hyd-b-elec-2", "SYSTEM B ELECTRIC PUMP 2", d.hydBPump2On, "B ELEC HYD PUMP 2",
            if (d.hydBPump2On) "3000 PSI SOURCE — ${d.electricalSource2}"
            else if (!s.elecHyd2) "SWITCH OFF"
            else if (!hydBBus2Electrical) "NO HEALTHY AC ON TRANSFER BUS 2" else "NO RESERVOIR HEAD / QTY"))
        list.add(c("hyd-a-module", "SYSTEM A MODULE / DISTRIBUTION", hydA > 1500.0, "HYD A PRESSURE", "${r(hydA)} PSI — routes to A users"))
        list.add(c("hyd-b-module", "SYSTEM B MODULE / DISTRIBUTION", hydB > 1500.0, "HYD B PRESSURE", "${r(hydB)} PSI — routes to B users"))
        list.add(c("stby-module", "STANDBY MODULE / DISTRIBUTION", hydS > 1800.0, "STBY HYD CONTROL", "${r(hydS)} PSI — rudder / LE devices"))
        list.add(c("gear-selector-valve", "LANDING GEAR SELECTOR VALVE", hydA > 1500.0 && s.gear != "OFF", "LANDING GEAR LEVER",
            if (hydA > 1500.0) "SYSTEM A PRESSURE — ${s.gear}" else "NO SYSTEM A PRESSURE"))
        list.add(c("main-gear-isolation", "MAIN GEAR ISOLATION VALVE", gearDown || (!gearUp && hydA > 1500.0), "LANDING GEAR",
            "NORMAL OPEN — MAINTENANCE ISOLATION PATH"))
        list.add(c("gear-actuators", "GEAR ACTUATORS / LOCKS", hydA > 1500.0, "LANDING GEAR",
            if (gearDown) "DOWN / LOCKED" else if (gearUp) "UP / LOCKED" else "IN TRANSIT"))
        list.add(c("nose-steering-valve", "NOSE-WHEEL STEERING DEPRESSURIZATION VALVE", noseWheelSteeringAvailable, "LANDING GEAR",
            if (noseWheelSteeringAvailable) "PRESSURIZED — GND / NOSE GEAR DOWN" else "DEPRESSURIZED"))
        list.add(c("brake-source-a", "INBOARD BRAKE HYDRAULIC PATH", brakeLi == "A" || brakeRi == "A", "BRAKE SYSTEM",
            "SYSTEM A — LI ${r(lo.li)} / RI ${r(lo.ri)} PSI"))
        list.add(c("brake-source-b", "OUTBOARD BRAKE HYDRAULIC PATH", brakeLo == "B" || brakeRo == "B", "BRAKE SYSTEM",
            "SYSTEM B — LO ${r(lo.lo)} / RO ${r(lo.ro)} PSI"))
        list.add(c("antiskid-inbd", "INBD ANTISKID CHANNEL", indicationPower && d.antiskidInbdOperational, "INBOARD ANTISKID",
            if (d.antiskidInbdOperational) "CHANNEL ON" else if (gearLeverRetract) "INHIBITED — GEAR LEVER UP" else "SWITCH OFF / FAULT"))
        list.add(c("antiskid-outbd", "OUTBD ANTISKID CHANNEL", indicationPower && d.antiskidOutbdOperational, "OUTBOARD ANTISKID",
            if (d.antiskidOutbdOperational) "CHANNEL ON" else if (gearLeverRetract) "INHIBITED — GEAR LEVER UP" else "SWITCH OFF / FAULT"))
        list.add(c("autobrake-module", "AUTOBRAKE PRESSURE CONTROL", d.autobrakeActive, "AUTOBRAKE CONTR",
            if (d.autobrakeActive)
                "${if (s.autobrake == 4.0) "RTO" else autobrakeLabels[s.autobrake.toInt()]} — ${d.autobrakePressure.toInt()} PSI COMMAND"
            else "OFF / ARMED / DISARMED"))
        list.add(c("antiskid-valves", "ANTISKID VALVES → BRAKES", d.antiskidModulating, "ANTISKID CONTR",
            if (d.antiskidModulating) "AUTOBRAKE PRESSURE ROUTED THROUGH ANTISKID" else "NO AUTOMATIC BRAKE COMMAND"))
        // Inventory: every PARTS entry not already modeled above is listed with its system-level health (TS systemHealth).
        val modeledIds = list.map { it.id }.toHashSet()
        val systemHealth = mapOf(
            "powerplant" to (d.acPower || dcPower),
            "hydraulics" to (hydA > 1500.0 || hydB > 1500.0 || hydS > 1800.0),
            "aircond" to (d.packLFlow || d.packRFlow),
            "pneumatic" to (d.leftManifold || d.rightManifold),
            "cabin" to (s.cabinDiff > 0.05 || d.cabinSupplyFlow > 0.01),
            "flightcontrols" to (fcA || fcB || d.standbyRudderPowered),
            "landinggear" to (gearDown || gearUp || d.gearTransit),
            "fuel" to (d.fuelFeedPressure1 > 0.5 || d.fuelFeedPressure2 > 0.5 || s.fuelTank1 > 0.5 || s.fuelTank2 > 0.5 || s.fuelTankCenter > 0.5),
            "fire" to d.fireWarn,
            "apu" to (d.apuAvailable || s.apuMaster != "OFF"),
            "antiice" to (s.antiIce1 || s.antiIce2),
        )
        for (part in PartsTable.rows) {
            if (part.id in modeledIds) continue
            val controls = part.controls
            list.add(c(part.id, part.name, systemHealth[part.system] == true,
                if (controls.isNullOrEmpty()) "ATA ${part.ata}" else controls,
                "ATA ${part.ata} · ${part.group} · system-level state represented; control: ${controls ?: "undefined"}"))
        }
    }
}
