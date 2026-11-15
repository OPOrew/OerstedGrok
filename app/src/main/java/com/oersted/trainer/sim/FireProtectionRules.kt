package com.oersted.trainer.sim

/**
 * Rules behind the Fire Protection section of the TRAINING console (components/panel/FireProtectionTrainer.tsx,
 * action handlers only). Each function is one web handler: it returns the new state and the log line, or null when
 * the web handler does nothing (for example, a fire handle that is not pulled cannot be discharged).
 * The "puff" mist animation, the stubborn-bottle toggle and the layout stay in the view.
 */
object FireProtectionRules {
    /** A changed state and its log line (null = no log line, as a patch without a note). */
    data class Outcome(val state: SimState, val message: String?)

    private inline fun edited(s: SimState, edit: SimState.() -> Unit): SimState {
        val c = s.copy()
        c.edit()
        return c
    }

    /** Web pullEng: the handle can be pulled or reset only when pulled or a fire is detected. */
    fun pullEngineHandle(s: SimState, n: Int, detected: Boolean): Outcome? {
        val pulled = if (n == 1) s.engine1FirePulled else s.engine2FirePulled
        if (!pulled && !detected) return null
        val next = edited(s) { if (n == 1) engine1FirePulled = !pulled else engine2FirePulled = !pulled }
        val msg = if (pulled) "ENG $n fire handle reset."
        else "ENG $n FIRE handle pulled — fuel, bleed, generator and engine hydraulic supply isolated."
        return Outcome(next, msg)
    }

    /**
     * Web discharge: needs the handle pulled and that bottle not yet used. With [stubborn] the first bottle does not
     * clear the fire; the detected fault stays until the second bottle is used.
     */
    fun dischargeEngineBottle(s: SimState, n: Int, left: Boolean, stubborn: Boolean): Outcome? {
        val pulled = if (n == 1) s.engine1FirePulled else s.engine2FirePulled
        val already = if (left) s.fireBottleLDischarged else s.fireBottleRDischarged
        if (!pulled || already) return null
        val persists = stubborn && !(s.fireBottleLDischarged || s.fireBottleRDischarged)
        val next = edited(s) {
            if (left) fireBottleLDischarged = true else fireBottleRDischarged = true
            if (!persists) {
                if (n == 1) faults.engineFire1 = false else faults.engineFire2 = false
            }
        }
        val msg = if (persists) "ENG $n bottle ${if (left) "L" else "R"} discharged — FIRE PERSISTS. Discharge the other bottle."
        else "ENG $n fire handle rotated ${if (left) "LEFT" else "RIGHT"} — bottle ${if (left) "L" else "R"} discharged, fire out."
        return Outcome(next, msg)
    }

    /** Web pullApu: the APU handle needs a detected fire or an already pulled handle. */
    fun pullApuHandle(s: SimState): Outcome? {
        if (!s.apuFirePulled && !s.faults.apuFireDetected) return null
        val next = edited(s) { apuFirePulled = !s.apuFirePulled }
        val msg = if (s.apuFirePulled) "APU fire handle reset."
        else "APU FIRE handle pulled — APU shut down, fuel, bleed and generator isolated."
        return Outcome(next, msg)
    }

    /** Web dischargeApu: needs the APU handle pulled and the bottle unused. Clears the APU fire detection. */
    fun dischargeApuBottle(s: SimState): Outcome? {
        if (!s.apuFirePulled || s.apuBottleFired) return null
        val next = edited(s) {
            apuBottleFired = true
            faults.apuFireDetected = false
        }
        return Outcome(next, "APU bottle discharged, fire out.")
    }

    /** Web cargoArm: a press selects FWD or AFT, or clears the selector if it is already on that compartment. */
    fun armCargo(s: SimState, compartment: String): Outcome? {
        if (!s.cargoFireInstalled) return null
        val off = s.cargoArmed == compartment
        val next = edited(s) { cargoArmed = if (off) "NONE" else compartment }
        val msg = if (off) "CARGO FIRE ARM selector OFF." else "CARGO FIRE ARM — $compartment compartment selected."
        return Outcome(next, msg)
    }

    /** Web cargoDischarge: needs the system installed, a compartment armed and the bottle unused. */
    fun dischargeCargo(s: SimState): Outcome? {
        if (!s.cargoFireInstalled || s.cargoArmed == "NONE" || s.cargoBottleDischarged) return null
        val next = edited(s) {
            cargoBottleDischarged = true
            cargoAgentTimer = 0.0
        }
        return Outcome(next, "CARGO FIRE DISCH — agent released into the ${s.cargoArmed} compartment.")
    }

