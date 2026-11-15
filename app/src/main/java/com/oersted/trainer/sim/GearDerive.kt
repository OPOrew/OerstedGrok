package com.oersted.trainer.sim

/**
 * Port of the landing-gear position/indication section of src/lib/sim/derive.ts (ATA 32): gear down/up,
 * transit, selector disagreement, warning horn, per-gear green/red lights, injected gear faults.
 * Down/locked threshold 0.98, up 0.02, retarded throttle 0.12, flaps horn 15: trainer values from the TS.
 * Runs after ElectricalDerive (needs dcPower).
 */
object GearDerive {
    private fun clamp01(v: Double) = maxOf(0.0, minOf(1.0, v))

    fun apply(s: SimState, d: Derived) {
        val nose = clamp01(s.noseGearPos)
        val leftMain = clamp01(s.leftMainGearPos)
        val rightMain = clamp01(s.rightMainGearPos)
        d.gearPositions.nose = nose
        d.gearPositions.leftMain = leftMain
        d.gearPositions.rightMain = rightMain

        val gearDown = nose >= 0.98 && leftMain >= 0.98 && rightMain >= 0.98
        val gearUp = nose <= 0.02 && leftMain <= 0.02 && rightMain <= 0.02
        d.gearDown = gearDown
        d.gearUp = gearUp

        // Injected landing-gear position fault: persistent red indication while indication power exists.
        val injectedNose = s.landingGearFault == "NOSE" && nose < 0.98
        val injectedLeft = s.landingGearFault == "LEFT_MAIN" && leftMain < 0.98
        val injectedRight = s.landingGearFault == "RIGHT_MAIN" && rightMain < 0.98
        val dcPower = d.dcPower
        val throttlesRetarded = s.throttle1 <= 0.12 || s.throttle2 <= 0.12
        d.gearWarningHorn = dcPower && !gearDown && (throttlesRetarded || s.flaps > 15)

        fun red(pos: Double, injected: Boolean): Boolean = dcPower && (
            injected ||
                pos > 0.02 && pos < 0.98 ||
                (throttlesRetarded && pos < 0.98) ||
                (s.gear == "DN" && pos < 0.98) ||
                (s.gear == "UP" && pos > 0.02)
            )
        fun light(pos: Double, injected: Boolean): DerivedGearLightsNose = DerivedGearLightsNose().also {
            it.green = dcPower && pos >= 0.98
            it.red = red(pos, injected)
        }
        d.gearLights.nose = light(nose, injectedNose)
        d.gearLights.leftMain = DerivedGearLightsLeftMain().also {
            it.green = dcPower && leftMain >= 0.98
            it.red = red(leftMain, injectedLeft)
        }
        d.gearLights.rightMain = DerivedGearLightsRightMain().also {
            it.green = dcPower && rightMain >= 0.98
            it.red = red(rightMain, injectedRight)
        }

        d.gearPositionDisagree = (s.gear == "UP" && !gearUp) || (s.gear == "DN" && !gearDown)
        d.gearTransit = (nose > 0.02 && nose < 0.98) ||
            (leftMain > 0.02 && leftMain < 0.98) ||
            (rightMain > 0.02 && rightMain < 0.98) ||
            d.gearPositionDisagree
    }
}
