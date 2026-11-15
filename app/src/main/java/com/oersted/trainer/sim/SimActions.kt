package com.oersted.trainer.sim

import kotlin.math.floor
import kotlin.math.max

/**
 * Port of the trainer actions in src/lib/sim/store.ts: startEngine, shutdownEngine,
 * injectLandingGearFault, clearLandingGearFault, and toggleFault. Each takes the current state and
 * returns the new one. The switch-click sound is not here: the caller plays it.
 */
object SimActions {
    /** TS startEngine. [side] is 1 or 2. */
    fun startEngine(s: SimState, side: Int, nowMs: Double): SimState {
        val d = SimDerive.computeAll(s)
        val running = if (side == 1) d.eng1Running else d.eng2Running
        val firePulled = if (side == 1) s.engine1FirePulled else s.engine2FirePulled
        val psi = if (side == 1) d.leftDuctPsi else d.rightDuctPsi
        if (running) return StepSim.pushLog(s, "Engine $side already running.", "info", nowMs = nowMs)
        if (firePulled) return StepSim.pushLog(s, "Engine $side start inhibited — fire handle pulled.", "warn", nowMs = nowMs)
        if (!d.dcPower) return StepSim.pushLog(s, "Engine $side start inhibited — no DC power (battery / bus).", "warn", nowMs = nowMs)
        val next = s.copy()
        if (side == 1) {
            next.start1 = "GRD"; next.lever1 = false; next.autoStart1 = true
        } else {
            next.start2 = "GRD"; next.lever2 = false; next.autoStart2 = true
        }
        val text = if (psi < 30) {
            "ENG $side START — GRD selected, but starter air is below 30 psi. Supply APU bleed, ground air or cross-bleed."
        } else {
            "ENG $side START — GRD selected, starter valve opening. IDLE will be set at ~20% N2."
        }
        return StepSim.pushLog(next, text, if (psi < 30) "caution" else "info", nowMs = nowMs)
    }

    /** TS shutdownEngine. */
    fun shutdownEngine(s: SimState, side: Int, nowMs: Double): SimState {
        val next = s.copy()
        if (side == 1) {
            next.start1 = "OFF"; next.lever1 = false; next.autoStart1 = false
        } else {
            next.start2 = "OFF"; next.lever2 = false; next.autoStart2 = false
        }
        return StepSim.pushLog(next, "ENG $side shutdown — START LEVER to CUTOFF.", "info", nowMs = nowMs)
    }

    private val GEAR_CHOICES = listOf("NOSE", "LEFT_MAIN", "RIGHT_MAIN")

    /**
     * TS injectLandingGearFault: picks one gear that fails to extend. A different gear is chosen when a
     * fault already exists. [random] returns a value in [0, 1) and is injected for tests.
     */
    fun injectLandingGearFault(s: SimState, nowMs: Double, random: () -> Double = { kotlin.random.Random.nextDouble() }): SimState {
        val available = if (s.landingGearFault == "NONE") GEAR_CHOICES else GEAR_CHOICES.filter { it != s.landingGearFault }
        val fault = available.getOrNull(floor(random() * available.size).toInt())
            ?: GEAR_CHOICES[floor(random() * GEAR_CHOICES.size).toInt()]
        val next = s.copy()
        next.gear = "DN"
        next.landingGearFault = fault
        if (fault == "NOSE") next.noseGearPos = 0.0
        if (fault == "LEFT_MAIN") next.leftMainGearPos = 0.0
        if (fault == "RIGHT_MAIN") next.rightMainGearPos = 0.0
        val label = when (fault) {
            "NOSE" -> "NOSE GEAR"
            "LEFT_MAIN" -> "LEFT MAIN GEAR"
            else -> "RIGHT MAIN GEAR"
        }
        return StepSim.pushLog(next, "Fault injected: LANDING GEAR POSITION — $label failed to extend.", "warn", nowMs = nowMs)
    }

    /** TS clearLandingGearFault: no change (and no log line) when there is no fault. */
    fun clearLandingGearFault(s: SimState, nowMs: Double): SimState {
        if (s.landingGearFault == "NONE") return s
        val next = s.copy()
        next.landingGearFault = "NONE"
        return StepSim.pushLog(next, "Fault cleared: LANDING GEAR POSITION fault.", "ok", nowMs = nowMs)
    }

    /** TS toggleFault: flips one fault by its key and logs it. */
    fun toggleFault(s: SimState, key: String, nowMs: Double): SimState {
        val next = s.copy()
        val on = !FaultTable.get(next.faults, key)
        FaultTable.set(next.faults, key, on)
        val label = FaultTable.LABELS[key] ?: key
        return StepSim.pushLog(
            next,
            if (on) "Fault on: $label" else "Fault cleared: $label",
            if (on) "warn" else "ok",
            nowMs = nowMs,
        )
    }

