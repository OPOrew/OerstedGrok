package com.oersted.trainer.sim

/**
 * Handler rules behind the Pitot / Static section of the TRAINING console (components/panel/PitotStaticTrainer.tsx).
 * Each function is one web handler and returns the new state. Flip messages are "<label> → ON/OFF" and are built by
 * the view from the value returned by [flip]; the fixed messages are returned here as the web writes them.
 */
object PitotStaticRules {
    private class Flag(val get: (SimState) -> Boolean, val set: (SimState, Boolean) -> Unit)

    /** Every boolean the web's flip() accepts (its BoolKey type). */
    private val FLAGS: Map<String, Flag> = mapOf(
        "psPitotCaptCover" to Flag({ it.psPitotCaptCover }, { s, v -> s.psPitotCaptCover = v }),
        "psPitotFoCover" to Flag({ it.psPitotFoCover }, { s, v -> s.psPitotFoCover = v }),
        "psPitotAux1Cover" to Flag({ it.psPitotAux1Cover }, { s, v -> s.psPitotAux1Cover = v }),
        "psPitotAux2Cover" to Flag({ it.psPitotAux2Cover }, { s, v -> s.psPitotAux2Cover = v }),
        "psPitotCaptBlocked" to Flag({ it.psPitotCaptBlocked }, { s, v -> s.psPitotCaptBlocked = v }),
        "psPitotFoBlocked" to Flag({ it.psPitotFoBlocked }, { s, v -> s.psPitotFoBlocked = v }),
        "psPitotAux1Blocked" to Flag({ it.psPitotAux1Blocked }, { s, v -> s.psPitotAux1Blocked = v }),
        "psPitotAux2Blocked" to Flag({ it.psPitotAux2Blocked }, { s, v -> s.psPitotAux2Blocked = v }),
        "psElevPitotLBlocked" to Flag({ it.psElevPitotLBlocked }, { s, v -> s.psElevPitotLBlocked = v }),
        "psElevPitotRBlocked" to Flag({ it.psElevPitotRBlocked }, { s, v -> s.psElevPitotRBlocked = v }),
        "psAltStaticCapt" to Flag({ it.psAltStaticCapt }, { s, v -> s.psAltStaticCapt = v }),
        "psAltStaticFo" to Flag({ it.psAltStaticFo }, { s, v -> s.psAltStaticFo = v }),
        "psIcing" to Flag({ it.psIcing }, { s, v -> s.psIcing = v }),
        "psStaticCaptLeak" to Flag({ it.psStaticCaptLeak }, { s, v -> s.psStaticCaptLeak = v }),
        "psStaticFoLeak" to Flag({ it.psStaticFoLeak }, { s, v -> s.psStaticFoLeak = v }),
        "psAux1StaticLeak" to Flag({ it.psAux1StaticLeak }, { s, v -> s.psAux1StaticLeak = v }),
        "psAux2StaticLeak" to Flag({ it.psAux2StaticLeak }, { s, v -> s.psAux2StaticLeak = v }),
        "psAltStaticCaptLeak" to Flag({ it.psAltStaticCaptLeak }, { s, v -> s.psAltStaticCaptLeak = v }),
        "psAltStaticFoLeak" to Flag({ it.psAltStaticFoLeak }, { s, v -> s.psAltStaticFoLeak = v }),
        "psStaticSelectorCaptLeak" to Flag({ it.psStaticSelectorCaptLeak }, { s, v -> s.psStaticSelectorCaptLeak = v }),
        "psStaticSelectorFoLeak" to Flag({ it.psStaticSelectorFoLeak }, { s, v -> s.psStaticSelectorFoLeak = v }),
        "psPitotCaptLeak" to Flag({ it.psPitotCaptLeak }, { s, v -> s.psPitotCaptLeak = v }),
        "psPitotFoLeak" to Flag({ it.psPitotFoLeak }, { s, v -> s.psPitotFoLeak = v }),
        "psAux1PitotLeak" to Flag({ it.psAux1PitotLeak }, { s, v -> s.psAux1PitotLeak = v }),
        "psAux2PitotLeak" to Flag({ it.psAux2PitotLeak }, { s, v -> s.psAux2PitotLeak = v }),
        "psTestPressureSource" to Flag({ it.psTestPressureSource }, { s, v -> s.psTestPressureSource = v }),
        "psTestVacuumSource" to Flag({ it.psTestVacuumSource }, { s, v -> s.psTestVacuumSource = v }),
        "psMachWarningTest" to Flag({ it.psMachWarningTest }, { s, v -> s.psMachWarningTest = v }),
        "psAirspeedWarningInhibit" to Flag({ it.psAirspeedWarningInhibit }, { s, v -> s.psAirspeedWarningInhibit = v }),
        "psPitotStaticA" to Flag({ it.psPitotStaticA }, { s, v -> s.psPitotStaticA = v }),
        "psPitotStaticB" to Flag({ it.psPitotStaticB }, { s, v -> s.psPitotStaticB = v }),
        "psCbCaptUpperL" to Flag({ it.psCbCaptUpperL }, { s, v -> s.psCbCaptUpperL = v }),
        "psCbCaptLowerL" to Flag({ it.psCbCaptLowerL }, { s, v -> s.psCbCaptLowerL = v }),
        "psCbFoUpperR" to Flag({ it.psCbFoUpperR }, { s, v -> s.psCbFoUpperR = v }),
        "psCbFoLowerR" to Flag({ it.psCbFoLowerR }, { s, v -> s.psCbFoLowerR = v }),
        "psCbElevL" to Flag({ it.psCbElevL }, { s, v -> s.psCbElevL = v }),
        "psCbElevR" to Flag({ it.psCbElevR }, { s, v -> s.psCbElevR = v }),
        "psCbTempL" to Flag({ it.psCbTempL }, { s, v -> s.psCbTempL = v }),
        "psCbTempR" to Flag({ it.psCbTempR }, { s, v -> s.psCbTempR = v }),
        "psCbTemp" to Flag({ it.psCbTemp }, { s, v -> s.psCbTemp = v }),
        "psCadc1Cb" to Flag({ it.psCadc1Cb }, { s, v -> s.psCadc1Cb = v }),
        "psCadc2Cb" to Flag({ it.psCadc2Cb }, { s, v -> s.psCadc2Cb = v }),
        "psCadc1Ac115" to Flag({ it.psCadc1Ac115 }, { s, v -> s.psCadc1Ac115 = v }),
        "psCadc1Ac26" to Flag({ it.psCadc1Ac26 }, { s, v -> s.psCadc1Ac26 = v }),
        "psCadc2Ac115" to Flag({ it.psCadc2Ac115 }, { s, v -> s.psCadc2Ac115 = v }),
        "psCadc2Ac26" to Flag({ it.psCadc2Ac26 }, { s, v -> s.psCadc2Ac26 = v }),
        "psStandbyPitotLeak" to Flag({ it.psStandbyPitotLeak }, { s, v -> s.psStandbyPitotLeak = v }),
        "psStandbyStaticLeak" to Flag({ it.psStandbyStaticLeak }, { s, v -> s.psStandbyStaticLeak = v }),
    )

