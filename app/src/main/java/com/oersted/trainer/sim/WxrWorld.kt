package com.oersted.trainer.sim

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan
import kotlin.math.PI

/**
 * Port of wxrWorld.ts: training weather scene for the weather radar, in the same nm frame as the radio-nav scene
 * (x = east, y = north). The TS Zustand stores become small holders; the pure geometry and colour math are direct ports.
 */
object WxrWorld {
    /** Dead-reckoned airplane position / heading the instrument panel publishes (TS useWxrNav). */
    data class WxrNav(val x: Double, val y: Double, val hdg: Double)

    class WxrNavHolder(initial: WxrNav = WxrNav(RadioNav.NAV_START_X, RadioNav.NAV_START_Y, RadioNav.NAV_START_HDG.toDouble())) {
        var state: WxrNav = initial
            private set

        fun publish(next: WxrNav) {
            state = next
        }
    }

    /** Thunderstorm cell. [r] is the radius (nm) of the outer, light-rain contour; [asp] the elongation; [ang] the major-axis bearing (deg true). */
    data class WxCell(val x: Double, val y: Double, val r: Double, val asp: Double, val ang: Double)

    /** Thunderstorm cells: three nested contours (levels 1 / 2 / 3 = green / yellow / red) at 100 / 62 / 30 percent of the radius. */
    val WX_CELLS: List<WxCell> = listOf(
        WxCell(12.0, 6.0, 7.0, 1.6, 40.0),
        WxCell(22.0, -10.0, 9.0, 1.3, 120.0),
        WxCell(5.0, -14.0, 5.0, 1.2, 80.0),
        WxCell(35.0, 8.0, 12.0, 2.0, 60.0),
        WxCell(-4.0, 24.0, 8.0, 1.5, 20.0),
        WxCell(-30.0, -6.0, 6.0, 1.2, 0.0),
        WxCell(62.0, -1.0, 8.0, 1.4, 90.0),
        WxCell(90.0, 30.0, 15.0, 1.8, 70.0),
    )

    /** Coast line and cities for the MAP mode (the same nm frame). */
    val WX_COAST: List<Pair<Double, Double>> = listOf(
        -40.0 to -26.0, -20.0 to -22.0, -6.0 to -17.0, 4.0 to -11.0, 14.0 to -12.0,
        26.0 to -17.0, 40.0 to -24.0, 60.0 to -26.0, 90.0 to -34.0,
    )
    val WX_CITIES: List<Pair<Double, Double>> = listOf(18.0 to -16.0, 40.0 to -24.0, -6.0 to -17.0)

    /**
     * TS beamFactor: how much of a storm the tilted beam shows. 1 = full depth, 0 = beam above or below the storm.
     * Cells are modelled as 3,000 to 38,000 ft. Uses 6076 ft/nm, as in the TS source.
     */
    fun beamFactor(rangeNm: Double, tiltDeg: Double, altFt: Double): Double {
        val beam = altFt + rangeNm * 6076 * tan(tiltDeg * PI / 180)
        return max(0.0, 1 - abs(beam - 20500) / 21000)
    }

    /** Live precipitation cells derived from real weather (TS useLiveWx). */
    data class LiveWxState(
        val cells: List<WxCell> = emptyList(),
        val intensity: Double = 0.0,
        val inWeather: Boolean = false,
        val thunderstorm: Boolean = false,
    )

    class LiveWxHolder {
        var state: LiveWxState = LiveWxState()
            private set

        /** TS setLiveWeather: cells and intensity, with inWeather above 0.15 and thunderstorm above 0.75 intensity. */
        fun setLiveWeather(cells: List<WxCell>, intensity: Double, thunderstorm: Boolean = false) {
            state = LiveWxState(
                cells = cells,
                intensity = intensity,
                inWeather = intensity > 0.15,
                thunderstorm = thunderstorm || intensity > 0.75,
            )
        }
    }

    /** TS intensityFromRgb: rough 0-1 intensity from a colour sample (RainViewer Universal Blue). */
    fun intensityFromRgb(r: Double, g: Double, b: Double): Double {
        val warmth = (r + g * 0.6) / (b + 40)
        return max(0.0, min(1.0, (warmth - 0.4) * 1.8))
    }

    /** TS turbulenceFromIntensity: turbulence level 0-1 from precipitation intensity. */
    fun turbulenceFromIntensity(intensity: Double): Double = when {
        intensity < 0.15 -> 0.0
        intensity < 0.4 -> 0.25 // light
        intensity < 0.7 -> 0.55 // moderate
        else -> 0.85 // severe
    }
}
