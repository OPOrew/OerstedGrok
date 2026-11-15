package com.oersted.trainer.sim

/** Result of one pack's derivePack() call (mirrors the object returned by the TS closure). */
data class PackResult(
    val inletPsi: Double, val flowRate: Double, val flowLbsMin: Double,
    val coldTempF: Double, val sensorTempF: Double, val hotBypassRate: Double, val warmAirTempF: Double,
    val coldAirFlow: Double, val warmAirFlow: Double, val mixedAirFlow: Double, val airMixValve: Double,
    val mixTempF: Double, val mixingChamberTempF: Double, val compressorDischargeTempF: Double,
    val turbineInletTempF: Double, val turbineDischargeTempF: Double,
    val compressorOverheat: Boolean, val turbineOverheat: Boolean, val packOverheatRelay: Boolean,
    val ductOverheat: Boolean, val ductLimitDemand: Boolean, val packTrip250: Boolean,
    val mixValveHotLimit: Boolean, val mixValveColdLimit: Boolean,
    val hotBypassFlow: Double, val coolingPathFlow: Double, val f35BypassFlow: Double,
    val acmColdFlow: Double, val separatorFlow: Double,
    val f35SensorFailure: Boolean, val f35ValveFailure: Boolean, val packTrip: Boolean,
    val turbofanValve: Double,
    val stagePackValve: Boolean, val stagePrimaryHx: Boolean, val stageAcmCompressor: Boolean,
    val stageSecondaryHx: Boolean, val stageTurbine: Boolean, val stageWaterSeparator: Boolean,
    val stageF35Valve: Boolean, val stageHotBypass: Boolean, val stageColdCheckValve: Boolean, val stageHotCheckValve: Boolean,
)

/**
 * Port of the ATA 21 pack model in src/lib/sim/derive.ts (derivePack + left/right pack outputs +
 * cabin supply). Thermal set-points and flow schedules are trainer values copied from the TS.
 * Pack pressure/flow thresholds: open at 18 psi, full flow at 46 psi (18 + 28).
 */
object PackDerive {
    private fun clamp01(v: Double) = maxOf(0.0, minOf(1.0, v))