    /** Flag names the web's flip() accepts. */
    val FLAG_NAMES: Set<String> get() = FLAGS.keys

    private inline fun edited(s: SimState, edit: SimState.() -> Unit): SimState {
        val c = s.copy()
        c.edit()
        return c
    }

    /** Web flip(k): inverts one boolean. Returns the new state and the new value (for the "→ ON/OFF" message). */
    fun flip(s: SimState, key: String): Pair<SimState, Boolean> {
        val f = FLAGS[key] ?: throw IllegalArgumentException("not a Pitot/Static flag: $key")
        val next = !f.get(s)
        val c = s.copy()
        f.set(c, next)
        return Pair(c, next)
    }

    /** Web cycleCadcTest: OFF -> FUNCTION -> SLEW -> FAIL -> OFF. */
    fun cycleCadcTest(s: SimState, channel: Int): SimState {
        val current = if (channel == 1) s.psCadc1Test else s.psCadc2Test
        val next = when (current) {
            "OFF" -> "FUNCTION"
            "FUNCTION" -> "SLEW"
            "SLEW" -> "FAIL"
            else -> "OFF"
        }
        return edited(s) { if (channel == 1) psCadc1Test = next else psCadc2Test = next }
    }

    /**
     * Web togglePort: blocks or clears one static port. When a port is blocked while its partner on the same side is
     * also blocked, the frozen altitude is captured from the current flight altitude.
     */
    fun togglePort(s: SimState, key: String): Pair<SimState, Boolean> {
        val current = when (key) {
            "psStaticCaptL" -> s.psStaticCaptL
            "psStaticCaptR" -> s.psStaticCaptR
            "psStaticFoL" -> s.psStaticFoL
            "psStaticFoR" -> s.psStaticFoR
            else -> throw IllegalArgumentException("not a static port: $key")
        }
        val next = !current
        val capt = key.startsWith("psStaticCapt")
        val other = when (key) {
            "psStaticCaptL" -> s.psStaticCaptR
            "psStaticCaptR" -> s.psStaticCaptL
            "psStaticFoL" -> s.psStaticFoR
            else -> s.psStaticFoL
        }
        val c = edited(s) {
            when (key) {
                "psStaticCaptL" -> psStaticCaptL = next
                "psStaticCaptR" -> psStaticCaptR = next
                "psStaticFoL" -> psStaticFoL = next
                else -> psStaticFoR = next
            }
            if (next && other) {
                if (capt) psStaticCaptFrozenAltFt = s.psFlightAltFt else psStaticFoFrozenAltFt = s.psFlightAltFt
            }
        }
        return Pair(c, next)
    }

