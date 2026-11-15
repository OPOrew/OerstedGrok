package com.oersted.trainer.sim

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Pure rules of components/panel/PedestalParts.tsx: the CDU (FMC control display unit) page table and key handling,
 * and the geometry of Knob, Selector3 and TuneKnob. The Compose views are in ui/pedestal/PedestalParts.kt.
 */
object PedestalPartsRules {
    /** web CduField */
    data class CduField(val id: String, val side: Char, val row: Int, val label: String, val def: String? = null)

    /** web PAGES entry */
    data class CduPage(val title: String, val pg: String? = null, val fields: List<CduField>)

    private fun f(id: String, side: Char, row: Int, label: String, def: String? = null) = CduField(id, side, row, label, def)

    val PAGES: Map<String, CduPage> = linkedMapOf(
        "CRZ" to CduPage("ACT ECON CRZ", "1/1", listOf(
            f("alt", 'L', 1, "CRZ ALT", "FL310"), f("optmax", 'C', 1, "OPT/MAX", "FL340/361"), f("step", 'R', 1, "STEP", "-----"),
            f("spd", 'L', 2, "TGT SPD", ".780"), f("todist", 'R', 2, "TO T/D", "2004.5Z/ 100NM"),
            f("turb", 'L', 3, "TURB N1", "87.3/ 87.3%"), f("wind", 'R', 3, "ACTUAL WIND", "129°/ 14"),
            f("fuel", 'L', 4, "FUEL AT KATL", "12.5"), f("eng", 'R', 5, "", "ENG OUT>"),
            f("lrc", 'L', 6, "", "<LRC"), f("rta", 'R', 6, "", "RTA>"))),
        "LEGS" to CduPage("ACT RTE LEGS", "1/4", listOf(
            f("w1", 'L', 1, "129° HDG", "LACO1"), f("w1a", 'R', 1, "1NM", "250/ 4690"),
            f("w2", 'L', 2, "70°", "VAMPS"), f("w2a", 'R', 2, "7NM", "250/ 8000A"),
            f("w3", 'L', 3, "85°", "BANDR"), f("w3a", 'R', 3, "35NM", ".800/FL190"),
            f("w4", 'L', 4, "330°", "BANO1"), f("w4a", 'R', 4, "1NM BYPASS", ".800/FL190"),
            f("w5", 'L', 5, "89°", "BEEZR"), f("w5a", 'R', 5, "20NM", ".800/FL190"),
            f("rnp", 'L', 6, "RNP/ACTUAL", "0.50/0.21NM"), f("data", 'R', 6, "", "RTE DATA>"))),
        "RTE" to CduPage("RTE 1", "1/2", listOf(
            f("org", 'L', 1, "ORIGIN", "-----"), f("dst", 'R', 1, "DEST", "-----"),
            f("rwy", 'L', 2, "RUNWAY", "-----"), f("flt", 'R', 2, "FLT NO", "--------"),
            f("co", 'L', 3, "CO ROUTE", "--------"), f("act", 'R', 6, "", "ACTIVATE>"))),
        "CLB" to CduPage("ACT ECON CLB", "1/1", listOf(
            f("alt", 'L', 1, "CRZ ALT", "FL310"), f("spd", 'L', 2, "TGT SPD", "290/.780"),
            f("lim", 'R', 1, "SPD REST", "250/10000"))),
        "DES" to CduPage("ACT ECON DES", "1/1", listOf(
            f("spd", 'L', 1, "E/D AT", "-----"), f("mach", 'L', 2, "TGT SPD", ".780/280"), f("alt", 'R', 1, "SPD REST", "240/10000"))),
        "PROG" to CduPage("PROGRESS", "1/2", listOf(
            f("last", 'L', 1, "LAST", "LACO1"), f("to", 'L', 2, "TO", "VAMPS"), f("next", 'L', 3, "NEXT", "BANDR"))),
        "HOLD" to CduPage("HOLD", null, listOf(f("fix", 'L', 1, "FIX", "-----"), f("q", 'L', 2, "QUAD/RADIAL", "---/---°"))),
        "INIT REF" to CduPage("INIT/REF INDEX", null, listOf(
            f("id", 'L', 1, "", "<IDENT"), f("pos", 'L', 2, "", "<POS"), f("perf", 'L', 3, "", "<PERF"),
            f("to", 'L', 4, "", "<TAKEOFF"), f("app", 'R', 1, "", "APPROACH>"))),
        "N1 LIMIT" to CduPage("N1 LIMIT", null, listOf(
            f("sel", 'L', 1, "SEL/OAT", "TO/15°C"), f("n1", 'R', 1, "N1", "93.8%"), f("cl", 'L', 3, "", "<CLB"))),
        "FIX" to CduPage("FIX INFO", null, listOf(f("fix", 'L', 1, "FIX", "-----"))),
        "DEP ARR" to CduPage("DEP/ARR INDEX", null, listOf(f("dep", 'L', 1, "<DEP", "-----"), f("arr", 'R', 1, "ARR>", "-----"))),
        "DIR INTC" to CduPage("DIRECT-TO", null, listOf(f("to", 'L', 1, "DIRECT TO", "-----"))),
        "MENU" to CduPage("MENU", null, listOf(f("fmc", 'L', 1, "", "<FMC"), f("acars", 'L', 2, "", "<ACARS"))),
    )

