package com.oersted.trainer.sim

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pure rules of the lower pedestal (components/panel/CenterPedestal.tsx 1258 to 1530 and 1475 to 1545): the LOWER PLATE (PANEL / FLOOD
 * bright, rudder trim knob, STAB BRAKE RELEASE), the AILERON TRIM drum, and the "other pedestal functions" shown under the pedestal
 * (CARGO FIRE panel, MIC / audio selector panels, DATA PRINTER lamps, FIRE SWITCHES LOCK OVRD). Compose is ui/pedestal/PedestalLower.kt.
 */
object PedestalLowerRules {
    private fun toFixed(v: Double, d: Int) = PedestalTopDeckRules.toFixed(v, d)
    private fun clamp(v: Double, lo: Double, hi: Double) = max(lo, min(hi, v))

    // ---- lower plate ----

    /** PANEL BRIGHT knob: `+v.toFixed(2)`. */
    fun panelBright(v: Double): Double = toFixed(v, 2).toDouble()

    /** Rudder trim knob: `+v.toFixed(3)`. */
    fun rudderValue(v: Double): Double = toFixed(v, 3).toDouble()

    /** Rudder trim readout: "0" at centre, else "L 3.0" / "R 3.0" (1 = 15 units). */
    fun rudderLabel(trim: Double): String = if (trim == 0.0) "0" else "${if (trim < 0) "L" else "R"} ${toFixed(abs(trim * 15), 1)}"

    /** The STAB BRAKE RELEASE knob holds the `stabBrakeRel` switch while pulled. */
    const val STAB_BRAKE_REL = "stabBrakeRel"

    // ---- aileron trim drum ----

    /** Drag [dx] design px: `clamp(+(trim + dx * 0.004).toFixed(3), -1, 1)`. */
    fun aileronDrag(trim: Double, dx: Double): Double = clamp(toFixed(trim + dx * 0.004, 3).toDouble(), -1.0, 1.0)

    /** Background x offset of the stripes, px. */
    fun aileronStripeShift(trim: Double): Double = trim * 150

    fun aileronLabel(trim: Double): String = "L WING DN ← ${toFixed(trim * 15, 1)} U → R WING DN"

    // ---- fire switches lock override ----

    const val LOCK_OVERRIDE_MS = 12000.0
    const val LOCK_OVERRIDE_REPAINT_MS = 12100L

    /** `Date.now() + 12000` */
    fun lockOverrideUntil(nowMs: Long): Double = nowMs + LOCK_OVERRIDE_MS

    // ---- stab trim plate (OtherFunctions) ----

    /** NOSE UP / NOSE DN button: null when both cutouts block it, else the new units (0.1 U per press). */
    fun stabTrimPress(s: SimState, dir: Int): Double? =
        if (PedestalStandRules.trimCut(s.stabTrimCutoutA, s.stabTrimCutoutB, s.stabTrimOverride)) null
        else PedestalStandRules.trimNext(s.stabTrimUnits, dir * 0.1)

    fun stabOverrideNote(on: Boolean) = "STAB TRIM OVERRIDE ${if (on) "ON" else "OFF"}"
    fun cutoutNote(which: Char, on: Boolean) = "STAB TRIM CUTOUT $which ${if (on) "ACTIVE" else "NORMAL"}"

    // ---- cargo fire panel ----

    private fun loopBad(sel: Pedestal.CargoDet, s: SimState): Boolean = when (sel) {
        Pedestal.CargoDet.A -> !s.cargoLoopA
        Pedestal.CargoDet.B -> !s.cargoLoopB
        Pedestal.CargoDet.NORM -> !s.cargoLoopA && !s.cargoLoopB
    }

    /** DETECTOR FAULT lamp. */
    fun cargoFault(pw: Boolean, s: SimState, p: Pedestal.PedestalState): Boolean =
        pw && s.cargoFireInstalled && (loopBad(p.cargoDetFwd, s) || loopBad(p.cargoDetAft, s) || p.fireTest == Pedestal.FireTest.FAULT)

    /** The two EXT lamps. */
    fun cargoExtLit(pw: Boolean, s: SimState, extTest: Boolean): Boolean = pw && s.cargoFireInstalled && (extTest || !s.cargoBottleDischarged)

    /** ARMED FWD / AFT press: always logs; changes the selector only when the system is installed. */
    fun cargoArm(s: SimState, c: String): FirePanelRules.Action {
        if (!s.cargoFireInstalled) return FirePanelRules.Action("CARGO FIRE system not installed (pre-SB 26A1081).") {}
        val off = s.cargoArmed == c
        return FirePanelRules.Action(if (off) "CARGO FIRE ARM selector OFF." else "CARGO FIRE ARM — $c compartment selected.") {
            cargoArmed = if (off) "NONE" else c
        }
    }

    /** DISCH press: needs the system, an armed compartment and an unused bottle. */
    fun cargoDischarge(s: SimState): FirePanelRules.Action? {
        if (!s.cargoFireInstalled || s.cargoArmed == "NONE" || s.cargoBottleDischarged) return null
        return FirePanelRules.Action("CARGO FIRE DISCH — agent released into the ${s.cargoArmed} compartment.") {
            cargoBottleDischarged = true
            cargoAgentTimer = 0.0
        }
    }

    // ---- MIC / audio panels ----

    val RX = listOf("VHF-1", "VHF-2", "HF-1", "HF-2", "CABIN", "INT", "PA")
    val AUD = listOf("1", "2", "ADF-1", "ADF-2", "MKR", "SPKR")

    fun key(side: String, name: String) = "$side:$name"

    /** The transmit selector currently armed on this side (first match in RX order). */
    fun transmitting(mic: Map<String, Boolean>, side: String): String? = RX.firstOrNull { mic[key(side, "tx:$it")] == true }

    /** `selectTx`: clears every transmit selector of the side, then toggles [r]. */
    fun selectTx(mic: Map<String, Boolean>, side: String, r: String): Map<String, Boolean> {
        val next = mic.toMutableMap()
        RX.forEach { next[key(side, "tx:$it")] = false }
        next[key(side, "tx:$r")] = mic[key(side, "tx:$r")] != true
        return next
    }

    /** `flip`: toggles one entry. */
    fun flip(mic: Map<String, Boolean>, k: String): Map<String, Boolean> = mic + (k to (mic[k] != true))

    /** PTT status line. */
    fun micStatus(pw: Boolean, ptt: Boolean, tx: String?): String = when {
        !pw -> "NO DC POWER"
        ptt && tx != null -> "TX ▸ $tx"
        ptt -> "PTT · NO TX RADIO SELECTED"
        tx != null -> "ARMED ▸ $tx"
        else -> "—"
    }

    /** The PTT key is lit amber while powered, held and a radio is selected. */
    fun pttActive(pw: Boolean, ptt: Boolean, tx: String?): Boolean = pw && ptt && tx != null

    // ---- data printer ----

    /** The MSG / FAIL lamps follow the TEST button; PAPER also lights when the paper is empty. */
    fun printerMsgLit(pw: Boolean, test: Boolean) = pw && test
    fun printerPaperLit(pw: Boolean, test: Boolean, paper: Boolean) = pw && (test || !paper)
    fun paperText(paper: Boolean) = if (paper) "PAPER LOADED" else "PAPER EMPTY"
}
