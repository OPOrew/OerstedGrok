package com.oersted.trainer.sim

/**
 * Port of the second part of step() in src/lib/sim/derive.ts (TS ~1905-2475): engine and APU fire
 * pulls, wheel-well and cargo fire, window heat test, center-tank scavenge, APU start/oil/EGT/bleed,
 * the two engine start/spool sequences with their oil-pressure coupling, fuel draw from the tanks,
 * hydraulic reservoirs and pressures, and the automatic / landing autobrake state.
 *
 * Mirrors the TS order. Where the TS calls derive(n) again mid-tick, this calls
 * SimDerive.computeAll(n) at the same point. `d` is the start-of-tick derived view.
 */
internal object StepPart2 {
    /** Result of one engine's start/spool update (TS runEngine return value). */
    data class EngineNext(
        val nextValve: Boolean,
        val nextN2: Double,
        val nextN1: Double,
        val nextEgt: Double,
        val nextFf: Double,
        val nextOilPsi: Double,
    )

    /** Result of [autobrakeAndBrakeState]: the mid-tick derived views used by part 3. */
    data class Part2Result(val d2: Derived, val brakeState: Derived)

    private fun cb(n: SimState, id: String): Boolean = n.circuitBreakers[id] == true

    private fun approach(v: Double, t: Double, r: Double, dt: Double) = SimMath.approach(v, t, r, dt)

    /** TS engine/APU fire-pull blocks that run right after the attitude model. */
    fun firePulledBlocks(n: SimState) {
        if (n.apuFirePulled) {
            n.apuMaster = "OFF"
            n.apuBleed = false
            n.apuGen = false
            n.apuLeftControl = false
            n.apuRightControl = false
        }
        if (n.engine1FirePulled) {
            n.lever1 = false
            n.gen1 = false
            n.bleed1 = false
            n.engHyd1 = false
            n.start1 = "OFF"
        }
        if (n.engine2FirePulled) {
            n.lever2 = false
            n.gen2 = false
            n.bleed2 = false
            n.engHyd2 = false
            n.start2 = "OFF"
        }
    }

    /** Wheel-well fire, cargo fire, window heat OVHT latch, and the center-tank scavenge transfer. */
    fun wheelWellCargoScavenge(n: SimState, d: Derived, dt: Double) {
        // Wheel well: fire builds with the gear retracted and is starved / cooled once the gear is down.
        if (n.wheelWellFire) {
            val geared = (n.noseGearPos + n.leftMainGearPos + n.rightMainGearPos) / 3.0
            if (geared < 0.3) n.wheelWellLevel = minOf(1.0, n.wheelWellLevel + 0.04 * dt)
            else if (geared > 0.9) n.wheelWellLevel = maxOf(0.0, n.wheelWellLevel - 0.1 * dt)
            if (n.wheelWellLevel <= 0.02 && geared > 0.9) {
                n.wheelWellFire = false
                n.wheelWellLevel = 0.0
            }
        } else if (n.wheelWellLevel > 0) {
            n.wheelWellLevel = maxOf(0.0, n.wheelWellLevel - 0.2 * dt)
        }

        // Cargo (effectivity-dependent, SB 26A1081): dual-loop detection + single bottle.
        if (n.cargoBottleDischarged) n.cargoAgentTimer += dt
        for (fwd in listOf(true, false)) {
            val armedHere = n.cargoFireInstalled && n.cargoArmed == (if (fwd) "FWD" else "AFT")
            var fire = if (fwd) n.cargoFwdFire else n.cargoAftFire
            var level = if (fwd) n.cargoFwdLevel else n.cargoAftLevel
            if (fire) {
                if (n.cargoBottleDischarged && armedHere) {
                    // Agent suppresses the fire; residual smoke clears more slowly after ~30 s.
                    val agentRate = if (n.cargoAgentTimer < 30) 0.12 else 0.03
                    level = maxOf(0.02, level - agentRate * dt)
                    if (level <= 0.08 && n.cargoAgentTimer > 8) fire = false
                } else {
                    level = minOf(1.0, level + 0.015 * dt)
                }
            } else if (level > 0) {
                level = maxOf(0.0, level - 0.04 * dt)
            }
            if (fwd) {
                n.cargoFwdFire = fire
                n.cargoFwdLevel = level
            } else {
                n.cargoAftFire = fire
                n.cargoAftLevel = level
            }
        }
        if (!n.cargoFireInstalled) {
            n.cargoArmed = "NONE"
            n.cargoBottleDischarged = false
        }

        // Window heat P5-9 (ATA 30-41): OVHT test latches overheat on each window that is ON.
        if (n.windowHeatTest == "OVHT") {
            if (n.windowHeatLFwd) n.windowHeatOvhtLFwd = true
            if (n.windowHeatLSide) n.windowHeatOvhtLSide = true
            if (n.windowHeatRFwd) n.windowHeatOvhtRFwd = true
            if (n.windowHeatRSide) n.windowHeatOvhtRSide = true
        }

        // ATA 28-15 center-tank scavenge: trainer-normalized transfer into tank No. 1.
        val centerPumpsOff = !n.fuelPumpCenter1 && !n.fuelPumpCenter2
        if (!centerPumpsOff || n.fuelTankCenter <= 0.5 || !d.fuelPump1On) n.fuelScavengeTimer = 0.0
        else if (n.fuelScavengeTimer <= 0) n.fuelScavengeTimer = 0.01
        if (n.fuelScavengeTimer > 0 && n.fuelScavengeTimer < 1200 && d.fuelPump1On && n.fuelTankCenter > 0.5) {
            n.fuelScavengeTimer = minOf(1200.0, n.fuelScavengeTimer + dt)
            val transfer = minOf(n.fuelTankCenter, 0.015 * dt)
            n.fuelTankCenter -= transfer
            n.fuelTank1 = minOf(100.0, n.fuelTank1 + transfer)
        }
    }

