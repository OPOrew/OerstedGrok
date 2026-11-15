package com.oersted.trainer.sim

import com.oersted.trainer.sim.MipLocal.AttitudeSide
import com.oersted.trainer.sim.MipLocal.DmeChannels
import com.oersted.trainer.sim.MipLocal.MipFail
import com.oersted.trainer.sim.InstrumentSources.SrcPower
import com.oersted.trainer.sim.Sources.SrcFail
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure rules of components/panel/MainInstrumentPanel.tsx `Panel()` (lines 1156-1257) and the instrument faces of
 * components/panel/instruments.tsx / SixPack.tsx: source transfers, per-side flag sets, derived panel values and the
 * scale maths of the faces. The Compose views are in ui/instruments/Faces.kt and ui/panel/MainPanel.kt.
 */
object MainPanelRules {
    const val HPA_PER_INHG = 33.8639

    /** web: displayNavSel / displayEfiSide / displayCompSel / displayGyroSel after the transfer switches. */
    data class Display(val navSel: Int, val efiSide: Int?, val compSel: Int, val gyroSel: Int)

    fun display(navXfr: String, efiXfr: String, navSel: Int, compSel: Int, gyroSel: Int): Display {
        val navD = if (navXfr == "BOTH ON 1") 0 else if (navXfr == "BOTH ON 2") 2 else navSel
        val efi: Int? = if (efiXfr == "BOTH ON L") 0 else if (efiXfr == "BOTH ON R") 2 else null
        return Display(navD, efi, efi ?: compSel, efi ?: gyroSel)
    }

    /** web: sourcesOf(...) plus the IRS NORMAL override when an EFI transfer is active. */
    fun sources(d: Display, irsXfr: String, f: SrcFail): Sources.SideSources {
        val src = Sources.sourcesOf(Sources.SourceSelect(d.navSel, d.compSel, d.gyroSel), f)
        if (d.efiSide != null && irsXfr == "NORMAL") {
            val left = d.efiSide == 0
            val att = if (left) f.gyro1 else f.gyro2
            val hdg = if (left) f.comp1 else f.comp2
            return Sources.SideSources(src.capt.copy(att = att, hdg = hdg), src.fo.copy(att = att, hdg = hdg))
        }
        return src
    }

    /** web failC: the captain-side flag set. */
    fun failCapt(m: MipFail, src: Sources.SideSources, sp: SrcPower, att: AttitudeSide, d: Display): MipFail = m.copy(
        att = m.att || src.capt.att || !att.attValid || (!sp.gyro1Cb && d.gyroSel != 0) || (!sp.gyroAuxCb && d.gyroSel == 0),
        hdg = m.hdg || src.capt.hdg || !att.hdgValid || (!sp.comp1Cb && d.compSel != 2) || (!sp.comp2Cb && d.compSel == 2),
        loc = m.loc || src.capt.nav || (if (d.navSel == 2) !sp.nav2Cb else !sp.nav1Cb),
        gs = m.gs || src.capt.nav || (if (d.navSel == 2) !sp.nav2GsCb else !sp.nav1GsCb),
        dme1 = m.dme1 || !sp.dme1Cb,
        dme2 = m.dme2 || !sp.dme2Cb,
        adf = m.adf || (!sp.adf1Cb && !sp.adf2Cb),
    )

    /** web failF: the first-officer flag set. */
    fun failFo(m: MipFail, src: Sources.SideSources, sp: SrcPower, att: AttitudeSide, d: Display): MipFail = m.copy(
        att = m.att || src.fo.att || !att.attValid || (!sp.gyro2Cb && d.gyroSel != 2) || (!sp.gyroAuxCb && d.gyroSel == 2),
        hdg = m.hdg || src.fo.hdg || !att.hdgValid || (!sp.comp2Cb && d.compSel != 0) || (!sp.comp1Cb && d.compSel == 0),
        loc = m.loc || src.fo.nav || (if (d.navSel == 0) !sp.nav1Cb else !sp.nav2Cb),
        gs = m.gs || src.fo.nav || (if (d.navSel == 0) !sp.nav1GsCb else !sp.nav2GsCb),
        dme1 = m.dme1 || !sp.dme1Cb,
        dme2 = m.dme2 || !sp.dme2Cb,
        adf = m.adf || (!sp.adf1Cb && !sp.adf2Cb),
    )

