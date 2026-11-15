package com.oersted.trainer.sim

/**
 * Port of hfModel.ts (ATA 23, HF COMM training model): coupler tune, band limits, simple usability.
 * Not ionospheric propagation; a day/night band table only.
 */
object HfModel {
    /** HfMode: 0 OFF, 1 USB, 2 AM. */
    const val MODE_OFF = 0
    const val MODE_USB = 1
    const val MODE_AM = 2

    data class HfChannelState(
        val mode: Int,
        val actKhz: Int,
        val stbyKhz: Int,
        /** coupler tuning in progress */
        val tuning: Boolean,
        val tuneT: Double,
        /** 0 unusable, 1 marginal, 2 good */
        val usability: Int,
        val powered: Boolean,
    )

    private const val TUNE_SEC = 3.5
    private const val HF_MIN = 2000
    private const val HF_MAX = 29999

    /** Simplified day/night usability by frequency family (kHz). */
    fun hfUsability(khz: Int, isDay: Boolean): Int {
        if (khz < HF_MIN || khz > HF_MAX) return 0
        // Very low HF: better at night
        if (khz < 4000) return if (isDay) 0 else 2
        if (khz < 8000) return if (isDay) 1 else 2
        if (khz < 12000) return if (isDay) 2 else 1
        if (khz < 18000) return if (isDay) 2 else 0
        // High HF: day only
        return if (isDay) 1 else 0
    }

    /**
     * TS stepHfChannel. [retune] requests a coupler retune (frequency change or TUNE press).
     * A frequency change against [prev] also starts a retune.
     */
    fun stepHfChannel(
        prev: HfChannelState,
        mode: Int,
        actKhz: Int,
        stbyKhz: Int,
        powered: Boolean,
        retune: Boolean,
        isDay: Boolean,
        dtSec: Double,
    ): HfChannelState {
        if (!powered || mode == MODE_OFF) {
            return HfChannelState(mode, actKhz, stbyKhz, tuning = false, tuneT = 0.0, usability = 0, powered = false)
        }

        var tuneT = prev.tuneT
        var tuning = prev.tuning
        val freqChanged = prev.actKhz != actKhz
        if (retune || freqChanged) {
            tuning = true
            tuneT = 0.0
        }
        if (tuning) {
            tuneT += dtSec
            if (tuneT >= TUNE_SEC) {
                tuning = false
                tuneT = TUNE_SEC
            }
        }

        val usability = if (tuning) 0 else hfUsability(actKhz, isDay)
        return HfChannelState(mode, actKhz, stbyKhz, tuning, tuneT, usability, powered = true)
    }

    val IDLE_HF = HfChannelState(
        mode = MODE_OFF,
        actKhz = 5628,
        stbyKhz = 8942,
        tuning = false,
        tuneT = 0.0,
        usability = 0,
        powered = false,
    )
}
