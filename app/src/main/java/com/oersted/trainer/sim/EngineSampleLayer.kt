package com.oersted.trainer.sim

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Recorded-engine layer mapping (audio.ts, update() under `if (smp)`): the JT8D recording (idle loop + power loop) is mixed and
 * pitched from the acoustic spool values. Pure: the caller ramps the gains and playback rates on the sample players.
 */
object EngineSampleLayer {
    data class Mix(val idleGain: Double, val runGain: Double, val idleRate: Double, val runRate: Double)

    private fun clamp(v: Double, lo: Double, hi: Double) = max(lo, min(hi, v))

    /**
     * [eff]: effective spool 0-1 (N1 when running, an N2-based guess while motoring). [jet]: exhaust-noise thrust 0-1.
     * [detune]: the engine's detune factor (audio.ts uses a small per-engine offset).
     */
    fun mix(eff: Double, jet: Double, detune: Double = 1.0): Mix {
        // lit: 0 while only motoring on the starter, 1 once the engine has spooled to idle (eff ~ 0.32 = idle N1)
        val lit = clamp((eff - 0.16) / 0.16, 0.0, 1.0)
        // th: thrust above idle, 0 at idle -> 1 at max N1, used for the rotor pitch
        val th = clamp((eff - 0.32) / 0.68, 0.0, 1.0)
        val thG = clamp(jet, 0.0, 1.0)
        // recording: idle is ~15 dB below high power, so the level follows that spread
        val level = (0.17 + 0.83 * thG.pow(1.45)) * lit.pow(1.4) * 0.55
        // equal-power crossfade idle -> high-power loop between 10 % and 80 % of the thrust range
        val x = clamp((thG - 0.1) / 0.7, 0.0, 1.0)
        val ph = x * x * (3 - 2 * x) * PI / 2
        val wRun = sin(ph)
        val wIdle = cos(ph)
        // pitch: the idle loop is shifted up to the moving pitch; the power loop is 1.0 at max N1, where it was recorded
        val rise = 1 + 1.15 * th
        val idleRate = (0.42 + 0.58 * lit) * (0.8 + 0.2 * lit) * min(rise, 1.85) * detune
        val runRate = (rise / 1.9) * detune
        return Mix(level * wIdle, level * wRun, idleRate, runRate)
    }
}
