package com.oersted.trainer.sim

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * Port of the data part of components/panel/SixPack.tsx: which attitude feeds the six-pack, the clamps, the yaw rate,
 * and the blanking of unpowered air-data indications (IAS, altitude and VSI read zero, as in the web).
 * The dials themselves are drawn by the Android UI.
 */
data class SixPackData(
    val roll: Double,
    val pitch: Double,
    val heading: Double,
    val yawRate: Double,
    val captIasKt: Double,
    val captAltFt: Double,
    val captVsiFpm: Double,
    val captPowered: Boolean,
    val foIasKt: Double,
    val foAltFt: Double,
    val foVsiFpm: Double,
    val foPowered: Boolean,
) {
    companion object {
        fun clamp(n: Double, lo: Double, hi: Double): Double = min(hi, max(lo, n))

        /**
         * [attLive] / [att] come from the MIP attitude model (used when the MIP model is running, so every instrument shows one attitude);
         * otherwise the yoke-driven trainer attitude in the sim is used.
         */
        fun build(s: SimState, attLive: Boolean, att: MipLocal.Attitude, air: PitotStatic.AirData): SixPackData {
            val tas = max(100.0, s.psFlightCasKt)
            val rollSrc = if (attLive) att.roll else s.attitudeRollDeg
            val roll = clamp(rollSrc, -60.0, 60.0)
            val pitch = clamp(if (attLive) att.pitch else s.attitudePitchDeg, -30.0, 30.0)
            val hdgRaw = if (attLive) att.hdg else s.attitudeHeadingDeg
            val heading = ((hdgRaw % 360.0) + 360.0) % 360.0
            val yawRate = clamp(
                if (attLive) (if (!s.wow) (1091.0 * tan(roll * PI / 180.0)) / tas else 0.0) else s.attitudeYawRate,
                -12.0, 12.0,
            )
            val capt = air.capt
            val fo = air.fo
            val captPowered = capt.adc.data.powered
            val foPowered = fo.adc.data.powered
            return SixPackData(
                roll = roll, pitch = pitch, heading = heading, yawRate = yawRate,
                captIasKt = if (captPowered) capt.iasKt else 0.0,
                captAltFt = if (captPowered) capt.altFt else 0.0,
                captVsiFpm = if (captPowered) capt.vsiFpm else 0.0,
                captPowered = captPowered,
                foIasKt = if (foPowered) fo.iasKt else 0.0,
                foAltFt = if (foPowered) fo.altFt else 0.0,
                foVsiFpm = if (foPowered) fo.vsiFpm else 0.0,
                foPowered = foPowered,
            )
        }
    }
}