    /** ATA 49-11-0 APU: control power, fuel valve, inlet door, starter, RPM, timers, EGT, oil, bleed. */
    fun apu(n: SimState, d: Derived, dt: Double) {
        val apuControlPower = d.batteryPower ||
            (d.apuAvailable && n.apuGen && !n.faults.apuGeneratorBreakerFailure && !n.apuFirePulled)
        val wantFuel = if (n.apuMaster != "OFF" && !n.apuFirePulled && apuControlPower) 1.0 else 0.0
        n.apuFuelValve = approach(n.apuFuelValve, wantFuel, 1.2, dt)

        val apuFuelValveClosed = n.apuFuelValve <= 0.02
        val doorTarget = when {
            n.apuFirePulled -> 0.0
            n.apuFuelValve > 0.98 && n.apuMaster != "OFF" -> if (n.faults.apuDoorJam) 0.35 else 1.0
            apuFuelValveClosed -> 0.0
            else -> n.apuDoor
        }
        n.apuDoor = approach(n.apuDoor, doorTarget, 0.1, dt) // ~10 s inlet door travel

        val apuStarterElectrical = d.batteryPower
        val apuStarterCutoutPercent = if (n.apuStart50PercentEffectivityInstalled) 50.0 else 35.0
        val apuStartTimeoutSeconds = if (n.apuStart50PercentEffectivityInstalled) 90.0 else 60.0
        val starter = n.apuMaster == "START" && n.apuDoor > 0.98 && n.apuRpm < apuStarterCutoutPercent && apuStarterElectrical
        n.apuStarter = starter

        var rpmTarget = 0.0
        val lightoffPermitted = n.apuOilPsi >= 4.5
        val lowOilRunningHold = n.faults.apuLowOilPressure && n.apuRpm >= 95
        if (n.apuMaster != "OFF" && n.apuFuelValve > 0.5 && n.apuDoor > 0.98 &&
            (lightoffPermitted || lowOilRunningHold) && !n.faults.apuOverspeed && !n.faults.apuHighOilTemperature) {
            rpmTarget = if (starter || n.apuRpm > 12) 100.0 else n.apuRpm
        }
        if (starter) n.apuRpm = approach(n.apuRpm, 55.0, 2.5, dt)
        else if (n.apuRpm > 12 && n.apuMaster != "OFF") n.apuRpm = approach(n.apuRpm, rpmTarget, 4.0, dt)
        else n.apuRpm = approach(n.apuRpm, 0.0, 3.0, dt)

        if (n.apuMaster == "START" && n.apuRpm >= apuStarterCutoutPercent) n.apuMaster = "ON"
        val apuStartAttemptActive = n.apuDoor > 0.98 &&
            (n.apuMaster == "START" || (n.apuMaster == "ON" && n.apuStartTimer > 0 && n.apuRpm < 95))
        if (apuStartAttemptActive && n.apuRpm < 95) n.apuStartTimer += dt
        else if (!apuStartAttemptActive || n.apuRpm >= 95) n.apuStartTimer = 0.0
        if (n.apuStartTimer >= apuStartTimeoutSeconds && n.apuRpm < 95) {
            n.apuMaster = "OFF"
            n.apuStarter = false
            n.apuStartTimer = 0.0
        }
        // ATA 49-00-00: low-oil indication near 45 psi; automatic shutdown follows in 10 +/- 2 s.
        if (n.apuRpm >= 95 && n.apuOilPsi <= 45) n.apuLowOilShutdownTimer += dt
        else n.apuLowOilShutdownTimer = 0.0
        if (n.apuRpm >= 95 && (n.faults.apuOverspeed || n.faults.apuHighOilTemperature || n.faults.apuFireDetected || n.apuLowOilShutdownTimer >= 10)) {
            n.apuMaster = "OFF"
            n.apuStarter = false
            n.apuBleed = false
            n.apuGen = false
            n.apuLeftControl = false
            n.apuRightControl = false
            n.apuStartTimer = 0.0
        }

        // Trainer EGT assumption: modest light-off hump near 70% rpm, settles above 95%.
        val apuEgtHump = if (n.apuRpm > 8 && n.apuRpm < 95) 160.0 * maxOf(0.0, 1.0 - kotlin.math.abs(n.apuRpm - 70.0) / 30.0) else 0.0
        val apuCombustion = n.apuMaster != "OFF" && n.apuFuelValve > 0.98 &&
            lightoffPermitted && n.apuRpm > 8 && !n.apuFirePulled
        val apuEgtTarget = if (apuCombustion) 340.0 + n.apuBleedValve * 80.0 + (if (n.apuGen) 30.0 else 0.0) + apuEgtHump else 18.0
        n.apuEgt = approach(n.apuEgt, apuEgtTarget, 25.0, dt)

        // Trainer oil-pressure ramp during start (sequencing switch closes at ~4.5 psi).
        val apuOilTarget = when {
            n.faults.apuLowOilPressure -> 0.0
            n.apuRpm >= 75 -> 95.0
            n.apuRpm >= 35 -> 50.0
            n.apuRpm > 5 -> (n.apuRpm / 35.0) * 50.0
            else -> 0.0
        }
        n.apuOilPsi = approach(n.apuOilPsi, apuOilTarget, 8.0, dt)
        // Pressure switch hysteresis: on at 45 psi, off at 55 psi.
        if (n.apuMaster == "OFF") n.apuLowOilLightOn = false
        else if (n.apuOilPsi <= 45) n.apuLowOilLightOn = true
        else if (n.apuOilPsi >= 55) n.apuLowOilLightOn = false

        val bleedWant = if (d.apuAvailable && n.apuBleed) 1.0 else 0.0
        n.apuBleedValve = approach(n.apuBleedValve, bleedWant, 1.0 / 13.0, dt) // 12–14 s per AMM data
    }

