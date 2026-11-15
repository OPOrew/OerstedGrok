package com.oersted.trainer.sim

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Target values of the sound-effect voices (audio.ts buildFx(), the methods of its returned object). Pure: each function
 * returns what the web passes to setTargetAtTime (value, time constant) and the bus gate. The Web Audio nodes are built
 * by the Android layer. Time constants are seconds, as in audio.ts.
 */
object FxTargets {
    /** One setTargetAtTime call: the value and the time constant. */
    data class Ramp(val target: Double, val timeConstant: Double)

    /** A linear ramp of a playback rate (the stick-shaker motors): from [start] to [end] over [durationS]. */
    data class RateRamp(val start: Double, val end: Double, val durationS: Double)

    data class Gear(
        val busOn: Boolean,
        val motorFreq: Ramp, val motor2Freq: Ramp, val motorGain: Ramp, val motor2Gain: Ramp,
        val rumbleGain: Ramp, val windFreq: Ramp, val windGain: Ramp, val windHiGain: Ramp,
    )

    fun gear(hyd: Double, free: Double, wind: Double): Gear = Gear(
        busOn = hyd > 0.01 || free > 0.01 || wind > 0.01,
        motorFreq = Ramp(130 + hyd * 70, 0.1),
        motor2Freq = Ramp(262 + hyd * 140, 0.1),
        motorGain = Ramp(0.016 * hyd, 0.12),
        motor2Gain = Ramp(0.008 * hyd, 0.12),
        rumbleGain = Ramp(0.1 * max(hyd * 0.7, free), 0.1),
        windFreq = Ramp(500 + 500 * wind, 0.15),
        windGain = Ramp(0.06 * wind + 0.05 * free, 0.15),
        windHiGain = Ramp(0.012 * wind + 0.03 * free, 0.15),
    )

    data class Flap(val busOn: Boolean, val motorFreq: Ramp, val motor2Freq: Ramp, val motorGain: Ramp, val motor2Gain: Ramp, val whirGain: Ramp)

    fun flap(level: Double): Flap = Flap(
        busOn = level > 0.01,
        motorFreq = Ramp(80 + 40 * level, 0.1),
        motor2Freq = Ramp(160 + 80 * level, 0.1),
        motorGain = Ramp(0.02 * level, 0.1),
        motor2Gain = Ramp(0.008 * level, 0.1),
        whirGain = Ramp(0.045 * level, 0.12),
    )

    data class Door(val busOn: Boolean, val motorGain: Ramp, val whirGain: Ramp)

    fun door(level: Double): Door = Door(
        busOn = level > 0.01,
        motorGain = Ramp(0.012 * level, 0.1),
        whirGain = Ramp(0.03 * level, 0.1),
    )

    data class Spoiler(val busOn: Boolean, val hissGain: Ramp, val rumbleGain: Ramp)

    fun spoiler(level: Double): Spoiler = Spoiler(
        busOn = level > 0.01,
        hissGain = Ramp(0.07 * level, 0.08),
        rumbleGain = Ramp(0.08 * level, 0.08),
    )

    data class Hyd(
        val busOn: Boolean, val engFreq: Ramp, val engGain: Ramp, val elecGain: Ramp, val elec2Gain: Ramp,
    )

    /** Hydraulic pump hum: on when a pump is running and pressure is above 300 psi. */
    fun hyd(press: Double, pumps: Double, elec: Boolean): Hyd {
        val on = pumps > 0 && press > 300
        val pr = min(1.0, max(0.0, press / 3000))
        val elecLevel = if (on && elec) 0.003 + 0.004 * pr else 0.0
        return Hyd(
            busOn = on,
            engFreq = Ramp(230 + pr * 120, 0.2),
            engGain = Ramp(if (on) 0.004 + 0.008 * pr else 0.0, 0.25),
            elecGain = Ramp(elecLevel, 0.25),
            elec2Gain = Ramp(elecLevel, 0.25),
        )
    }

    data class Roll(
        val busOn: Boolean, val rumbleFreq: Ramp, val bumpRate: Ramp, val bumpDepth: Ramp,
        val rumbleGain: Ramp, val humFreq: Ramp, val humGain: Ramp,
    )

