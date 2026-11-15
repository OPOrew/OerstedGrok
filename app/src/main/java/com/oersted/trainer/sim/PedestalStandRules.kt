package com.oersted.trainer.sim

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pure rules of the CONTROL STAND of components/panel/CenterPedestal.tsx (lines 475-852): thrust levers and reversers,
 * start levers, the photo-version flap quadrant and speed brake lever, stabilizer trim wheels and scales, and the
 * parking brake handle. The Compose views are in ui/pedestal/PedestalControlStand.kt.
 * Design coordinates are the web's pixels: the stage is drawn in dp at 1 px = 1 dp and scaled as a whole.
 */
object PedestalStandRules {
    private fun jsRound(x: Double): Double = kotlin.math.floor(x + 0.5)
    private fun clamp(v: Double, lo: Double, hi: Double): Double = max(lo, min(hi, v))

    // ------------------------------------------------------------ thrust levers

    /** web LEV_TOP: handle-centre photo y at full thrust */
    const val LEV_TOP = 430.0

    /** web LEV_IDLE: handle-centre photo y at idle */
    const val LEV_IDLE = 566.0

    /** web `yc`: the handle centre y for a throttle of 0..1 */
    fun thrustY(throttle: Double): Double = LEV_IDLE - throttle * (LEV_IDLE - LEV_TOP)

    /** web thrust lever drag: the throttle after dragging [dy] design px from a start throttle [v] (down is positive), snapped to 0.01 */
    fun thrustDrag(v: Double, dy: Double): Double = clamp(jsRound((v - dy / (LEV_IDLE - LEV_TOP)) * 100.0) / 100.0, 0.0, 1.0)

    /** web ThrustLever `apply`: which throttles take the new value (both when the levers are linked) */
    fun thrustTargets(linked: Boolean, n: Int): Pair<Boolean, Boolean> = if (linked) Pair(true, true) else Pair(n == 1, n == 2)

    /** web useReverse outcome */
    enum class ReverserAction { STOW, INHIBIT_THROTTLE, INHIBIT_INTERLOCK, DEPLOY }

    data class ReverserDecision(val action: ReverserAction, val note: String)

    /**
     * web useReverse `toggle`: stow when commanded; deploy only at idle (throttle at most 0.05) and with the ground,
     * engine running, fire handle not pulled and HYD A above 1500 psi interlock satisfied.
     */
    fun reverserToggle(n: Int, commandedRev: Boolean, throttle: Double, wow: Boolean, running: Boolean, firePulled: Boolean, hydA: Double): ReverserDecision {
        if (commandedRev) return ReverserDecision(ReverserAction.STOW, "ENG $n REVERSER STOW")
        if (throttle > 0.05) return ReverserDecision(ReverserAction.INHIBIT_THROTTLE, "ENG $n REVERSER DEPLOY INHIBITED — thrust lever must be at idle.")
        if (!(wow && running && !firePulled && hydA > 1500)) {
            return ReverserDecision(ReverserAction.INHIBIT_INTERLOCK, "ENG $n REVERSER DEPLOY INHIBITED — ground/engine/fire/hydraulic interlock not satisfied.")
        }
        return ReverserDecision(ReverserAction.DEPLOY, "ENG $n REVERSER DEPLOY")
    }

    /** web reverse fork top: aft (380) when deployed, else 320 */
    fun reverserForkY(rev: Boolean): Int = if (rev) 380 else 320

    // ------------------------------------------------------------ start levers

    /** web start lever note */
    fun startLeverNote(n: Int, idle: Boolean): String = "ENG $n START LEVER → ${if (idle) "IDLE" else "CUTOFF"}"

    /** web Detent2 `pick`: the upper position when the pointer [relY] is in the top half of a track [h] high; null when that is already the value */
    fun detent2Pick(relY: Double, h: Double, value: Boolean): Boolean? {
        val up = relY < h / 2
        return if (up != value) up else null
    }

    /** web Detent2 handle top: 3 at the upper position, else h - hh - 3 */
    fun detent2HandleTop(value: Boolean, h: Double, hh: Double): Double = if (value) 3.0 else h - hh - 3

    // ------------------------------------------------------------ flap quadrant (photo version)

