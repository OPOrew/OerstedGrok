package com.oersted.trainer.sim

/**
 * Port of the interlocks advisory list of src/lib/sim/derive.ts (ATA 21/24/26/29/32/36).
 * Each block mirrors one TS condition, in the same order (order matters for the displayed list).
 * Trainer thresholds copied from the TS: APU low oil 45 psi, APU door 0.98 / 0.05, APU fuel valve 0.95,
 * start air 30 psi, idle N2 window 18-35, generator protection trip 0.40 s.
 * Runs after AutobrakeDerive (antiskid) and HydraulicsDerive (hydA, reverser state).
 */
object InterlocksDerive {
    fun apply(s: SimState, d: Derived) {
        val list = d.interlocks
        fun add(id: String, text: String, tone: String) { list.add(Interlock().also { it.id = id; it.text = text; it.tone = tone }) }

        val apuLowOil = s.apuMaster != "OFF" && (s.apuLowOilLightOn || s.apuOilPsi <= 45.0)
        d.apuLowOil = apuLowOil

        // Generator protection: latched trip, or abnormal condition persisting 0.40 s (same as ElectricalDerive's g1Tripped).
        val gen1Demand = s.gen1 && (s.faults.generator1OverFrequency || s.faults.generator1UnderFrequency ||
            s.faults.generator1OverVoltage || s.faults.generator1UnderVoltage || s.faults.generator1OverCurrent ||
            s.faults.generator1Differential)
        val gen2Demand = s.gen2 && (s.faults.generator2OverFrequency || s.faults.generator2UnderFrequency ||
            s.faults.generator2OverVoltage || s.faults.generator2UnderVoltage || s.faults.generator2OverCurrent ||
            s.faults.generator2Differential)
        val gen1Tripped = s.generator1ProtectionLatched || (gen1Demand && s.generator1ProtectionTimer >= 0.40)
        val gen2Tripped = s.generator2ProtectionLatched || (gen2Demand && s.generator2ProtectionTimer >= 0.40)
        d.generator1ProtectionTripped = gen1Tripped
        d.generator2ProtectionTripped = gen2Tripped

        if (apuLowOil) add("apu-low-oil", "APU LOW OIL PRESSURE — normal during start until oil pressure reaches 55 psi; abnormal if it remains low at service speed.", "caution")
        if (s.apuMaster == "START" && s.apuDoor < 0.98) add("apu-door", "Starter lockout relay held — APU inlet door is not fully open.", "caution")
        if (s.apuMaster != "OFF" && s.apuFuelValve < 0.95 && s.apuDoor < 0.05) add("apu-fuel", "Inlet door waits on the fuel shutoff valve. Valve must reach full open first.", "info")
        if ((s.start1 == "GRD" && d.leftDuctPsi < 30.0) || (s.start2 == "GRD" && d.rightDuctPsi < 30.0)) add("start-air", "Pneumatic starter needs ≥ 30 psi at the starter. Use APU bleed, the ground pneumatic source, an engine bleed, or cross-bleed as applicable.", "caution")
        if (s.start1 == "GRD" && !s.lever1 && s.n2_1 > 18.0 && s.n2_1 < 35.0) add("idle-1", "Engine 1 N2 is on starter. Move start lever to IDLE to introduce fuel and ignition.", "info")
        if (s.start2 == "GRD" && !s.lever2 && s.n2_2 > 18.0 && s.n2_2 < 35.0) add("idle-2", "Engine 2 N2 is on starter. Move start lever to IDLE to introduce fuel and ignition.", "info")
        if (d.dualBleed) add("dual-bleed", "DUAL BLEED — APU bleed valve is open, an engine bleed valve is open, and the isolation valve is open (ATA 36 source conflict).", "warn")
        if (gen1Tripped && s.gen1) add("gen1-off-bus-protection", "GEN 1 OFF BUS — protection trip has removed GEN 1 from its generator bus. Transfer relay can pick up Transfer Bus 1 from GEN 2 if BUS TRANSFER is AUTO.", "warn")
        if (gen2Tripped && s.gen2) add("gen2-off-bus-protection", "GEN 2 OFF BUS — protection trip has removed GEN 2 from its generator bus. Transfer relay can pick up Transfer Bus 2 from GEN 1 if BUS TRANSFER is AUTO.", "warn")
        if (s.gear == "UP" && d.hydA < 1500.0) add("gear-hyd", "Landing gear is powered by Hydraulic System A only. Pressure is too low to retract.", "caution")
        if (s.rev1 && !d.thrustReverser1Active) add("rev1-isolation", "ENGINE 1 thrust reverser inhibited — requires WOW, engine running, idle thrust lever, System A hydraulic pressure, and no fire-handle shutdown.", "warn")
        if (s.rev2 && !d.thrustReverser2Active) add("rev2-isolation", "ENGINE 2 thrust reverser inhibited — requires WOW, engine running, idle thrust lever, System A hydraulic pressure, and no fire-handle shutdown.", "warn")
        if (s.faults.apuDoorJam) add("door-jam", "Fault injected: APU inlet door actuator jammed. Start will not complete.", "warn")
        if (s.faults.hydALeak) add("hyd-leak", "Fault injected: System A leak. Standby pump can cover rudder / LE flaps.", "warn")
        if (s.faults.packOverheat) add("pack-oh", "Fault injected: L pack overheat. PACK TRIP OFF — mix valve drives full cold.", "warn")
        if (s.faults.packOverheatR) add("pack-oh-r", "Fault injected: R pack overheat. PACK TRIP OFF — mix valve drives full cold.", "warn")
        if (s.faults.bleedOverheat1) add("bleed-oh-1", "Fault injected: engine 1 bleed duct overheat. BLEED TRIP 1 — bleed valve closed.", "warn")
        if (s.faults.bleedOverheat2) add("bleed-oh-2", "Fault injected: engine 2 bleed duct overheat. BLEED TRIP 2 — bleed valve closed.", "warn")
        if (s.faults.engineFire1 && !s.engine1FirePulled) add("eng1-fire", "ENGINE 1 FIRE — pull the ENG 1 fire handle to shut down the engine and arm extinguishing.", "warn")
        if (s.faults.engineFire2 && !s.engine2FirePulled) add("eng2-fire", "ENGINE 2 FIRE — pull the ENG 2 fire handle to shut down the engine and arm extinguishing.", "warn")
        if (s.faults.apuFireDetected && !s.apuFirePulled) add("apu-fire", "APU FIRE WARN — fire detected. Pull the APU FIRE handle to shut down and arm the bottle.", "warn")
        if (s.faults.tr1Failure) add("tr1-fail", "TR 1 failure injected — DC Bus 1 relies on TR 3 backup from Generator Bus 2 when that AC input is available.", "warn")
        if (s.faults.tr2Failure) add("tr2-fail", "TR 2 failure injected — DC Bus 2 relies on TR 3 backup from Generator Bus 2 when that AC input is available.", "warn")
        if (s.faults.generator1Failure) add("gen1-fail", "GEN 1 failure injected — GEN 1 is unavailable; BUS TRANSFER AUTO can transfer the No. 1 side to GEN 2.", "warn")
        if (s.faults.generator2Failure) add("gen2-fail", "GEN 2 failure injected — GEN 2 is unavailable; BUS TRANSFER AUTO can transfer the No. 2 side to GEN 1.", "warn")
        if (gen1Tripped) add("gen1-protection", "GEN 1 protection trip — abnormal generator condition persisted for 0.40 s; generator breaker/source is tripped.", "warn")
        if (gen2Tripped) add("gen2-protection", "GEN 2 protection trip — abnormal generator condition persisted for 0.40 s; generator breaker/source is tripped.", "warn")
        if (s.faults.transferRelay1Failure) add("xfr1-fail", "XFR BUS 1 relay failure — automatic transfer to GEN 2 is unavailable.", "warn")
        if (s.faults.transferRelay2Failure) add("xfr2-fail", "XFR BUS 2 relay failure — automatic transfer to GEN 1 is unavailable.", "warn")
        if (s.faults.btb1Failure) add("btb1-fail", "BTB 1 failure — APU or external power cannot connect to load bus 1 through BTB 1.", "warn")
        if (s.faults.btb2Failure) add("btb2-fail", "BTB 2 failure — APU or external power cannot connect to load bus 2 through BTB 2.", "warn")
        if (s.faults.apuGeneratorBreakerFailure) add("apu-gb-fail", "APU generator breaker failure — APU generator is unavailable to the tie bus.", "warn")
        if (s.faults.apuOverspeed) add("apu-overspeed", "APU overspeed shutdown fault — APU control has tripped the unit.", "warn")
        if (s.faults.apuLowOilPressure) add("apu-low-oil-fault", "APU low-oil-pressure shutdown fault — automatic shutdown is active.", "warn")
        if (s.faults.apuHighOilTemperature) add("apu-high-oil-temp", "APU high-oil-temperature shutdown fault — automatic shutdown is active.", "warn")
        if (s.faults.externalPowerContactorFailure) add("epc-fail", "External power contactor failure — GPU is connected but cannot energize the aircraft AC buses.", "warn")
        if (s.faults.openPhase1) add("phase1-fail", "GEN 1 open-phase fault — three-phase loads on the No. 1 source lose the affected phase; TR1 and System B electric pump 1 cannot establish healthy three-phase power. The generator bus itself is not treated as a total loss.", "warn")
        if (s.faults.openPhase2) add("phase2-fail", "GEN 2 open-phase fault — three-phase loads on the No. 2 source lose the affected phase; TR2, TR3 and System B electric pump 2 cannot establish healthy three-phase power. The generator bus itself is not treated as a total loss.", "warn")
        if (s.faults.tr3Failure) add("tr3-fail", "TR 3 failure — DC backup path unavailable.", "warn")
        if (s.faults.batteryFailure) add("battery-fail", "Battery failure — battery-backed standby power and hot-battery functions are unavailable.", "warn")
        if (s.faults.staticInverterFailure) add("inv-fail", "Static inverter failure — AUTO standby cannot transfer to standby AC from the battery path.", "warn")
        if (s.faults.teFlapHydMotorFail && !s.altFlapsArm) add("te-flap-hyd-fail", "TE flap hydraulic motor failure — normal System A drive is unavailable. Arm ALTERNATE FLAPS and use the alternate position switch for electric drive.", "warn")
        if (s.faults.antiskidInop) add("antiskid-fail", "ANTISKID fault injected — automatic braking is inhibited while the fault is present.", "warn")
        if (s.landingGearFault != "NONE") {
            val label = when (s.landingGearFault) { "NOSE" -> "NOSE GEAR"; "LEFT_MAIN" -> "LEFT MAIN GEAR"; else -> "RIGHT MAIN GEAR" }
            add("gear-position-fault", "Fault injected: $label position fault — selector DN but the affected gear is not down-and-locked.", "warn")
        }
        if (s.autobrake > 0.0 && !d.antiskidOperational) add("autobrake-antiskid", "AUTOBRAKE DISARM — both antiskid channels must be operative.", "warn")
        if (s.gear == "UP") add("antiskid-gear-retract", "ANTISKID DISABLED — two microswitches on the gear lever inhibit antiskid while the lever is selected UP (gear retraction).", "caution")
    }
}
