package com.oersted.trainer.sim

/**
 * Handler rules behind the Ice & Rain Protection section of the TRAINING console (components/panel/IceRainTrainer.tsx,
 * ATA 30). Each function is one web handler and returns the new state with its log line (null = no line).
 * The rain and wing-test switches are local to the web panel and stay in the view, as do the hold-to-test buttons' timing.
 */
object IceRainRules {
    data class Outcome(val state: SimState, val message: String?)

    private inline fun edited(s: SimState, edit: SimState.() -> Unit): SimState {
        val c = s.copy()
        c.edit()
        return c
    }

    /** Web setIcing: icing conditions ON or OFF. */
    fun setIcing(s: SimState, on: Boolean): Outcome =
        Outcome(edited(s) { icingConditions = on }, "ICING CONDITIONS ${if (on) "ON" else "OFF"}")

    /** Web setWh: a window heat switch. Switching it OFF also clears that window's overheat latch. */
    fun setWindowHeat(s: SimState, key: String, on: Boolean): Outcome {
        val next = edited(s) {
            when (key) {
                "windowHeatLFwd" -> { windowHeatLFwd = on; if (!on) windowHeatOvhtLFwd = false }
                "windowHeatLSide" -> { windowHeatLSide = on; if (!on) windowHeatOvhtLSide = false }
                "windowHeatRFwd" -> { windowHeatRFwd = on; if (!on) windowHeatOvhtRFwd = false }
                "windowHeatRSide" -> { windowHeatRSide = on; if (!on) windowHeatOvhtRSide = false }
                else -> throw IllegalArgumentException("not a window heat switch: $key")
            }
        }
        return Outcome(next, "WINDOW HEAT ${key.removePrefix("windowHeat")} ${if (on) "ON" else "OFF"}")
    }

    /** Web hold-to-test on the window heat test switch. [mode] is "PWR" or "OVHT" when held, "OFF" when released. */
    fun setWindowHeatTest(s: SimState, mode: String): Outcome {
        val msg = when (mode) {
            "PWR" -> "Window heat test \u2014 PWR (full power forced)."
            "OVHT" -> "Window heat test \u2014 OVHT."
            else -> "Window heat test released."
        }
        return Outcome(edited(s) { windowHeatTest = mode }, msg)
    }

    /**
     * Web overheat latch effect: a window that trips at about 145 \u00b0F latches its overheat light. Returns null when no
     * window tripped this step.
     */
    fun latchWindowOverheat(s: SimState, tripL1: Boolean, tripL2: Boolean, tripR1: Boolean, tripR2: Boolean): Outcome? {
        if (!tripL1 && !tripL2 && !tripR1 && !tripR2) return null
        val next = edited(s) {
            if (tripL1) windowHeatOvhtLFwd = true
            if (tripL2) windowHeatOvhtLSide = true
            if (tripR1) windowHeatOvhtRFwd = true
            if (tripR2) windowHeatOvhtRSide = true
        }
        return Outcome(next, "WINDOW HEAT OVERHEAT \u2014 power cut at \u2248145 \u00b0F. Cycle the window switch OFF to reset.")
    }

    /** Web setWipers: both wipers to the same mode. */
    fun setWipers(s: SimState, mode: String): Outcome {
        val msg = if (mode == "PARK") "WIPERS \u2014 PARK (drive to park position)" else "WIPERS $mode"
        return Outcome(edited(s) { wiperL = mode; wiperR = mode }, msg)
    }

    /** Web probe heat switches (Rocker). Only the alpha vanes write a log line. */
    fun setProbeHeat(s: SimState, key: String, on: Boolean): Outcome {
        val next = edited(s) {
            when (key) {
                "probeHeatElevL" -> probeHeatElevL = on
                "probeHeatElevR" -> probeHeatElevR = on
                "probeHeatTemp" -> probeHeatTemp = on
                "probeHeatAlphaL" -> probeHeatAlphaL = on
                "probeHeatAlphaR" -> probeHeatAlphaR = on
                "probeHeatCaptPitot" -> probeHeatCaptPitot = on
                "probeHeatFoPitot" -> probeHeatFoPitot = on
                else -> throw IllegalArgumentException("not a probe heat switch: $key")
            }
        }
        val msg = when (key) {
            "probeHeatAlphaL" -> "LEFT ALPHA VANE HEAT ${if (on) "ON" else "OFF"}"
            "probeHeatAlphaR" -> "RIGHT ALPHA VANE HEAT ${if (on) "ON" else "OFF"}"
            else -> null
        }
        return Outcome(next, msg)
    }