    /** TS runEngine closure: one engine's start, light-off, hot-start and spool response. */
    fun runEngine(
        n: SimState,
        d: Derived,
        dt: Double,
        side: Int,
        start: String,
        lever: Boolean,
        n2: Double,
        n1: Double,
        egt: Double,
        ff: Double,
        sidePsi: Double,
        fuelPsi: Double,
        throttle: Double,
        ignitionLeftOn: Boolean,
        ignitionRightOn: Boolean,
    ): EngineNext {
        val ignitionOn = ignitionLeftOn || ignitionRightOn
        val psiOk = sidePsi >= 30
        val fuelAvailable = fuelPsi > 0.5
        // Ground start: GRD commands the pneumatic starter valve; L/R IGN do not open it.
        val startCommand = start == "GRD"
        val priorOnTime = if (side == 1) n.starterOnTime1 else n.starterOnTime2
        val priorCooldown = if (side == 1) n.starterCooldown1 else n.starterCooldown2
        val extendedUsed = if (side == 1) n.starterExtendedUsed1 else n.starterExtendedUsed2
        val abortTimer = if (side == 1) n.startAbortTimer1 else n.startAbortTimer2
        val hungTimer = if (side == 1) n.startHungTimer1 else n.startHungTimer2
        // ATA 71: re-engagement is not permitted above 20% N2 (a continuing run may cross 20%).
        val reengagementAllowed = priorOnTime > 0 || n2 <= 20
        val normalDutyAvailable = priorCooldown <= 0.01 && priorOnTime < 30
        val extendedDutyAvailable = priorCooldown <= 0.01 && priorOnTime < 60 && (extendedUsed || n2 < 35)
        val dutyAvailable = normalDutyAvailable || (priorOnTime >= 30 && extendedDutyAvailable)
        val wantValve = startCommand && psiOk && d.dcPower && n2 < 38 && dutyAvailable && reengagementAllowed
        val nextValve = wantValve || (abortTimer > 0 && psiOk && d.dcPower && n2 < 38)
        var nextN2 = n2
        var nextN1 = n1
        var nextEgt = egt
        var nextFf = ff
        var nextOilPsi = if (side == 1) n.oilPsi1 else n.oilPsi2
        // Trainer starter timing: N2 rises ~1.4 %/s; dry motoring cuts out at 38%.
        if (nextValve) nextN2 = approach(n2, 42.0, 1.4, dt)
        // Fuel and ignition are gated on established N2 rotation; combustion latches once fuel flows.
        val combustionEstablished = ff > 25 && n2 > 24
        val hotStart = lever && !combustionEstablished && n2 > 18 && (ff >= 1100 || egt >= 400)
        val startLightTimer = if (side == 1) n.startLightTimer1 else n.startLightTimer2
        if (lever && n2 >= 15 && !combustionEstablished && !hotStart) {
            val nextLightTimer = startLightTimer + dt
            if (side == 1) n.startLightTimer1 = nextLightTimer else n.startLightTimer2 = nextLightTimer
            // ATA 71: no light-off within 20 seconds is an abnormal start.
            if (nextLightTimer >= 20) {
                if (side == 1) { n.lever1 = false; n.startAbortTimer1 = 12.0 }
                else { n.lever2 = false; n.startAbortTimer2 = 12.0 }
            }
        } else if (combustionEstablished) {
            if (side == 1) n.startLightTimer1 = 0.0 else n.startLightTimer2 = 0.0
        }
        if (hotStart) {
            // ATA 71: CUTOFF and motor to clear fuel/vapor (12 s trainer timer = 10–15 s clearing).
            if (side == 1) { n.lever1 = false; n.startAbortTimer1 = 12.0 }
            else { n.lever2 = false; n.startAbortTimer2 = 12.0 }
        }
        if (combustionEstablished) {
            val nextHungTimer = hungTimer + dt
            if (side == 1) n.startHungTimer1 = nextHungTimer else n.startHungTimer2 = nextHungTimer
            // ATA 71: never more than 30 s from light-off to idle (trainer idle target 57% N2).
            if (n2 >= 56) {
                if (side == 1) n.startHungTimer1 = 0.0 else n.startHungTimer2 = 0.0
            } else if (nextHungTimer >= 30) {
                if (side == 1) { n.lever1 = false; n.startAbortTimer1 = 12.0 }
                else { n.lever2 = false; n.startAbortTimer2 = 12.0 }
            }
        } else if (!lever) {
            if (side == 1) n.startHungTimer1 = 0.0 else n.startHungTimer2 = 0.0
        }
        if (lever && n2 > 18 && fuelAvailable && (ignitionOn || combustionEstablished) && !hotStart) {
            // Light-off EGT hump around 30–40% N2; idle ~32% N1 / 57% N2.
            val egtHump = if (n2 > 20 && n2 < 48) 200.0 * maxOf(0.0, 1.0 - kotlin.math.abs(n2 - 32.0) / 16.0) else 0.0
            val thrust = maxOf(0.0, minOf(1.0, throttle))
            val n1Target = 32.0 + 60.0 * thrust
            val n2Target = 57.0 + 41.0 * thrust
            val ffTarget = 650.0 + 1250.0 * thrust
            // Above ~55% N2 the engine answers the lever like a turbine (fast, rounded response).
            val selfSustaining = n2 >= 55
            if (selfSustaining) {
                nextN2 = SimMath.spool(nextN2, n2Target, dt, SimMath.N2_ACCEL, SimMath.N2_DECEL)
                nextN1 = SimMath.spool(n1, n1Target, dt, if (thrust >= 0.95) SimMath.N1_ACCEL_TO else SimMath.N1_ACCEL, SimMath.N1_DECEL)
                nextFf = approach(ff, ffTarget + maxOf(0.0, n1Target - n1) * 9.0, 520.0, dt)
                val accelBump = maxOf(0.0, minOf(45.0, (n1Target - n1) * 1.1))
                nextEgt = approach(egt, 320.0 + n1 * 0.7 + thrust * 70.0 + accelBump, 110.0, dt)
            } else {
                nextFf = approach(ff, ffTarget, 180.0, dt)
                nextEgt = approach(egt, 320.0 + n1 * 0.7 + egtHump + thrust * 70.0, 60.0, dt)
                nextN2 = approach(nextN2, n2Target, 1.35, dt)
                nextN1 = approach(n1, n1Target, 0.9, dt)
            }
            nextOilPsi = approach(nextOilPsi, if (n2 >= 75) 95.0 else if (n2 >= 20) 50.0 else 0.0, 15.0, dt)
        } else {
            nextFf = approach(ff, 0.0, 400.0, dt)
            if (!nextValve) {
                // Coast-down / cool-down (~1 min at 1x).
                nextN2 = approach(n2, 0.0, 0.9, dt)
                nextN1 = approach(n1, 0.0, 0.6, dt)
                nextEgt = approach(egt, 12.0, 8.0, dt)
                nextOilPsi = approach(nextOilPsi, 0.0, 2.0, dt)
            }
        }
        // ATA 80/71: the starter cutout opens at 35–40% N2 and GRD returns to OFF automatically.
        if (startCommand && nextN2 >= 38) {
            if (side == 1) n.start1 = "OFF" else n.start2 = "OFF"
        }
        return EngineNext(nextValve, nextN2, nextN1, nextEgt, nextFf, nextOilPsi)
    }

