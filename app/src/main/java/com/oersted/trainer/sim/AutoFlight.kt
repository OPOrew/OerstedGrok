package com.oersted.trainer.sim

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI

/**
 * Port of src/lib/sim/autoflight.ts: pure helpers for the Collins FD-108 flight director and Sperry SP-77
 * autopilot (737-100/200 AMM 34-31-0, 22-11-0). No platform code, so the local runner can compile it.
 */
object AutoFlight {
    /** Same formula as the TS angDiff: signed smallest difference, result in [-180, 180). */
    fun angDiff(to: Double, from: Double): Double = ((((to - from) % 360) + 540) % 360) - 180

    /** JS Math.max/min semantics for clamp (NaN propagates like the TS version). */
    private fun clamp(v: Double, lo: Double, hi: Double): Double = max(lo, min(hi, v))

    /** AMM: values the manual gives. */
    object AMM {
        /** AMM 22-11-0: radio lateral law - intercept the course at up to 45 deg; bank 25 deg intercepting, 8 deg on course (also the TURB limit) */
        const val interceptMaxDeg = 45.0
        const val bankInterceptDeg = 25.0
        const val bankOnCourseDeg = 8.0
        /** roll-rate limits, deg/s: intercept 4, VOR on-course 1.3, LOC on-course 4 */
        const val rollRateInterceptDps = 4.0
        const val rollRateVorOnCourseDps = 1.3
        const val rollRateLocOnCourseDps = 4.0
        /** AMM 34-31-0: flight-director steering commands limited to 30 deg roll, 12 deg pitch */
        const val fdBankDeg = 30.0
        const val fdPitchDeg = 12.0
        /** capture thresholds derived from AMM test levels: LOC about 1.0 deg, VOR 3 deg */
        const val locCaptureDeg = 1.0
        const val vorCaptureDeg = 3.0
        /** AMM 22-11-0: capture -> on-course when bank < 6 deg and course error < 15 deg */
        const val onCourseBankDeg = 6.0
        const val onCourseCrsErrDeg = 15.0
        /** AMM 22-11-0: glide-slope capture phase 10 s at 700 ft/min, gain reduced with the radio altimeter below 1500 ft; nominal beam 3 deg */
        const val gsCaptureS = 10.0
        const val gsCaptureFpm = 700.0
        const val gsRaGainFt = 1500.0
        const val gsBeamDeg = 3.0
    }

    /** Trainer-chosen values. NOT claimed as Boeing values. */
    object TRAINER {
        /** proportional gains: degrees of intercept angle per degree of beam deviation, and bank per degree of course error */
        const val locInterceptGain = 10.0
        const val vorInterceptGain = 4.5
        const val courseGain = 1.2
        /** on-course window on the beam deviation, deg */
        const val locOnCourseDevDeg = 0.6
        const val vorOnCourseDevDeg = 2.0
        /** glide slope: correction per degree of beam error (limited to +/- gsErrClampDeg) and the VBS drop-out window */
        const val gsErrGain = 4.0
        const val gsErrClampDeg = 3.0
        const val gsCaptureErrDeg = 0.25
        /** gain programmer shapes: 10 s ease-on, floor 0.25, time-since-capture roll-off above 1500 ft */
        const val gsEaseOnS = 10.0
        const val gsGainFloor = 0.25
        const val gsGainDecayStartS = 90.0
        const val gsGainDecayS = 120.0
        /** VOR zone of confusion: ground radius = this x altitude (nm), never under the minimum */
        const val zoneK = 1.2
        const val zoneMinNm = 0.15
        /** altitude acquire: commanded vertical speed per foot of altitude error, its limit, and the band inside which ALT HOLD is annunciated */
        const val altAcqFpmPerFt = 1.5
        const val altAcqMaxFpm = 1800.0
        const val altHoldBandFt = 100.0
    }

    const val FD_LIMIT_BANK = AMM.fdBankDeg
    const val FD_LIMIT_PITCH = AMM.fdPitchDeg
    const val LOC_CAPTURE_DEG = AMM.locCaptureDeg
    const val VOR_CAPTURE_DEG = AMM.vorCaptureDeg
    const val ONCOURSE_BANK_DEG = AMM.onCourseBankDeg
    const val ONCOURSE_CRSERR_DEG = AMM.onCourseCrsErrDeg
    const val GS_CAPTURE_S = AMM.gsCaptureS
    const val GS_CAPTURE_FPM = AMM.gsCaptureFpm
    const val GS_CAPTURE_ERR_DEG = TRAINER.gsCaptureErrDeg
    const val GS_EASE_ON_S = TRAINER.gsEaseOnS
    const val GS_RA_GAIN_FT = AMM.gsRaGainFt

    // ------------------------------------------------------------------
    // Engage interlocks and disconnect reasons
    // ------------------------------------------------------------------

    data class AfPlant(
        /** electronic bus 1 / dc power available */
        val pw: Boolean,
        /** hydraulic system B pressure good (AMM 22-11-0 p.10) */
        val hydB: Boolean,
        /** vertical gyro (ATT) and compass (HDG) valid */
        val attOk: Boolean,
        val hdgOk: Boolean,
    )

