package com.oersted.trainer.sim

/**
 * Port of the cargo fire detection and FIRE WARN summary of src/lib/sim/derive.ts (ATA 26).
 * Detection requires the SB 26A1081 installation and at least one healthy loop. Both loops healthy use the
 * 0.35 threshold; a single loop uses the 0.65 delayed threshold (trainer values copied from the TS).
 * Runs after StartBleedDerive: needs the engine/APU/wheel-well warnings already on Derived.
 */
object CargoFireDerive {
    fun apply(s: SimState, d: Derived, e: ElecLocals) {
        val detOk = s.cargoFireInstalled && (s.cargoLoopA || s.cargoLoopB)
        val threshold = if (s.cargoLoopA && s.cargoLoopB) 0.35 else 0.65
        d.cargoFwdFireWarn = e.indicationPower && (s.cargoFwdFire || s.cargoFwdLevel >= threshold) && detOk
        d.cargoAftFireWarn = e.indicationPower && (s.cargoAftFire || s.cargoAftLevel >= threshold) && detOk
        d.fireWarn = d.engine1FireWarn || d.engine2FireWarn || d.apuFireWarn || d.wheelWellFireWarn ||
            d.cargoFwdFireWarn || d.cargoAftFireWarn
    }
}
