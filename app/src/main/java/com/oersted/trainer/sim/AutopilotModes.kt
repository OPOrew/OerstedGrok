package com.oersted.trainer.sim

/**
 * Port of the mode-gating block of useAttitude (MainInstrumentPanel.tsx, lines ~986–1033): which autopilot axes stay
 * engaged, which modes drop out (NAV / HDG SEL / ALT HOLD / A/P disconnect), and the flight director's glide-slope capture.
 * The caller supplies the navigation solution and the glide-slope capture state, and applies the returned values to the MIP.
 */
object AutopilotModes {
    data class Inputs(
        /** AFInterlocks result for this tick (electrical, hydraulic and attitude / heading sources) */
        val interlocksOk: Boolean,
        val apAil: Boolean,
        val apElev: Boolean,
        /** 0 = off, 1 = NAV (VOR/LOC), 2 = APPR LOC, 3 = G/S (see kindOf / beamOk) */
        val apNav: Int,
        /** 0 = TURB, 1 = normal ALT, 2 = ALT HOLD engaged */
        val apPitch: Int,
        /** 1 = HDG HOLD, 2 = HDG SEL */
        val apHdgSw: Int,
        val fdMode: Int,
        val fdAlt: Boolean,
        val flying: Boolean,
        val altFail: Boolean,
        val attBad: Boolean,
        val fdFail: Boolean,
        val power: Boolean,
        val yokeRoll: Double,
        val yokePitch: Double,
        /** the tuned NAV receiver is a VOR */
        val captIsVor: Boolean,
        val vorValid: Boolean,
        val locValid: Boolean,
        val gsValid: Boolean,
        /** glide-slope capture state after the glide step for this tick (ALT HOLD releases on capture) */
        val gsCapAp: Boolean,
        /** the flight director's glide-slope mode is selected */
        val gsErr: Double,
        val gsCapFd: Boolean,
    )

    data class Result(
        val ga: Boolean,
        val ailOn: Boolean,
        val elevOn: Boolean,
        val turb: Boolean,
        val cwsR: Boolean,
        val cwsP: Boolean,
        val navHeld: Boolean,
        /** A/P disconnect: an axis that was engaged has lost its interlocks */
        val disconnect: Boolean,
        val apAil: Boolean,
        val apElev: Boolean,
        val apNav: Int,
        val apHdgSw: Int,
        val apPitch: Int,
        val fdUp: Boolean,
        val fdAlt: Boolean,
        val gsCapFd: Boolean,
    )

    private const val CWS_DETENT = 0.08

    fun evaluate(p: Inputs): Result {
        val ga = AutoFlight.goAroundSelected(p.fdMode, p.flying)
        val ailOn = p.apAil && p.interlocksOk
        val elevOn = p.apElev && p.interlocksOk
        val turb = elevOn && p.apPitch == 0 // TURB: bank limit 8 deg and localizer inoperative
        val cwsR = Math.abs(p.yokeRoll) > CWS_DETENT
        val cwsP = Math.abs(p.yokePitch) > CWS_DETENT

        // NAV receiver validity for the selected mode (TS beamOk / kindOf)
        val isLocKind = !(p.apNav == 1 && p.captIsVor)
        val beamOk = when (p.apNav) {
            1 -> if (p.captIsVor) p.vorValid else p.locValid
            2 -> p.locValid
            3 -> p.locValid && p.gsValid
            else -> true
        }
        val navHeld = p.apNav != 0 && ailOn && !ga && beamOk && !(turb && isLocKind) && (p.apNav < 2 || (elevOn && !cwsP))
        val apNav = if (p.apNav != 0 && !navHeld) 0 else p.apNav

        var apHdgSw = p.apHdgSw
        if ((ailOn && cwsR && p.apHdgSw == 2) || (navHeld && p.apHdgSw == 2) || (!ailOn && p.apHdgSw != 1)) apHdgSw = 1

        val fdUp = p.power && !p.fdFail && !p.attBad
        val fdAlt = if (p.fdAlt && (!fdUp || p.fdMode == 1)) false else p.fdAlt

        val disconnect = (p.apAil || p.apElev) && !p.interlocksOk
        val apAil = if (p.apAil && !p.interlocksOk) false else p.apAil
        val apElev = if (p.apElev && !p.interlocksOk) false else p.apElev

        // ALT HOLD is solenoid held: it releases on glide-slope capture, altitude failure or go-around
        var apPitch = p.apPitch
        if (p.apPitch == 2 && (!elevOn || p.gsCapAp || p.altFail || ga)) apPitch = 1

        // Flight director glide-slope capture: gs mode 4 or 5, a valid glide signal, and (5 or within 0.25 deg)
        val gsWantFd = fdUp && (p.fdMode == 4 || p.fdMode == 5) && p.gsValid
        var gsCapFd = p.gsCapFd
        var fdAltOut = fdAlt
        if (!gsWantFd) {
            gsCapFd = false
        } else if (!gsCapFd && (p.fdMode == 5 || Math.abs(p.gsErr) < 0.25)) {
            gsCapFd = true
            if (fdAltOut) fdAltOut = false
        }

        return Result(
            ga = ga, ailOn = ailOn, elevOn = elevOn, turb = turb, cwsR = cwsR, cwsP = cwsP, navHeld = navHeld,
            disconnect = disconnect, apAil = apAil, apElev = apElev, apNav = apNav, apHdgSw = apHdgSw, apPitch = apPitch,
            fdUp = fdUp, fdAlt = fdAltOut, gsCapFd = gsCapFd,
        )
    }
}