    /** Web "Clear all faults": probe covers, blockages, static ports, leaks, CBs back to closed, test sources off. */
    fun clearFaults(s: SimState): SimState = edited(s) {
        psPitotCaptCover = false; psPitotFoCover = false; psPitotAux1Cover = false; psPitotAux2Cover = false
        psPitotCaptBlocked = false; psPitotFoBlocked = false; psPitotAux1Blocked = false; psPitotAux2Blocked = false
        psElevPitotLBlocked = false; psElevPitotRBlocked = false
        psStaticCaptL = false; psStaticCaptR = false; psStaticFoL = false; psStaticFoR = false
        psAltStaticCapt = false; psAltStaticFo = false
        psStaticCaptLeak = false; psStaticFoLeak = false; psAux1StaticLeak = false; psAux2StaticLeak = false
        psAltStaticCaptLeak = false; psAltStaticFoLeak = false; psStaticSelectorCaptLeak = false; psStaticSelectorFoLeak = false
        psPitotCaptLeak = false; psPitotFoLeak = false; psAux1PitotLeak = false; psAux2PitotLeak = false
        psTestPressureSource = false; psTestVacuumSource = false; psTestTarget = "ALL"
        psPitotStaticA = false; psPitotStaticB = false
        psCbCaptUpperL = true; psCbCaptLowerL = true; psCbFoUpperR = true; psCbFoLowerR = true
        psCbElevL = true; psCbElevR = true; psCbTempL = true; psCbTempR = true; psCbTemp = true
        psCadc1Cb = true; psCadc2Cb = true; psCadc1Ac115 = true; psCadc1Ac26 = true; psCadc2Ac115 = true; psCadc2Ac26 = true
        psStandbyPitotLeak = false; psStandbyStaticLeak = false
        psMachWarningTest = false; psAirspeedWarningInhibit = false; psTestClockSec = 0.0
        psIcing = false
    }

    /** Web flight-condition buttons. Ground zeroes everything; the test profiles set a target and a VSI. */
    fun ground(s: SimState): SimState = edited(s) {
        psFlightTargetAltFt = 0.0; psFlightTargetCasKt = 0.0; psFlightAltFt = 0.0; psFlightCasKt = 0.0
        psFlightVsFpm = 0.0; psFlightProgressive = true
    }

    fun cruiseTest(s: SimState): SimState = edited(s) {
        psFlightTargetAltFt = 10000.0; psFlightTargetCasKt = 180.0; psFlightVsFpm = 0.0; psFlightProgressive = true
    }

    fun climbTest(s: SimState): SimState = edited(s) {
        psFlightTargetAltFt = 10000.0; psFlightTargetCasKt = 180.0; psFlightVsFpm = 1500.0; psFlightProgressive = true
    }

    /** Web pitot pressure test: connecting it disconnects the vacuum source and restarts the test clock. */
    fun togglePressureTest(s: SimState): SimState {
        val next = !s.psTestPressureSource
        return edited(s) { psTestPressureSource = next; psTestVacuumSource = false; psTestClockSec = 0.0 }
    }

    /** Web static vacuum test: connecting it disconnects the pressure source and restarts the test clock. */
    fun toggleVacuumTest(s: SimState): SimState {
        val next = !s.psTestVacuumSource
        return edited(s) { psTestVacuumSource = next; psTestPressureSource = false; psTestClockSec = 0.0 }
    }

    /** Quick fault scenarios. */
    fun scenarioCaptPitotBlocked(s: SimState): SimState = edited(s) { psPitotCaptBlocked = true }

    fun scenarioFoStaticTrapped(s: SimState): SimState = edited(s) {
        psStaticFoL = true
        psStaticFoR = true
        psStaticFoFrozenAltFt = s.psFlightAltFt
    }

    fun scenarioCaptAltStatic(s: SimState): SimState = edited(s) { psAltStaticCapt = true }

    fun scenarioCoversOnTakeoffRoll(s: SimState): SimState = edited(s) {
        psPitotCaptCover = true; psPitotFoCover = true; psPitotAux1Cover = true; psPitotAux2Cover = true
        psFlightCasKt = 80.0
    }
}
