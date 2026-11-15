package com.oersted.trainer.sim

/**
 * Handler rules behind the Electrical Power section of the TRAINING console (components/panel/ElectricalPowerTrainer.tsx,
 * the control buttons). Each function returns the new state and its log line (null = no line). The APU control
 * buttons reuse [ApuTrainerRules.toggleGen], which is the same web handler. The wiring diagram stays in the view.
 */
object ElectricalTrainerRules {
    data class Outcome(val state: SimState, val message: String?)

    private inline fun edited(s: SimState, edit: SimState.() -> Unit): SimState {
        val c = s.copy()
        c.edit()
        return c
    }

    /** Web "GROUND CART": connecting or disconnecting the GPU also drops GRD PWR. */
    fun toggleGroundCart(s: SimState): SimState = edited(s) {
        externalPowerConnected = !s.externalPowerConnected
        groundPowerOn = false
    }

    /** Web BATTERY button. */
    fun toggleBattery(s: SimState): SimState = edited(s) { battery = !s.battery }

    /** Web BUS TRANS AUTO button. */
    fun toggleBusTie(s: SimState): SimState = edited(s) { busTie = !s.busTie }

    /** Web GEN 1 / GEN 2 buttons. Enabled only while the engine runs (see [genEnabled]). */
    fun toggleGen(s: SimState, d: Derived, engine: Int): SimState {
        if (!genEnabled(d, engine)) return s
        return edited(s) { if (engine == 1) gen1 = !s.gen1 else gen2 = !s.gen2 }
    }

    fun genEnabled(d: Derived, engine: Int): Boolean = if (engine == 1) d.eng1Running else d.eng2Running

    /** Web GRD PWR ON / OFF. Enabled only with the GPU connected. Turning it on overrides the ground service switch. */
    fun toggleGroundPower(s: SimState): Outcome {
        val next = !s.groundPowerOn
        val st = edited(s) {
            groundPowerOn = next
            if (next) groundServiceOn = false
        }
        val msg = if (next) "GROUND POWER ON \u2014 ground service switch overridden by normal external-power distribution."
        else "GROUND POWER OFF"
        return Outcome(st, msg)
    }

    /** Web GRD PWR is enabled only with the ground cart connected. */
    fun groundPowerEnabled(s: SimState): Boolean = s.externalPowerConnected

    /** Web GROUND SERVICE button: enabled with ground power or AC power available. */
    fun toggleGroundService(s: SimState, d: Derived): SimState {
        if (!(d.groundPowerAvailable || d.acPower)) return s
        return edited(s) { groundServiceOn = !s.groundServiceOn }
    }

    /** Web STBY OFF / AUTO / BAT selector. */
    fun setStandbyPower(s: SimState, mode: String): SimState = edited(s) { standbyPower = mode }

    /** Web APU CONTROL LEFT / RIGHT. Same handler as the APU section; enabled only with APU AVAILABLE. */
    fun toggleApuControl(s: SimState, d: Derived, left: Boolean): SimState = ApuTrainerRules.toggleGen(s, d, left)

    /**
     * Web APU MASTER button (electrical panel). OFF starts the APU: battery power is applied unless external DC control
     * already supplies the starter. Any other state switches the APU off and drops its generator controls.
     */
    fun toggleApuMaster(s: SimState, d: Derived): Outcome {
        if (s.apuMaster == "OFF") {
            val msg = if (d.externalDcControl) "APU START command \u2014 external electrical power supplying the starter circuit."
            else "APU START command \u2014 battery power applied for the electric APU starter."
            val st = edited(s) {
                if (!d.externalDcControl) battery = true
                apuMaster = "START"
            }
            return Outcome(st, msg)
        }
        val st = edited(s) {
            apuMaster = "OFF"
            apuGen = false
            apuLeftControl = false
            apuRightControl = false
        }
        return Outcome(st, null)
    }
}
