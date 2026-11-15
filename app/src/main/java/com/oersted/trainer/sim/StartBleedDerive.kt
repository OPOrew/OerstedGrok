package com.oersted.trainer.sim

/**
 * Port of the start-valve, 13th-stage bleed and off-bus / fire annunciator block of src/lib/sim/derive.ts
 * (ATA 21/36/75/24/26). Starter valve opens at duct pressure >= 30 psi with DC power (TS threshold).
 * 13th-stage bleed is inhibited while the starter valve is open (start-bleed control interrupts actuating air).
 * Runs after BleedDerive and ElectricalDerive: needs d.leftDuctPsi, d.rightDuctPsi, eng running, apuAvailable, ElecLocals.
 */
object StartBleedDerive {
    fun apply(s: SimState, d: Derived, e: ElecLocals) {
        val dcPower = d.dcPower
        val startValveOpen1 = s.startValve1 && d.leftDuctPsi >= 30.0 && dcPower
        val startValveOpen2 = s.startValve2 && d.rightDuctPsi >= 30.0 && dcPower
        d.startValveOpen1 = startValveOpen1
        d.startValveOpen2 = startValveOpen2

        // 13th-stage availability: running engine, bleed selected, not tripped, starter cut out.
        d.bleed13Available1 = d.eng1Running && s.bleed1 && !s.bleedTrip1 && !startValveOpen1
        d.bleed13Available2 = d.eng2Running && s.bleed2 && !s.bleedTrip2 && !startValveOpen2

        // GEN OFF BUS: generator capable or selected but not powering its bus.
        val apuGenOnBus = e.apuLeftSource || e.apuRightSource
        d.apuGenOnBus = apuGenOnBus
        d.apuGenOffBus = e.indicationPower && d.apuAvailable && s.apuGen && !apuGenOnBus
        d.gen1OffBus = e.indicationPower && d.eng1Running && s.gen1 && !e.generator1BusPowered
        d.gen2OffBus = e.indicationPower && d.eng2Running && s.gen2 && !e.generator2BusPowered

        // FIRE WARN reflects detection, not the handle. Detector warnings need indication power.
        d.engine1FireWarn = e.indicationPower && s.faults.engineFire1
        d.engine2FireWarn = e.indicationPower && s.faults.engineFire2
        d.apuFireWarn = e.indicationPower && s.faults.apuFireDetected
        d.wheelWellFireWarn = e.indicationPower && (s.wheelWellFire || s.wheelWellLevel >= 0.5)
    }
}