    /** Engine 1 starter timers and state writes after runEngine (TS block following e1). */
    private fun tailEngine1(n: SimState, dt: Double, e: EngineNext, priorValve: Boolean, priorOnTime: Double) {
        n.startValve1 = e.nextValve
        n.starterOnTime1 = if (n.startValve1) priorOnTime + dt else 0.0
        if (n.startAbortTimer1 > 0) n.startAbortTimer1 = maxOf(0.0, n.startAbortTimer1 - dt)
        if (priorValve && !n.startValve1) {
            val extended = priorOnTime >= 30
            n.starterCooldown1 = if (extended) 300.0 else 60.0
            n.starterExtendedUsed1 = extended
        } else {
            n.starterCooldown1 = maxOf(0.0, n.starterCooldown1 - dt)
            if (n.starterCooldown1 <= 0.01 && !n.startValve1) n.starterExtendedUsed1 = false
        }
        if (n.starterOnTime1 >= 30 && !n.starterExtendedUsed1 && n.n2_1 < 35) n.starterExtendedUsed1 = true
        if (n.startAbortTimer1 <= 0 && !n.lever1 && n.startLightTimer1 > 0) n.startLightTimer1 = 0.0
        if (n.startAbortTimer1 <= 0 && !n.lever1) n.startHungTimer1 = 0.0
        n.n2_1 = e.nextN2
        n.n1_1 = e.nextN1
        n.egt1 = e.nextEgt
        n.ff1 = e.nextFf
        n.oilPsi1 = e.nextOilPsi
    }

