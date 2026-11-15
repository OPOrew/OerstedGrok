package com.oersted.trainer.sim

/**
 * Port of sources.ts: flight-instrument source selection (AMM ch. 34 / 22). The VHF NAV and COMPASS selectors are
 * BOTH ON 1 / NORMAL / BOTH ON 2 (0 / 1 / 2), and the VERTICAL GYRO selector is CAPT ON AUX / NORMAL / F/O ON AUX.
 * The simulator has one true attitude, heading and position, so a source differs only by being failed.
 */
object Sources {
    data class SrcFail(
        val gyro1: Boolean,
        val gyro2: Boolean,
        val gyroAux: Boolean,
        val comp1: Boolean,
        val comp2: Boolean,
        val nav1: Boolean,
        val nav2: Boolean,
    )

    val NO_SRC_FAIL = SrcFail(gyro1 = false, gyro2 = false, gyroAux = false, comp1 = false, comp2 = false, nav1 = false, nav2 = false)

    data class SideSrc(val att: Boolean, val hdg: Boolean, val nav: Boolean)

    data class SourceSelect(val navSel: Int, val compSel: Int, val gyroSel: Int)

    data class SideSources(val capt: SideSrc, val fo: SideSrc)

    /** Which sides' selected sources are failed (true = the source feeding that side is failed). */
    fun sourcesOf(sel: SourceSelect, f: SrcFail): SideSources = SideSources(
        capt = SideSrc(
            att = if (sel.gyroSel == 0) f.gyroAux else f.gyro1,
            hdg = if (sel.compSel == 2) f.comp2 else f.comp1,
            nav = if (sel.navSel == 2) f.nav2 else f.nav1,
        ),
        fo = SideSrc(
            att = if (sel.gyroSel == 2) f.gyroAux else f.gyro2,
            hdg = if (sel.compSel == 0) f.comp1 else f.comp2,
            nav = if (sel.navSel == 0) f.nav1 else f.nav2,
        ),
    )
}