    /** web FLAP_Y: photo y of each gate */
    val FLAP_Y: List<Int> = listOf(362, 380, 397, 414, 457, 505, 546, 585, 622)

    /** web FLAP_TOP: photo y of the quadrant's top edge */
    const val FLAP_TOP = 340

    /** web FlapQuadrant `pick`: the gate nearest the pointer, [relY] design px below the quadrant top */
    fun flapGate(relY: Double): Int {
        val rel = relY + FLAP_TOP
        var best = 0
        FLAP_Y.forEachIndexed { i, y -> if (abs(y - rel) < abs(FLAP_Y[best] - rel)) best = i }
        return best
    }

    // ------------------------------------------------------------ speed brake (photo version)

    val SB_NAME: List<String> = listOf("DOWN", "ARMED", "FLIGHT DETENT", "UP")

    /** web SB_Y: legend offsets down the gate plate */
    val SB_Y: List<Int> = listOf(4, 40, 78, 170)

    /** web SB_C: lever centre (px below the gate top) at each detent */
    val SB_C: List<Int> = listOf(19, 58, 118, 177)
    const val SB_H = 200

    /** web SpeedBrake `pick`: the detent nearest the pointer, [rel] design px below the gate top */
    fun speedBrakeGate(rel: Double): Int {
        var best = 0
        SB_C.forEachIndexed { i, y -> if (abs(y - rel) < abs(SB_C[best] - rel)) best = i }
        return best
    }

    // ------------------------------------------------------------ stabilizer trim

    /** web trim interlock: both cutout switches in CUTOUT stop the wheels unless the override is held */
    fun trimCut(cutoutA: Boolean, cutoutB: Boolean, override: Boolean): Boolean = (cutoutA || cutoutB) && !override

    /** web `clamp(+(units + dU).toFixed(2), 0, 15)` */
    fun trimNext(units: Double, dU: Double): Double = clamp(PedestalTopDeckRules.toFixed(units + dU, 2).toDouble(), 0.0, 15.0)

    /** web wheel drag: [dy] design px (down positive) to trim units */
    fun trimDragUnits(dy: Double): Double = -dy * 0.03

    /** web wheel texture offset: 36 px per unit */
    fun trimWheelOffset(units: Double): Double = units * 36

    const val NOTE_TRIM = "STABILIZER TRIM WHEEL MOVED"
    const val TRIM_LIGHT_MS = 500L

    /** web StabScale `yy`: scale y of a trim unit 0..15 */
    fun stabScaleY(u: Double): Double = 16 + (u / 15) * 150

    /** web stab trim light: lit with DC power while the wheel moves or the fire panel lamp test runs */
    fun stabTrimLight(dcPower: Boolean, moving: Boolean, lampTest: Boolean): Boolean = dcPower && (moving || lampTest)

    /** web ControlStand lampTest: DC power with the fire test switch off centre */
    fun lampTest(dcPower: Boolean, fireTest: Pedestal.FireTest): Boolean = dcPower && fireTest != Pedestal.FireTest.OFF

    /** web park brake light: DC power with the brake set */
    fun parkBrakeLight(dcPower: Boolean, parkingBrake: Boolean): Boolean = dcPower && parkingBrake

    // ------------------------------------------------------------ parking brake handle

    const val PARK_TRAVEL = 40.0

    /** web ParkBrakeHandle top: 44 stowed, 4 fully pulled */
    fun parkHandleTop(frac: Double): Double = 44 - frac * PARK_TRAVEL

    /** web drag: the pulled fraction after dragging [dy] design px (down positive) from a start of 0 or 1 */
    fun parkDragFrac(base: Double, dy: Double): Double = clamp(base - dy / PARK_TRAVEL, 0.0, 1.0)

    /** web drag threshold: more than 3 px counts as a drag, not a tap */
    fun parkMoved(dy: Double): Boolean = abs(dy) > 3

    /** web release: past half travel latches after a drag, a tap toggles */
    fun parkRelease(moved: Boolean, live: Double?, value: Boolean): Boolean = if (moved) (live ?: 0.0) > 0.5 else !value

    fun parkNote(set: Boolean): String = if (set) "PARKING BRAKE SET — handle pulled." else "PARKING BRAKE RELEASED — handle stowed."
}
