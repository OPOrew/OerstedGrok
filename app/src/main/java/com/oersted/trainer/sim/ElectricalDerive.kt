package com.oersted.trainer.sim

/**
 * Port of the electrical-source section of src/lib/sim/derive.ts (ATA 24 power sources, transfer
 * relays, TR1/TR2/TR3, DC/AC bus powered flags, ignition power, indication power).
 *
 * Parity: ElectricalParityTest compares against tools/parity/electrical.json, which is produced by
 * running the real web derive() (tools/parity/dump-electrical.mts). Keep the TS comments intact.
 * Remaining derive.ts sections (hydraulics, fuel, pneumatic, lights, flight, nav...) are NOT ported.
 */
object ElectricalDerive {
    /** Breaker closed unless explicitly open (TS: s.circuitBreakers?.[id] !== false). */
    fun cbClosed(s: SimState, id: String): Boolean = s.circuitBreakers[id] != false

    fun compute(s: SimState): Derived {
        val d = Derived()
        apply(s, d)
        return d
    }

    /** Writes the electrical outputs into [d] and returns the TS locals that later sections need. */
    fun apply(s: SimState, d: Derived): ElecLocals {
        val batteryPower = s.battery && !s.faults.batteryFailure
        val groundPowerAvailable = s.externalPowerConnected
        val externalAcConnected = groundPowerAvailable && s.groundPowerOn && !s.faults.externalPowerContactorFailure
        val apuAvailable = s.apuRpm >= 95 && s.apuMaster != "OFF" && !s.apuFirePulled && !s.faults.apuOverspeed && !s.faults.apuHighOilTemperature
        // Accessory rotation is N2-based, distinct from the fuel lever (see derive.ts).
        val eng1AccessoryDriven = s.n2_1 > 5
        val eng2AccessoryDriven = s.n2_2 > 5
        val eng1Running = eng1AccessoryDriven && s.lever1
        val eng2Running = eng2AccessoryDriven && s.lever2

        // Generator protection (ATA 24): abnormal conditions trip after a 0.40 s delay.
        val g1Demand = s.gen1 && (s.faults.generator1OverFrequency || s.faults.generator1UnderFrequency || s.faults.generator1OverVoltage || s.faults.generator1UnderVoltage || s.faults.generator1OverCurrent || s.faults.generator1Differential)
        val g2Demand = s.gen2 && (s.faults.generator2OverFrequency || s.faults.generator2UnderFrequency || s.faults.generator2OverVoltage || s.faults.generator2UnderVoltage || s.faults.generator2OverCurrent || s.faults.generator2Differential)
        val g1Tripped = s.generator1ProtectionLatched || (g1Demand && s.generator1ProtectionTimer >= 0.40)
        val g2Tripped = s.generator2ProtectionLatched || (g2Demand && s.generator2ProtectionTimer >= 0.40)
        val g1Healthy = !s.faults.openPhase1 && !g1Tripped
        val g2Healthy = !s.faults.openPhase2 && !g2Tripped
        val externalPowerSelected = externalAcConnected

        // ATA 26-21-0: engine fire switch pulled removes the generator source (modelled immediately).
        val gen1DriveDisconnected = s.gen1Disc && cbClosed(s, "gen-drive-disc-1") && !s.faults.genDriveDisconnectFailure1
        val gen2DriveDisconnected = s.gen2Disc && cbClosed(s, "gen-drive-disc-2") && !s.faults.genDriveDisconnectFailure2
        val gen1Source = eng1Running && s.gen1 && !gen1DriveDisconnected && !s.groundPowerOn && !externalPowerSelected && !s.faults.generator1Failure && !g1Tripped && !s.engine1FirePulled
        val gen2Source = eng2Running && s.gen2 && !gen2DriveDisconnected && !s.groundPowerOn && !externalPowerSelected && !s.faults.generator2Failure && !g2Tripped && !s.engine2FirePulled

        // ATA 24: one APU generator; L/R APU controls select the load bus, not separate generators.
        val apuGeneratorAvailable = apuAvailable && s.apuGen && !s.groundPowerOn && !s.faults.apuGeneratorBreakerFailure && !externalAcConnected
        val apuLeftSource = apuGeneratorAvailable && s.apuLeftControl
        val apuRightSource = apuGeneratorAvailable && s.apuRightControl

        val externalAc = externalAcConnected && !gen1Source && !gen2Source
        val generator1BusPowered = gen1Source
        val generator2BusPowered = gen2Source
        val externalPowerContactorClosed = externalAc
        val externalPowerNotInUse = groundPowerAvailable && !externalPowerContactorClosed
        val tieSource1 = !generator1BusPowered && !s.faults.btb1Failure && (externalAc || apuLeftSource)
        val tieSource2 = !generator2BusPowered && !s.faults.btb2Failure && (externalAc || apuRightSource)

        // ATA 24 transfer relays R3/R4: the alternate path closes after the 0.15 s transfer interval.
        val transferRelay1Demand = s.busTie && !s.faults.transferRelay1Failure && !generator1BusPowered && !tieSource1 && generator2BusPowered
        val transferRelay2Demand = s.busTie && !s.faults.transferRelay2Failure && !generator2BusPowered && !tieSource2 && generator1BusPowered
        val transferRelay1Alt = transferRelay1Demand && s.transferRelay1Timer >= 0.15
        val transferRelay2Alt = transferRelay2Demand && s.transferRelay2Timer >= 0.15
        val transferBus1Powered = generator1BusPowered || tieSource1 || transferRelay1Alt
        val transferBus2Powered = generator2BusPowered || tieSource2 || transferRelay2Alt
        val acMainBus1Powered = generator1BusPowered || tieSource1
        val acMainBus2Powered = generator2BusPowered || tieSource2
        val ac28vMainBus2Powered = acMainBus2Powered && cbClosed(s, "ac-28v-main-bus-2")
        val btb1Closed = tieSource1
        val btb2Closed = tieSource2

        // ATA 24-31-00: TR1 <- transfer bus 1, TR2 <- transfer bus 2, TR3 <- generator bus 2 only.
        val tr1InputAvailable = (generator1BusPowered && g1Healthy) || tieSource1 || (transferRelay1Alt && g2Healthy)
        val tr2InputAvailable = (generator2BusPowered && g2Healthy) || tieSource2 || (transferRelay2Alt && g1Healthy)
        val tr1Powered = tr1InputAvailable && !s.faults.tr1Failure
        val tr2Powered = tr2InputAvailable && !s.faults.tr2Failure
        val tr3InputAvailable = (generator2BusPowered && g2Healthy) || tieSource2
        val tr3Powered = tr3InputAvailable && !s.faults.tr3Failure
        val dcBus1Powered = tr1Powered || tr3Powered
        val dcBus2Powered = tr2Powered || tr3Powered
        // The battery bus can be fed from TR3 while the battery switch is ON.
        val batteryBusPowered = batteryPower || (s.battery && tr3Powered)
        val essentialDcPower = batteryBusPowered || dcBus1Powered || dcBus2Powered
        val dcPower = essentialDcPower

        // ATA 74: ignition needs DC control power, lever IDLE, a CB closed and a matching selection.
        val ignition1LeftOn = dcPower && s.lever1 && cbClosed(s, "eng1-ign-left") && !s.faults.igniter1LeftFailure && (s.start1 == "GRD" || s.start1 == "FLT" || s.start1 == "IGN_L")
        val ignition1RightOn = dcPower && s.lever1 && cbClosed(s, "eng1-ign-right") && !s.faults.igniter1RightFailure && (s.start1 == "GRD" || s.start1 == "FLT" || s.start1 == "IGN_R")
        val ignition2LeftOn = dcPower && s.lever2 && cbClosed(s, "eng2-ign-left") && !s.faults.igniter2LeftFailure && (s.start2 == "GRD" || s.start2 == "FLT" || s.start2 == "IGN_L")
        val ignition2RightOn = dcPower && s.lever2 && cbClosed(s, "eng2-ign-right") && !s.faults.igniter2RightFailure && (s.start2 == "GRD" || s.start2 == "FLT" || s.start2 == "IGN_R")
        val ignition1On = ignition1LeftOn || ignition1RightOn
        val ignition2On = ignition2LeftOn || ignition2RightOn
        val externalDcControl = externalAc || tieSource1 || tieSource2
        val acPower = transferBus1Powered || transferBus2Powered
        val indicationPower = dcPower || externalDcControl

        d.eng1Running = eng1Running
        d.eng2Running = eng2Running
        d.apuAvailable = apuAvailable
        d.dcPower = dcPower
        d.acPower = acPower
        d.acMainBus1Powered = acMainBus1Powered
        d.acMainBus2Powered = acMainBus2Powered
        d.ac28vMainBus2Powered = ac28vMainBus2Powered
        d.transferBus1Powered = transferBus1Powered
        d.transferBus2Powered = transferBus2Powered
        d.dcBus1Powered = dcBus1Powered
        d.dcBus2Powered = dcBus2Powered
        d.tr1Powered = tr1Powered
        d.tr2Powered = tr2Powered
        d.tr3Powered = tr3Powered
        d.batteryBusPowered = batteryBusPowered
        d.ignition1On = ignition1On
        d.ignition2On = ignition2On
        d.externalPowerContactorClosed = externalPowerContactorClosed
        d.externalPowerNotInUse = externalPowerNotInUse
        d.groundPowerAvailable = groundPowerAvailable
        d.batteryPower = batteryPower
        d.generator1BusPowered = generator1BusPowered
        d.generator2BusPowered = generator2BusPowered
        d.generator1ThreePhaseHealthy = g1Healthy
        d.generator2ThreePhaseHealthy = g2Healthy
        d.transferRelay1Alt = transferRelay1Alt
        d.transferRelay2Alt = transferRelay2Alt
        d.hotBatteryBusPowered = batteryPower
        d.essentialDcPower = essentialDcPower
        d.externalDcControl = externalDcControl
        d.ignition1LeftOn = ignition1LeftOn
        d.ignition1RightOn = ignition1RightOn
        d.ignition2LeftOn = ignition2LeftOn
        d.ignition2RightOn = ignition2RightOn
        d.generatorDrive1Disconnected = gen1DriveDisconnected
        d.generatorDrive2Disconnected = gen2DriveDisconnected
        return ElecLocals(
            indicationPower = indicationPower,
            externalDcControl = externalDcControl,
            gen1DriveDisconnected = gen1DriveDisconnected,
            gen2DriveDisconnected = gen2DriveDisconnected,
            eng1AccessoryDriven = eng1AccessoryDriven,
            eng2AccessoryDriven = eng2AccessoryDriven,
            batteryPower = batteryPower,
            groundPowerAvailable = groundPowerAvailable,
            externalAcConnected = externalAcConnected,
            externalAc = externalAc,
            generator1BusPowered = generator1BusPowered,
            generator2BusPowered = generator2BusPowered,
            transferRelay1Alt = transferRelay1Alt,
            transferRelay2Alt = transferRelay2Alt,
            apuLeftSource = apuLeftSource,
            apuRightSource = apuRightSource,
            g1Healthy = g1Healthy,
            g2Healthy = g2Healthy,
            tieSource1 = tieSource1,
            tieSource2 = tieSource2,
        )
    }
}

/** Local (non-returned) values from the electrical section of derive.ts that other sections use. */
data class ElecLocals(
    val indicationPower: Boolean,
    val externalDcControl: Boolean,
    val gen1DriveDisconnected: Boolean,
    val gen2DriveDisconnected: Boolean,
    val eng1AccessoryDriven: Boolean,
    val eng2AccessoryDriven: Boolean,
    val batteryPower: Boolean,
    val groundPowerAvailable: Boolean,
    val externalAcConnected: Boolean,
    val externalAc: Boolean,
    val generator1BusPowered: Boolean,
    val generator2BusPowered: Boolean,
    val transferRelay1Alt: Boolean,
    val transferRelay2Alt: Boolean,
    val apuLeftSource: Boolean,
    val apuRightSource: Boolean,
    val g1Healthy: Boolean,
    val g2Healthy: Boolean,
    val tieSource1: Boolean,
    val tieSource2: Boolean,
)