    /** AIL / ELEV engage interlocks (AMM 22-11-0 p.9, p.32). */
    fun afInterlocks(p: AfPlant): Boolean = p.pw && p.hydB && p.attOk && p.hdgOk

    /** Which interlock broke, in technician check order; null while all hold. */
    fun afDisconnectReason(p: AfPlant): String? = when {
        !p.pw -> "DC power lost"
        !p.hydB -> "hydraulic system B pressure low"
        !p.attOk -> "attitude gyro (vertical gyro) invalid"
        !p.hdgOk -> "compass (heading) invalid"
        else -> null
    }

    data class ApDrill(val id: String, val label: String, val expect: String)

    /** Fault drills for the Autopilot trainer. */
    val AP_DRILLS: List<ApDrill> = listOf(
        ApDrill("gyro", "Vertical gyro fails", "A/P disconnects (A/P DISC light); reason: attitude gyro invalid. Select the other VERTICAL GYRO source to recover."),
        ApDrill("compass", "Compass fails", "A/P disconnects; reason: compass invalid. Select the other COMPASS source to recover."),
        ApDrill("altitude", "Altitude source fails", "ALT HOLD releases and pitch returns to attitude hold; the A/P stays engaged."),
    )

    // ------------------------------------------------------------------
    // Lateral
    // ------------------------------------------------------------------

    data class LateralCommand(val bank: Double, val lim: Double, val rate: Double)

    /**
     * Radio lateral law (AMM 22-11-0). `dev` is the bar deflection in degrees (positive = fly right).
     * TS returns `lim` and `rate` too; `lim` is the bank limit actually applied.
     */
    fun radioLateral(kind: String, dev: Double, crs: Double, hdg: Double): LateralCommand {
        val isLoc = kind == "LOC"
        val inter = clamp(dev * (if (isLoc) TRAINER.locInterceptGain else TRAINER.vorInterceptGain), -AMM.interceptMaxDeg, AMM.interceptMaxDeg)
        val onCourse = abs(dev) < (if (isLoc) TRAINER.locOnCourseDevDeg else TRAINER.vorOnCourseDevDeg) && abs(angDiff(crs, hdg)) < AMM.onCourseCrsErrDeg
        val lim = if (onCourse) AMM.bankOnCourseDeg else AMM.bankInterceptDeg
        return LateralCommand(
            bank = clamp(angDiff(crs + inter, hdg) * TRAINER.courseGain, -lim, lim),
            lim = lim,
            rate = if (onCourse && !isLoc) AMM.rollRateVorOnCourseDps else AMM.rollRateInterceptDps,
        )
    }

    enum class Light(val label: String) { OFF("OFF"), AMBER("AMBER"), GREEN("GREEN") }

    /** Roll channel radio submodes. */
    enum class LatSub { OFF, INTERCEPT, CAPTURE, ON_COURSE, OVER_STATION }

    /** Pitch channel glide-slope phases. */
    enum class GsPhase { OFF, ARMED, CAPTURE, TRACK }

    data class LatIn(
        /** NAV switch held in VOR LOC or AUTO APP with a valid beam */
        val active: Boolean,
        val kind: String,
        /** beam deviation, deg (positive = fly right) */
        val dev: Double,
        val bank: Double,
        /** heading minus selected course, deg */
        val crsErr: Double,
        /** VOR zone of confusion */
        val overStation: Boolean,
    )

    fun stepLateral(prev: LatSub, i: LatIn): LatSub {
        if (!i.active) return LatSub.OFF
        if (prev == LatSub.OFF || prev == LatSub.INTERCEPT) {
            return if (abs(i.dev) < (if (i.kind == "LOC") LOC_CAPTURE_DEG else VOR_CAPTURE_DEG)) LatSub.CAPTURE else LatSub.INTERCEPT
        }
        if (prev == LatSub.CAPTURE) {
            return if (abs(i.bank) < ONCOURSE_BANK_DEG && abs(i.crsErr) < ONCOURSE_CRSERR_DEG) LatSub.ON_COURSE else LatSub.CAPTURE
        }
        if (i.kind != "VOR") return LatSub.ON_COURSE
        return if (i.overStation) LatSub.OVER_STATION else LatSub.ON_COURSE
    }

    /** Zone of confusion: ground radius = zoneK x altitude, never under the minimum. */
    fun inZoneOfConfusion(vorDistNm: Double, altFt: Double): Boolean =
        vorDistNm < max(TRAINER.zoneMinNm, (max(0.0, altFt) / 6076.12) * TRAINER.zoneK)

    /** Roll command for the current submode. */
    fun lateralLaw(sub: LatSub, kind: String, dev: Double, crs: Double, hdg: Double, holdHdg: Double): LateralCommand {
        val onLim = AMM.bankOnCourseDeg
        val inLim = AMM.bankInterceptDeg
        if (sub == LatSub.OVER_STATION) {
            return LateralCommand(clamp(angDiff(holdHdg, hdg) * TRAINER.courseGain, -onLim, onLim), onLim, AMM.rollRateVorOnCourseDps)
        }
        val base = radioLateral(kind, dev, crs, hdg)
        if (sub == LatSub.ON_COURSE) {
            return LateralCommand(
                clamp(base.bank, -onLim, onLim),
                onLim,
                if (kind == "VOR") AMM.rollRateVorOnCourseDps else AMM.rollRateLocOnCourseDps,
            )
        }
        return LateralCommand(clamp(base.bank, -inLim, inLim), inLim, AMM.rollRateInterceptDps)
    }

