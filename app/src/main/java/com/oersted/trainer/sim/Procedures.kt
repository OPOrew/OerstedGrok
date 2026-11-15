package com.oersted.trainer.sim

/**
 * Port of data/procedures.ts: the training procedures and their step checks.
 *
 * Each step's `done` predicate reads the sim state and the derived values. The web code calls derive(s) inside the
 * predicate; here the caller computes [Derived] once with SimDerive.computeAll and passes it in, which gives the same
 * answers (derive is pure). Text (title, why, hint, summary) is copied verbatim from the web source.
 */
object Procedures {

    enum class ProcedureCategory { NORMAL, EMERGENCY }

    data class ProcStep(
        val id: String,
        val title: String,
        val why: String,
        val hint: String,
        val done: (s: SimState, d: Derived) -> Boolean,
    )

    data class Procedure(
        val id: String,
        val category: ProcedureCategory,
        val title: String,
        val ata: String,
        val summary: String,
        val steps: List<ProcStep>,
    )

    /** Convenience: run a step's check against a state, computing the derived values. */
    fun isDone(step: ProcStep, s: SimState): Boolean = step.done(s, SimDerive.computeAll(s))

    val PROCEDURES: List<Procedure> = listOf(
        Procedure(
            id = "apu-start", category = ProcedureCategory.NORMAL, title = "APU start", ata = "49",
            summary = "Watch the real sequence: fuel valve, inlet door, starter lockout, then 95% arming.",
            steps = listOf(
                ProcStep("bat", "Battery on",
                    "APU control, the door actuator, and fire circuits are 28V DC.",
                    "BAT switch on the status strip.") { s, _ -> s.battery },
                ProcStep("master", "APU MASTER to ON",
                    "ON opens the fuel shutoff valve. The door will not move until that valve is fully open.",
                    "Overhead APU rotary — first detent.") { s, _ -> s.apuMaster != "OFF" || s.apuFuelValve > 0.9 },
                ProcStep("door", "Wait for inlet door full open",
                    "A door position switch releases the starter lockout relay. Cycle is ~17–20 s in the aircraft (sped up here).",
                    "Watch the tail inlet on the airframe and the DOOR gauge.") { s, _ -> s.apuDoor > 0.97 },
                ProcStep("start", "APU MASTER to START",
                    "START engages the starter motor only after the door reports fully open.",
                    "Spring-loaded START. It will return to ON as rpm rises.") { s, _ -> s.apuRpm > 20 || s.apuMaster == "START" },
                ProcStep("oil", "Confirm LOW OIL PRESSURE extinguishes",
                    "The AMM says the APU low-oil-pressure light comes on immediately after START and extinguishes when oil pressure reaches 55 psi.",
                    "Watch APU OIL PSI and the LOW OIL PRESS annunciator.") { s, _ -> s.apuOilPsi >= 55 },
                ProcStep("avail", "Stabilize at 95%+",
                    "At service speed the APU is available for pneumatic or electrical power when the appropriate controls are selected.",
                    "APU % gauge and APU GEN OFF BUS light.") { _, d -> d.apuAvailable },
            ),
        ),
        Procedure(
            id = "engine-start", category = ProcedureCategory.NORMAL, title = "Engine start on APU air", ata = "80 / 36",
            summary = "APU bleed into the manifold, starter valve, N2, fuel at IDLE, starter cutout.",
            steps = listOf(
                ProcStep("apu", "APU available",
                    "The pneumatic starting system can use APU air, a ground pneumatic source, or cross-bleed air from an operating engine.",
                    "Complete APU start, or connect the ground pneumatic cart.") { s, d -> d.apuAvailable || s.groundPneumaticConnected },
                ProcStep("bleed", "APU BLEED OPEN",
                    "The valve opens in 12–14 s so APU speed does not droop. Duct pressure should rise through ~40 psi.",
                    "APU BLEED switch. Watch duct PSI.") { s, _ -> s.apuBleed && s.apuBleedValve > 0.7 },
                ProcStep("pack", "Packs off (training tip)",
                    "Packs steal manifold air. For a strong start, close them so the starter sees full duct pressure.",
                    "L PACK and R PACK off.") { s, _ -> !s.packL && !s.packR },
                ProcStep("grd", "Engine 1 START to GRD",
                    "Energizes the starter-valve solenoid. START VALVE OPEN must illuminate.",
                    "START 1 rotary to GRD.") { s, _ -> s.start1 == "GRD" || s.n2_1 > 15 },
                ProcStep("idle", "Start lever IDLE at ~20% N2",
                    "Introduces fuel and fires the igniters. EGT and fuel flow come alive.",
                    "ENG 1 start lever.") { s, _ -> s.lever1 && s.n2_1 > 20 },
                ProcStep("cutout", "Starter cutout 35–40% N2",
                    "The valve must close. A light that stays on after cutout is an immediate shutdown item.",
                    "Watch START VALVE OPEN go dark and N2 continue climbing.") { _, d -> d.eng1Running },
            ),
        ),
        Procedure(
            id = "dual-bleed", category = ProcedureCategory.NORMAL, title = "Dual bleed caution", ata = "36",
            summary = "See why APU bleed and engine bleed together light DUAL BLEED and MASTER CAUTION.",
            steps = listOf(
                ProcStep("apu-b", "APU running with bleed open",
                    "APU is a ground/backup pneumatic source on the same manifold as the engines.",
                    "Use the APU running preset, APU BLEED on.") { s, d -> d.apuAvailable && s.apuBleed },
                ProcStep("eng", "Open an engine bleed with APU bleed cross-feed",
                    "With APU BLEED open, an engine BLEED valve open, and the isolation valve open, the DUAL BLEED warning illuminates.",
                    "With APU BLEED ON, set ISOLATION OPEN and then BLEED 1 ON.") { _, d -> d.dualBleed },
                ProcStep("fix", "Close APU bleed",
                    "Standard: after engine bleed is available, APU bleed off. Isolation AUTO manages the rest.",
                    "APU BLEED off. DUAL BLEED should go dark.") { s, d -> d.eng1Running && s.bleed1 && !d.dualBleed },
            ),
        ),
        Procedure(
            id = "hydraulics", category = ProcedureCategory.NORMAL, title = "Hydraulic A / B / standby", ata = "29",
            summary = "Engine pumps make A. Electric pumps make B. Standby covers rudder and leading edges if A dies.",
            steps = listOf(
                ProcStep("eng", "Run at least one engine",
                    "System A engine-driven pumps live on the accessory gearboxes.",
                    "Complete an engine start, ENG HYD pumps on.") { s, d -> (d.eng1Running || d.eng2Running) && s.hydAPress > 2000 },
                ProcStep("elec", "Put an AC source on the bus, then ELEC HYD",
                    "System B pumps are 115V AC motor-driven. APU GEN or engine GEN must be on.",
                    "APU GEN or GEN 1, then ELEC 1 HYD PUMP.") { s, _ -> s.hydBPress > 2000 },
                ProcStep("leak", "Inject a System A leak",
                    "LOW PRESSURE on A. In AUTO, the standby pump should pick up rudder / LE devices.",
                    "Faults → System A leak. Watch STBY RUD ON.") { s, d -> s.faults.hydALeak && d.stbyOn },
            ),
        ),
        Procedure(
            id = "gear", category = ProcedureCategory.NORMAL, title = "Gear cycle", ata = "32",
            summary = "Gear is System A only. Airborne logic is simulated by lifting weight-on-wheels.",
            steps = listOf(
                ProcStep("press", "System A pressurized",
                    "Selector valve, actuators, uplocks, and steering all drink from A.",
                    "Engines running with ENG HYD pumps on.") { s, d ->
                    s.hydAPress > 2000 || (s.hydGroundPowerConnected && s.groundInterconnect && s.wow && s.parkingBrake && d.hydB > 1500)
                },
                ProcStep("air", "Lift weight-on-wheels",
                    "On the ground the lever latch and steering logic assume WOW. Training: go AIR.",
                    "AIR / GND toggle on the status strip.") { s, _ -> !s.wow },
                ProcStep("up", "Gear lever UP",
                    "Cable/drum to the selector valve. Watch the trucks tuck into the wells.",
                    "GEAR UP. Transit lights until uplock.") { s, _ -> s.gearPos < 0.08 },
                ProcStep("dn", "Gear lever DN",
                    "Reverse the selector. Confirm three green (gear down and locked).",
                    "GEAR DN.") { s, _ -> s.gear == "DN" && s.gearPos > 0.95 },
            ),
        ),
        Procedure(
            id = "emergency-gear-selector-position-disagree", category = ProcedureCategory.EMERGENCY,
            title = "Landing gear selector / position disagreement", ata = "32",
            summary = "Recognize a mismatch between the LANDING GEAR selector and the actual gear position, then use the independent manual-extension releases when normal hydraulic extension is unavailable.",
            steps = listOf(
                ProcStep("identify", "Confirm selector / gear position disagreement",
                    "The warning condition exists when the selector commands UP while any gear is not up, or commands DN while any gear is not down-and-locked.",
                    "Check the three individual gear indications and the selector/position agreement display in the Servicing / Test panel.") { _, d -> d.gearPositionDisagree },
                ProcStep("off", "Return the landing gear selector to OFF",
                    "The simulator requires the selector to be OFF before an individual manual-extension T-handle can be operated.",
                    "Select OFF, then verify the selector no longer commands hydraulic movement.") { s, d -> d.gearPositionDisagree && s.gear == "OFF" },
                ProcStep("manual", "Manually extend the affected gear",
                    "Each manual-extension release is independent. Pulling a T-handle releases only its corresponding uplock; that gear then travels through its full range to down-and-locked without moving the other two gears.",
                    "Pull and release the NOSE, LEFT MAIN, or RIGHT MAIN manual-extension handle corresponding to the gear that has not reached down-and-locked.") { _, d -> d.gearDown },
                ProcStep("verify", "Verify all three gears down and locked",
                    "The final condition is three independent down-and-locked positions with the corresponding green indications and no selector/position disagreement.",
                    "Confirm NOSE, LEFT MAIN, and RIGHT MAIN are DOWN / LOCKED and the disagreement indication is clear.") { _, d ->
                    d.gearDown && !d.gearPositionDisagree
                },
            ),
        ),
        Procedure(
            id = "emergency-engine-fire", category = ProcedureCategory.EMERGENCY, title = "Engine fire — fire-system response", ata = "26",
            summary = "Training sequence based on the supplied 737-200 fire-protection description: detect, isolate the engine, arm the extinguisher, then select a bottle.",
            steps = listOf(
                ProcStep("detect", "Confirm ENGINE FIRE warning",
                    "The fire-warning system provides the visual warning; the handle is the response, not the cause of the warning.",
                    "Inject ENGINE 1 or ENGINE 2 FIRE from the separate Training Console Fault Injection panel.") { s, _ ->
                    s.faults.engineFire1 || s.faults.engineFire2
                },
                ProcStep("pull", "Pull the affected engine fire handle",
                    "The supplied ATA 26 description states that pulling the handle arms the extinguishing system and shuts down the engine and associated components.",
                    "Use the actual P8 fire handle or the corresponding cockpit control — do not use the fault injector as the response.") { s, _ ->
                    (s.faults.engineFire1 && s.engine1FirePulled) || (s.faults.engineFire2 && s.engine2FirePulled)
                },
                ProcStep("isolate", "Verify engine isolation effects",
                    "The supplied fire-system test procedure observes fuel shutoff, System A hydraulic shutoff, engine bleed-valve closure and generator low-voltage indication after handle operation.",
                    "Watch the component/system-flow display and P5/P1-P3 indications.") { s, d ->
                    val engine1Stopped = s.engine1FirePulled && !d.eng1Running
                    val engine2Stopped = s.engine2FirePulled && !d.eng2Running
                    engine1Stopped || engine2Stopped
                },
                ProcStep("bottle", "Select an extinguisher bottle",
                    "After the handle is armed, the engine fire switch can be rotated to select the applicable bottle discharge path.",
                    "Rotate the pulled handle in the simulator. The two bottle paths are represented separately.") { s, _ ->
                    s.fireBottleLDischarged || s.fireBottleRDischarged
                },
            ),
        ),
        Procedure(
            id = "emergency-apu-fire", category = ProcedureCategory.EMERGENCY, title = "APU fire — normal / remote response", ata = "26",
            summary = "Training sequence for the APU fire system, including the separate remote wheel-well control panel.",
            steps = listOf(
                ProcStep("detect", "Confirm APU FIRE warning",
                    "The supplied ATA 26 description identifies APU fire warning and the associated cockpit/remote indications.",
                    "Inject APU FIRE from Fault Injection.") { s, _ -> s.faults.apuFireDetected },
                ProcStep("pull", "Pull the APU fire handle",
                    "Pulling the APU fire handle arms the extinguishing system and shuts down the APU and associated components.",
                    "Use the cockpit APU FIRE control, or the remote wheel-well control when the procedure is being trained from that station.") { s, _ -> s.apuFirePulled },
                ProcStep("shutdown", "Verify APU shutdown / isolation",
                    "The APU fire system is designed to shut down the APU before the extinguisher is discharged.",
                    "Watch APU RPM, fuel/inlet-door state and the fire-system component chain.") { s, _ -> s.apuFirePulled && s.apuRpm < 10 },
                ProcStep("discharge", "Discharge the APU bottle",
                    "The supplied manual states that the bottle may be discharged after the fire switch is pulled; the remote panel uses a spring-loaded discharge switch.",
                    "Use the APU BOTTLE DISCH control after the handle is armed.") { s, _ -> s.apuBottleFired },
            ),
        ),
        Procedure(
            id = "emergency-hydraulic-a-loss", category = ProcedureCategory.EMERGENCY, title = "System A hydraulic loss — standby response", ata = "29 / 27",
            summary = "Train the loss of System A and observe the standby hydraulic path to the rudder and leading-edge devices.",
            steps = listOf(
                ProcStep("fault", "Inject System A hydraulic loss",
                    "The training fault represents loss of System A pressure rather than directly moving a final indication.",
                    "Fault Injection → System A leak.") { s, _ -> s.faults.hydALeak },
                ProcStep("standby", "Confirm standby hydraulic activation",
                    "The simulator models standby hydraulic power for the rudder and leading-edge devices when normal hydraulic authority is unavailable.",
                    "Watch STBY RUD and the hydraulic component flow.") { _, d -> d.standbyRudderPowered },
                ProcStep("control", "Exercise the rudder",
                    "The standby system is represented as the alternate hydraulic source for rudder control in this trainer.",
                    "Move the rudder pedals and observe the rudder surface/PCU response.") { s, _ -> Math.abs(s.rudderPedal) > 0.1 },
            ),
        ),
        Procedure(
            id = "emergency-electrical-source-loss", category = ProcedureCategory.EMERGENCY, title = "DC source loss — backup path", ata = "24",
            summary = "Inject a transformer-rectifier failure and observe the backup DC path and indications.",
            steps = listOf(
                ProcStep("source", "Establish normal electrical power",
                    "The backup path is meaningful only when the normal DC distribution is operating first.",
                    "Use APU or engine generation and confirm DC power on the electrical indication panel.") { _, d -> d.dcPower },
                ProcStep("fault", "Inject a TR failure",
                    "The supplied ATA 24 material describes TR3 as the backup DC source path and emphasizes observing breakers, lights and annunciations when troubleshooting.",
                    "Fault Injection → TR1 or TR2 failure.") { s, _ -> s.faults.tr1Failure || s.faults.tr2Failure },
                ProcStep("backup", "Observe the backup DC path",
                    "Watch the TR indication, affected DC bus and the simulator's component-flow representation rather than changing a final indication directly.",
                    "Open Component / System Flow and inspect the DC path.") { s, d ->
                    (s.faults.tr1Failure || s.faults.tr2Failure) && d.dcPower
                },
            ),
        ),
    )
}
