package com.oersted.trainer.sim

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * Output step of useAttitude (MainInstrumentPanel.tsx, ~1118–1145): the dynamic-branch altitude that the flight
 * navigation and the flight-plan sim fields follow, and the change gate that decides whether the attitude is written
 * back to MIP. Pure; the caller applies the results to SimStore / MIP and calls advanceFlightNavigation.
 */
object AttitudeOutputs {
    data class Altitude(
        /** navAltitudeFt: simAlt when not dynamic, else simAlt plus the vertical speed over dt */
        val navAltitudeFt: Double,
        /** psFlightVsFpm (rounded fpm), set only in the dynamic branch; null means no SimStore write */
        val psFlightVsFpm: Double?,
    )

    /** dyn branch: vsNow = sin(gamma) * tas * 1.688 * 60 (fpm); altitude follows it. Not dynamic: the altitude is unchanged. */
    fun altitude(dyn: Boolean, gammaDeg: Double, tasKt: Double, simAltFt: Double, dt: Double): Altitude {
        if (!dyn) return Altitude(simAltFt, null)
        val vsNow = sin(gammaDeg * Math.PI / 180.0) * tasKt * 1.688 * 60.0
        return Altitude(
            navAltitudeFt = max(0.0, simAltFt + (vsNow / 60.0) * dt),
            psFlightVsFpm = FlightPhaseControl.jsRound(vsNow),
        )
    }

    /**
     * Whether the integrated attitude is written back to MIP. The web returns early (no write) when all three axes
     * moved less than 0.02 deg. The hook's internal current attitude is always updated regardless.
     */
    fun shouldWriteAttitude(prev: MipLocal.Attitude, next: MipLocal.Attitude): Boolean =
        !(abs(next.pitch - prev.pitch) < 0.02 && abs(next.roll - prev.roll) < 0.02 && abs(next.hdg - prev.hdg) < 0.02)
}