    /** TS readyApu: a preset with the APU running at 100 %, generator on the bus, bleed open. The log is replaced, not appended. */
    fun readyApu(nowMs: Double): SimState {
        val s = initialSimState()
        s.apuMaster = "ON"
        s.apuFuelValve = 1.0
        s.apuDoor = 1.0
        s.apuRpm = 100.0
        s.apuEgt = 420.0
        s.apuOilPsi = 95.0
        s.apuGen = true
        s.apuLeftControl = true
        s.apuRightControl = true
        s.apuBleed = true
        s.apuBleedValve = 1.0
        s.groundPowerOn = false
        s.externalPowerConnected = false
        s.busTie = true
        s.groundPneumaticConnected = false
        s.groundPneumaticPsi = 0.0
        s.packL = true
        s.packR = true
        s.log = mutableListOf(LogEntry().also {
            it.t = nowMs
            it.text = "Preset: APU running at 100%, generator on bus, bleed open."
            it.tone = "ok"
        })
        return s
    }

    /**
     * TS readyForDeparture: a preparation preset, not a reset. Fuel quantities, injected faults,
     * maintenance state and the active plan are kept; the aircraft is configured parked, with engines
     * stabilized at idle.
     */
    fun readyForDeparture(s: SimState, nowMs: Double): SimState {
        val n = s.copy()
        n.battery = true
        n.busTie = true
        n.apuMaster = "ON"; n.apuFuelValve = 1.0; n.apuDoor = 1.0; n.apuRpm = 100.0; n.apuEgt = 420.0; n.apuOilPsi = 95.0
        n.apuGen = true; n.apuLeftControl = true; n.apuRightControl = true; n.apuBleed = true; n.apuBleedValve = 1.0
        n.lever1 = true; n.lever2 = true; n.gen1 = true; n.gen2 = true
        n.start1 = "OFF"; n.start2 = "OFF"; n.autoStart1 = false; n.autoStart2 = false
        n.n2_1 = max(s.n2_1, 62.0); n.n2_2 = max(s.n2_2, 62.0)
        n.n1_1 = max(s.n1_1, 22.0); n.n1_2 = max(s.n1_2, 22.0)
        n.egt1 = max(s.egt1, 480.0); n.egt2 = max(s.egt2, 480.0)
        n.ff1 = max(s.ff1, 800.0); n.ff2 = max(s.ff2, 800.0)
        n.oilPsi1 = max(s.oilPsi1, 45.0); n.oilPsi2 = max(s.oilPsi2, 45.0)
        n.throttle1 = 0.05; n.throttle2 = 0.05
        n.wow = true; n.airborneTest = false; n.groundSenseTest = false
        n.gear = "DN"; n.noseGearPos = 1.0; n.leftMainGearPos = 1.0; n.rightMainGearPos = 1.0
        n.flaps = 5.0; n.speedBrake = "ARMED"; n.parkingBrake = true
        n.groundSpeedKt = 0.0; n.psFlightCasKt = 0.0; n.psFlightTargetCasKt = 0.0; n.psFlightVsFpm = 0.0
        n.psFlightAltFt = if (s.wow) s.psFlightAltFt else 0.0
        n.psFlightTargetAltFt = if (s.wow) s.psFlightAltFt else 0.0
        n.psFlightProgressive = true; n.psGroundRoll = false
        // Clear any still-active runway scenario: its APPROACH phase would reassert airborneTest every tick.
        n.rwyPhase = "IDLE"; n.rwyPosM = -1800.0; n.rwyAltFt = 0.0; n.rwyLatM = 0.0; n.rwyHdgDeg = 0.0
        n.rwyTrackDeg = 0.0; n.rwyYawRate = 0.0; n.rwyNote = ""
        n.wheelSpeeds = SimStateWheelSpeeds().apply { li = 0.0; lo = 0.0; ri = 0.0; ro = 0.0 }
        n.wheelBrakePressures = SimStateWheelBrakePressures().apply { li = 0.0; lo = 0.0; ri = 0.0; ro = 0.0 }
        n.actualDecel = 0.0
        n.fuelPump1Fwd = true; n.fuelPump1Aft = true; n.fuelPump2Fwd = true; n.fuelPump2Aft = true
        n.fuelPumpCenter1 = s.fuelTankCenter > 0; n.fuelPumpCenter2 = s.fuelTankCenter > 0
        n.fuelCrossfeed = false
        n.engHyd1 = true; n.engHyd2 = true; n.elecHyd1 = true; n.elecHyd2 = true
        n.hydAPress = 3000.0; n.hydBPress = 3000.0; n.hydSPress = 3000.0
        n.bleed1 = true; n.bleed2 = true; n.packL = true; n.packR = true
        n.antiIce1 = true; n.antiIce2 = true; n.wingAntiIceL = true; n.wingAntiIceR = true
        n.ltPosition = "STROBE_STEADY"; n.ltAntiCol = true; n.ltLogo = true; n.ltNoSmoking = "ON"; n.ltSeatBelt = "ON"
        val entry = LogEntry().also {
            it.t = nowMs
            it.text = "Ready for departure: aircraft configured on the ground, engines stabilized at idle, systems and cabin signs configured. Existing fuel quantity, faults, and active flight plan preserved."
            it.tone = "ok"
        }
        n.log = (s.log.takeLast(29) + entry).toMutableList()
        return n
    }