    @Suppress("LongParameterList")
    fun derivePack(
        ductPsi: Double,
        packMode: String,
        trip: Boolean,
        apuSource: Boolean,
        ground: Boolean,
        flaps: Double,
        groundSpeedKt: Double,
        mix: Double,
        tempFault: String = "NONE",
        tempMode: String = "MANUAL",
        tempSelectF: Double = 75.0,
        cabinTempF: Double = 75.0,
        ambientF: Double = 75.0,
        controlPower: Boolean = true,
        mixValveJammed: Boolean = false,
    ): PackResult {
        val packOn = packMode != "OFF"
        val ambientFlowBias = clamp01((75 - ambientF) / 40) * 0.06
        val flowSchedule = if (apuSource) 1.05 + ambientFlowBias * 0.5 else 1.0 + ambientFlowBias * 0.3
        val commandedInletPsi = if (packOn && !trip) maxOf(0.0, ductPsi) else 0.0
        val packValveInitiallyOpen = commandedInletPsi >= 18
        val packFlowLbsPerUnit = 68.0
        val totalFlowNorm = if (packValveInitiallyOpen) clamp01((commandedInletPsi - 18) / 28) * flowSchedule else 0.0
        val totalFlow = totalFlowNorm
        val totalFlowLbsMin = totalFlowNorm * packFlowLbsPerUnit

        // ATA 21 air mix valve: AUTO uses the duct-limit-capped regulator bridge approximation.
        var airMixValve: Double = when (tempMode) {
            "AUTO" -> minOf(0.31, clamp01(0.12 + (tempSelectF - cabinTempF) * 0.045))
            "MANUAL_COOL" -> 0.12
            "MANUAL_WARM" -> 0.82
            "OFF" -> 0.0
            else -> clamp01(mix)
        }
        if (tempFault == "STUCK_COLD") airMixValve = 0.0
        if (tempFault == "STUCK_HOT") airMixValve = 1.0
        if (tempFault == "SENSOR_FAIL" || tempFault == "REGULATOR_FAIL") {
            airMixValve = if (tempMode == "AUTO") clamp01(mix) else airMixValve
        }
        if (mixValveJammed) airMixValve = clamp01(mix)
        if (!packValveInitiallyOpen || !controlPower) airMixValve = 0.0

        val mixValveHotLimit = airMixValve >= 0.995
        val mixValveColdLimit = airMixValve <= 0.005
        val hotBypassFlow = totalFlow * airMixValve
        val coolingPathFlow = totalFlow * (1 - airMixValve)

        // ATA 21-52 turbofan / ram-air sequence is discrete.
        val coolingActive = coolingPathFlow > 0.05
        val turbofanFullOpen = if (ground && coolingActive) 1.0 else 0.0
        val turbofanIntermediate = if (!ground && flaps > 0.02 && coolingActive) 0.40 else 0.0
        val turbofanValve = maxOf(turbofanFullOpen, turbofanIntermediate)
        val ramCooling = clamp01(groundSpeedKt / 220)
        val coolingEffect = clamp01(0.28 + ramCooling * 0.38 + turbofanValve * 0.32 + (if (ground) 0.04 else 0.0))

        // Cold path: primary HX -> ACM compressor -> secondary HX -> turbine.
        val primaryHx = coolingPathFlow > 0.05
        val primaryHxOutTempF = if (primaryHx) maxOf(90.0, 150 - coolingEffect * 55 + (ambientF - 75) * 0.10) else 0.0
        val acmCompressor = primaryHx
        val compressorDischargeTempF = if (acmCompressor) primaryHxOutTempF + 62 + commandedInletPsi * 0.78 else 0.0
        val secondaryHx = acmCompressor
        val turbineInletTempF = if (secondaryHx) compressorDischargeTempF - (42 + coolingEffect * 34) else 0.0

        val forcedCompressorOverheat = tempFault == "COMPRESSOR_OVERHEAT"
        val forcedTurbineOverheat = tempFault == "TURBINE_OVERHEAT"
        val compressorOverheat = forcedCompressorOverheat || compressorDischargeTempF >= 365
        val turbineOverheat = forcedTurbineOverheat || turbineInletTempF >= 210
        val packOverheatRelay = compressorOverheat || turbineOverheat

        val turbine = secondaryHx && !packOverheatRelay
        val turbineDischargeTempF = if (turbine) maxOf(5.0, 20 + (1 - coolingEffect) * 34 + (1 - coolingPathFlow) * 10) else 0.0
        val waterSeparator = turbine

        // 35 F system: bypasses the ACM with warm primary-HX air to prevent separator icing.
        val f35TargetF = 38.5
        val f35SensorFailure = tempFault == "F35_SENSOR_FAIL"
        val f35ValveFailure = tempFault == "F35_VALVE_FAIL"
        var f35BypassRate = 0.0
        if (waterSeparator && !f35SensorFailure && controlPower) {
            val delta = f35TargetF - turbineDischargeTempF
            val warmDelta = maxOf(1.0, primaryHxOutTempF - f35TargetF)
            f35BypassRate = clamp01(if (delta > 0) delta / warmDelta else 0.0)
        }
        if (f35ValveFailure) f35BypassRate = 0.0
        val f35Valve = waterSeparator && f35BypassRate > 0.01
        val f35BypassFlow = if (f35Valve) coolingPathFlow * f35BypassRate else 0.0
        val acmColdFlow = maxOf(0.0, coolingPathFlow - f35BypassFlow)
        val f35BypassTempF = if (primaryHx) primaryHxOutTempF else 0.0
        val separatorFlow = acmColdFlow + f35BypassFlow
        val separatorSensorTempF = if (separatorFlow > 0)
            ((turbineDischargeTempF * acmColdFlow) + (f35BypassTempF * f35BypassFlow)) / separatorFlow else 0.0
        val coldTempF = separatorSensorTempF

        // Hot bypass joins cold air only at the mixing chamber (regulated pneumatic air ~370 F).
        val warmAirTempF = if (packValveInitiallyOpen) 370.0 else 0.0
        val coldAirFlow = separatorFlow
        val warmAirFlow = hotBypassFlow
        val mixedAirFlow = coldAirFlow + warmAirFlow
        val rawMixTempF = if (mixedAirFlow > 0)
            ((coldTempF * coldAirFlow) + (warmAirTempF * warmAirFlow)) / mixedAirFlow else 0.0

        // 190 F duct switch drives the mix valve full cold; 250 F pack overheat relay trips the pack.
        val ductOverheat190 = tempFault == "DUCT_OVERHEAT" || rawMixTempF >= 190
        val packTrip250 = tempFault == "PACK_OVERHEAT" || tempFault == "DUCT_OVERHEAT_250" || rawMixTempF >= 250
        val finalMixValve = if (ductOverheat190) 0.0 else airMixValve
        val mixingChamberTempF = if (mixedAirFlow > 0)
            (if (ductOverheat190) ((coldTempF * totalFlow) + (warmAirTempF * 0)) / maxOf(totalFlow, 0.0001) else rawMixTempF)
        else 0.0

        val effectivePackTrip = trip || packOverheatRelay || packTrip250
        val finalPackValve = packValveInitiallyOpen && !effectivePackTrip
        val finalFlowRate = if (finalPackValve) totalFlow else 0.0
        val finalFlowLbsMin = if (finalPackValve) totalFlowLbsMin else 0.0
        val finalColdAirFlow = if (finalPackValve) coldAirFlow else 0.0
        val finalWarmAirFlow = if (finalPackValve) warmAirFlow else 0.0
        val finalMixedAirFlow = if (finalPackValve) mixedAirFlow else 0.0

        return PackResult(
            inletPsi = if (finalPackValve) commandedInletPsi else 0.0,
            flowRate = finalFlowRate,
            flowLbsMin = finalFlowLbsMin,
            coldTempF = if (finalPackValve) coldTempF else 0.0,
            sensorTempF = if (finalPackValve) separatorSensorTempF else 0.0,
            hotBypassRate = if (finalPackValve) f35BypassRate else 0.0,
            warmAirTempF = if (finalPackValve) warmAirTempF else 0.0,
            coldAirFlow = finalColdAirFlow,
            warmAirFlow = finalWarmAirFlow,
            mixedAirFlow = finalMixedAirFlow,
            airMixValve = if (finalPackValve) finalMixValve else 0.0,
            mixTempF = if (finalPackValve) (if (ductOverheat190) coldTempF else rawMixTempF) else 0.0,
            mixingChamberTempF = if (finalPackValve) mixingChamberTempF else 0.0,
            compressorDischargeTempF = compressorDischargeTempF,
            turbineInletTempF = turbineInletTempF,
            turbineDischargeTempF = turbineDischargeTempF,
            compressorOverheat = compressorOverheat,
            turbineOverheat = turbineOverheat,
            packOverheatRelay = packOverheatRelay,
            ductOverheat = ductOverheat190,
            ductLimitDemand = ductOverheat190 || rawMixTempF >= 140,
            packTrip250 = packTrip250,
            mixValveHotLimit = finalPackValve && finalMixValve >= 0.995,
            mixValveColdLimit = finalPackValve && finalMixValve <= 0.005,
            hotBypassFlow = if (finalPackValve) hotBypassFlow else 0.0,
            coolingPathFlow = if (finalPackValve) coolingPathFlow else 0.0,
            f35BypassFlow = if (finalPackValve) f35BypassFlow else 0.0,
            acmColdFlow = if (finalPackValve) acmColdFlow else 0.0,
            separatorFlow = if (finalPackValve) separatorFlow else 0.0,
            f35SensorFailure = f35SensorFailure,
            f35ValveFailure = f35ValveFailure,
            packTrip = effectivePackTrip,
            turbofanValve = if (finalPackValve) turbofanValve else 0.0,
            stagePackValve = finalPackValve,
            stagePrimaryHx = finalPackValve && primaryHx,
            stageAcmCompressor = finalPackValve && acmCompressor,
            stageSecondaryHx = finalPackValve && secondaryHx,
            stageTurbine = finalPackValve && turbine,
            stageWaterSeparator = finalPackValve && waterSeparator,
            stageF35Valve = finalPackValve && f35Valve,
            stageHotBypass = finalPackValve && hotBypassFlow > 0.01,
            stageColdCheckValve = finalPackValve && coldAirFlow > 0.01,
            stageHotCheckValve = finalPackValve && warmAirFlow > 0.01,
        )
    }

