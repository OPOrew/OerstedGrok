package com.oersted.trainer.sim

/**
 * Approach progress display lamps (ApproachProgress.tsx, AMM 22-11-0): VOR/LOC and GLIDE SLOPE.
 * Dark when the bus is off; press-to-test forces green; otherwise the annunciator light (amber armed, green captured).
 */
object ApproachProgressLamps {
    fun state(pw: Boolean, test: Boolean, light: AutoFlight.Light): AutoFlight.Light = when {
        !pw -> AutoFlight.Light.OFF
        test -> AutoFlight.Light.GREEN
        else -> light
    }
}
