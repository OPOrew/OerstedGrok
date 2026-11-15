package com.oersted.trainer.sim

import kotlin.math.max
import kotlin.math.min

/**
 * Engine gauge rules (MainInstrumentPanel.tsx: EnginePanels). Pure. Band limits are the web's; a band is a [from, to) span
 * with the colour the gauge draws it in.
 */
object EngineGaugeRules {
    /** The gauge bands the web draws (EGT, N1, oil pressure, oil temperature). */
    data class Band(val from: Double, val to: Double, val tone: String)

    val EGT_BANDS: List<Band> = listOf(Band(0.0, 620.0, "green"), Band(620.0, 700.0, "amber"), Band(700.0, 900.0, "red"))
    val N1_BANDS: List<Band> = listOf(Band(0.0, 100.0, "green"), Band(100.0, 110.0, "red"))
    val OIL_PRESSURE_BANDS: List<Band> = listOf(Band(0.0, 13.0, "red"), Band(40.0, 90.0, "green"))
    val OIL_TEMP_BANDS: List<Band> = listOf(Band(0.0, 40.0, "blue"), Band(40.0, 140.0, "green"), Band(140.0, 160.0, "red"))

    /** The low-oil annunciator lights while the engine runs with oil pressure below 13 psi. */
    fun lowOilLit(running: Boolean, oilPressure: Double): Boolean = running && oilPressure < 13.0

    /**
     * Oil temperature indication: 20 with no DC power and the engine stopped; otherwise it follows N2 from 30 at 0 % to
     * 125 at 100 % (30 + min(100, N2) * 0.95), as the web does.
     */
    fun oilTemperature(dcPower: Boolean, running: Boolean, n2: Double): Double =
        if (dcPower || running) 30 + min(100.0, n2) * 0.95 else 20.0

    /** Oil quantity in gallons: a fixed 4 while the quantity test is held, otherwise the reading divided by 20. */
    fun oilQuantityGallons(test: Boolean, qty: Double): Double = if (test) 4.0 else qty / 20.0

    /** Fuel flow in pounds per hour from kilograms per hour (the web clamps at zero first). */
    fun fuelFlowLbPerHour(kgPerHour: Double, kgToLb: Double): Double = max(0.0, kgPerHour) * kgToLb

    /** Which band a value sits in, or null when none does. The first matching band wins, and the upper limit is excluded. */
    fun bandFor(value: Double, bands: List<Band>): Band? = bands.firstOrNull { value >= it.from && value < it.to }
}
