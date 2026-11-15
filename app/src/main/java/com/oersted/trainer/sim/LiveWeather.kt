package com.oersted.trainer.sim

import com.oersted.trainer.sim.WxrWorld.WxCell

/**
 * Port of liveWeather.ts (pure part): sample a 64x64 RainViewer tile centred on the aircraft and turn bright
 * pixels into radar cells. The network fetch and bitmap decode are Android-only and live in platform/LiveWeatherSampler.kt.
 */
object LiveWeather {
    /** TS tileUrl: RainViewer tile path, zoom 5 (about a regional view), coordinates to 3 decimals. */
    fun tileUrl(host: String, framePath: String, lat: Double, lon: Double): String =
        "$host$framePath/256/5/${fixed3(lat)}/${fixed3(lon)}/2/1_1.png"

    /** JS toFixed(3) for the magnitudes used here. */
    fun fixed3(x: Double): String = String.format(java.util.Locale.US, "%.3f", x)

    data class Analysis(val cells: List<WxCell>, val intensity: Double, val thunderstorm: Boolean)

    /**
     * TS sampling loop: a coarse 3x3 grid (y and x each 8, 24, 40) over the 64x64 tile.
     * [rgbAt] returns the (r, g, b) channels of a pixel; [lastMetar] is the most recent METAR text.
     */
    fun analyze(rgbAt: (x: Int, y: Int) -> Triple<Int, Int, Int>, lastMetar: String): Analysis {
        var sum = 0.0
        var count = 0
        val cells = mutableListOf<WxCell>()
        for (y in intArrayOf(8, 24, 40)) {
            for (x in intArrayOf(8, 24, 40)) {
                val (r, g, b) = rgbAt(x, y)
                val inten = WxrWorld.intensityFromRgb(r.toDouble(), g.toDouble(), b.toDouble())
                sum += inten
                count++
                if (inten > 0.25) {
                    // Map pixel offset to approximate nm (very rough regional scale)
                    val nmX = (x - 32) * 4.0
                    val nmY = (32 - y) * 4.0
                    cells.add(WxCell(nmX, nmY, 6 + inten * 8, 1.2, 30.0))
                }
            }
        }
        val intensity = if (count > 0) sum / count else 0.0
        val thunderstorm = intensity > 0.75 || Regex("TS|TSRA|VCTS").containsMatchIn(lastMetar)
        return Analysis(cells, intensity, thunderstorm)
    }
}
