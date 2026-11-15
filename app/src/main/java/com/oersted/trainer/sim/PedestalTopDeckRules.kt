package com.oersted.trainer.sim

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Pure rules of the TOP DECK units of components/panel/CenterPedestal.tsx (lines 270-457): the FreeFlight GPS display
 * lines, the VHF COMM windows, the transponder code pairs and the CVR erase interlock. The Compose views are in
 * ui/pedestal/PedestalTopDeck.kt.
 */
object PedestalTopDeckRules {
    /** JS Number.prototype.toFixed: the exact decimal value rounded half away from zero (a small negative keeps its sign: "-0"). */
    fun toFixed(v: Double, digits: Int): String {
        val s = BigDecimal(abs(v)).setScale(digits, RoundingMode.HALF_UP).toPlainString()
        return if (v < 0) "-$s" else s
    }

    /** JS `String(n)` for a whole or fractional number: whole values without ".0" */
    fun jsNum(v: Double): String = if (v == floor(v) && abs(v) < 1e15) v.toLong().toString() else v.toString()

    /** JS Math.round: half rounds up */
    fun jsRound(v: Double): Long = floor(v + 0.5).toLong()

    private fun pad3(s: String): String = s.padStart(3, '0')

    fun clamp(v: Int, lo: Int, hi: Int): Int = max(lo, min(hi, v))

    // ------------------------------------------------------------------ GPS

    val GPS_KEYS: List<String> = listOf("NAV", "WPT", "APT/VOR", "D→", "FPL", "CALC", "AUX", "MSG")
    val GPS_TEXT: Map<String, String> = mapOf(
        "NAV" to "NAV  NO ACTIVE WPT", "WPT" to "WPT  ----", "APT/VOR" to "NEAREST APT  ----", "D→" to "DIRECT TO  ----",
        "FPL" to "FPL 0  EMPTY", "CALC" to "CALC  GS / TAS / WIND", "AUX" to "AUX  SETUP", "MSG" to "NO MESSAGES",
    )

    /** web `p.gpsPage in GPS_TEXT ? p.gpsPage : "NAV"` */
    fun gpsPage(page: String): String = if (GPS_TEXT.containsKey(page)) page else "NAV"

    private fun fmtRange(n: Double?): String = if (n == null || !n.isFinite()) "---.-NM" else "${toFixed(n, 1)}NM"

    /**
     * The lines the GPS shows for [page] (web `gpsLines[page] ?? [GPS_TEXT[page]]`). Without an active plan the unit
     * shows its default text; with one it shows the plan and the navigator's state.
     */
    fun gpsLines(plan: ActiveFlightPlan?, nav: FlightNavigationState?, page: String): List<String> {
        if (plan == null) return listOf(GPS_TEXT.getValue(page))
        val first = plan.waypoints.firstOrNull()
        val heading = pad3(toFixed(nav?.headingDeg ?: 0.0, 0))
        val checked = plan.procedureStatus == "chart-checked"
        return when (page) {
            "NAV" -> listOf("NAV ${plan.departure} > ${plan.destination}", "${nav?.phase?.label ?: "PLAN ACTIVE"} · HDG $heading")
            "WPT" -> {
                val brg = nav?.bearingToNextDeg
                val dtk = nav?.desiredTrackDeg
                val xtk = nav?.crossTrackNm
                listOf(
                    "TO ${nav?.activeLegName ?: first?.name ?: plan.destination}",
                    "BRG ${if (brg == null) "---" else pad3(toFixed(brg, 0))} · ${fmtRange(nav?.distanceToNextNm)}",
                    "DTK ${if (dtk == null) "---" else pad3(toFixed(dtk, 0))} · XTK ${if (xtk == null) "---" else "${if (xtk >= 0) "R" else "L"}${toFixed(abs(xtk), 1)}"}NM",
                )
            }
            "APT/VOR" -> listOf("${plan.departure} > ${plan.destination}", "ALT ${plan.alternate.ifEmpty { "----" }}")
            "D→" -> listOf("DEST ${plan.destination}", "RNG ${fmtRange(nav?.distanceToDestinationNm)}")
            "FPL" -> listOf(
                "FPL ${plan.departure} > ${plan.destination}",
                "LEG ${(nav?.activeLegIndex ?: 0) + 1} · ${plan.waypoints.size} FIX",
                "PROC ${if (checked) "USER CHECKED" else "UNVERIFIED"}",
            )
            "CALC" -> {
                val te = nav?.trackErrorDeg
                listOf(
                    "GS ${toFixed(nav?.groundSpeedKt ?: 0.0, 0)} KT · HDG $heading",
                    "ALT ${jsRound(nav?.altitudeFt ?: 0.0)} FT",
                    "TRK ERR ${if (te == null) "---" else "${if (te >= 0) "+" else ""}${toFixed(te, 0)}"}°",
                )
            }
            "AUX" -> listOf("AIRCRAFT POSITION", "${toFixed(nav?.latitude ?: 0.0, 3)}, ${toFixed(nav?.longitude ?: 0.0, 3)}")
            "MSG" -> listOf(
                if (checked) "USER MARKED CHECKED" else "PROCEDURES UNVERIFIED",
                if (nav?.positionMode == PositionMode.MOVING_WITH_SIMULATED_AIRCRAFT) "POS TRACKING ACTIVE" else "POSITION NOT MOVING",
            )
            else -> listOf(GPS_TEXT.getValue(page))
        }
    }

