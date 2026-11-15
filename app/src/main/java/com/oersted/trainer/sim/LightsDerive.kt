package com.oersted.trainer.sim

import kotlin.math.abs
import kotlin.math.floor

/**
 * Port of src/lib/sim/lights.ts (737-200 lighting trainer model, ATA 33 style): the light inventory
 * (DEFS, order preserved), commanded levels from the P5 / attendant / emergency switches, bus and
 * circuit-breaker gating, bulb failures, and the lighting component list.
 *
 * Runs last in SimDerive.computeAll: it needs the bus flags, gearUp and dcPower already stored on [d].
 * Parity: LightsParityTest compares against tools/parity/lights.json.
 * Bus/CB values remain a trainer model unless the Boeing Chapter 33 excerpt establishes them.
 */
object LightsDerive {
    private data class Def(
        val id: String,
        val label: String,
        val group: String,
        val color: String,
        val supply: String,
        val cb: String,
        val flash: String? = null,
    )

    private val DEFS: List<Def> = listOf(
        // Exterior
        Def("ldg-ret-l", "Landing light · retractable L", "Exterior", "white", "AC1", "LDG LT RETRACT L"),
        Def("ldg-ret-r", "Landing light · retractable R", "Exterior", "white", "AC2", "LDG LT RETRACT R"),
        Def("ldg-fix-l", "Landing light · fixed L", "Exterior", "white", "AC1", "LDG LT FIXED L"),
        Def("ldg-fix-r", "Landing light · fixed R", "Exterior", "white", "AC2", "LDG LT FIXED R"),
        Def("rwy-l", "Runway turnoff light L", "Exterior", "white", "DC1", "RWY TURNOFF L"),
        Def("rwy-r", "Runway turnoff light R", "Exterior", "white", "DC2", "RWY TURNOFF R"),
        Def("taxi", "Taxi light (nose gear)", "Exterior", "white", "DC1", "TAXI LT"),
        Def("wing", "Wing illumination lights", "Exterior", "white", "AC1", "WING ILLUM"),
        Def("logo", "Logo lights (stabilizers)", "Exterior", "white", "AC2", "LOGO LT"),
        Def("wheelwell", "Wheel-well lights", "Exterior", "white", "HOT", "WHEEL WELL LT"),
        Def("pos-l", "Position light · left wingtip (red)", "Exterior", "red", "DC1", "POSITION LTS"),
        Def("pos-r", "Position light · right wingtip (green)", "Exterior", "green", "DC2", "POSITION LTS"),
        Def("pos-t", "Position light · tail (white)", "Exterior", "white", "DC1", "POSITION LTS"),
        Def("strobe-l", "Strobe · left wingtip", "Exterior", "white", "DC1", "STROBE LTS", "strobe"),
        Def("strobe-r", "Strobe · right wingtip", "Exterior", "white", "DC2", "STROBE LTS", "strobe"),
        Def("strobe-t", "Strobe · tail", "Exterior", "white", "DC1", "STROBE LTS", "strobe"),
        Def("beacon-top", "Anti-collision beacon · top", "Exterior", "red", "DC1", "ANTI COLLISION", "beacon"),
        Def("beacon-bot", "Anti-collision beacon · bottom", "Exterior", "red", "DC2", "ANTI COLLISION", "beacon"),
        // Flight deck
        Def("dome", "Dome light", "Flight deck", "white", "BAT", "DOME LT"),
        Def("pnl-ovhd", "Overhead panel (P5) lighting", "Flight deck", "amber", "28VAC", "PANEL LTS"),
        Def("pnl-capt", "Captain instrument / panel lighting", "Flight deck", "amber", "28VAC", "PANEL LTS"),
        Def("pnl-fo", "First Officer instrument / panel lighting", "Flight deck", "amber", "28VAC", "PANEL LTS"),
        Def("pnl-ped", "Pedestal lighting", "Flight deck", "amber", "28VAC", "PANEL LTS"),
        Def("map-capt", "Map light · Captain", "Flight deck", "white", "DC1", "MAP LTS"),
        Def("map-fo", "Map light · First Officer", "Flight deck", "white", "DC2", "MAP LTS"),
        Def("storm", "Storm lights (flood, full bright)", "Flight deck", "white", "DC1", "STORM LT"),
        Def("compass", "Compass light", "Flight deck", "white", "BAT", "COMPASS LT"),
        Def("observer", "Observer reading light", "Flight deck", "white", "DC2", "OBSERVER LT"),
        Def("shield-emer", "Lightshield emergency lamps", "Emergency", "white", "BAT", "PILOT BACKGROUND LT"),
        // Cabin
        Def("cab-main", "Passenger cabin lights", "Cabin", "white", "AC1", "CABIN LTS"),
        Def("cab-entry", "Entry lights", "Cabin", "white", "AC2", "ENTRY / GALLEY LTS"),
        Def("cab-galley", "Galley lights", "Cabin", "white", "AC2", "ENTRY / GALLEY LTS"),
        Def("cab-lav", "Lavatory lights", "Cabin", "white", "AC1", "LAV LTS"),
        Def("cab-read", "Passenger reading lights", "Cabin", "white", "28VAC", "PASS READING LTS"),
        Def("cab-sidewall", "Cabin sidewall / cove lights", "Cabin", "white", "AC1", "CABIN SIDEWALL LTS"),
        Def("cab-window", "Cabin window lights", "Cabin", "white", "AC1", "WINDOW LTS"),
        Def("att-work", "Attendant work lights", "Cabin", "white", "AC2", "ATT WORK LTS"),
        Def("pass-call", "Passenger call lights", "Cabin", "amber", "28VAC", "PASS CALL LTS"),
        Def("lav-call", "Lavatory call lights", "Cabin", "amber", "28VAC", "LAV CALL LTS"),
        Def("lav-mirror", "Lavatory mirror lights", "Cabin", "white", "AC1", "LAV MIRROR LTS"),
        Def("threshold", "Door threshold lights", "Cabin", "white", "AC2", "THRESHOLD LTS"),
        Def("closet", "Coat closet lights", "Cabin", "white", "AC2", "COAT COMPT"),
        Def("airstair", "Forward airstair step-riser lights (if installed)", "Cabin", "white", "AC2", "AIRSTAIR STEP LTS"),
        Def("sign-ns", "NO SMOKING sign", "Cabin", "amber", "DC1", "PASS SIGNS"),
        Def("sign-sb", "FASTEN SEAT BELTS sign", "Cabin", "amber", "DC1", "PASS SIGNS"),
        // Emergency
        Def("exit-sign", "Exit signs", "Emergency", "red", "BAT", "EXIT LTS"),
        Def("emer-aisle", "Aisle / floor-path emergency lights", "Emergency", "white", "EMER", "EMER LT PACK"),
        Def("emer-ovhd", "Cabin overhead emergency lights", "Emergency", "white", "EMER", "EMER LT PACK"),
        Def("emer-exit", "Exit-area exterior lights", "Emergency", "white", "EMER", "EMER LT PACK"),
        Def("exit-locator", "Emergency exit locator signs", "Emergency", "green", "EMER", "EXIT LOCATOR LTS"),
        Def("hatch-handle", "Overwing hatch handle illumination", "Emergency", "white", "EMER", "HATCH HANDLE LTS"),
        Def("overwing-path-l", "Overwing emergency path lights L", "Emergency", "white", "EMER", "OVERWING PATH LTS"),
        Def("overwing-path-r", "Overwing emergency path lights R", "Emergency", "white", "EMER", "OVERWING PATH LTS"),
        Def("self-exit", "Self-illuminating exit signs", "Emergency", "green", "EMER", "SELF EXIT SIGNS"),
        Def("flight-emer", "Flight-compartment emergency light", "Emergency", "white", "BAT", "FLIGHT COMP EMER LT"),
        // Cargo / service compartments
        Def("fwd-cargo-dome", "Forward cargo compartment dome lights", "Cabin", "white", "AC2", "FWD CARGO LTS"),
        Def("fwd-cargo-spot", "Forward cargo door-frame spotlight", "Cabin", "white", "AC2", "FWD CARGO SPOT"),
        Def("aft-cargo-dome", "Aft cargo compartment dome lights", "Cabin", "white", "AC2", "AFT CARGO LTS"),
        Def("aft-cargo-spot", "Aft cargo door-frame spotlight", "Cabin", "white", "AC2", "AFT CARGO SPOT"),
        Def("equip-flood", "A/C equipment compartment floodlights", "Cabin", "white", "AC2", "EQUIP COMP LTS"),
        Def("aft-accessory", "Aft accessory compartment light", "Cabin", "white", "AC2", "AFT ACCESSORY LTS"),
        Def("apu-comp", "APU compartment light", "Cabin", "white", "AC2", "APU COMP LTS"),
        Def("tail-cone", "Tail-cone compartment light", "Cabin", "white", "AC2", "TAIL CONE LTS"),
    )

