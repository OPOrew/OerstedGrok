package com.oersted.trainer.sim

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * Port of the kinematic core of useAttitude (MainInstrumentPanel.tsx, the "attitude targets" and integration steps):
 * the rate-limited approach of pitch and roll to their targets, and the heading change from bank.
 * The targets themselves depend on the autopilot and radio-nav orchestration, which is not ported yet; this step
 * takes them as inputs. Verified by hand-computed unit tests, not by a web parity harness yet.
 */
object AttitudeKinematics {
    /** TS step(c, tgt, rate, dt): move c toward tgt by at most rate * dt. */
    fun step(c: Double, tgt: Double, rate: Double, dt: Double): Double =
        c + min(rate * dt, max(-rate * dt, tgt - c))

    /** Pitch approaches its target at 1.2 deg/s in turbulence, 2.0 deg/s otherwise (TS: turb ? 1.2 : 2.0). */
    fun pitchRate(turb: Boolean): Double = if (turb) 1.2 else 2.0

    /**
     * One integration step. [pitchTarget] and [rollTarget] are the attitude targets (deg), [rollRate] the roll-rate limit (deg/s).
     * When flying, heading changes at the standard turn rate for the new bank: 1091 * tan(bank) / max(tas, 100) degrees per second.
     */
    fun integrate(
        a: MipLocal.Attitude,
        pitchTarget: Double,
        rollTarget: Double,
        rollRate: Double,
        tas: Double,
        flying: Boolean,
        turb: Boolean,
        dt: Double,
    ): MipLocal.Attitude {
        val pitch = step(a.pitch, pitchTarget, pitchRate(turb), dt)
        val roll = step(a.roll, rollTarget, rollRate, dt)
        val turn = if (flying) ((1091.0 * tan(roll * PI / 180.0)) / max(tas, 100.0)) * dt else 0.0
        val hdg = (((a.hdg + turn) % 360.0) + 360.0) % 360.0
        return MipLocal.Attitude(pitch = pitch, roll = roll, hdg = hdg)
    }
}