    /** Engine 2 starter timers and state writes after runEngine (mirror of [tailEngine1]). */
    private fun tailEngine2(n: SimState, dt: Double, e: EngineNext, priorValve: Boolean, priorOnTime: Double) {
        n.startValve2 = e.nextValve
        n.starterOnTime2 = if (n.startValve2) priorOnTime + dt else 0.0
        if (n.startAbortTimer2 > 0) n.startAbortTimer2 = maxOf(0.0, n.startAbortTimer2 - dt)
        if (priorValve && !n.startValve2) {
            val extended = priorOnTime >= 30
            n.starterCooldown2 = if (extended) 300.0 else 60.0
            n.starterExtendedUsed2 = extended
        } else {
            n.starterCooldown2 = maxOf(0.0, n.starterCooldown2 - dt)
            if (n.starterCooldown2 <= 0.01 && !n.startValve2) n.starterExtendedUsed2 = false
        }
        if (n.starterOnTime2 >= 30 && !n.starterExtendedUsed2 && n.n2_2 < 35) n.starterExtendedUsed2 = true
        if (n.startAbortTimer2 <= 0 && !n.lever2 && n.startLightTimer2 > 0) n.startLightTimer2 = 0.0
        if (n.startAbortTimer2 <= 0 && !n.lever2) n.startHungTimer2 = 0.0
        n.n2_2 = e.nextN2
        n.n1_2 = e.nextN1
        n.egt2 = e.nextEgt
        n.ff2 = e.nextFf
        n.oilPsi2 = e.nextOilPsi
    }