    val KEYROWS: List<List<String>> = listOf(
        listOf("A", "B", "C", "D", "E"), listOf("F", "G", "H", "I", "J"), listOf("K", "L", "M", "N", "O"),
        listOf("P", "Q", "R", "S", "T"), listOf("U", "V", "W", "X", "Y"), listOf("Z", "SP", "DEL", "/", "CLR"),
    )
    val FUNCS: List<List<String>> = listOf(
        listOf("INIT REF", "RTE", "CLB", "CRZ", "DES"), listOf("MENU", "LEGS", "DEP ARR", "HOLD", "PROG"), listOf("N1 LIMIT", "FIX", "DIR INTC"),
    )
    val FKEY_LABEL: Map<String, String> = mapOf("INIT REF" to "INIT\nREF", "DEP ARR" to "DEP\nARR", "N1 LIMIT" to "N1\nLIMIT", "DIR INTC" to "DIR\nINTC")

    /** web `PAGES[st.page] ?? PAGES.CRZ` */
    fun pageOf(st: Pedestal.CduSide): CduPage = PAGES[st.page] ?: PAGES.getValue("CRZ")

    /** web fieldVal: the typed value for this page and field, else the default */
    fun fieldVal(st: Pedestal.CduSide, fld: CduField): String = st.fields["${st.page}:${fld.id}"] ?: fld.def ?: ""

    /** web `typed`: a character typed on the keyboard (no change when unpowered) */
    fun typed(st: Pedestal.CduSide, power: Boolean, ch: String): Pedestal.CduSide {
        if (!power) return st
        if (st.pad == "DELETE" || st.msg.isNotEmpty()) return st.copy(pad = ch, msg = "")
        if (st.pad.length < 24) return st.copy(pad = st.pad + ch)
        return st
    }

    /** web `lsk`: a line select key press */
    fun lsk(st: Pedestal.CduSide, power: Boolean, fld: CduField): Pedestal.CduSide {
        if (!power) return st
        // web: !f.def || /[<>]$|^[<]|[>]$/.test(f.def): a blank default or a < > navigation prompt
        val d = fld.def
        if (d.isNullOrEmpty() || d.endsWith("<") || d.endsWith(">") || d.startsWith("<")) {
            val act = fld.id == "act"
            return st.copy(page = if (act) "CRZ" else st.page, exec = if (act) true else st.exec)
        }
        val key = "${st.page}:${fld.id}"
        if (st.pad == "DELETE") return st.copy(fields = st.fields - key, pad = "", exec = true)
        if (st.pad.isNotEmpty()) return st.copy(fields = st.fields + (key to st.pad), pad = "", exec = true)
        return st.copy(pad = fieldVal(st, fld))
    }

    /** web `key`: an alpha-numeric key (CLR, DEL, SP and characters) */
    fun key(st: Pedestal.CduSide, power: Boolean, k: String): Pedestal.CduSide {
        if (!power) return st
        if (k == "CLR") {
            return if (st.msg.isNotEmpty()) st.copy(msg = "") else st.copy(pad = if (st.pad == "DELETE") "" else st.pad.dropLast(1))
        }
        if (k == "DEL") return st.copy(pad = "DELETE")
        if (k == "SP") return typed(st, power, " ")
        return typed(st, power, k)
    }

    /** web function key press: opens the page when there is one, and clears the message */
    fun funcKey(st: Pedestal.CduSide, power: Boolean, f: String): Pedestal.CduSide =
        if (!power) st else st.copy(page = if (PAGES.containsKey(f)) f else st.page, msg = "")