    /** TS runScenario: the named training scenarios. Any name other than the four known ones is the TE flap scenario. */
    fun runScenario(s: SimState, name: String, nowMs: Double): SimState {
        when (name) {
            "TR3_BACKUP" -> {
                val n = s.copy()
                n.battery = false; n.externalPowerConnected = false; n.groundPowerOn = false; n.busTie = true
                n.lever2 = true; n.n2_2 = 60.0; n.gen2 = true
                n.faults.tr1Failure = true; n.faults.tr2Failure = false
                return StepSim.pushLog(n, "Scenario: GEN 2 operating, TR 1 failed — TR 3 is fed from GEN BUS 2 and supplies the DC tie backup path.", "warn", nowMs = nowMs)
            }
            "ADVERSE_YAW" -> {
                val n = s.copy()
                n.battery = true; n.wow = false; n.airborneTest = true; n.groundSenseTest = false
                n.lever1 = true; n.lever2 = true; n.n2_1 = 70.0; n.n2_2 = 70.0
                n.bleed1 = true; n.bleed2 = true; n.engHyd1 = true; n.engHyd2 = true; n.elecHyd1 = true; n.elecHyd2 = true
                n.gen1 = true; n.gen2 = true
                n.gear = "UP"; n.noseGearPos = 0.0; n.leftMainGearPos = 0.0; n.rightMainGearPos = 0.0
                n.fltCtrlA = true; n.fltCtrlB = true; n.spoilerA = true; n.spoilerB = true
                n.hydServicingConnected = false; n.hydGroundPowerConnected = false
                n.groundPneumaticConnected = false; n.groundPneumaticPsi = 0.0
                n.parkingBrake = false; n.hydAPress = 3000.0; n.hydBPress = 3000.0
                n.groundSpeedKt = 140.0; n.speedBrake = "FLIGHT"; n.yokeRoll = 0.55; n.rudderPedal = 0.0
                n.adverseYawScenario = true
                return StepSim.pushLog(n, "Scenario: ADVERSE YAW — apply coordinated rudder in the direction of the commanded roll while observing the opposite yaw cue.", "warn", nowMs = nowMs)
            }
            "AUTOBRAKE_RTO" -> {
                val n = s.copy()
                n.battery = true; n.wow = true; n.gear = "DN"; n.gearPos = 1.0; n.parkingBrake = false
                n.antiskidInbd = true; n.antiskidOutbd = true; n.autobrake = 4.0
                n.groundSpeedKt = 105.0; n.speedBrake = "ARMED"; n.autobrakeTestTimer = 3.0; n.autobrakeRtoArmed = true
                n.lever1 = true; n.lever2 = true; n.throttle1 = 1.0; n.throttle2 = 1.0
                n.engHyd1 = true; n.engHyd2 = true; n.elecHyd1 = true; n.elecHyd2 = true
                n.externalPowerConnected = true; n.groundPowerOn = true
                n.hydServicingConnected = true; n.hydGroundPowerConnected = true
                n.hydAPress = 3000.0; n.hydBPress = 3000.0
                n.faults.antiskidInop = false
                return StepSim.pushLog(n, "Scenario: RTO — RTO selected and armed above 90 kt. Retard both thrust levers to trigger maximum autobraking.", "warn", nowMs = nowMs)
            }
            "AUTOBRAKE_ANTISKID" -> {
                val n = s.copy()
                n.battery = true; n.wow = true; n.gear = "DN"; n.gearPos = 1.0; n.parkingBrake = false
                n.antiskidInbd = true; n.antiskidOutbd = true; n.autobrake = 1.0
                n.groundSpeedKt = 75.0; n.speedBrake = "ARMED"; n.autobrakeTestTimer = 3.0; n.autobrakeRtoArmed = false
                n.lever1 = true; n.lever2 = true; n.throttle1 = 0.0; n.throttle2 = 0.0
                n.engHyd1 = true; n.engHyd2 = true; n.elecHyd1 = true; n.elecHyd2 = true
                n.externalPowerConnected = true; n.groundPowerOn = true
                n.hydServicingConnected = true; n.hydGroundPowerConnected = true
                n.hydAPress = 3000.0; n.hydBPress = 3000.0
                n.faults.antiskidInop = false
                return StepSim.pushLog(n, "Scenario: ANTISKID + AUTOBRAKE landing — MIN selected, wheel speed 75 kt, brakes will be controlled through the antiskid valves.", "ok", nowMs = nowMs)
            }
            else -> {
                val n = s.copy()
                n.battery = false; n.externalPowerConnected = true; n.groundPowerOn = true
                n.flaps = 15.0; n.flapPos = 0.0; n.altFlapsArm = false; n.altFlapsControl = "OFF"
                n.faults.teFlapHydMotorFail = true
                return StepSim.pushLog(n, "Scenario: TE flap hydraulic motor failed — use ALTERNATE FLAPS ARM, then the alternate position switch.", "warn", nowMs = nowMs)
            }
        }
    }
}
