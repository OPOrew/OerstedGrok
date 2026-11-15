package com.oersted.trainer.sim

import com.oersted.trainer.sim.NavaidsChangi.NavaidKind

/**
 * Port of adfBearing.ts (ATA 34-57, training only). Modes: 0 OFF, 1 ANT, 2 ADF (bearing), 3 TEST, 4 LOOP.
 * The TS version reads the flight-navigation store and the pedestal store; here the caller passes them in.
 */
object AdfBearing {
    data class AdfResult(val brg1: Double?, val brg2: Double?, val dist1Nm: Double?, val dist2Nm: Double?)

    /** Aircraft position from the flight navigation state; null when unavailable. */
    data class NavPos(val latitude: Double, val longitude: Double)

    /** AMM self-test drives the indicator toward a fixed relative bearing (~45 deg). */
    const val ADF_TEST_REL_BRG = 45.0

    private data class One(val brg: Double?, val dist: Double?)

    /**
     * TS computeAdfBearings. [freqL]/[freqR] are kHz from the pedestal; [mode1]/[mode2] are the pedestal modes
     * (the TS `??` defaults of 2 are applied by the caller).
     */
    fun computeAdfBearings(
        hdgMag: Double,
        powered: Boolean,
        failAdf: Boolean,
        nav: NavPos?,
        freqL: Double,
        freqR: Double,
        mode1: Int = 2,
        mode2: Int = 2,
        adf1Powered: Boolean = true,
        adf2Powered: Boolean = true,
    ): AdfResult {
        val empty = AdfResult(null, null, null, null)
        if (!powered || failAdf) return empty
        val one = if (adf1Powered) resolveOne(freqL, mode1, nav, hdgMag) else One(null, null)
        val two = if (adf2Powered) resolveOne(freqR, mode2, nav, hdgMag) else One(null, null)
        return AdfResult(one.brg, two.brg, one.dist, two.dist)
    }

    private fun resolveOne(freqKhz: Double, mode: Int, nav: NavPos?, hdgMag: Double): One {
        // OFF / ANT / LOOP: no auto relative bearing
        if (mode == 0 || mode == 1 || mode == 4) return One(null, null)
        // TEST: fixed relative bearing (AMM calibration cue)
        if (mode == 3) return One(ADF_TEST_REL_BRG, null)
        if (nav == null || !nav.latitude.isFinite() || !nav.longitude.isFinite()) return One(null, null)

        val station = findChangiNavaid(freqKhz, "NDB")
            ?: NavaidsChangi.CHANGI_NAVAIDS.firstOrNull { it.kind == NavaidKind.NDB && it.available && Math.abs(it.frequency - freqKhz) < 1 }
            ?: return One(null, null)
        val dist = distNm(nav.latitude, nav.longitude, station.lat, station.lon)
        if (dist > station.rangeNm) return One(null, null)
        val trueBrg = bearingTrue(nav.latitude, nav.longitude, station.lat, station.lon)
        val magBrg = (trueBrg - station.magVarDeg + 360) % 360
        return One(AutoFlight.angDiff(magBrg, hdgMag), dist)
    }
}