    /** Distinct breaker names, in first-seen order (TS LIGHT_CBS). */
    val LIGHT_CBS: List<String> = DEFS.map { it.cb }.distinct()

    private fun brt(v: String): Double = when (v) {
        "BRT" -> 1.0
        "DIM" -> 0.45
        else -> 0.0
    }

    private fun b01(v: Boolean): Double = if (v) 1.0 else 0.0

    private fun busOn(b: LightBuses, bus: String): Boolean = when (bus) {
        "AC1" -> b.ac1
        "AC2" -> b.ac2
        "DC1" -> b.dc1
        "DC2" -> b.dc2
        "BAT" -> b.bat
        "HOT" -> b.hot
        "28VAC" -> b.ac28
        "EMER" -> true // own battery pack; charge state is not modelled
        else -> false
    }

    fun lightFailed(s: SimState, id: String): Boolean = s.ltFail.split(",").contains(id)

    fun cbPulled(s: SimState, cb: String): Boolean = s.ltCb.split("|").contains(cb)

    /** JS-style number text, so Kotlin lines match the TS Object.values output. */
    fun jsNum(v: Double): String =
        if (v == floor(v) && abs(v) < 1e15) v.toLong().toString() else v.toString()

    /** Parity line per light, in the TS Object.values order (flash only when the TS def has it). */
    fun lightLines(lights: List<LightStatus>): String = lights.joinToString("\n") { l ->
        listOfNotNull(
            l.id, l.label, l.group, l.color, l.supply, l.cb, l.flash,
            jsNum(l.commanded), l.powered.toString(), l.failed.toString(), l.cbOut.toString(),
            l.lit.toString(), jsNum(l.level), l.reason,
        ).joinToString("|")
    }