    /** web: tasKt = ias * (1 + alt/1000 * 0.016) */
    fun tasKt(iasKt: Double, altFt: Double): Double = iasKt * (1 + (altFt / 1000) * 0.016)

    /** web: gsKt = wow ? groundSpeedKt : tasKt */
    fun gsKt(wow: Boolean, groundSpeedKt: Double, tasKt: Double): Double = if (wow) groundSpeedKt else tasKt

    /** web: ra = wow ? 0 : (lrra.h1 ?? 9999). 9999 is the out-of-range indication. */
    fun ra(wow: Boolean, h1: Double?): Double = if (wow) 0.0 else h1 ?: 9999.0

    /** web: test = pw && (ltTest || raTest || lightKey === 2) */
    fun test(pw: Boolean, ltTest: Boolean, raTest: Boolean, lightKey: Int): Boolean = pw && (ltTest || raTest || lightKey == 2)

    /** web: tatC = (Number(ambientTempF ?? 59) - 32) * 5 / 9 */
    fun tatC(ambientTempF: Double?): Double = (((ambientTempF ?: 59.0) - 32) * 5) / 9

    /** web TAT window text: sign, one decimal and the degree sign; blank without power. */
    fun tatText(pw: Boolean, tatC: Double): String =
        if (!pw) "" else (if (tatC >= 0) "+" else "-") + PedestalTopDeckRules.toFixed(abs(tatC), 1) + "°C"

    /** web: dhOn = test || (pw && !wow && ra <= raDh) */
    fun dhOn(test: Boolean, pw: Boolean, wow: Boolean, ra: Double, raDh: Double): Boolean = test || (pw && !wow && ra <= raDh)

    /** web HeightDial input: ft = ra < 9000 ? ra : -1 */
    fun heightFt(ra: Double): Double = if (ra < 9000) ra else -1.0

    /** web HeightDial powered flag */
    fun heightPowered(pw: Boolean, failHt: Boolean, lrra1Cb: Boolean, h1: Double?): Boolean = pw && !failHt && lrra1Cb && h1 != null

    /** web dmeDisplayNm: STBY gives no readout, a failed channel none, else the channel's display value. */
    fun dmeDisplayNm(side: Int, fail: Boolean, dme: DmeChannels?): Double? {
        if (fail) return null
        val ch = if (side == 0) dme?.ch1 else dme?.ch2
        return ch?.displayNm
    }

    /**
     * web RMI pointer source: the VOR (relative to the heading) when the pedestal needle selector is 0, the VOR is received
     * and the NAV environment is on, else the ADF bearing.
     */
    fun rmiBearing(sel: Int, navOn: Boolean, vorValid: Boolean, radial: Double, attHdg: Double, adfBrg: Double?): Double? =
        if (sel == 0 && navOn && vorValid) AutoFlight.angDiff(radial, attHdg) else adfBrg

    /** web light(): the zone overlay colours. [flood] wash alpha outer / middle, [dark] = alpha of the dark veil. */
    data class LightOverlay(val floodOuter: Double, val floodMid: Double, val dark: Double)

    fun lightOverlay(pw: Boolean, panel: Double, flood: Double): LightOverlay {
        val pl = if (pw) clamp(panel, 0.0, 1.0) else 0.0
        val fl = if (pw) clamp(flood, 0.0, 1.0) else 0.0
        return LightOverlay(fl * 0.28, fl * 0.1, (1 - pl) * 0.42)
    }

    fun clamp(v: Double, lo: Double, hi: Double): Double = max(lo, min(hi, v))

    /** web pad(n, w): String(Math.round(n)).padStart(w, "0") */
    fun pad(n: Double, w: Int): String = floor(n + 0.5).toLong().toString().padStart(w, '0')

    fun norm360(a: Double): Double = ((a % 360) + 360) % 360

