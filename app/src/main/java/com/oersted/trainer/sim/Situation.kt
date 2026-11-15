package com.oersted.trainer.sim

import com.oersted.trainer.sim.AutoFlight.GsPhase
import com.oersted.trainer.sim.AutoFlight.LatSub
import com.oersted.trainer.sim.RadioNav.RadioNavData
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min

/**
 * Port of situation.ts: the situation snapshot the attitude / autopilot model publishes (about 10 Hz), plus the
 * plain-language `explain` text. The TS Zustand store becomes [SituationHolder]; the throttle takes an explicit nowMs.
 */
object Situation {
    /** Snapshot without the trail (TS Omit<Situation, "trail">). nm east / north of the RPLL RWY 06 threshold. */
    data class SituationSample(
        val x: Double,
        val y: Double,
        val hdg: Double,
        val altFt: Double,
        val tasKt: Double,
        val flying: Boolean,
        val latSub: LatSub,
        val gsPhase: GsPhase,
        val gsCapT: Double,
    )

    data class TrailPoint(val x: Double, val y: Double)

    data class SituationState(val sample: SituationSample, val trail: List<TrailPoint>)

    const val TRAIL_MAX = 600

    private val INITIAL_SAMPLE = SituationSample(
        x = -12.0, y = -0.8, hdg = 90.0, altFt = 0.0, tasKt = 0.0, flying = false,
        latSub = LatSub.OFF, gsPhase = GsPhase.OFF, gsCapT = 0.0,
    )

    /** TS publish: the trail grows only when the aircraft moved more than 0.03 nm since the last point. */
    fun nextTrail(trail: List<TrailPoint>, x: Double, y: Double): List<TrailPoint> {
        val last = trail.lastOrNull()
        val moved = last == null || hypot(x - last.x, y - last.y) > 0.03
        return if (moved) trail.takeLast(TRAIL_MAX - 1) + TrailPoint(x, y) else trail
    }

    class SituationHolder(initial: SituationState = SituationState(INITIAL_SAMPLE, emptyList())) {
        var state: SituationState = initial
            private set

        fun publish(sample: SituationSample) {
            state = SituationState(sample, nextTrail(state.trail, sample.x, sample.y))
        }

        fun clearTrail() {
            state = state.copy(trail = emptyList())
        }
    }

    /**
     * TS publishSituation: ~10 Hz throttle from the instrument-panel loop. [nowMs] replaces performance.now().
     * Returns true when the sample was published.
     */
    class Publisher(private val holder: SituationHolder) {
        private var lastPublishMs = 0.0

        fun publishThrottled(sample: SituationSample, nowMs: Double): Boolean {
            if (nowMs - lastPublishMs < 100) return false
            lastPublishMs = nowMs
            holder.publish(sample)
            return true
        }
    }

    enum class Tone(val label: String) { INFO("info"), ARMED("armed"), OK("ok"), WARN("warn") }

    data class Line(val tone: Tone, val text: String)

    data class ExplainIn(
        val sit: SituationSample,
        val navOn: Boolean,
        /** "VOR" or "ILS" */
        val tuned: String,
        val nav: RadioNavData,
        val navValid: Boolean,
        val selCrs: Double,
        val selHdg: Double,
        /** 0 MAN, 1 VOR LOC, 2 AUTO APP, 3 MAN G/S */
        val apNav: Int,
        /** 0 off, 1 hold, 2 sel */
        val apHdgSw: Int,
        val apAil: Boolean,
        val apElev: Boolean,
        /** 2 = ALT HOLD */
        val apPitch: Int,
        /** last disconnect / release reason from the autopilot model ("" = none) */
        val apWhy: String = "",
        /** VOR deviation for the selected course (deg, + = fly right) */
        val vorDev: Double,
    )

    data class Explanation(val lateral: List<Line>, val vertical: List<Line>)

    /** JS toFixed(n) for the non-negative and signed values used here (HALF_UP on the exact decimal value). */
    private fun toFixed(x: Double, digits: Int): String = String.format(Locale.ROOT, "%.${digits}f", x)

    /** JS Math.round: floor(x + 0.5). */
    private fun jsRound(x: Double): Long = floor(x + 0.5).toLong()