    /** Parity line per lighting component, in TS Object.values order. */
    fun componentLines(list: List<LightComponent>): String = list.joinToString("\n") { c ->
        listOf(c.id, c.name, c.group, c.location, c.function, c.feeds, c.powered.toString(), c.state).joinToString("|")
    }

    /** Writes [LightsDerive] outputs (lights, lightComponents) into [d]. */
    fun apply(s: SimState, d: Derived) {
        val b = LightBuses().also {
            it.ac1 = d.transferBus1Powered
            it.ac2 = d.transferBus2Powered
            it.dc1 = d.dcBus1Powered
            it.dc2 = d.dcBus2Powered
            it.bat = d.batteryBusPowered
            it.hot = d.hotBatteryBusPowered
            it.ac28 = d.ac28vPowered
            it.dc = d.dcPower
        }
        val gearUp = d.gearUp
        val (lights, comps) = derive(s, b, gearUp, s.flaps)
        d.lights = lights.toMutableList()
        d.lightComponents = comps.toMutableList()
    }

    fun derive(s: SimState, b: LightBuses, gearUp: Boolean, flaps: Double): Pair<List<LightStatus>, List<LightComponent>> {
        val signNs = !gearUp
        val signSb = !gearUp || flaps > 0
        fun signLevel(m: String, auto: Boolean): Double = when {
            m == "ON" -> 1.0
            m == "AUTO" && auto -> 1.0
            else -> 0.0
        }
        // Emergency lights: ON, or ARMED and aircraft DC power lost.
        val emerActive = s.ltEmergency == "ON" || (s.ltEmergency == "ARMED" && !b.dc)
        val pos = s.ltPosition != "OFF"
        val strobe = s.ltPosition == "STROBE_STEADY"

        val cmd: Map<String, Double> = mapOf(
            "ldg-ret-l" to b01(s.ltRetL == "ON"),
            "ldg-ret-r" to b01(s.ltRetR == "ON"),
            "ldg-fix-l" to b01(s.ltFixedL),
            "ldg-fix-r" to b01(s.ltFixedR),
            "rwy-l" to b01(s.ltTurnoffL),
            "rwy-r" to b01(s.ltTurnoffR),
            "taxi" to b01(s.ltTaxi),
            "wing" to b01(s.ltWing),
            "logo" to b01(s.ltLogo),
            "wheelwell" to b01(s.ltWheelWell),
            "pos-l" to b01(pos), "pos-r" to b01(pos), "pos-t" to b01(pos),
            "strobe-l" to b01(strobe), "strobe-r" to b01(strobe), "strobe-t" to b01(strobe),
            "beacon-top" to b01(s.ltAntiCol), "beacon-bot" to b01(s.ltAntiCol),
            "dome" to brt(s.ltDome),
            "pnl-ovhd" to s.ltPanelOvhd, "pnl-capt" to s.ltPanelCapt, "pnl-fo" to s.ltPanelFo, "pnl-ped" to s.ltPanelPed,
            "map-capt" to b01(s.ltMapCapt), "map-fo" to b01(s.ltMapFo),
            "storm" to b01(s.ltStorm),
            "compass" to b01(s.ltCompass), "observer" to b01(s.ltObserver),
            "shield-emer" to b01(emerActive && s.ltShieldEmer),
            "cab-main" to brt(s.ltCabin),
            "cab-sidewall" to b01(s.ltSidewall), "cab-window" to b01(s.ltWindow),
            "att-work" to b01(s.ltAttWork), "pass-call" to b01(s.ltCall), "lav-call" to b01(s.ltCall),
            "lav-mirror" to b01(s.ltLavMirror), "threshold" to b01(s.ltThreshold), "closet" to b01(s.ltCloset),
            "airstair" to b01(s.ltEntry),
            "fwd-cargo-dome" to b01(s.ltCargo), "fwd-cargo-spot" to b01(s.ltCargo),
            "aft-cargo-dome" to b01(s.ltCargo), "aft-cargo-spot" to b01(s.ltCargo),
            "equip-flood" to b01(s.ltEquip), "aft-accessory" to b01(s.ltAftAccessory),
            "apu-comp" to b01(s.ltApuComp), "tail-cone" to b01(s.ltTailCone),
            "cab-entry" to b01(s.ltEntry), "cab-galley" to b01(s.ltGalley),
            "cab-lav" to b01(s.ltLav), "cab-read" to b01(s.ltReading),
            "sign-ns" to signLevel(s.ltNoSmoking, signNs),
            "sign-sb" to signLevel(s.ltSeatBelt, signSb),
            "exit-sign" to 1.0, // energized from normal/emergency source
            "emer-aisle" to b01(emerActive), "emer-ovhd" to b01(emerActive), "emer-exit" to b01(emerActive),
            "exit-locator" to b01(emerActive), "hatch-handle" to b01(emerActive),
            "overwing-path-l" to b01(emerActive), "overwing-path-r" to b01(emerActive),
            "self-exit" to b01(emerActive), "flight-emer" to b01(emerActive),
        )

        val lights: List<LightStatus> = DEFS.map { def ->
            val commanded = maxOf(0.0, minOf(1.0, cmd[def.id] ?: 0.0))
            val cbOut = cbPulled(s, def.cb)
            val failed = lightFailed(s, def.id)
            var supplyOk = busOn(b, def.supply)
            // Exit signs fall back to the emergency pack when armed/on and normal power is gone.
            if (def.id == "exit-sign" && !supplyOk && emerActive) supplyOk = true
            val powered = supplyOk && !cbOut
            val wants = commanded > 0.02
            var level = if (wants && powered && !failed) commanded else 0.0
            var reason = ""
            if (wants && !powered) reason = if (cbOut) "CB OUT" else "NO ${def.supply}"
            else if (wants && failed) reason = "BULB FAILED"
            if (def.id == "ldg-ret-l" || def.id == "ldg-ret-r") {
                // Retractable landing lights must be extended to shine; EXT extends without lighting.
                val sel = if (def.id == "ldg-ret-l") s.ltRetL else s.ltRetR
                val ext = sel != "RETRACT" && powered
                if (reason.isEmpty()) {
                    reason = when {
                        sel == "RETRACT" -> "RETRACTED"
                        ext -> if (sel == "ON") "" else "EXTENDED · OFF"
                        else -> "NO ${def.supply}"
                    }
                }
                if (!ext) level = 0.0
            }
            // Master DIM only reduces the flight-deck / sign lamps it serves. Storm lights are always full bright.
            if (s.ltMasterDim == "DIM" && def.id != "storm" && (def.group == "Flight deck" || def.id.startsWith("sign-"))) level *= 0.6
            LightStatus().also {
                it.id = def.id
                it.label = def.label
                it.group = def.group
                it.color = def.color
                it.supply = def.supply
                it.cb = def.cb
                it.flash = def.flash
                it.commanded = commanded
                it.powered = powered
                it.failed = failed
                it.cbOut = cbOut
                it.lit = level > 0.02
                it.level = level
                it.reason = reason
            }
        }

        val byId: Map<String, LightStatus> = lights.associateBy { it.id }
        val swOn = listOf(s.ltFixedL, s.ltFixedR, s.ltTurnoffL, s.ltTurnoffR, s.ltTaxi, s.ltWing, s.ltLogo, s.ltWheelWell, s.ltAntiCol)
            .count { it } +
            (if (s.ltRetL != "RETRACT") 1 else 0) + (if (s.ltRetR != "RETRACT") 1 else 0) + (if (pos) 1 else 0)
        val pulled = if (s.ltCb.isEmpty()) 0 else s.ltCb.split("|").size
        val mastDimLabel = s.ltMasterDim

        fun comp(
            id: String, name: String, group: String, location: String, function: String,
            feeds: String, powered: Boolean, state: String,
        ) = LightComponent().also {
            it.id = id; it.name = name; it.group = group; it.location = location; it.function = function
            it.feeds = feeds; it.powered = powered; it.state = state
        }

        val lightComponents: List<LightComponent> = listOf(
            comp("p5-ext-sw", "P5 exterior light switches", "Controls", "P5 forward overhead",
                "Select landing, turnoff, taxi, wing, logo, position, anti-collision and wheel-well lights.",
                "Switch → relay → fixture", true, "$swOn selected"),
            comp("ret-act-l", "Retractable landing light actuator L", "Exterior", "Left wing, inboard lower surface",
                "Motor extends / retracts the lamp. EXT extends without lighting; ON extends and lights.",
                "Landing light L", byId.getValue("ldg-ret-l").powered,
                if (s.ltRetL == "RETRACT") "RETRACTED" else if (byId.getValue("ldg-ret-l").powered) "EXTENDED" else "NO POWER"),
            comp("ret-act-r", "Retractable landing light actuator R", "Exterior", "Right wing, inboard lower surface",
                "Motor extends / retracts the lamp. EXT extends without lighting; ON extends and lights.",
                "Landing light R", byId.getValue("ldg-ret-r").powered,
                if (s.ltRetR == "RETRACT") "RETRACTED" else if (byId.getValue("ldg-ret-r").powered) "EXTENDED" else "NO POWER"),
            comp("beacon-flasher", "Anti-collision flasher / power supply", "Exterior", "Aft fuselage equipment area",
                "Drives the red top and bottom beacons.", "Beacon top, beacon bottom",
                byId.getValue("beacon-top").powered || byId.getValue("beacon-bot").powered,
                if (s.ltAntiCol) "FLASHING" else "OFF"),
            comp("strobe-psu", "Strobe power supply", "Exterior", "Aft fuselage equipment area",
                "Charges and fires the white wingtip / tail strobes in STROBE & STEADY.", "Strobe L, R, tail",
                byId.getValue("strobe-l").powered || byId.getValue("strobe-r").powered || byId.getValue("strobe-t").powered,
                if (strobe) "FIRING" else "OFF"),
            comp("pos-wiring", "Position light circuit", "Exterior", "Wingtips and tail cone",
                "Steady navigation lights: red left, green right, white tail.", "Position L, R, tail",
                byId.getValue("pos-l").powered || byId.getValue("pos-r").powered || byId.getValue("pos-t").powered,
                if (pos) "STEADY" else "OFF"),
            comp("master-dim", "MASTER DIM & TEST switch", "Controls", "P5 overhead",
                "BRT / DIM sets annunciator brightness; TEST lights every annunciator lamp.", "Flight-deck annunciators",
                b.dc, if (s.ltTest) "TEST" else mastDimLabel),
            comp("panel-dimmers", "Panel light dimmers", "Flight deck", "P5 overhead, pilots' panels, pedestal",
                "Rheostats for overhead, Captain, First Officer and pedestal lighting.", "Panel and instrument lighting",
                b.ac28,
                "OVHD ${pct(s.ltPanelOvhd)}% · CAPT ${pct(s.ltPanelCapt)}% · F/O ${pct(s.ltPanelFo)}% · PED ${pct(s.ltPanelPed)}%"),
            comp("ac28-xfmr", "28 V AC lighting transformers", "Power", "Electrical / electronic equipment bay",
                "Step the 115 V AC system down to 28 V AC for panel and reading lights.", "Panel lighting, passenger reading lights",
                b.ac28, if (b.ac28) "ENERGIZED" else "OFF"),
            comp("fa-panel", "Flight attendant lighting panel", "Cabin", "Forward attendant panel",
                "Cabin BRT / DIM / OFF, entry, galley and lavatory lights.", "Cabin, entry, galley, lav lights",
                byId.getValue("cab-main").powered || byId.getValue("cab-entry").powered, "CABIN ${s.ltCabin}"),
            comp("cab-ballasts", "Cabin fluorescent ballasts", "Cabin", "Ceiling and sidewall light strips",
                "Start and run the cabin fluorescent tubes; DIM reduces output.", "Passenger cabin lights",
                byId.getValue("cab-main").powered,
                if (byId.getValue("cab-main").lit) (if (s.ltCabin == "DIM") "DIM" else "BRIGHT") else "OFF"),
            comp("sign-logic", "Passenger sign relay + AUTO logic", "Cabin", "Overhead / relay panel",
                "AUTO lights NO SMOKING with gear not up; FASTEN SEAT BELTS with gear not up or flaps extended.",
                "NO SMOKING, FASTEN SEAT BELTS", byId.getValue("sign-ns").powered, "NS ${s.ltNoSmoking} · SB ${s.ltSeatBelt}"),
            comp("emer-sw", "EMER LIGHTS switch", "Controls", "P5 overhead (guarded)",
                "OFF / ARMED / ON. ARMED turns the emergency lights on automatically when DC power is lost.",
                "Emergency light pack", true, s.ltEmergency),
            comp("emer-pack", "Emergency light battery pack", "Emergency", "Cabin / aft equipment area",
                "Own battery: supplies aisle, overhead, exit-area lights and exit signs when normal power is lost.",
                "Emergency lights, exit signs", true,
                if (emerActive) "DISCHARGING" else if (s.ltEmergency == "ARMED") "READY · CHARGING" else "STANDBY"),
            comp("light-cbs", "Lighting circuit breakers", "Controls", "P6 circuit breaker panels",
                "Protect each lighting circuit; a pulled breaker kills every light on it.",
                "${LIGHT_CBS.size} breakers", true, if (pulled > 0) "$pulled PULLED" else "ALL IN"),
        )
        return lights to lightComponents
    }

    /** TS Math.round(v * 100) for the panel-dimmer percentages. */
    private fun pct(v: Double): String = Math.round(v * 100).toString()
}
