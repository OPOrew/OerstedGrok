package com.oersted.trainer.sim

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Port of the flight-director steering commands in useAttitude (MainInstrumentPanel.tsx, the V-pointer section).
 * Bank and pitch pointers are limited by the AMM values in AutoFlight.FD_LIMIT_BANK / FD_LIMIT_PITCH and quantised to half degrees.
 * The caller supplies the navigation solution and flags. Radio lateral and glide commands come from the ported AutoFlight helpers.
 */
object FlightDirector {
    data class Inputs(
        val power: Boolean,
        /** 0 GA, 1 OFF, 2 HDG, 3 VOR/LOC, 4 AUTO APPR, 5 (glide) */
        val fdMode: Int,
        /** FD available: power, no FD fail, attitude valid */
        val fdUp: Boolean,
        val hdgBad: Boolean,
        /** navigation beam validity for the receiver the FD uses */
        val vorValid: Boolean,
        val locValid: Boolean,
        /** the tuned receiver is a VOR (captKind == "VOR") */
        val captIsVor: Boolean,
        /** lateral deviation for the tuned receiver (VOR radial deviation or LOC deviation) */
        val lateralDev: Double,
        val selCrs: Double,
        val selHdg: Double,
        val hdg: Double,
        val roll: Double,
        val pitch: Double,
        val aoa: Double,
        val flying: Boolean,
        val altFt: Double,
        val gsCapFd: Boolean,
        val gsErr: Double,
        val altFd: Double,
        val fdAlt: Boolean,
        val fdPitch: Double,
    )

    /** show / flag as in the web; bank and pitch are already quantised to half degrees. */
    data class Command(val show: Boolean, val flag: Boolean, val bank: Double, val pitch: Double)

    /** JS Math.round(x * 2) / 2: half rounds up, unlike Kotlin's default. */
    fun quantizeHalf(x: Double): Double = floor(x * 2.0 + 0.5) / 2.0

    fun compute(p: Inputs): Command {
        val limBank = AutoFlight.FD_LIMIT_BANK
        val limPitch = AutoFlight.FD_LIMIT_PITCH
        var show = false
        var flag = false
        var bank = 0.0
        var pit = 0.0
        if (p.power && p.fdMode != 1) {
            val radio = p.fdMode >= 3
            val kindVor = p.fdMode == 3 && p.captIsVor
            val beam = if (kindVor) p.vorValid else p.locValid
            if (!p.fdUp || (radio && !beam) || (p.fdMode == 2 && p.hdgBad)) {
                flag = true // COMPUTER warning flag
            } else {
                show = true
                if (p.fdMode == 0) {
                    // GA: wings level, preset pitch-up
                    bank = clamp(-p.roll, -limBank, limBank)
                    pit = clamp(15.0 - p.pitch, -limPitch, limPitch)
                } else {
                    val bc = if (radio) {
                        AutoFlight.radioLateral(if (kindVor) "VOR" else "LOC", p.lateralDev, p.selCrs, p.hdg).bank
                    } else {
                        clamp(AutoFlight.angDiff(p.selHdg, p.hdg) * 1.5, -limBank, limBank)
                    }
                    val gT = if (p.gsCapFd) AutoFlight.gsGamma(p.gsErr)
                    else if (p.fdAlt) clamp((p.altFd - p.altFt) * 0.015, -5.0, 5.0)
                    else p.fdPitch
                    bank = clamp(bc - p.roll, -limBank, limBank)
                    pit = clamp((if (p.flying) p.aoa else 0.0) + gT - p.pitch, -limPitch, limPitch)
                }
            }
        }
        return Command(show, flag, quantizeHalf(bank), quantizeHalf(pit))
    }

    private fun clamp(v: Double, lo: Double, hi: Double): Double = max(lo, min(hi, v))
}