    // ------------------------------------------------------------------
    // Vertical
    // ------------------------------------------------------------------

    data class GsIn(
        /** AUTO APP or MAN G/S held with a valid glide slope and pitch engaged */
        val want: Boolean,
        /** MAN G/S: the intercept phase is bypassed and capture begins at once */
        val manual: Boolean,
        /** localizer already captured (green VOR/LOC) */
        val locCaptured: Boolean,
        /** glide-slope error, deg (positive = above the beam) */
        val gsErr: Double,
    )

    data class GlideStep(val phase: GsPhase, val capT: Double)

    fun stepGlide(prev: GsPhase, capT: Double, i: GsIn, dt: Double): GlideStep {
        if (!i.want) return GlideStep(GsPhase.OFF, 0.0)
        if (prev == GsPhase.OFF || prev == GsPhase.ARMED) {
            return if (i.manual || (i.locCaptured && abs(i.gsErr) < GS_CAPTURE_ERR_DEG)) GlideStep(GsPhase.CAPTURE, 0.0) else GlideStep(GsPhase.ARMED, 0.0)
        }
        if (prev == GsPhase.CAPTURE) {
            val t = capT + dt
            return if (t >= GS_CAPTURE_S) GlideStep(GsPhase.TRACK, t) else GlideStep(GsPhase.CAPTURE, t)
        }
        return GlideStep(GsPhase.TRACK, min(capT + dt, 120.0))
    }

    /** Glide-slope track: flight-path angle command (deg). gsErr positive = above the beam. */
    fun gsGamma(gsErr: Double): Double =
        -AMM.gsBeamDeg - clamp(gsErr * TRAINER.gsErrGain, -TRAINER.gsErrClampDeg, TRAINER.gsErrClampDeg)

    /** Gain programmer: ease-on after capture, reduced near the transmitter (radio altitude below 1500 ft, else time since capture). */
    fun glideGain(capT: Double, raFt: Double): Double {
        val ease = clamp((capT - GS_CAPTURE_S) / TRAINER.gsEaseOnS, 0.0, 1.0)
        val floor = TRAINER.gsGainFloor
        val ra = if (raFt < GS_RA_GAIN_FT) {
            clamp(raFt / GS_RA_GAIN_FT, floor, 1.0)
        } else {
            clamp(1 - max(0.0, capT - TRAINER.gsGainDecayStartS) / TRAINER.gsGainDecayS, floor, 1.0)
        }
        return ease * ra
    }

    /** Flight-path angle (deg) that gives a vertical speed (fpm) at a true airspeed (kt). */
    fun fpmToGamma(fpm: Double, tasKt: Double): Double =
        asin(clamp(fpm / 60 / max(tasKt * 1.688, 60.0), -0.3, 0.3)) * 180 / PI

    /** Flight-path angle command (deg). Capture flies the 700 ft/min descent bias; track follows the beam through the gain programmer. */
    fun glideGamma(
        phase: GsPhase,
        gsErr: Double,
        tasKt: Double,
        capT: Double = GS_CAPTURE_S + TRAINER.gsEaseOnS,
        raFt: Double = 9999.0,
    ): Double =
        if (phase == GsPhase.CAPTURE) -fpmToGamma(GS_CAPTURE_FPM, tasKt) else gsGamma(gsErr * glideGain(capT, raFt))

    data class AltAcquire(val vsFpm: Double, val gamma: Double, val phase: String)

    /** ALT HOLD: altitude acquire then hold. errFt = selected altitude minus current (positive = climb). */
    fun altAcquire(errFt: Double, tasKt: Double): AltAcquire {
        val vsFpm = clamp(errFt * TRAINER.altAcqFpmPerFt, -TRAINER.altAcqMaxFpm, TRAINER.altAcqMaxFpm)
        return AltAcquire(
            vsFpm = vsFpm,
            gamma = fpmToGamma(vsFpm, tasKt),
            phase = if (abs(errFt) < TRAINER.altHoldBandFt) "HOLD" else "ACQ",
        )
    }

    /** FD mode knob: 0 GA, 1 OFF, 2 HDG, 3 VOR/LOC, 4 AUTO APPR. Selecting GA while flying ends the approach modes. */
    fun goAroundSelected(fdMode: Int, flying: Boolean): Boolean = flying && fdMode == 0

    /** Approach progress display. Lights latch green until MAN is selected. */
    fun nextLight(prev: Light, selected: Boolean, captured: Boolean): Light = when {
        !selected -> Light.OFF
        captured -> Light.GREEN
        prev == Light.GREEN -> Light.GREEN
        else -> Light.AMBER
    }
}
