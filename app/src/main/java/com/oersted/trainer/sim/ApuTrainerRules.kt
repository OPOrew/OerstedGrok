package com.oersted.trainer.sim

/**
 * Rules behind the APU section of the TRAINING console (components/panel/ApuTrainer.tsx).
 * Switch actions copy the state and return the web's log message (null = no line). The start sequence and the
 * AUTO START demo step are pure functions of the state and the derived values, so the view only renders them.
 */
object ApuTrainerRules {
    /** Start-sequence step state, as the web's StepState. */
    enum class StepState { DONE, ACTIVE, PENDING, BLOCKED }

    /** One step of the start sequence (web Step). [blocked] is the reason text when the step cannot complete. */
    data class Step(val id: String, val title: String, val detail: String, val done: Boolean, val blocked: String? = null)

    /** Result of a switch action: the new state and the log line, if any. */
    data class Action(val state: SimState, val message: String?)

    /** Result of one AUTO START demo step (web effect). */
    sealed class AutoAction {
        /** Stop the demo (APU available, fire handle pulled, door jammed, or battery fault). */
        object Stop : AutoAction()
        object SetBattery : AutoAction()
        object SetMasterOn : AutoAction()
        object SetMasterStart : AutoAction()
    }

    const val START_LOG = "APU START command — inlet door opening / starter lockout sequence initiated."
    const val START_INHIBITED_LOG = "APU START inhibited — no 28V DC starter/control power available."

    private inline fun edited(s: SimState, edit: SimState.() -> Unit): SimState {
        val c = s.copy()
        c.edit()
        return c
    }

    /** Web doorJammed: the door is stuck part-way while the APU is selected and fuel is open. */
    fun doorJammed(s: SimState): Boolean =
        s.faults.apuDoorJam && s.apuMaster != "OFF" && s.apuFuelValve > 0.98 && s.apuDoor < 0.98

    /** Web annBus: annunciators stay dark until essential DC or battery power is there. */
    fun annunciatorBus(d: Derived): Boolean = d.dcPower || d.batteryPower

    /** Web lampTest: the MASTER DIM & TEST switch with annunciator power. */
    fun lampTest(s: SimState, d: Derived): Boolean = s.ltTest && annunciatorBus(d)

    /** Web CaptionLight "on" expression: lampTest || (annBus && condition). */
    fun captionLit(s: SimState, d: Derived, condition: Boolean): Boolean =
        lampTest(s, d) || (annunciatorBus(d) && condition)

    /** Web setMaster: START without starter or control power is refused with a log line. */
    fun setMaster(s: SimState, d: Derived, master: String): Action {
        if (master == "START" && !(d.batteryPower || d.externalDcControl)) {
            return Action(edited(s) { apuMaster = "OFF" }, START_INHIBITED_LOG)
        }
        val msg = if (master == "START") START_LOG else null
        return Action(edited(s) { apuMaster = master }, msg)
    }

    /** Web START EFFECTIVITY button: flips the 35%/60 s and 50%/90 s setting and resets the start timer. */
    fun toggleStartEffectivity(s: SimState): Action {
        val next = !s.apuStart50PercentEffectivityInstalled
        val msg = if (next) "APU START EFFECTIVITY: 50% CUTOUT / 90-SECOND TIMER" else "APU START EFFECTIVITY: 35% CUTOUT / 60-SECOND TIMER"
        return Action(edited(s) {
            apuStart50PercentEffectivityInstalled = next
            apuStartTimer = 0.0
        }, msg)
    }

    fun toggleBattery(s: SimState): SimState = edited(s) { battery = !s.battery }

    fun toggleApuBleed(s: SimState): SimState = edited(s) { apuBleed = !s.apuBleed }

    /** Web APU GEN L / R buttons: disabled until APU AVAILABLE; apuGen follows either control. */
    fun toggleGen(s: SimState, d: Derived, left: Boolean): SimState {
        if (!d.apuAvailable) return s
        return if (left) {
            val v = !s.apuLeftControl
            edited(s) { apuLeftControl = v; apuGen = v || s.apuRightControl }
        } else {
            val v = !s.apuRightControl
            edited(s) { apuRightControl = v; apuGen = s.apuLeftControl || v }
        }
    }

    /** Web SHUTDOWN APU: only while the APU is running. */
    fun shutdown(s: SimState, d: Derived): SimState {
        if (!d.apuAvailable) return s
        return edited(s) {
            apuMaster = "OFF"
            apuBleed = false
            apuLeftControl = false
            apuRightControl = false
            apuGen = false
        }
    }

    /**
     * The start-sequence steps in web order, with done / blocked. The view takes the first undone step as ACTIVE.
     */
    fun startSteps(s: SimState, d: Derived): List<Step> {
        val fifty = s.apuStart50PercentEffectivityInstalled
        val cutout = if (fifty) 50 else 35
        val jam = doorJammed(s)
        return listOf(
            Step("bat", "BAT switch ON", "Battery / DC control power to the APU start circuit", d.batteryPower),
            Step("fuel", "APU MASTER → ON: fuel valve opens", "APU fuel shutoff valve opens",
                s.apuMaster != "OFF" && s.apuFuelValve > 0.95),
            Step("door", "Inlet door fully open (~10 s)", "Starter lockout relay holds the start until the door is open",
                s.apuDoor > 0.98, if (jam) "Inlet door jammed — start cannot complete" else null),
            Step("start", "APU MASTER → START (momentary)", "Electric starter engages and motors the APU",
                s.apuStarter || s.apuRpm >= 12),
            Step("motor", "Starter to ~$cutout%: start relay drops out",
                "Master returns to ON, starter disengages, turbine self-accelerates", s.apuRpm >= cutout),
            Step("oil", "Oil pressure ≥ 55 psi",
                "APU LOW OIL PRESSURE light goes out (normal while below 55 psi during start)",
                s.apuMaster != "OFF" && s.apuOilPsi >= 55),
            Step("avail", "APU AVAILABLE at 95% RPM", "APU may now supply electrical and pneumatic loads", d.apuAvailable),
            Step("gen", "APU GEN on bus (L and/or R control)", "Generator connects to the tie bus via BTB 1 / 2", d.apuGenOnBus),
            Step("bleed", "APU BLEED valve open (~13 s)", "Bleed air to the left pneumatic manifold",
                s.apuBleed && s.apuBleedValve > 0.7),
        )
    }

    /** Web stateOf: done, else blocked, else the first undone step is ACTIVE, the rest PENDING. */
    fun stepStates(steps: List<Step>): List<StepState> {
        val activeIdx = steps.indexOfFirst { !it.done }
        return steps.mapIndexed { i, st ->
            when {
                st.done -> StepState.DONE
                st.blocked != null -> StepState.BLOCKED
                i == activeIdx -> StepState.ACTIVE
                else -> StepState.PENDING
            }
        }
    }

    /**
     * One tick of the AUTO START (DEMO) effect. Returns null while the demo is between steps with nothing to do.
     * Web order: stop checks first, then battery, then master ON, then master START once the door is open and RPM is low.
     */
    fun autoStep(s: SimState, d: Derived): AutoAction? {
        if (d.apuAvailable || s.apuFirePulled || doorJammed(s)) return AutoAction.Stop
        if (!(d.batteryPower || d.externalDcControl)) {
            return if (s.battery) AutoAction.Stop else AutoAction.SetBattery
        }
        if (s.apuMaster == "OFF") return AutoAction.SetMasterOn
        if (s.apuMaster == "ON" && s.apuDoor > 0.98 && s.apuRpm < 12) return AutoAction.SetMasterStart
        return null
    }
}