    /* ───────────── instrument face scales ───────────── */

    private val ASI_PTS = listOf(
        0.0 to -8.0, 60.0 to 0.0, 80.0 to 25.0, 100.0 to 55.0, 120.0 to 85.0, 140.0 to 115.0, 160.0 to 145.0,
        180.0 to 172.0, 200.0 to 198.0, 250.0 to 236.0, 300.0 to 270.0, 350.0 to 302.0, 400.0 to 332.0,
    )

    /** web asiAng: pointer angle of the non-linear airspeed scale. */
    fun asiAng(kt: Double): Double {
        val v = clamp(kt, 0.0, 400.0)
        for (i in 1 until ASI_PTS.size) {
            val (k1, a1) = ASI_PTS[i]
            val (k0, a0) = ASI_PTS[i - 1]
            if (v <= k1) return a0 + ((v - k0) / (k1 - k0)) * (a1 - a0)
        }
        return 332.0
    }

    /** web dmeText: one decimal below 100 NM, padded to five. */
    fun dmeText(nm: Double): String {
        val v = clamp(nm, 0.0, 199.9)
        return if (v < 100) PedestalTopDeckRules.toFixed(v, 1).padStart(5, ' ') else PedestalTopDeckRules.toFixed(v, 0).padStart(5, ' ')
    }

    /** web altimeter: the 10000 ft and 1000 ft pointer angles. */
    fun altHandAngles(altFt: Double): Pair<Double, Double> {
        val alt = max(-1000.0, altFt)
        fun wrap(x: Double, m: Double) = ((x % m) + m) % m
        return Pair((wrap(alt, 10000.0) / 10000) * 360, (wrap(alt, 1000.0) / 1000) * 360)
    }

    /** web altimeter drum digits: pad(max(0, alt), 5) */
    fun altDigits(altFt: Double): String = pad(max(0.0, max(-1000.0, altFt)), 5)

    /** web IN HG window: hg.toFixed(2) without the point */
    fun hgText(hg: Double): String = PedestalTopDeckRules.toFixed(hg, 2).replace(".", "")

    /** web Mach window text: mach.toFixed(2).slice(1), blank below M 0.40 */
    fun machText(mach: Double): String? = if (mach >= 0.4) PedestalTopDeckRules.toFixed(mach, 2).substring(1) else null

    /** SixPack VerticalSpeed pointer angle: 0 at 9 o'clock, square-root scale to +-6000 fpm */
    fun vsiAngle(fpm: Double): Double {
        val f = clamp(fpm, -6000.0, 6000.0)
        val sign = if (f > 0) 1.0 else if (f < 0) -1.0 else 0.0
        return -90 + sign * sqrt(abs(f) / 6000) * 88
    }

    /** Which sign text the VSI shows: "+n" above zero, else n */
    fun vsiText(fpm: Double): String {
        val r = floor(clamp(fpm, -6000.0, 6000.0) + 0.5).toLong()
        return (if (r > 0) "+" else "") + r
    }

    /** web ADI localizer / glideslope scales */
    fun adiLocX(locDev: Double): Double = clamp(locDev / 1.25, -2.4, 2.4)

    fun adiGsY(gsErr: Double): Double = clamp(gsErr / 0.35, -2.4, 2.4)

    /** web HSI deviation bar position in dots/2: VOR full scale 10 deg, localizer 2.5 deg */
    fun hsiDev(isVor: Boolean, vorDevDeg: Double, locDev: Double): Double = if (isVor) vorDevDeg / 10 else locDev / 2.5

    /** web HSI CRS / HDG windows: 0 shows 360 */
    fun hsiWindowDeg(deg: Double): String = pad(if (norm360(deg) == 0.0) 360.0 else norm360(deg), 3)

    /** Chronometer ticking helper: elapsed seconds as the web's `pad(floor(e/60) % 100, 2):pad(e % 60, 2)` */
    fun elapsedText(e: Double): String {
        val m = floor(e / 60).toLong() % 100
        return m.toString().padStart(2, '0') + ":" + pad(e % 60, 2)
    }
}