    /** Tyre rumble on the runway ([grass] picks the grass set). */
    fun roll(r: Double, grass: Boolean): Roll = Roll(
        busOn = r > 0.005,
        rumbleFreq = Ramp((if (grass) 90.0 else 130.0) + r * (if (grass) 300 else 650), 0.2),
        bumpRate = Ramp(1 + r * (if (grass) 5 else 9), 0.2),
        bumpDepth = Ramp((if (grass) 0.09 else 0.035) * r, 0.2),
        rumbleGain = Ramp((if (grass) 0.16 else 0.1) * r.pow(0.8) * (if (grass) 1.3 else 1.0), 0.2),
        humFreq = Ramp(300 + r * 700, 0.2),
        humGain = Ramp(0.045 * r, 0.2),
    )

    data class Skid(val busOn: Boolean, val squealFreq: Ramp, val squealLfoDepth: Ramp, val squealGain: Ramp, val chatterGain: Ramp)

    fun skid(squeal: Double, pitchHz: Double, chatter: Double): Skid = Skid(
        busOn = squeal > 0.005 || chatter > 0.005,
        squealFreq = Ramp(pitchHz, 0.06),
        squealLfoDepth = Ramp(squeal * 220, 0.1),
        squealGain = Ramp(0.18 * squeal, 0.06),
        chatterGain = Ramp(0.03 * chatter, 0.05),
    )

    data class Brake(val busOn: Boolean, val hissFreq: Ramp, val hissGain: Ramp, val groanGain: Ramp)

    fun brake(p: Double): Brake = Brake(
        busOn = p > 0.005,
        hissFreq = Ramp(500 + 500 * p, 0.15),
        hissGain = Ramp(0.05 * p, 0.12),
        groanGain = Ramp(0.014 * p * p, 0.15),
    )

    data class Rev(val busOn: Boolean, val roarGain: Ramp, val roarFreq: Ramp, val lowGain: Ramp)

    fun rev(level: Double): Rev = Rev(
        busOn = level > 0.005,
        roarGain = Ramp(0.2 * level, 0.25),
        roarFreq = Ramp(500 + 1500 * level, 0.25),
        lowGain = Ramp(0.14 * level, 0.3),
    )

    data class Amb(
        val busOn: Boolean, val humGain: Ramp, val hum2Gain: Ramp, val fanGain: Ramp, val packGain: Ramp, val fuelGain: Ramp,
    )

    fun amb(ac: Boolean, dc: Boolean, packs: Double, fuel: Double): Amb = Amb(
        busOn = ac || dc || packs > 0 || fuel > 0,
        humGain = Ramp(if (ac) 0.005 else 0.0, 0.3),
        hum2Gain = Ramp(if (ac) 0.002 else 0.0, 0.3),
        fanGain = Ramp(if (dc) 0.004 else 0.0, 0.4),
        packGain = Ramp(packs * 0.011, 0.5),
        fuelGain = Ramp(if (fuel > 0) 0.004 + 0.002 * min(3.0, fuel) else 0.0, 0.3),
    )

    /** Gear warning horn gate. */
    fun horn(on: Boolean): Pair<Boolean, Ramp> = on to Ramp(if (on) 0.1 else 0.0, 0.02)

    /** Autopilot disconnect wailer gate. */
    fun wailer(on: Boolean): Pair<Boolean, Ramp> = on to Ramp(if (on) 0.1 else 0.0, 0.025)

    /** Mach / airspeed clacker gate. */
    fun clacker(on: Boolean): Pair<Boolean, Ramp> = on to Ramp(if (on) 0.17 else 0.0, 0.01)

    data class Shaker(val busOn: Boolean, val enableGain: Ramp, val rateRamp: RateRamp)

    /**
     * Stick shakers. [currentRate] is the motor's playback rate now (the web coasts down from it). The caller applies
     * [rateRamp] only when the state changed, as audio.ts does (shOn check).
     */
    fun shaker(on: Boolean, currentRate: Double): Shaker = Shaker(
        busOn = on,
        enableGain = Ramp(if (on) 0.35 else 0.0, if (on) 0.03 else 0.12),
        rateRamp = if (on) RateRamp(0.5, 1.0, 0.45) else RateRamp(max(currentRate, 0.6), 0.55, 0.4),
    )
}
