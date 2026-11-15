package com.oersted.trainer.sim

import kotlin.math.max
import kotlin.math.min

/**
 * Port of the attitude-target and flight-path logic in useAttitude (MainInstrumentPanel.tsx, "attitude targets" section
 * and the dyn branch). Everything the hook computes from autopilot, navigation and flight-director state arrives as an
 * input, so this stays pure. The caller computes lateralLaw / altAcquire / glideGamma, which are already ported in AutoFlight.kt.
 */
object AttitudeTargets {
    fun clamp(v: Double, lo: Double, hi: Double): Double = max(lo, min(hi, v))

    /** Inputs for one tick. Angles in degrees; error terms are already signed (target minus current). */
    data class Inputs(
        val flying: Boolean,
        val wow: Boolean,
        val ias: Double,
        val turb: Boolean,
        val yokePitch: Double,
        val yokeRoll: Double,
        val gamma: Double,
        val aoa: Double,
        val ailOn: Boolean,
        val cwsR: Boolean,
        val navHeld: Boolean,
        /** lateral law output, read only when navHeld */
        val lateralBank: Double,
        val lateralLim: Double,
        val lateralRate: Double,
        /** plan heading command present and usable (planLat in the hook) */
        val planLat: Boolean,
        /** angDiff(plan heading command, heading) */
        val planHdgErr: Double,
        /** autopilot heading switch: 1 = hold, 2 = HDG SEL */
        val apHdgSw: Int,
        /** angDiff(selected heading, heading) */
        val selHdgErr: Double,
        val hdgHold: Boolean,
        /** angDiff(reference heading, heading) */
        val refHdgErr: Double,
        val refRoll: Double,
        val dyn: Boolean,
        val elevOn: Boolean,
        val cwsP: Boolean,
        val gsCapAp: Boolean,
        val altSel: Boolean,
        val refPitch: Double,
        /** glideGamma (only when gsCapAp) or altAcquire gamma (only when altSel) */
        val capturedGamma: Double,
    )

    data class Result(
        val rollTarget: Double,
        val rollRate: Double,
        val pitchTarget: Double,
        /** new flight-path angle (only changes when dyn) */
        val gamma: Double,
    )

    fun compute(p: Inputs, dt: Double): Result {
        val bankMax = if (p.turb) 8.0 else 25.0
        var rT = if (p.flying) clamp(p.yokeRoll * 30.0, -35.0, 35.0) else 0.0
        var rollRate = 8.0
        var pT = if (p.flying) clamp(p.gamma + p.aoa + p.yokePitch * 8.0, -25.0, 30.0)
        else if (p.wow && p.ias > 110.0) clamp(p.yokePitch * 12.0, 0.0, 12.0) else 0.0
        if (p.flying && p.ailOn && !p.cwsR) {
            rollRate = 7.0 // heading hold roll-rate limit
            if (p.navHeld) {
                val lim = min(p.lateralLim, bankMax)
                rT = clamp(p.lateralBank, -lim, lim)
                rollRate = p.lateralRate
            } else if (p.planLat) {
                rT = clamp(p.planHdgErr * 1.2, -bankMax, bankMax)
            } else if (p.apHdgSw == 2) {
                rT = clamp(p.selHdgErr * 1.2, -bankMax, bankMax)
            } else if (p.apHdgSw == 1 && p.hdgHold) {
                rT = clamp(p.refHdgErr * 1.2, -bankMax, bankMax)
            } else {
                rT = p.refRoll
            }
        }
        var gamma = p.gamma
        if (p.dyn) {
            if (p.elevOn && !p.cwsP) {
                val target = if (p.gsCapAp) p.capturedGamma else if (p.altSel) p.capturedGamma else p.refPitch - p.aoa
                gamma = step(p.gamma, target, if (p.turb) 0.75 else 1.5, dt)
            } else {
                gamma = clamp(p.gamma + p.yokePitch * 3.0 * dt, -15.0, 15.0) // pull = climb: the yoke commands flight-path rate
            }
            pT = clamp(gamma + p.aoa, -25.0, 30.0)
        } else if (p.flying && p.elevOn && !p.cwsP) {
            pT = if (p.altSel) clamp(p.aoa + p.capturedGamma, -25.0, 30.0) else p.refPitch
        }
        return Result(rollTarget = rT, rollRate = rollRate, pitchTarget = pT, gamma = gamma)
    }

    private fun step(c: Double, tgt: Double, rate: Double, dt: Double): Double = AttitudeKinematics.step(c, tgt, rate, dt)
}