    /** Web engine anti-ice switches (no log line). */
    fun setEngineAntiIce(s: SimState, engine: Int, on: Boolean): SimState =
        edited(s) { if (engine == 1) antiIce1 = on else antiIce2 = on }

    /**
     * Web wing anti-ice switches. On the ground the valve is inhibited, so the message says so.
     */
    fun setWingAntiIce(s: SimState, left: Boolean, on: Boolean): Outcome {
        val next = edited(s) { if (left) wingAntiIceL = on else wingAntiIceR = on }
        val side = if (left) "LEFT" else "RIGHT"
        val msg = if (on) {
            if (s.wow) "$side WING ANTI-ICE SELECTED \u2014 valve inhibited on ground." else "$side WING ANTI-ICE ON."
        } else "$side WING ANTI-ICE OFF."
        return Outcome(next, msg)
    }

    /** Web fuel heat switches. Turning a heater on restarts its elapsed timer. */
    fun setFuelHeat(s: SimState, engine: Int, on: Boolean): SimState = edited(s) {
        if (engine == 1) {
            fuelHeat = on
            if (on) fuelHeatElapsedSec = 0.0
        } else {
            fuelHeat2 = on
            if (on) fuelHeat2ElapsedSec = 0.0
        }
    }

    /** Web "All window heat ON": every switch on, test off, overheat latches cleared. */
    fun allWindowHeatOn(s: SimState): Outcome = Outcome(edited(s) {
        windowHeatLFwd = true; windowHeatRFwd = true; windowHeatLSide = true; windowHeatRSide = true
        windowHeatTest = "OFF"
        windowHeatOvhtLFwd = false; windowHeatOvhtLSide = false; windowHeatOvhtRFwd = false; windowHeatOvhtRSide = false
    }, "All window heat ON.")

    /** Web "All probe heat ON". */
    fun allProbeHeatOn(s: SimState): Outcome = Outcome(edited(s) {
        probeHeatCaptPitot = true; probeHeatFoPitot = true; probeHeatElevL = true; probeHeatElevR = true
        probeHeatTemp = true; probeHeatAlphaL = true; probeHeatAlphaR = true
    }, "All probe heat ON.")

    /** Web "Eng + wing AI ON". */
    fun engineAndWingAntiIceOn(s: SimState): Outcome = Outcome(edited(s) {
        antiIce1 = true; antiIce2 = true; wingAntiIceL = true; wingAntiIceR = true
    }, "Engine + wing anti-ice ON.")

    /** Web "OVHT test (FWD on)". */
    fun overheatTestForward(s: SimState): Outcome = Outcome(edited(s) {
        windowHeatTest = "OVHT"; windowHeatLFwd = true; windowHeatRFwd = true
    }, "OVHT test with FWD windows ON \u2014 overheat lights should illuminate.")

    /** Web "All OFF / PARK": every heater and anti-ice switch off, wipers parked. The rain and wing-test UI stays local. */
    fun allOffPark(s: SimState): Outcome = Outcome(edited(s) {
        windowHeatLFwd = false; windowHeatLSide = false; windowHeatRFwd = false; windowHeatRSide = false
        windowHeatTest = "OFF"
        windowHeatOvhtLFwd = false; windowHeatOvhtLSide = false; windowHeatOvhtRFwd = false; windowHeatOvhtRSide = false
        probeHeatCaptPitot = false; probeHeatFoPitot = false; probeHeatElevL = false; probeHeatElevR = false
        probeHeatTemp = false; probeHeatAlphaL = false; probeHeatAlphaR = false
        antiIce1 = false; antiIce2 = false; wingAntiIceL = false; wingAntiIceR = false
        fuelHeat = false; fuelHeat2 = false; fuelHeatElapsedSec = 0.0; fuelHeat2ElapsedSec = 0.0
        wiperL = "PARK"; wiperR = "PARK"
    }, "Ice & rain \u2014 all OFF / PARK.")
}