    /** Both engines' start/spool, then the oil quantity and pressure coupling (ATA 79). */
    fun engines(n: SimState, d: Derived, dt: Double) {
        val priorValve1 = n.startValve1
        val priorOnTime1 = n.starterOnTime1
        val e1 = runEngine(n, d, dt, 1, n.start1, n.lever1, n.n2_1, n.n1_1, n.egt1, n.ff1,
            d.leftDuctPsi, d.fuelFeedPressure1, n.throttle1, d.ignition1LeftOn, d.ignition1RightOn)
        tailEngine1(n, dt, e1, priorValve1, priorOnTime1)

        val priorValve2 = n.startValve2
        val priorOnTime2 = n.starterOnTime2
        val e2 = runEngine(n, d, dt, 2, n.start2, n.lever2, n.n2_2, n.n1_2, n.egt2, n.ff2,
            d.rightDuctPsi, d.fuelFeedPressure2, n.throttle2, d.ignition2LeftOn, d.ignition2RightOn)
        tailEngine2(n, dt, e2, priorValve2, priorOnTime2)

        // Pressure follows engine speed only while useful oil quantity remains; LOW OIL faults suppress it.
        if (n.n1_1 > 20) n.oilQty1 = maxOf(0.0, n.oilQty1 - 0.0015 * n.n1_1 * dt)
        if (n.n1_2 > 20) n.oilQty2 = maxOf(0.0, n.oilQty2 - 0.0015 * n.n1_2 * dt)
        if (n.faults.oilLow1 || n.oilQty1 < 8) n.oilPsi1 = minOf(n.oilPsi1, maxOf(0.0, n.n1_1 * 0.55))
        if (n.faults.oilLow2 || n.oilQty2 < 8) n.oilPsi2 = minOf(n.oilPsi2, maxOf(0.0, n.n1_2 * 0.55))
    }