    /** JS number-to-string for a whole value (3.0 prints as "3", as in a template literal). */
    private fun jsNum(x: Double): String = if (x == floor(x)) floor(x).toLong().toString() else x.toString()

    private fun f1(n: Double) = toFixed(abs(n), 1)
    private fun side(dev: Double) = if (dev > 0) "left of" else "right of"
    private fun turn(dev: Double) = if (dev > 0) "right" else "left"
    private fun dots(deg: Double, perDot: Double) = toFixed(abs(deg) / perDot, 1)
    private fun hd(n: Double): String = jsRound(((n % 360) + 360) % 360).toString().padStart(3, '0')

    /** TS explain: plain-language lateral and vertical guidance text. */
    fun explain(i: ExplainIn): Explanation {
        val lat = mutableListOf<Line>()
        val vert = mutableListOf<Line>()
        val sit = i.sit
        val engaged = i.apAil || i.apElev

        // Trust sit.flying, but also accept altitude above 200 ft as airborne
        val airborne = sit.flying || sit.altFt > 200
        if (!airborne) return Explanation(lat, vert)

        if (!engaged && i.apWhy.isNotEmpty()) {
            lat.add(Line(Tone.WARN, "Autopilot is off because: ${i.apWhy.removePrefix("A/P disconnect: ")}. Fix the cause, then engage AIL / ELEV again."))
        }

        val radioSel = i.apNav == 1 || i.apNav == 2
        val isLoc = i.tuned == "ILS"

        // where am I relative to the beam? (always useful, even with the autopilot off)
        if (i.navOn && i.navValid) {
            if (isLoc) {
                lat.add(Line(Tone.INFO, "You are ${f1(i.nav.locDev)}° (${dots(i.nav.locDev, 0.5)} dots) ${side(i.nav.locDev)} the localizer. The CDI bar sits to the ${turn(i.nav.locDev)}: it points to where the beam is."))
                if (abs(AutoFlight.angDiff(i.selCrs, RadioNav.RWY_CRS)) > 5) {
                    lat.add(Line(Tone.WARN, "Course selector reads ${hd(i.selCrs)}°, but RPLL RWY 06 inbound course is ${hd(RadioNav.RWY_CRS)}°. Set the course to ${hd(RadioNav.RWY_CRS)}° so the autopilot intercepts the right way."))
                }
            } else {
                val to = abs(AutoFlight.angDiff(i.nav.radial, i.selCrs)) >= 90
                lat.add(Line(Tone.INFO, "You are on the ${hd(i.nav.radial)}° radial, ${f1(i.nav.vorDist)} nm from the VOR. With course ${hd(i.selCrs)}° selected the bar is ${f1(i.vorDev)}° to the ${if (i.vorDev > 0) "right" else "left"} (${if (to) "TO" else "FROM"} flag)."))
            }
        } else if (radioSel || i.navOn) {
            lat.add(Line(Tone.WARN, if (!i.navOn) "NAV receiver is off, so there is no beam to fly. Turn it on in Navigation." else "No valid signal on the tuned frequency, or a nav or compass source has failed. The autopilot cannot capture a beam it cannot see."))
        }

        // lateral channel
        if (!engaged) {
            lat.add(Line(Tone.INFO, "Autopilot is OFF. You are flying. Set the course and heading, choose NAV mode, then engage AIL (roll) and ELEV (pitch)."))
        } else if (!i.apAil) {
            lat.add(Line(Tone.INFO, "AIL (roll) is not engaged, so the autopilot is not steering. You are controlling roll."))
        } else if (radioSel && i.navOn && i.navValid) {
            val kind = if (isLoc) "localizer" else "VOR"
            val dev = if (isLoc) i.nav.locDev else i.vorDev
            val capDeg = if (isLoc) AutoFlight.LOC_CAPTURE_DEG else AutoFlight.VOR_CAPTURE_DEG
            val crsErr = AutoFlight.angDiff(i.selCrs, sit.hdg)
            when (sit.latSub) {
                LatSub.INTERCEPT -> lat.add(Line(Tone.ARMED, "INTERCEPT (VOR/LOC amber): the $kind is ${f1(dev)}° away, so the autopilot points you ${toFixed(min(45.0, abs(dev) * (if (isLoc) 10.0 else 4.5)), 0)}° toward it (max 45°) and banks up to 25°. Heading ${hd(sit.hdg)}°, course ${hd(i.selCrs)}°. Capture starts inside ${jsNum(capDeg)}°."))
                LatSub.CAPTURE -> lat.add(Line(Tone.OK, "CAPTURE (VOR/LOC green): the bar is inside ${jsNum(capDeg)}°, so the autopilot is turning you onto the course. Bank is still allowed to 25° until it is under 6° and you are within 15° of the course (off by ${f1(crsErr)}° now)."))
                LatSub.ON_COURSE -> lat.add(Line(Tone.OK, "ON COURSE: tracking the $kind. Bank is now limited to 8° to keep corrections small and smooth. Small heading changes are normal."))
                LatSub.OVER_STATION -> lat.add(Line(Tone.INFO, "OVER THE STATION (zone of confusion): the VOR signal is unreliable overhead, so the autopilot holds the last heading until the bar is usable again."))
                else -> lat.add(Line(Tone.INFO, "Lateral radio mode is selected but not active yet. Check the NAV switch and AIL engage."))
            }
        } else if (i.apHdgSw == 2) {
            val e = AutoFlight.angDiff(i.selHdg, sit.hdg)
            if (abs(e) < 2) {
                lat.add(Line(Tone.OK, "HDG SEL: holding heading ${hd(i.selHdg)}°."))
            } else {
                lat.add(Line(Tone.INFO, "HDG SEL: turning ${turn(e)} to the heading bug at ${hd(i.selHdg)}°. Heading is ${hd(sit.hdg)}° (${f1(e)}° to go). Bank is limited to 25°."))
            }
        } else {
            lat.add(Line(Tone.INFO, "Roll is engaged without a nav mode: the autopilot holds the heading or bank you had when you engaged it. Choose VOR LOC or AUTO APP to fly a beam, or HDG SEL to fly the heading bug."))
        }

        // vertical channel
        val gsWanted = i.apNav == 2 || i.apNav == 3
        if (i.navOn && isLoc && i.nav.locValid && !i.nav.gsValid && gsWanted) {
            vert.add(Line(Tone.WARN, "No glide-slope signal yet: ${f1(i.nav.thrDist)} nm from the threshold. In this trainer the glide slope is valid inside 10 nm, in front of the runway, and within 8° of the localizer."))
        } else if (i.navOn && isLoc && i.nav.gsValid) {
            vert.add(Line(Tone.INFO, "You are ${f1(i.nav.gsErr)}° ${if (i.nav.gsErr > 0) "above" else "below"} the 3° glide slope (${jsRound(sit.altFt)} ft, ${f1(i.nav.thrDist)} nm out)."))
        }
        when {
            sit.gsPhase == GsPhase.ARMED -> vert.add(Line(Tone.ARMED, "G/S ARMED (amber): waiting until the localizer is captured and you reach the beam, then it captures by itself. If you are below the beam, hold altitude and let it come to you."))
            sit.gsPhase == GsPhase.CAPTURE -> vert.add(Line(Tone.OK, "G/S CAPTURE (green): descending at about 700 ft/min for 10 s (${toFixed(min(10.0, sit.gsCapT), 0)}/10 s) before the beam is fully tracked."))
            sit.gsPhase == GsPhase.TRACK -> vert.add(Line(Tone.OK, "G/S TRACK: following the beam. The autopilot eases the signal in, then reduces its gain as the beam strengthens near the runway."))
            i.apElev && i.apPitch == 2 -> vert.add(Line(Tone.OK, "ALT HOLD: holding about ${jsRound(sit.altFt)} ft."))
            i.apElev -> vert.add(Line(Tone.INFO, "ELEV engaged: the autopilot holds the pitch attitude it had at engagement. Use ALT HOLD to hold altitude, or AUTO APP / MAN G/S to fly the glide slope."))
            engaged -> vert.add(Line(Tone.INFO, "ELEV (pitch) is not engaged. You are controlling pitch."))
        }

        return Explanation(lat, vert)
    }
}
