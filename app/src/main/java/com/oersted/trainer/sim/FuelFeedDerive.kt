package com.oersted.trainer.sim

/**
 * Port of the ATA 28 fuel-feed section of src/lib/sim/derive.ts: boost/center pumps, crossfeed,
 * engine fuel shutoff, feed pressure and source labels, low-pressure lights, scavenge flag.
 * Trainer pressures (32 / 5 psi) are copied from the TS, not Boeing values.
 */
object FuelFeedDerive {
    fun apply(s: SimState, d: Derived) {
        val acPower = d.acPower
        val dcPower = d.dcPower

        val fuelPump1Selected = s.fuelPump1Fwd || s.fuelPump1Aft
        val fuelPump2Selected = s.fuelPump2Fwd || s.fuelPump2Aft
        val fuelCenterSelected = s.fuelPumpCenter1 || s.fuelPumpCenter2

        // Each P5 boost-pump switch feeds an individual pump. Legacy tank-level faults fail both pumps in a tank.
        d.fuelPump1FwdOn = acPower && s.fuelPump1Fwd && !s.faults.fuelBoostPump1Fail && !s.faults.fuelTank1FwdPumpFail && s.fuelTank1 > 0.5
        d.fuelPump1AftOn = acPower && s.fuelPump1Aft && !s.faults.fuelBoostPump1Fail && !s.faults.fuelTank1AftPumpFail && s.fuelTank1 > 0.5
        d.fuelPump2FwdOn = acPower && s.fuelPump2Fwd && !s.faults.fuelBoostPump2Fail && !s.faults.fuelTank2FwdPumpFail && s.fuelTank2 > 0.5
        d.fuelPump2AftOn = acPower && s.fuelPump2Aft && !s.faults.fuelBoostPump2Fail && !s.faults.fuelTank2AftPumpFail && s.fuelTank2 > 0.5
        d.fuelCenterPump1On = acPower && s.fuelPumpCenter1 && !s.faults.fuelCenterPumpFail && !s.faults.fuelCenterPump1Fail && !s.fuelCenterPump1RelayTripped && s.fuelTankCenter > 0.5
        d.fuelCenterPump2On = acPower && s.fuelPumpCenter2 && !s.faults.fuelCenterPumpFail && !s.faults.fuelCenterPump2Fail && !s.fuelCenterPump2RelayTripped && s.fuelTankCenter > 0.5
        d.fuelPump1On = d.fuelPump1FwdOn || d.fuelPump1AftOn
        d.fuelPump2On = d.fuelPump2FwdOn || d.fuelPump2AftOn
        d.fuelCenterPumpOn = d.fuelCenterPump1On || d.fuelCenterPump2On
        d.fuelPump1FwdLowPressure = s.fuelPump1Fwd && !d.fuelPump1FwdOn
        d.fuelPump1AftLowPressure = s.fuelPump1Aft && !d.fuelPump1AftOn
        d.fuelPump2FwdLowPressure = s.fuelPump2Fwd && !d.fuelPump2FwdOn
        d.fuelPump2AftLowPressure = s.fuelPump2Aft && !d.fuelPump2AftOn
        d.fuelCenterPump1LowPressure = s.fuelPumpCenter1 && !d.fuelCenterPump1On
        d.fuelCenterPump2LowPressure = s.fuelPumpCenter2 && !d.fuelCenterPump2On
        // crossfeedOpen is a TS local (not returned); it is recomputed here for the feed logic.
        val crossfeedOpen = dcPower && s.fuelCrossfeed && !s.faults.fuelCrossfeedValveFail

        // Engine fuel shutoff valves: fire switch and start-lever cutoff close them.
        d.fuelShutoff1Open = dcPower && s.lever1 && !s.engine1FirePulled
        d.fuelShutoff2Open = dcPower && s.lever2 && !s.engine2FirePulled
        val own1Pressure = if (d.fuelPump1On) 32.0 else if (s.fuelTank1 > 0.5) 5.0 else 0.0
        val own2Pressure = if (d.fuelPump2On) 32.0 else if (s.fuelTank2 > 0.5) 5.0 else 0.0
        val centerPressure = if (d.fuelCenterPumpOn) 32.0 else 0.0
        val feed1Candidates = if (crossfeedOpen) listOf(own1Pressure, own2Pressure, centerPressure) else listOf(own1Pressure, centerPressure)
        val feed2Candidates = if (crossfeedOpen) listOf(own2Pressure, own1Pressure, centerPressure) else listOf(own2Pressure, centerPressure)
        d.fuelFeedPressure1 = if (d.fuelShutoff1Open) feed1Candidates.max() else 0.0
        d.fuelFeedPressure2 = if (d.fuelShutoff2Open) feed2Candidates.max() else 0.0

        // Labels follow what fuelDrawSource() draws: center first, then crossfeed, then own tank.
        val cf = crossfeedOpen
        d.fuelFeedSource1 = when {
            d.fuelFeedPressure1 <= 0 -> "SHUTOFF"
            d.fuelCenterPumpOn -> "CENTER BOOST"
            cf && d.fuelPump1On && d.fuelPump2On -> "TANKS 1+2 CROSSFEED"
            cf && d.fuelPump2On && !d.fuelPump1On -> "TANK 2 CROSSFEED"
            d.fuelPump1On -> "TANK 1 BOOST"
            else -> "TANK 1 SUCTION"
        }
        d.fuelFeedSource2 = when {
            d.fuelFeedPressure2 <= 0 -> "SHUTOFF"
            d.fuelCenterPumpOn -> "CENTER BOOST"
            cf && d.fuelPump1On && d.fuelPump2On -> "TANKS 1+2 CROSSFEED"
            cf && d.fuelPump1On && !d.fuelPump2On -> "TANK 1 CROSSFEED"
            d.fuelPump2On -> "TANK 2 BOOST"
            else -> "TANK 2 SUCTION"
        }

        // ATA 28-42: main-tank low-pressure lights follow pump pressure switches; center lights go out when center pumps are OFF.
        d.fuelLowPressure1 = fuelPump1Selected && !d.fuelPump1On
        d.fuelLowPressure2 = fuelPump2Selected && !d.fuelPump2On
        d.fuelCenterLowPressure = fuelCenterSelected && !d.fuelCenterPumpOn
        d.fuelScavengeActive = s.fuelScavengeTimer > 0 && s.fuelScavengeTimer < 1200 && d.fuelPump1On && !d.fuelCenterPumpOn && s.fuelTankCenter > 0.5
        d.fuelQuantityIndicationPowered = dcPower
    }
}
