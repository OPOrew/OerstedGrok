package com.oersted.trainer.sim

/**
 * Port of the ATA 36 bleed/manifold section of src/lib/sim/derive.ts: engine bleed valves, isolation
 * valve, APU/ground/engine manifold sources, anti-ice load, duct pressure, DUAL BLEED.
 * Runs after ElectricalDerive (needs eng running + apuAvailable from Derived).
 * Pressures (30 + 14*valve, 44, 5 psi anti-ice) are trainer values copied from the TS.
 */
object BleedDerive {
    fun apply(s: SimState, d: Derived) {
        val eng1Running = d.eng1Running
        val eng2Running = d.eng2Running

        val leftBleed = eng1Running && s.bleed1 && !s.bleedTrip1 && !s.engine1FirePulled
        val rightBleed = eng2Running && s.bleed2 && !s.bleedTrip2 && !s.engine2FirePulled

        // ATA 36: AUTO is closed only when BOTH engine bleed and BOTH pack switches are ON; otherwise OPEN.
        val isolationOpen = s.isolation == "OPEN" || (s.isolation == "AUTO" && !(s.bleed1 && s.bleed2 && s.packL && s.packR))

        // APU bleed is the left-side pneumatic source; ground pneumatic service feeds the right manifold.
        val apuBleedOpen = d.apuAvailable && s.apuBleed && s.apuBleedValve > 0.7
        val apuBleedLeft = apuBleedOpen
        val groundBleed = s.groundPneumaticConnected
        val leftManifold = apuBleedLeft || leftBleed || ((rightBleed || groundBleed) && isolationOpen)
        val rightManifold = groundBleed || rightBleed || (leftBleed && isolationOpen) || (apuBleedLeft && isolationOpen)
        d.leftManifold = leftManifold
        d.rightManifold = rightManifold
        d.isolationOpen = isolationOpen

        val apuPsi = if (apuBleedOpen) 30 + s.apuBleedValve * 14 else 0.0
        val groundPsi = if (s.groundPneumaticConnected) s.groundPneumaticPsi else 0.0
        val engine1Psi = if (leftBleed) 44.0 else 0.0
        val engine2Psi = if (rightBleed) 44.0 else 0.0

        // Left/right pneumatic network is kept physical: a source reaches the other side only through the isolation valve.
        val antiIceLoad1 = if (s.antiIce1 && leftBleed) 5.0 else 0.0
        val antiIceLoad2 = if (s.antiIce2 && rightBleed) 5.0 else 0.0
        val leftLocalPsi = maxOf(0.0, maxOf(apuPsi, engine1Psi) - antiIceLoad1)
        val rightLocalPsi = maxOf(0.0, maxOf(groundPsi, engine2Psi) - antiIceLoad2)
        d.leftDuctPsi = if (leftManifold) maxOf(leftLocalPsi, if (isolationOpen) rightLocalPsi else 0.0) else 0.0
        d.rightDuctPsi = if (rightManifold) maxOf(rightLocalPsi, if (isolationOpen) leftLocalPsi else 0.0) else 0.0
        d.ductPsi = maxOf(d.leftDuctPsi, d.rightDuctPsi)

        // DUAL BLEED: APU and engine source conflict, not simply both engine switches ON.
        d.dualBleed = apuBleedOpen && isolationOpen && (leftBleed || rightBleed)
        d.bleed1Available = leftBleed
        d.bleed2Available = rightBleed
    }
}