    // ------------------------------------------------------------------ VHF COMM

    /** web rf: kHz-by-ten to MHz text, e.g. 11900 -> "119.00" */
    fun rf(v: Int): String = toFixed(v / 100.0, 2)

    /** web vhfWin: VHF COMM window, kHz -> MHz with the 0.005 digit not shown (AMM 23-21-0) */
    fun vhfWin(kHz: Int): String = toFixed(floor(kHz / 10.0) / 100.0, 2)

    /** web VhfCommTop step: the frequency in 5 kHz steps within 118.00 - 135.95 */
    fun vhfStep(v: Int, dir: Int): Int = clamp(v + dir * 5, 11800, 13595)

    /** web VhfCommTop window text: dashes without DC power */
    fun vhfWindowText(v: Int, dcPower: Boolean): String = if (dcPower) rf(v) else "---.--"

    // ------------------------------------------------------------------ transponder

    val XPDR_MODES: List<String> = listOf("STBY", "ALT OFF", "ALT ON", "TEST")

    /** JS parseInt(s, 8): the leading octal digits, null (NaN) when there are none */
    private fun parseOct(s: String): Int? {
        val digits = s.takeWhile { it in '0'..'7' }
        return if (digits.isEmpty()) null else digits.toInt(8)
    }

    /** web TransponderUnit `pair`: steps the two code digits at [k] (0 or 2) by [dir] as one octal pair, wrapping 00..77 */
    fun xpdrPair(code: String, k: Int, dir: Int): String {
        val c = code.padStart(4, '0')
        val cur = parseOct(c.substring(k, k + 2)) ?: 0
        val nxt = ((cur + dir + 64) % 64)
        return c.substring(0, k) + nxt.toString(8).padStart(2, '0') + c.substring(k + 2)
    }

    /** web ATC FAIL lamp: lit with DC power while the mode knob is on TEST */
    fun xpdrFail(dcPower: Boolean, mode: Int): Boolean = dcPower && mode == 3

    // ------------------------------------------------------------------ radar

    /** web WXR_RANGES (PrimusWxr.tsx): the radar range legends, indexed by radarRange */
    val WXR_RANGES: List<String> = listOf("TEST", "10", "25", "50", "100", "200", "300")

    /** web RadarUnit mode keys: legend and mode number */
    val RADAR_MODE_KEYS: List<Pair<String, Int>> = listOf("STBY" to 1, "WX" to 2, "CYC" to 3, "MAP" to 4)

    /** web mode key press: the mode, or 0 (off) when it is already selected */
    fun radarModeToggle(current: Int, key: Int): Int = if (current == key) 0 else key

    /** web gain knob: values below 0.08 snap to the PRESET end */
    fun radarGain(v: Double): Double = if (v < 0.08) 0.0 else v

    /** web TILT legend: "TILT +5", "TILT 0", "TILT -3" */
    fun radarTiltLabel(tilt: Double): String = "TILT ${if (tilt > 0) "+" else ""}${jsNum(tilt)}"

    // ------------------------------------------------------------------ CVR

    const val CVR_ERASE_HOLD_MS = 2000L
    const val CVR_ERASE_RUN_MS = 8000L

    const val NOTE_TEST = "CVR TEST"
    const val NOTE_INHIBITED = "CVR ERASE inhibited — requires ground, parking brake, DC power and an operative recorder."
    const val NOTE_TOO_SHORT = "CVR ERASE — hold the pushbutton for at least 2 seconds."
    const val NOTE_RUNNING = "CVR bulk erase running — trainer indication for an 8-second erase interval."
    const val NOTE_DONE = "CVR bulk erase interval complete."

    /** AMM 23-71 trainer interlock: on ground, parking brake set, DC available and recorder operative. */
    fun cvrEraseAllowed(cvrPowered: Boolean, dcPower: Boolean, wow: Boolean, parkingBrake: Boolean, cvrFailure: Boolean): Boolean =
        cvrPowered && dcPower && wow && parkingBrake && !cvrFailure

    enum class EraseOutcome { NOT_ALLOWED, TOO_SHORT, START }

    /** web eraseRelease: what a release does after being held [heldMs] when the press was ([allowed]) */
    fun cvrEraseOutcome(allowed: Boolean, heldMs: Long): EraseOutcome = when {
        !allowed -> EraseOutcome.NOT_ALLOWED
        heldMs < CVR_ERASE_HOLD_MS -> EraseOutcome.TOO_SHORT
        else -> EraseOutcome.START
    }

    /** web CVR monitor needle: swings to 40 degrees on test, rests at -45 */
    fun cvrNeedleDeg(cvrPowered: Boolean, cvrTest: Boolean): Int = if (cvrPowered && cvrTest) 40 else -45
}