    /** Left and right packs plus cabin supply, mirroring the TS block after derivePack. */
    fun apply(s: SimState, d: Derived) {
        val cb = { id: String -> ElectricalDerive.cbClosed(s, id) }
        val apuBleedLeft = d.apuAvailable && s.apuBleed && s.apuBleedValve > 0.7
        val ground = s.wow

        val left = derivePack(
            d.leftDuctPsi, if (s.packL) "AUTO" else "OFF", s.packTripL, apuBleedLeft, ground, s.flaps, s.groundSpeedKt,
            s.mixCabin, s.tempControlFaultL ?: "NONE", s.tempModeL ?: "MANUAL", s.tempSelectL ?: 75.0,
            s.controlCabinTempF ?: 75.0, s.ambientTempF ?: 75.0, d.acPower, s.faults.mixValveJamL || !cb("mix-valve"),
        )
        val right = derivePack(
            d.rightDuctPsi, if (s.packR) "AUTO" else "OFF", s.packTripR, false, ground, s.flaps, s.groundSpeedKt,
            s.mixPass, s.tempControlFaultR ?: "NONE", s.tempModeR ?: "MANUAL", s.tempSelectR ?: 75.0,
            s.passengerCabinTempF ?: 75.0, s.ambientTempF ?: 75.0, d.acPower, s.faults.mixValveJamR || !cb("mix-valve"),
        )
        d.packLFlow = left.flowRate > 0.05
        d.packRFlow = right.flowRate > 0.05
        putSide(d, true, left)
        putSide(d, false, right)

        // ATA 21-21: left pack -> control cabin, right pack -> passenger cabin; the surviving pack feeds both.
        val packLFlow = d.packLFlow
        val packRFlow = d.packRFlow
        d.groundConditionedAirActive = s.groundConditionedAirConnected && s.groundConditionedAirPsi > 0 && !packLFlow && !packRFlow
        val groundConditionedAirFlow = if (d.groundConditionedAirActive) 0.65 else 0.0
        val controlCabinSupplyFlow = when {
            packLFlow && packRFlow -> left.flowRate
            packLFlow -> left.flowRate
            packRFlow -> right.flowRate
            else -> groundConditionedAirFlow
        }
        val passengerCabinSupplyFlow = when {
            packLFlow && packRFlow -> right.flowRate
            packLFlow -> left.flowRate
            packRFlow -> right.flowRate
            else -> groundConditionedAirFlow
        }
        d.cabinSupplyFlow = clamp01(maxOf(controlCabinSupplyFlow, passengerCabinSupplyFlow))
        d.controlCabinSupplyTempF = when {
            packLFlow && packRFlow -> left.mixingChamberTempF
            packLFlow -> left.mixingChamberTempF
            packRFlow -> right.mixingChamberTempF
            d.groundConditionedAirActive -> s.ambientTempF ?: 75.0
            else -> 0.0
        }
        d.passengerCabinSupplyTempF = when {
            packLFlow && packRFlow -> right.mixingChamberTempF
            packLFlow -> left.mixingChamberTempF
            packRFlow -> right.mixingChamberTempF
            d.groundConditionedAirActive -> s.ambientTempF ?: 75.0
            else -> 0.0
        }
        d.cabinExhaustFlow = clamp01(s.outflow)
        d.acSupplyLeft = controlCabinSupplyFlow > 0.01
        d.acSupplyRight = passengerCabinSupplyFlow > 0.01
    }

