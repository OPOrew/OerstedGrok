package com.oersted.trainer.sim

/**
 * Rules of the FIRE / OVERHEAT panel (components/panel/CenterPedestal.tsx FirePanel, lines 898 to 957): handle lock and pull notes,
 * bottle discharge, test lamps. Each action returns the log note and the edit to apply to a copy of the sim state, the way the web calls
 * `s.patch(patch, note)`. Pure; the Compose panel is ui/pedestal/PedestalFirePanel.kt.
 */
object FirePanelRules {
    /** [edit] is applied to a copy of the state by CockpitModel.patchSimNote. */
    class Action(val note: String, val edit: SimState.() -> Unit)

    private fun pulled(s: SimState, n: Int) = if (n == 1) s.engine1FirePulled else s.engine2FirePulled
    private fun detected(s: SimState, n: Int) = if (n == 1) s.faults.engineFire1 else s.faults.engineFire2

    /** True when the engine fire handle cannot be pulled: no fire warning and no FIRE SWITCHES LOCK OVERRIDE. */
    fun engineLocked(s: SimState, n: Int, override: Boolean): Boolean = !override && !detected(s, n)

    fun apuLocked(s: SimState, override: Boolean): Boolean = !override && !s.faults.apuFireDetected

    /** `pullEngine`: a locked handle only logs; otherwise the handle toggles. */
    fun pullEngine(s: SimState, n: Int, override: Boolean): Action {
        val p = pulled(s, n)
        if (!p && !detected(s, n) && !override) {
            return Action("ENG $n FIRE handle LOCKED — no fire warning. Use FIRE SWITCHES LOCK OVERRIDE.") {}
        }
        return Action(
            if (p) "ENG $n FIRE handle reset."
            else "ENG $n FIRE handle pulled — engine fuel, bleed, generator and engine hydraulic pump isolated.",
        ) { if (n == 1) engine1FirePulled = !p else engine2FirePulled = !p }
    }

    /** `dischargeEngine`: only with the handle pulled and the bottle not yet fired; also clears that engine's fire fault. */
    fun dischargeEngine(s: SimState, n: Int, bottle: Char): Action? {
        val already = if (bottle == 'L') s.fireBottleLDischarged else s.fireBottleRDischarged
        if (!pulled(s, n) || already) return null
        return Action("ENG $n fire handle rotated ${if (bottle == 'L') "LEFT" else "RIGHT"} — bottle $bottle discharged.") {
            if (bottle == 'L') fireBottleLDischarged = true else fireBottleRDischarged = true
            if (n == 1) faults.engineFire1 = false else faults.engineFire2 = false
        }
    }

    /** `pullApu`. */
    fun pullApu(s: SimState, override: Boolean): Action {
        if (!s.apuFirePulled && !s.faults.apuFireDetected && !override) {
            return Action("APU FIRE handle LOCKED — no fire warning. Use FIRE SWITCHES LOCK OVERRIDE.") {}
        }
        val p = s.apuFirePulled
        return Action(
            if (p) "APU fire handle reset." else "APU FIRE handle pulled — APU shut down, fuel, bleed and generator isolated.",
        ) { apuFirePulled = !p }
    }

    /** `dischApu`: only with the handle pulled and the bottle unfired. */
    fun dischargeApu(s: SimState): Action? {
        if (!s.apuFirePulled || s.apuBottleFired) return null
        return Action("APU fire bottle discharged — simulated extinguishing agent release.") {
            apuBottleFired = true
            faults.apuFireDetected = false
        }
    }

    /** `ovhtOn(n)`: ENG n OVERHEAT lens: powered, and the test or that bleed overheat fault. */
    fun overheatLit(powered: Boolean, testOvhtFire: Boolean, s: SimState, n: Int): Boolean =
        powered && (testOvhtFire || (if (n == 1) s.faults.bleedOverheat1 else s.faults.bleedOverheat2))

    /** `extLamp`: lit only while EXT TEST is held, powered, and that bottle's squib is intact (not yet fired). */
    fun extLampLit(powered: Boolean, extTestHeld: Boolean, intact: Boolean): Boolean = powered && extTestHeld && intact

    /** The bell cutout latches until every fire warning is gone (the useEffect of FirePanel). */
    fun bellShouldReset(anyWarn: Boolean, silenced: Boolean): Boolean = !anyWarn && silenced

    /** True when any fire warning is active (FirePanel `anyWarn`). */
    fun anyWarn(d: Derived): Boolean =
        d.engine1FireWarn || d.engine2FireWarn || d.apuFireWarn || d.wheelWellFireWarn || d.cargoFwdFireWarn || d.cargoAftFireWarn

    /** Rotate-button labels of a pulled handle: the engine handles turn L or R, the APU handle has the one DISCH. */
    fun rotateLabels(red: Boolean): List<String> = if (red) listOf("L", "R") else listOf("DISCH")
}
