package com.oersted.trainer.sim

/**
 * Light test rules on the panel (MainInstrumentPanel.tsx: GearColumn, Comparator, LightTestKey). Pure.
 * lightKey: 0 = BRT, 1 = DIM, 2 = TEST (the web's LightTestKey cycles them in that order).
 */
object LightTestLogic {
    /** Gear lamps are all forced on while the lamp test is held with DC power (ltTest or the TEST key). */
    fun gearTest(dcPower: Boolean, ltTest: Boolean, lightKey: Int): Boolean = dcPower && (ltTest || lightKey == 2)

    /** Comparator lamps: on with DC power while the lamp test, the comparator test or the TEST key is active. */
    fun comparatorLit(dcPower: Boolean, ltTest: Boolean, compTest: Boolean, lightKey: Int): Boolean =
        dcPower && (ltTest || compTest || lightKey == 2)

    /** Pointer angle of the light test key: BRT 90 deg, DIM 180 deg, TEST 0 deg (the web's [90, 180, 0]). */
    fun keyAngleDeg(lightKey: Int): Double = when (lightKey) {
        0 -> 90.0
        1 -> 180.0
        else -> 0.0
    }

    /** One press on the key moves to the next position: BRT -> DIM -> TEST -> BRT. */
    fun nextLightKey(lightKey: Int): Int = (lightKey + 1) % 3
}
