package com.oersted.trainer.sim

/**
 * ILS handoff (MainInstrumentPanel.tsx, useAttitude, ~1047–1059): when the plan's ILS arm is set on WSSS 02L, tune pedestal NAV 1 and NAV 2
 * (authority), set the course, select FD AUTO APP once the localizer is valid, and engage APP once the localizer is captured.
 * Pure: returns the writes the caller applies to MIP / pedestal. NAV 1 tuning uses NavReceiver.tuneNav1 (raw pedestal values, MIP fields).
 */
object IlsHandoff {
    data class Inputs(
        /** m.planNav */
        val planNav: Boolean,
        /** guide.active */
        val guideActive: Boolean,
        /** guide.ilsArm */
        val ilsArm: Boolean,
        /** sceneId === "WSSS02L" */
        val sceneIsWsss02L: Boolean,
        /** liveArr is present (the arrival carries an ILS) */
        val hasLiveArrival: Boolean,
        /** liveArr.ilsFrequencyMHz (used only when hasLiveArrival) */
        val ilsFreqMHz: Double,
        /** liveArr.courseMag (used only when hasLiveArrival) */
        val ilsCourseMag: Double,
        /** m.navOn */
        val navOn: Boolean,
        /** captRx.frequencyMHz: the captain's NAV receiver frequency */
        val captFreqMHz: Double,
        /** m.selCrs */
        val selCrs: Double,
        /** pedestal navL.stby (raw) */
        val navLStby: Int,
        /** pedestal navR.stby (raw) */
        val navRStby: Int,
        /** nv.locValid */
        val locValid: Boolean,
        /** m.fdMode */
        val fdMode: Int,
        /** m.apNav */
        val apNav: Int,
        val ailOn: Boolean,
        val elevOn: Boolean,
        val cwsR: Boolean,
        val ga: Boolean,
    )

    data class Result(
        /** true when the handoff conditions held this frame */
        val handoff: Boolean,
        /** NAV 1 retune to write to pedestal + MIP; null when no retune this frame */
        val nav1Tune: NavReceiver.Nav1Tune?,
        /** upd.navOn = true (set alongside the NAV 1 retune) */
        val navOn: Boolean?,
        /** upd.selCrs */
        val selCrs: Double?,
        /** pedestal navR.act (raw) from tuneNav2; null when the handoff did not run */
        val navRAct: Int?,
        /** upd.fdMode = 4 (AUTO APP) */
        val fdMode: Int?,
        /** upd.apNav (2 = APP, 1 = LOC/HDG only) */
        val apNav: Int?,
    )

    fun evaluate(p: Inputs): Result {
        if (!(p.planNav && p.guideActive && p.ilsArm && p.sceneIsWsss02L && p.hasLiveArrival)) {
            return Result(false, null, null, null, null, null, null)
        }
        var nav1: NavReceiver.Nav1Tune? = null
        var navOn: Boolean? = null
        if (!p.navOn || Math.abs(p.captFreqMHz - p.ilsFreqMHz) > 0.001) {
            nav1 = NavReceiver.tuneNav1(p.ilsFreqMHz, p.navLStby, powerOn = true)
            navOn = true
        }
        val selCrs = if (Math.abs(AutoFlight.angDiff(p.selCrs, p.ilsCourseMag)) > 0.5) p.ilsCourseMag else null
        val navRAct = NavReceiver.tuneNav2(p.ilsFreqMHz, p.navRStby).first
        val fdMode = if (p.locValid && p.fdMode != 1 && p.fdMode != 4) 4 else null
        val apNav = if (p.ailOn && p.apNav == 0 && p.locValid && !p.cwsR && !p.ga) (if (p.elevOn) 2 else 1) else null
        return Result(true, nav1, navOn, selCrs, navRAct, fdMode, apNav)
    }
}
