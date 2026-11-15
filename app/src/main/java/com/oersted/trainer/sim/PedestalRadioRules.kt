package com.oersted.trainer.sim

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Pure rules of the pedestal radio stack (components/panel/CenterPedestal.tsx 982 to 1230): VHF COMM, VHF NAV / DME, ADF, HF and the
 * DATA display. Frequencies are kHz (ADF in kHz too), as in pedestal.ts. Compose lives in ui/pedestal/PedestalRadios.kt.
 */
object PedestalRadioRules {
    private fun toFixed(v: Double, d: Int) = PedestalTopDeckRules.toFixed(v, d)

    /** VhfUnit tune: wraps above 135975 to the bottom of the band and below 118000 to the top. */
    fun tuneVhf(cur: Int, delta: Int): Int {
        var v = cur + delta
        if (v > 135975) v = 118000 + (v - 136000)
        if (v < 118000) v = 136000 - (118000 - v)
        return v
    }

    /** NavUnit tune: wraps above 117950 to 108000 and below 108000 to the top of the band. */
    fun tuneNav(cur: Int, delta: Int): Int {
        var v = cur + delta
        if (v > 117950) v = 108000 + (v - 118000)
        if (v < 108000) v = 118000 - (108000 - v)
        return v
    }

    /** HfUnit tune: whole kHz, wraps 29999 to 2000 and below 2000 to 29999. */
    fun tuneHf(cur: Int, delta: Int): Int {
        var v = cur + delta
        if (v > 29999) v = 2000
        if (v < 2000) v = 29999
        return v
    }

    /** AdfUnit `adj`: clamped 190..1750 kHz. */
    fun adjAdf(cur: Int, delta: Int): Int = max(190, min(1750, cur + delta))

    /** AdfUnit window text: the TEST position (3) shows 1888. */
    fun adfText(freq: Int, mode: Int): String = if (mode == 3) "1888" else freq.toString()

    /** NavUnit window text, `(v / 1000).toFixed(2)`. */
    fun navWin(kHz: Int): String = toFixed(kHz / 1000.0, 2)

    /** The NAV / ADF / VHF windows are lit when DC power is on and the head is not OFF / STBY. */
    fun navLit(dcPower: Boolean, dmeMode: Int): Boolean = dcPower && dmeMode > 0
    fun adfLit(dcPower: Boolean, mode: Int): Boolean = dcPower && mode > 0

    /** HfUnit: DC power, the HF-n breaker (JS truthiness: must be explicitly true) and a mode other than OFF. */
    fun hfLit(dcPower: Boolean, breakerClosed: Boolean?, mode: Int): Boolean = dcPower && breakerClosed == true && mode > 0

    /** AdfUnit setMode: the new mode pair and the ANT flags (ANT is mode 1). */
    fun adfSetMode(modes: Pair<Int, Int>, ant: Pair<Boolean, Boolean>, side: Int, v: Int): Pair<Pair<Int, Int>, Pair<Boolean, Boolean>> =
        if (side == 0) Pair(Pair(v, modes.second), Pair(v == 1, ant.second))
        else Pair(Pair(modes.first, v), Pair(ant.first, v == 1))

    /** Bat3 DME / HF mode names. */
    val DME_MODES = listOf("STBY", "NORM", "ORIDE")
    val HF_MODES = listOf("OFF", "USB", "AM")
    val ADF_MODES = listOf("OFF", "ANT", "ADF", "TEST", "LOOP")

    /** DataDisplay `f`: floor(v / 10) / 100 to two decimals. */
    fun dataF(kHz: Int): String = toFixed(floor(kHz / 10.0) / 100.0, 2)

    /** DataDisplay lines; [com3] is the MIP COM frequency in units of 10 kHz (mip.comAct, e.g. 11900 = 119.00). */
    fun dataLines(p: Pedestal.PedestalState, com3: Int): List<String> = listOf(
        "COM1  ${dataF(p.commL.act)}",
        "COM2  ${dataF(p.commR.act)}",
        "COM3  ${toFixed(com3 / 100.0, 2)}",
        "NAV1  ${dataF(p.navL.act)}",
        "NAV2  ${dataF(p.navR.act)}",
        "ADF1  ${p.adf.l}",
        "ADF2  ${p.adf.r}",
    )

    /** A pair with [idx] replaced (web `idx === 0 ? [v, cur[1]] : [cur[0], v]`). */
    fun <T> setPair(cur: Pair<T, T>, idx: Int, v: T): Pair<T, T> = if (idx == 0) Pair(v, cur.second) else Pair(cur.first, v)
}
