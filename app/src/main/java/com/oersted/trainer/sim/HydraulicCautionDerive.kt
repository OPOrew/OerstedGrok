package com.oersted.trainer.sim

/**
 * Port of the hydraulic LOW PRESS lights and MASTER CAUTION summary of src/lib/sim/derive.ts (ATA 26/29/36).
 * LOW PRESS indicates actual pressure (below 1500 psi), not commanded pressure, and is electrically powered.
 * MASTER CAUTION is the OR of the caution sources listed in the TS (trainer thresholds: 1500 psi, fuel pump 10 s).
 * Runs after CargoFireDerive: needs dualBleed and hydBPump*On from Derived, eng accessory flags from ElecLocals.
 */
object HydraulicCautionDerive {
    fun apply(s: SimState, d: Derived, e: ElecLocals) {
        val ind = e.indicationPower
        // ATA 26-21-0: a closed fire shutoff valve bypasses the associated low-pressure light.
        val pump1Selected = s.engHyd1 && e.eng1AccessoryDriven && !s.engine1FirePulled
        val pump2Selected = s.engHyd2 && e.eng2AccessoryDriven && !s.engine2FirePulled
        val hydALow = ind && (pump1Selected || pump2Selected) && s.hydAPress < 1500.0
        val hydBPump1Low = ind && s.elecHyd1 && (!d.hydBPump1On || s.hydBPress < 1500.0)
        val hydBPump2Low = ind && s.elecHyd2 && (!d.hydBPump2On || s.hydBPress < 1500.0)
        val hydBLow = ind && (hydBPump1Low || hydBPump2Low)

        d.hydALow = hydALow
        d.hydBLow = hydBLow
        d.hydBPump1Low = hydBPump1Low
        d.hydBPump2Low = hydBPump2Low
        d.masterCaution = ind && (
            d.dualBleed ||
                hydALow ||
                hydBLow ||
                s.faults.hydBLeak ||
                s.packTripL ||
                s.packTripR ||
                s.bleedTrip1 ||
                s.bleedTrip2 ||
                s.faults.packOverheat ||
                s.faults.apuDoorJam ||
                (s.fuelCenterPumpAutoShutoffInstalled &&
                    (s.fuelCenterPump1LowPressureTimer >= 10.0 || s.fuelCenterPump2LowPressureTimer >= 10.0))
            )
    }
}
