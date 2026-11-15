package com.oersted.trainer.sim

/**
 * Rules behind the TRAINING console (routes/cockpit.tsx TrainingDrawer / TrainingConsole, lines 197-355).
 * The web's s.patch calls with no note or changedKey write no log line, so these are plain copy-and-set functions.
 * The section panels that depend on other trainers are not ported yet; only the shell and the toggles on
 * the Flight Controls, Landing Gear and Brakes / Antiskid sections are here.
 */
object TrainingConsoleRules {
    /** Section names in the web order (the console's grid buttons). */
    val SECTIONS: List<String> = listOf(
        "Flight Controls", "Landing Gear", "Electrical Power", "Engine Start", "Hydraulics", "Pneumatic",
        "Air Conditioning", "Cabin Pressurization", "APU", "Fuel", "Brakes / Antiskid", "Fire Protection",
        "Ice & Rain", "Pitot / Static", "Navigation", "Autopilot", "Lights", "Fault Injection",
        "Component / System Flow",
    )

    /** The console opens on Hydraulics (web useState("Hydraulics")). */
    const val DEFAULT_SECTION = "Hydraulics"

    /** Returns a copy of [s] with [edit] applied; [s] itself is not changed. */
    private inline fun edited(s: SimState, edit: SimState.() -> Unit): SimState {
        val c = s.copy()
        c.edit()
        return c
    }

    fun toggleFltCtrlA(s: SimState): SimState = edited(s) { fltCtrlA = !s.fltCtrlA }
    fun toggleFltCtrlB(s: SimState): SimState = edited(s) { fltCtrlB = !s.fltCtrlB }
    fun toggleSpoilerA(s: SimState): SimState = edited(s) { spoilerA = !s.spoilerA }
    fun toggleSpoilerB(s: SimState): SimState = edited(s) { spoilerB = !s.spoilerB }

    /** Web: "Center flight controls" zeroes the yoke, rudder pedals and tiller. */
    fun centerFlightControls(s: SimState): SimState = edited(s) {
        yokePitch = 0.0
        yokeRoll = 0.0
        rudderPedal = 0.0
        tiller = 0.0
    }

    /** Web: "Center tiller & pedals". */
    fun centerTillerAndPedals(s: SimState): SimState = edited(s) {
        tiller = 0.0
        rudderPedal = 0.0
    }

    fun toggleGroundSenseTest(s: SimState): SimState = edited(s) { groundSenseTest = !s.groundSenseTest }
    fun toggleAirborneTest(s: SimState): SimState = edited(s) { airborneTest = !s.airborneTest }
    fun toggleHydServicingConnected(s: SimState): SimState = edited(s) { hydServicingConnected = !s.hydServicingConnected }
    fun toggleExternalPowerConnected(s: SimState): SimState = edited(s) { externalPowerConnected = !s.externalPowerConnected }
    fun toggleParkingBrake(s: SimState): SimState = edited(s) { parkingBrake = !s.parkingBrake }

    fun toggleAntiskidInbd(s: SimState): SimState = edited(s) { antiskidInbd = !s.antiskidInbd }
    fun toggleAntiskidOutbd(s: SimState): SimState = edited(s) { antiskidOutbd = !s.antiskidOutbd }

    /** Web: setAutobrake(0..4) for OFF, MIN, MED, MAX, RTO. */
    fun setAutobrake(s: SimState, level: Int): SimState {
        require(level in 0..4) { "autobrake level must be 0..4, was $level" }
        return edited(s) { autobrake = level.toDouble() }
    }

    /** Labels for the autobrake row, in web order. */
    val AUTOBRAKE_LABELS: List<Pair<Int, String>> = listOf(0 to "OFF", 1 to "MIN", 2 to "MED", 3 to "MAX", 4 to "RTO")

    /** Web button text, e.g. "FLT CTRL A · ON". */
    fun onOff(on: Boolean): String = if (on) "ON" else "OFF"
}
