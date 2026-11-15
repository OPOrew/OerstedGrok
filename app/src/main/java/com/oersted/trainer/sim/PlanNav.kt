package com.oersted.trainer.sim

/**
 * PLAN NAV gating (MainInstrumentPanel.tsx, useAttitude, "autopilot references" block, ~1034–1060).
 * Trainer aid, not a 737-200 system: heading command from the active plan while AIL is engaged and HDG SEL is not selected;
 * altitude target while ELEV is engaged with the pitch switch OFF (ALT HOLD on the panel always wins).
 * Pure: the caller supplies the guidance solution, the interlocked mode results and the previous-frame latches.
 */
object PlanNav {
    data class Inputs(
        /** m.planNav: the trainer PLAN NAV switch */
        val planNav: Boolean,
        /** guide.active */
        val guideActive: Boolean,
        /** guide.headingMagCmdDeg (null when the plan gives no heading command) */
        val headingMagCmdDeg: Double?,
        /** guide.targetAltFt (null when the plan gives no altitude target) */
        val targetAltFt: Double?,
        /** guide.note, shown in the status text */
        val guideNote: String,
        val ailOn: Boolean,
        val elevOn: Boolean,
        val cwsR: Boolean,
        val cwsP: Boolean,
        val navHeld: Boolean,
        /** m.apHdgSw: 1 HDG HOLD, 2 HDG SEL */
        val apHdgSw: Int,
        /** m.apPitch: 1 pitch hold, 2 ALT HOLD */
        val apPitch: Int,
        val flying: Boolean,
        val ga: Boolean,
        val altBad: Boolean,
        /** r.gsCapAp */
        val gsCapAp: Boolean,
        /** r.prevAltSel: altSel from the previous frame */
        val prevAltSel: Boolean,
    )

    data class Result(
        /** planLat: heading command from the plan drives the roll law this frame */
        val planLat: Boolean,
        /** planVert: altitude target from the plan drives the pitch law this frame */
        val planVert: Boolean,
        /** altSel: ALT HOLD (or plan vertical) is selected this frame */
        val altSel: Boolean,
        /** altAp = guide.targetAltFt when planVert; null means no override from this block */
        val altApFromPlan: Double?,
        /** altAp = h on the rising edge of altSel (caller passes h as the new hold altitude) */
        val altApCaptureHere: Boolean,
        /** planNavStatus text */
        val status: String,
    )

    fun evaluate(p: Inputs): Result {
        val planLat = p.planNav && p.guideActive && p.headingMagCmdDeg != null &&
            p.ailOn && !p.cwsR && !p.navHeld && p.apHdgSw != 2 && p.flying
        val planVert = p.planNav && p.guideActive && p.targetAltFt != null &&
            p.elevOn && !p.cwsP && p.apPitch == 1 && !p.gsCapAp && !p.altBad && !p.ga && p.flying
        val altSel = (p.elevOn && p.apPitch == 2 && !p.gsCapAp && !p.altBad && !p.ga) || planVert
        val altApFromPlan = if (planVert) p.targetAltFt else null
        val altApCaptureHere = !planVert && altSel && !p.prevAltSel
        val status = when {
            !p.planNav -> "OFF"
            !p.guideActive -> "NO PLAN"
            !p.ailOn -> "ARMED - ENGAGE AIL"
            p.navHeld -> "ILS"
            p.apHdgSw == 2 -> "HDG SEL OVERRIDES"
            planLat -> "ACTIVE ${p.guideNote}"
            else -> "ARMED"
        }
        return Result(planLat, planVert, altSel, altApFromPlan, altApCaptureHere, status)
    }
}