    /** Web cargo system toggle (installed / removed). Removing it also disarms and recharges the bottle. */
    fun toggleCargoInstalled(s: SimState): Outcome {
        val next = edited(s) {
            cargoFireInstalled = !s.cargoFireInstalled
            cargoArmed = "NONE"
            cargoBottleDischarged = false
        }
        val msg = if (s.cargoFireInstalled) "Cargo fire system REMOVED (pre-SB / not installed)."
        else "Cargo fire system INSTALLED (SB 26A1081)."
        return Outcome(next, msg)
    }

    /** Web cargo detector loop A / B. The message reports the state the loop has now (the old value is the one shown). */
    fun toggleCargoLoop(s: SimState, loopA: Boolean): Outcome {
        val old = if (loopA) s.cargoLoopA else s.cargoLoopB
        val next = edited(s) { if (loopA) cargoLoopA = !old else cargoLoopB = !old }
        val label = if (loopA) "A" else "B"
        return Outcome(next, "Cargo detector loop $label ${if (old) "FAILED" else "OK"}.")
    }

    /** Web cargo fire injection. The level is raised to at least 0.25 (FWD and AFT). */
    fun injectCargoFire(s: SimState, forward: Boolean): Outcome {
        val next = edited(s) {
            if (forward) {
                cargoFwdFire = true
                cargoFwdLevel = maxOf(0.25, s.cargoFwdLevel)
            } else {
                cargoAftFire = true
                cargoAftLevel = maxOf(0.25, s.cargoAftLevel)
            }
        }
        val msg = if (forward) "Fire injected: FWD cargo compartment smoke / fire." else "Fire injected: AFT cargo compartment smoke / fire."
        return Outcome(next, msg)
    }

    /** Web wheel well overheat injection. The level is raised to at least 0.15. */
    fun injectWheelWellFire(s: SimState): Outcome {
        val next = edited(s) {
            wheelWellFire = true
            wheelWellLevel = maxOf(0.15, s.wheelWellLevel)
        }
        return Outcome(next, "Overheat injected: main wheel well detection loop.")
    }

    /** Web gear lever UP / DOWN in the wheel well panel. */
    fun setGearLever(s: SimState, down: Boolean): Outcome {
        val next = edited(s) { gear = if (down) "DN" else "UP" }
        val msg = if (down) "Landing gear lever DOWN — gear extends, airflow cools the wheel well."
        else "Landing gear lever UP (gear retracts into the wheel well)."
        return Outcome(next, msg)
    }

    /** Web inject(k): sets one detected fire. */
    fun injectFault(s: SimState, key: String): Outcome {
        val next = edited(s) {
            when (key) {
                "engineFire1" -> faults.engineFire1 = true
                "engineFire2" -> faults.engineFire2 = true
                "apuFireDetected" -> faults.apuFireDetected = true
                else -> throw IllegalArgumentException("unknown fire fault: $key")
            }
        }
        val label = when (key) {
            "engineFire1" -> "ENG 1"
            "engineFire2" -> "ENG 2"
            else -> "APU"
        }
        return Outcome(next, "Fire injected: $label.")
    }

    /** Web clearAll: handles reset, bottles recharged, fires and cargo state cleared. */
    fun clearAll(s: SimState): Outcome {
        val next = edited(s) { clearFireState() }
        return Outcome(next, "Fire trainer: fires cleared, handles reset, bottles recharged.")
    }

    /**
     * Web setup: the APU ready state first (SimActions.readyApu), then engines and APU running with the gear down and
     * every bottle charged.
     */
    fun setupRunning(s: SimState, nowMs: Double): Outcome {
        val base = SimActions.readyApu(nowMs)
        val next = edited(base) {
            battery = true; gen1 = true; gen2 = true; bleed1 = true; bleed2 = true; lever1 = true; lever2 = true
            n1_1 = 35.0; n1_2 = 35.0; n2_1 = 62.0; n2_2 = 62.0; egt1 = 380.0; egt2 = 380.0
            engHyd1 = true; engHyd2 = true; elecHyd1 = true; elecHyd2 = true
            hydAPress = 3000.0; hydBPress = 3000.0
            gear = "DN"; noseGearPos = 1.0; leftMainGearPos = 1.0; rightMainGearPos = 1.0; gearPos = 1.0
            clearFireState()
        }
        return Outcome(next, "Fire trainer: engines and APU running, gear down, all bottles charged.")
    }

    /** The fire fields both clearAll and setup reset (web patch lists). */
    private fun SimState.clearFireState() {
        engine1FirePulled = false
        engine2FirePulled = false
        apuFirePulled = false
        apuBottleFired = false
        fireBottleLDischarged = false
        fireBottleRDischarged = false
        wheelWellFire = false
        wheelWellLevel = 0.0
        cargoFwdFire = false
        cargoAftFire = false
        cargoFwdLevel = 0.0
        cargoAftLevel = 0.0
        cargoArmed = "NONE"
        cargoBottleDischarged = false
        cargoAgentTimer = 0.0
        faults.engineFire1 = false
        faults.engineFire2 = false
        faults.apuFireDetected = false
    }
}