    private fun putSide(d: Derived, l: Boolean, p: PackResult) {
        if (l) {
            d.packLFlowRate = p.flowRate
            d.packLInletPsi = p.inletPsi
            d.packLColdTempF = p.coldTempF
            d.packL35SensorTempF = p.sensorTempF
            d.packLHotBypassRate = p.hotBypassRate
            d.packLWarmAirTempF = p.warmAirTempF
            d.packLColdAirFlow = p.coldAirFlow
            d.packLWarmAirFlow = p.warmAirFlow
            d.packLMixedAirFlow = p.mixedAirFlow
            d.packLAirMixValve = p.airMixValve
            d.packLMixTempF = p.mixTempF
            d.packLMixingChamberTempF = p.mixingChamberTempF
            d.packLCompressorDischargeTempF = p.compressorDischargeTempF
            d.packLTurbineInletTempF = p.turbineInletTempF
            d.packLCompressorOverheat = p.compressorOverheat
            d.packLTurbineOverheat = p.turbineOverheat
            d.packLPackOverheatRelay = p.packOverheatRelay
            d.packLDuctOverheat = p.ductOverheat
            d.packLDuctLimitDemand = p.ductLimitDemand
            d.packLMixValveHotLimit = p.mixValveHotLimit
            d.packLMixValveColdLimit = p.mixValveColdLimit
            d.packLHotBypassFlow = p.hotBypassFlow
            d.packLCoolingPathFlow = p.coolingPathFlow
            d.packLF35BypassFlow = p.f35BypassFlow
            d.packLAcColdFlow = p.acmColdFlow
            d.packLSeparatorFlow = p.separatorFlow
            d.packLTripActive = p.packTrip
            d.packLTurbineDischargeTempF = p.turbineDischargeTempF
            d.packLF35SensorFailure = p.f35SensorFailure
            d.packLF35ValveFailure = p.f35ValveFailure
            d.packLPackTrip250 = p.packTrip250
            d.packLTurbofanValve = p.turbofanValve
            d.packLStages = DerivedPackLStages().also {
                it.packValve = p.stagePackValve; it.primaryHx = p.stagePrimaryHx; it.acmCompressor = p.stageAcmCompressor
                it.secondaryHx = p.stageSecondaryHx; it.turbine = p.stageTurbine; it.waterSeparator = p.stageWaterSeparator
                it.f35Valve = p.stageF35Valve; it.mixValve = p.stagePackValve; it.hotBypass = p.stageHotBypass
                it.coldCheckValve = p.stageColdCheckValve; it.hotCheckValve = p.stageHotCheckValve
            }
        } else {
            d.packRFlowRate = p.flowRate
            d.packRInletPsi = p.inletPsi
            d.packRColdTempF = p.coldTempF
            d.packR35SensorTempF = p.sensorTempF
            d.packRHotBypassRate = p.hotBypassRate
            d.packRWarmAirTempF = p.warmAirTempF
            d.packRColdAirFlow = p.coldAirFlow
            d.packRWarmAirFlow = p.warmAirFlow
            d.packRMixedAirFlow = p.mixedAirFlow
            d.packRAirMixValve = p.airMixValve
            d.packRMixTempF = p.mixTempF
            d.packRMixingChamberTempF = p.mixingChamberTempF
            d.packRCompressorDischargeTempF = p.compressorDischargeTempF
            d.packRTurbineInletTempF = p.turbineInletTempF
            d.packRCompressorOverheat = p.compressorOverheat
            d.packRTurbineOverheat = p.turbineOverheat
            d.packRPackOverheatRelay = p.packOverheatRelay
            d.packRDuctOverheat = p.ductOverheat
            d.packRDuctLimitDemand = p.ductLimitDemand
            d.packRMixValveHotLimit = p.mixValveHotLimit
            d.packRMixValveColdLimit = p.mixValveColdLimit
            d.packRHotBypassFlow = p.hotBypassFlow
            d.packRCoolingPathFlow = p.coolingPathFlow
            d.packRF35BypassFlow = p.f35BypassFlow
            d.packRAcmColdFlow = p.acmColdFlow
            d.packRSeparatorFlow = p.separatorFlow
            d.packRTripActive = p.packTrip
            d.packRTurbineDischargeTempF = p.turbineDischargeTempF
            d.packRF35SensorFailure = p.f35SensorFailure
            d.packRF35ValveFailure = p.f35ValveFailure
            d.packRPackTrip250 = p.packTrip250
            d.packRTurbofanValve = p.turbofanValve
            d.packRStages = DerivedPackRStages().also {
                it.packValve = p.stagePackValve; it.primaryHx = p.stagePrimaryHx; it.acmCompressor = p.stageAcmCompressor
                it.secondaryHx = p.stageSecondaryHx; it.turbine = p.stageTurbine; it.waterSeparator = p.stageWaterSeparator
                it.f35Valve = p.stageF35Valve; it.mixValve = p.stagePackValve; it.hotBypass = p.stageHotBypass
                it.coldCheckValve = p.stageColdCheckValve; it.hotCheckValve = p.stageHotCheckValve
            }
        }
    }
}
