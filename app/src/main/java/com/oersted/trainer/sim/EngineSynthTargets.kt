package com.oersted.trainer.sim

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Synth layer targets of one engine voice (audio.ts engVoices update(), the part that sets AudioParam targets).
 * Pure: the caller ramps each value on its node with setTargetAtTime. Input is the acoustic spool already stepped by
 * EngineSpoolAudio.stepAcousticSpool. [sampleLayer] is true when the recorded engine loops are attached (smp in audio.ts).
 */
object EngineSynthTargets {
    data class Targets(
        val busOn: Boolean,
        val fanFreq: Double,
        val fanBFreq: Double,
        val fanGain: Double,
        val cmpFreq: Double,
        val cmpBFreq: Double,
        val cmpGain: Double,
        val roarLpFreq: Double,
        val roarGain: Double,
        val lfoDepth: Double,
        val bodyFreq: Double,
        val bodyGain: Double,
        val rushGain: Double,
        val lowShelfGain: Double,
    )

    private fun clamp(v: Double, lo: Double, hi: Double) = max(lo, min(hi, v))

    /** [detune]: 0.992 for the first engine, 1.008 for the second (audio.ts). [valve]: the engine's bleed or start valve input. */
    fun compute(spool: EngineSpoolAudio.AcousticSpool, valve: Boolean, detune: Double, sampleLayer: Boolean): Targets {
        val n1 = spool.n1
        val n2 = spool.n2
        val jet = spool.jet
        val r2 = clamp(n2 / 100, 0.0, 1.0)
        // effective spool: N1 when running, an N2-based guess while only motoring on the starter
        val eff = clamp(max(n1, n2 * 0.5) / 100, 0.0, 1.0)
        val spinning = n2 > 6
        // the recording carries the roar / body / fan: keep only a little synth (whine, starter rush)
        val sf = if (sampleLayer) 0.3 else 1.0
        val startAmt = clamp(1 - (eff - 0.16) / 0.16, 0.0, 1.0) // 1 while starting, 0 at idle and above

        val fanF = (70 + eff * 430) * detune
        val fanGain = if (spinning) sf * (0.008 + 0.05 * eff.pow(1.2)) else 0.0

        // 2.0 kHz idle -> 3.7 kHz full, as measured on the 737-200 recording
        val cmpF = (if (r2 < 0.57) 700 + (r2 / 0.57) * 1300 else 2000 + ((r2 - 0.57) / 0.43) * 1700) * detune
        val cmpGain = if (n2 > 10) (if (sampleLayer) 0.9 else 1.0) * (0.004 + 0.03 * r2 * r2) else 0.0

        val roarLp = 300 + eff * 2200
        val roarBase = if (n2 > 8) 0.02 + 0.045 * min(1.0, n2 / 57) + 0.18 * max(0.2, 0.32 + 0.68 * jet).pow(1.3) else 0.0
        val roarGain = roarBase * sf
        val lfoDepth = roarBase * sf * 0.22

        val bodyFreq = 180 + eff * 200
        val bodyGain = if (n2 > 8) (sf * 0.6 + (if (sampleLayer) 0.4 * startAmt else 0.0)) * (0.03 + 0.06 * eff) else 0.0

        val rushGain = if (valve && n2 < 48) 0.02 + 0.05 * (1 - n2 / 50) else 0.0

        // lowShelf is only ramped once the recorded layer is attached; before that it keeps its build value of 9 dB
        val lowShelf = if (sampleLayer) 9 + 5 * startAmt else 9.0

        return Targets(
            busOn = n2 > 2 || valve,
            fanFreq = fanF,
            fanBFreq = fanF * 2.01,
            fanGain = fanGain,
            cmpFreq = cmpF,
            cmpBFreq = cmpF * 1.51,
            cmpGain = cmpGain,
            roarLpFreq = roarLp,
            roarGain = roarGain,
            lfoDepth = lfoDepth,
            bodyFreq = bodyFreq,
            bodyGain = bodyGain,
            rushGain = rushGain,
            lowShelfGain = lowShelf,
        )
    }
}