    /** web EXEC key: puts out the EXEC light */
    fun exec(st: Pedestal.CduSide, power: Boolean): Pedestal.CduSide = if (!power) st else st.copy(exec = false)

    /** web +/- key */
    fun plusMinus(st: Pedestal.CduSide, power: Boolean): Pedestal.CduSide =
        if (!power) st else st.copy(pad = if (st.pad.startsWith("-")) st.pad.substring(1) else "-" + st.pad)

    // ---------------------------------------------------------------- knobs

    /** JS Math.round: half rounds up */
    private fun jsRound(x: Double): Double = floor(x + 0.5)

    /** web Knob angle: -135..135 degrees across min..max */
    fun knobAngle(value: Double, min: Double = 0.0, max: Double = 1.0): Double {
        val frac = (value - min) / (if (max - min != 0.0) max - min else 1.0)
        return -135.0 + max(0.0, min(1.0, frac)) * 270.0
    }

    /** web Knob `set`: the pointer angle (degrees from 12 o'clock) to a value, snapped to [step] when given */
    fun knobValue(angleDeg: Double, min: Double = 0.0, max: Double = 1.0, step: Double = 0.0): Double {
        val a = max(-135.0, min(135.0, angleDeg))
        var v = min + ((a + 135.0) / 270.0) * (max - min)
        if (step != 0.0) v = jsRound(v / step) * step
        return max(min, min(max, v))
    }

    /** web Knob tick angle: the first legend at -135 degrees, the last at +135 */
    fun tickAngle(i: Int, n: Int): Double = if (n <= 1) -135.0 else -135.0 + (i.toDouble() / (n - 1)) * 270.0

    /** web Knob: the legend the pointer is on (the first one nearest the pointer angle) */
    fun activeTick(n: Int, angle: Double): Int {
        if (n == 0) return -1
        var best = 0
        for (i in 0 until n) if (kotlin.math.abs(tickAngle(i, n) - angle) < kotlin.math.abs(tickAngle(best, n) - angle)) best = i
        return best
    }

    /** web Selector3 angOf */
    fun selectorAngle(i: Int, n: Int): Double {
        val span = min(120.0, 45.0 * (n - 1))
        return if (n == 1) 0.0 else (i - (n - 1) / 2.0) * (span / (n - 1))
    }

    /** web angleFrom: degrees clockwise from 12 o'clock of a point around a centre (screen y grows downward) */
    fun angleFrom(cx: Double, cy: Double, x: Double, y: Double): Double =
        kotlin.math.atan2(x - cx, -(y - cy)) * 180.0 / Math.PI

    /** web TuneKnob accumulator: rotation in degrees in, whole +1 / -1 steps out (every 20 degrees) and the remainder */
    data class TuneAcc(val acc: Double, val steps: List<Int>)

    fun tuneAccumulate(acc: Double, delta: Double): TuneAcc {
        var a = acc + delta
        val out = ArrayList<Int>()
        while (a >= 20.0) { a -= 20.0; out.add(1) }
        while (a <= -20.0) { a += 20.0; out.add(-1) }
        return TuneAcc(a, out)
    }

    /** web TuneKnob move: the shortest rotation between two pointer angles */
    fun tuneDelta(last: Double, now: Double): Double {
        var d = now - last
        if (d > 180) d -= 360
        if (d < -180) d += 360
        return d
    }

    // ------------------------------------------------- CenterPedestal state helpers

    /** web useKnobState read: the persisted knob value for [key], else [def] */
    fun knobOf(s: Pedestal.PedestalState, key: String, def: Double): Double = s.knobs[key] ?: def

    /** web useKnobState write */
    fun withKnob(s: Pedestal.PedestalState, key: String, v: Double): Pedestal.PedestalState = s.copy(knobs = s.knobs + (key to v))

    /** web useSwState read */
    fun swOf(s: Pedestal.PedestalState, key: String, def: Boolean): Boolean = s.sw[key] ?: def

    /** web useSwState write */
    fun withSw(s: Pedestal.PedestalState, key: String, v: Boolean): Pedestal.PedestalState = s.copy(sw = s.sw + (key to v))

    /** web Bat3 lever angle: -42, 0 or 42 degrees for the first, second and third option */
    fun bat3Rotation(options: List<String>, value: String): Int = listOf(-42, 0, 42)[max(0, options.indexOf(value))]

    /** web Bat3 default legend: N for NORMAL / NORM, else the option itself */
    fun bat3Legend(option: String): String = if (option == "NORMAL" || option == "NORM") "N" else option
}