    /** Fuel draw: center tank, then crossfeed-shared or per-engine wing tanks (TS fuelDrawSource). */
    private fun fuelDrawSource(
        n: SimState,
        want1In: Double,
        want2In: Double,
        fuelCenterPumpOn: Boolean,
        crossfeedOpen: Boolean,
        fuelPump1On: Boolean,
        fuelPump2On: Boolean,
    ) {
        var r1 = want1In
        var r2 = want2In
        if (fuelCenterPumpOn) {
            val t1 = minOf(n.fuelTankCenter, r1); n.fuelTankCenter -= t1; r1 -= t1
            val t2 = minOf(n.fuelTankCenter, r2); n.fuelTankCenter -= t2; r2 -= t2
        }
        if (crossfeedOpen && (fuelPump1On || fuelPump2On) && r1 + r2 > 0) {
            var shared = r1 + r2
            r1 = 0.0
            r2 = 0.0
            if (fuelPump1On && fuelPump2On) {
                val half = shared / 2.0
                val a1 = minOf(n.fuelTank1, half)
                val a2 = minOf(n.fuelTank2, half)
                n.fuelTank1 -= a1
                n.fuelTank2 -= a2
                shared -= a1 + a2
            }
            // One tank ran dry (or only one pump is on): the pumped tank(s) pick up the rest.
            if (fuelPump1On) { val t = minOf(n.fuelTank1, shared); n.fuelTank1 -= t; shared -= t }
            if (fuelPump2On) { val t = minOf(n.fuelTank2, shared); n.fuelTank2 -= t; shared -= t }
            // Anything left is drawn by suction from the tanks.
            if (shared > 0) { val t = minOf(n.fuelTank1, shared); n.fuelTank1 -= t; shared -= t }
            if (shared > 0) { val t = minOf(n.fuelTank2, shared); n.fuelTank2 -= t; shared -= t }
            return
        }
        n.fuelTank1 = maxOf(0.0, n.fuelTank1 - r1)
        n.fuelTank2 = maxOf(0.0, n.fuelTank2 - r2)
    }

