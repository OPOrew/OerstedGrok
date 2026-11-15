package com.oersted.trainer.sim

/**
 * Port of the hydraulic PRESSURE section of src/lib/sim/derive.ts (ATA 29): reservoir air, System A
 * engine pumps, System B electric pumps (bus feed + three-phase health), ground hydraulic service,
 * ground interconnect, flight-control availability, thrust reversers, standby pump demand.
 * Pressures (3000 / 2900 / 400 leak / 1500 threshold) are trainer values copied from the TS.
 * Runs after ElectricalDerive, ServicesDerive, AuxPowerDerive (needs standbyAcPowered).
 */
object HydraulicsDerive {
    fun apply(s: SimState, d: Derived, e: ElecLocals) {
        val groundReservoirAir = s.hydReservoirAirConnected && s.wow && s.hydReservoirAirPsi >= 30
        val reservoirEngineAir1 = e.eng1AccessoryDriven && !s.engine1FirePulled
        val reservoirEngineAir2 = e.eng2AccessoryDriven && !s.engine2FirePulled
        val reservoirAirAvailable = reservoirEngineAir1 || reservoirEngineAir2 || groundReservoirAir
        d.reservoirPressurized = reservoirAirAvailable
        d.reservoirGroundAir = groundReservoirAir

        // System A: engine-driven pumps, pressure-compensated to ~3000 psi.
        val hydAPump1On = reservoirAirAvailable && e.eng1AccessoryDriven && s.engHyd1 && !s.engine1FirePulled && s.hydAQty > 0
        val hydAPump2On = reservoirAirAvailable && e.eng2AccessoryDriven && s.engHyd2 && !s.engine2FirePulled && s.hydAQty > 0
        val hydAReservoirAvailable = s.hydAQty > 0
        var hydA = if ((hydAPump1On && hydAReservoirAvailable) || (hydAPump2On && hydAReservoirAvailable)) 3000.0 else 0.0
        if (s.faults.hydALeak) hydA = minOf(hydA, if (hydAReservoirAvailable) 400.0 else 0.0)

        // System B: electric pumps on AC bus feeds; engine-generator feeds need three-phase health.
        val hydBBus1Electrical = (e.generator1BusPowered && e.g1Healthy) || e.tieSource1 || (e.transferRelay1Alt && e.g2Healthy)
        val hydBBus2Electrical = (e.generator2BusPowered && e.g2Healthy) || e.tieSource2 || (e.transferRelay2Alt && e.g1Healthy)
        val hydBPump1On = reservoirAirAvailable && s.hydBQty > 0 && hydBBus1Electrical && s.elecHyd1
        val hydBPump2On = reservoirAirAvailable && s.hydBQty > 0 && hydBBus2Electrical && s.elecHyd2
        val hydBReservoirAvailable = s.hydBQty > 0
        var hydB = if ((hydBPump1On && hydBReservoirAvailable) || (hydBPump2On && hydBReservoirAvailable)) 3000.0 else 0.0
        if (s.faults.hydBLeak) hydB = minOf(hydB, if (hydBReservoirAvailable) 400.0 else 0.0)

        // Ground hydraulic service pressure enters System B first; the ground interconnect transfers it to A
        // only when parking-brake interlocked (AMM).
        val groundHydraulicSource = if (s.hydGroundPowerConnected && s.wow && s.hydBQty > 0) 3000.0 else 0.0
        hydB = maxOf(hydB, groundHydraulicSource)
        val groundInterconnectPermitted = s.wow && s.parkingBrake
        val groundAPress = if (s.groundInterconnect && groundInterconnectPermitted) hydB else 0.0
        if (groundAPress > 0) hydA = maxOf(hydA, groundAPress)

        // Flight-control shutoff switches gate the users, not the source.
        d.hydAPump1On = hydAPump1On
        d.hydAPump2On = hydAPump2On
        d.hydA = hydA
        d.hydBPump1On = hydBPump1On
        d.hydBPump2On = hydBPump2On
        d.hydB = hydB
        d.groundAPress = groundAPress
        d.flightControlAAvailable = hydA > 1500 && s.fltCtrlA
        d.flightControlBAvailable = hydB > 1500 && s.fltCtrlB

        // ATA 29-00-00: System A is the thrust-reverser source; deployment needs ground, running engine, idle lever.
        d.thrustReverser1Active = s.rev1 && s.wow && d.eng1Running && s.throttle1 <= 0.05 && hydA > 1500 && !s.engine1FirePulled
        d.thrustReverser2Active = s.rev2 && s.wow && d.eng2Running && s.throttle2 <= 0.05 && hydA > 1500 && !s.engine2FirePulled

        // Standby pump: ALTERNATE FLAPS ARM is itself a demand; AUTO demands it when primary pressure is lost.
        val primaryHydraulicLoss = hydA < 1500 && hydB < 1500
        val stbyNeed = s.altFlapsArm || s.stbyHyd == "ON" || (s.stbyHyd == "AUTO" && primaryHydraulicLoss)
        d.hydS = if (d.standbyAcPowered && stbyNeed && s.hydSQty > 0) 2900.0 else 0.0
    }
}