    /**
     * Fuel consumption and draw, reservoir servicing and leaks, hydraulic pressure approach, then the
     * autobrake prerequisite and landing autobrake latches. Returns the mid-tick derived views used later.
     */
    fun fuelHydraulicsAndAutobrake(n: SimState, d: Derived, dt: Double): Part2Result {
        // ATA 28 fuel: 100% = ~3,700 kg per tank, so 1% = ~37 kg.
        val kgPerTankPercent = 37.0
        fun consume(ff: Double) = (maxOf(0.0, ff) / 3600.0 / kgPerTankPercent) * dt
        val c1 = consume(n.ff1)
        val c2 = consume(n.ff2)
        // Snapshot the fuel-source state before the draw runs (TS d2).
        val d2 = SimDerive.computeAll(n)
        val fuelPump1On = d2.acPower && ((n.fuelPump1Fwd && !n.faults.fuelTank1FwdPumpFail) || (n.fuelPump1Aft && !n.faults.fuelTank1AftPumpFail)) &&
            !n.faults.fuelBoostPump1Fail && n.fuelTank1 > 0.5
        val fuelPump2On = d2.acPower && ((n.fuelPump2Fwd && !n.faults.fuelTank2FwdPumpFail) || (n.fuelPump2Aft && !n.faults.fuelTank2AftPumpFail)) &&
            !n.faults.fuelBoostPump2Fail && n.fuelTank2 > 0.5
        val fuelCenterPumpOn = d2.acPower && ((n.fuelPumpCenter1 && !n.faults.fuelCenterPump1Fail) || (n.fuelPumpCenter2 && !n.faults.fuelCenterPump2Fail)) &&
            !n.faults.fuelCenterPumpFail && n.fuelTankCenter > 0.5
        val crossfeedOpen = d2.dcPower && n.fuelCrossfeed && !n.faults.fuelCrossfeedValveFail
        val fuelShutoff1Open = d.fuelShutoff1Open
        val fuelShutoff2Open = d.fuelShutoff2Open
        val engine1FuelDemand = if (fuelShutoff1Open && n.ff1 > 0) c1 else 0.0
        val engine2FuelDemand = if (fuelShutoff2Open && n.ff2 > 0) c2 else 0.0
        fuelDrawSource(n, engine1FuelDemand, engine2FuelDemand, fuelCenterPumpOn, crossfeedOpen, fuelPump1On, fuelPump2On)
        if (n.faults.fuelTank1Leak) n.fuelTank1 = maxOf(0.0, n.fuelTank1 - 0.08 * dt)
        if (n.faults.fuelTank2Leak) n.fuelTank2 = maxOf(0.0, n.fuelTank2 - 0.08 * dt)
        if (n.faults.fuelCenterLeak) n.fuelTankCenter = maxOf(0.0, n.fuelTankCenter - 0.08 * dt)

        // Reservoir quantity (percent): ground servicing adds fluid on the ground only; leaks drain.
        val servicingRate = 2.5
        val leakRate = 2.0
        if (n.hydServicingConnected && n.wow) {
            n.hydAQty = minOf(100.0, n.hydAQty + servicingRate * dt)
            n.hydBQty = minOf(100.0, n.hydBQty + servicingRate * dt)
            n.hydSQty = minOf(100.0, n.hydSQty + servicingRate * dt)
        }
        if (n.faults.hydALeak) n.hydAQty = maxOf(0.0, n.hydAQty - leakRate * dt)
        if (n.faults.hydBLeak) n.hydBQty = maxOf(0.0, n.hydBQty - leakRate * dt)

        n.hydAPress = approach(n.hydAPress, d2.hydA, 2400.0, dt)
        n.hydBPress = approach(n.hydBPress, d2.hydB, 2400.0, dt)
        n.hydSPress = approach(n.hydSPress, d2.hydS, 2400.0, dt)

        // Automatic-braking turn-on self-check (waits ~2.5 s, then holds for the selector).
        val abPrereq = n.autobrake > 0 && cb(n, "autobrake") &&
            n.antiskidInbd && n.antiskidOutbd && cb(n, "antiskid-inbd") && cb(n, "antiskid-outbd") &&
            !n.faults.antiskidInop && !n.faults.antiskidInbdInop && !n.faults.antiskidOutbdInop && d2.dcPower &&
            n.hydAPress > 1500 && n.hydBPress > 1500
        n.autobrakeTestTimer = if (abPrereq) minOf(3.0, n.autobrakeTestTimer + dt) else 0.0
        if (!abPrereq || n.autobrake == 0.0) n.autobrakeRtoArmed = false
        if (n.autobrake == 4.0 && n.autobrakeTestTimer >= 2.5 && n.wow && n.throttle1 > 0.75 && n.throttle2 > 0.75 && n.groundSpeedKt > 90) {
            n.autobrakeRtoArmed = true
        }
        if (n.autobrake != 4.0) n.autobrakeRtoArmed = false

        // Landing autobrake: wheel-spin-up on-ramp at ~60 kt, latched disarm on dropout conditions.
        val landingAutobrakeSelectedNow = n.autobrake >= 1.0 && n.autobrake <= 3.0
        val landingAutobrakePrereqNow = n.autobrakeTestTimer >= 2.5 && d2.dcPower && cb(n, "autobrake") &&
            n.hydAPress > 1500 && n.hydBPress > 1500 &&
            n.antiskidInbd && n.antiskidOutbd && cb(n, "antiskid-inbd") && cb(n, "antiskid-outbd") &&
            !n.faults.antiskidInop && !n.faults.antiskidInbdInop && !n.faults.antiskidOutbdInop &&
            n.speedBrake == "ARMED"
        val landingPedalOverride = n.leftBrakePedal > 0.067 || n.rightBrakePedal > 0.067
        val landingThrottlesIdle = n.throttle1 <= 0.05 && n.throttle2 <= 0.05
        if (n.autobrake == 0.0) {
            n.autobrakeLandingActivated = false
            n.autobrakeLandingDisarmed = false
        } else if (n.autobrakeLandingActivated &&
            (!landingAutobrakeSelectedNow || !landingAutobrakePrereqNow || !n.wow || !landingThrottlesIdle || landingPedalOverride)) {
            n.autobrakeLandingActivated = false
            n.autobrakeLandingDisarmed = true
        }
        if (!n.autobrakeLandingDisarmed && landingAutobrakeSelectedNow && n.wow &&
            n.groundSpeedKt > 60 && landingAutobrakePrereqNow && landingThrottlesIdle && !landingPedalOverride) {
            n.autobrakeLandingActivated = true
        }

        // TS brakeState = derive(n), taken after the autobrake latches above.
        val brakeState = SimDerive.computeAll(n)
        return Part2Result(d2, brakeState)
    }
}
