package com.oersted.trainer.ui.instruments

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.oersted.trainer.sim.MainPanelRules as R
import com.oersted.trainer.sim.MipLocal
import com.oersted.trainer.sim.PitotStatic
import com.oersted.trainer.sim.RadioNav
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * Port of components/panel/instruments.tsx (ADI, HSI, RMI / DME, ASI, altimeter) and the VerticalSpeed / Attitude faces of
 * components/panel/SixPack.tsx, as used on the main instrument panel. Pure display: every value comes in through parameters.
 * Coordinates are the web SVG's, in each face's viewBox. The web animates the moving parts with 100-160 ms CSS transitions;
 * here the faces redraw on each published state instead. Compose only; verified on CI, not compiled locally.
 */

private val Ink = Color(0xFF3A2A00)
private val Yellow = Color(0xFFFFD23A)
private val Orange = Color(0xFFFF7A1A)
private val OrangeEdge = Color(0xFFFFB066)
private val OrangeText = Color(0xFF7A1500)
private val Muted = Color(0xFF9AA4AD)
private val Cream = Color(0xFFF4F1E4)

private fun clampF(v: Double, lo: Double, hi: Double) = max(lo, min(hi, v)).toFloat()

/* ═════════════════════════════ ADI ═════════════════════════════ */

/** web AdiNav: localizer / glideslope data the ADI shows (already selected through the VHF NAV switch and source failures). */
data class AdiNav(val ils: Boolean, val locValid: Boolean, val gsValid: Boolean, val locDev: Double, val gsErr: Double)

val NO_ADI_NAV = AdiNav(ils = false, locValid = false, gsValid = false, locDev = 0.0, gsErr = 0.0)

/** web adiNavFrom */
fun adiNavFrom(tunedIls: Boolean, locErr: Double?, gsErr: Double?): AdiNav =
    AdiNav(tunedIls, locErr != null, gsErr != null, locErr ?: 0.0, gsErr ?: 0.0)

private const val PX_DEG = 2.7f
private const val ADI_CX = 121f
private const val ADI_CY = 122f
private const val ADI_R = 86f

/** Flight-director ADI (Collins FD-108 style), viewBox 242 x 254. [slip] is the inclinometer ball, -1 (left) to +1 (right). */
@Composable
fun AdiFace(
    powered: Boolean,
    test: Boolean,
    fail: MipLocal.MipFail,
    pitch: Double,
    roll: Double,
    slip: Double,
    fd: MipLocal.FdCmd?,
    nav: AdiNav,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    FaceCanvas(242f, 254f, modifier) {
        val p = pitch + (if (test) 10 else 0)
        val r = roll + (if (test) 20 else 0)
        val attBad = !powered || fail.att || test
        val fdShown = powered && fd?.show == true && !fail.fd && !fail.att
        val locFlag = !powered || fail.loc || (nav.ils && !nav.locValid)
        val gsFlag = !powered || fail.gs || (nav.ils && !nav.gsValid)
        val locOn = powered && nav.ils && nav.locValid && !fail.loc
        val gsOn = powered && nav.ils && nav.gsValid && !fail.gs
        fun dot(i: Float) = ADI_CX + i * 26f

        fRect(0f, 0f, 242f, 254f, Color(0xFF0A0C0F), rx = 9f)
        fCircle(ADI_CX, ADI_CY, ADI_R + 5, Color(0xFF15181C), Color(0xFF4A525A), 1.5f)
        fClipCircle(ADI_CX, ADI_CY, ADI_R) {
            fRotate(-r.toFloat(), ADI_CX, ADI_CY) {
                withTransform({ translate(0f, clampF(p, -90.0, 90.0) * PX_DEG) }) {
                    drawRect(vGrad(ADI_CY - 600, ADI_CY, Color(0xFF1F6FB2), Color(0xFF4DA6E0)), Offset(-200f, ADI_CY - 600), androidx.compose.ui.geometry.Size(640f, 600f))
                    drawRect(vGrad(ADI_CY, ADI_CY + 600, Color(0xFF1D2024), Color(0xFF08090B)), Offset(-200f, ADI_CY), androidx.compose.ui.geometry.Size(640f, 600f))
                    fLine(-200f, ADI_CY, 440f, ADI_CY, Color.White, 2f)
                    for (a in listOf(2.5, 5.0, 7.5, 10.0, 12.5, 15.0, 17.5, 20.0, 25.0, 30.0, 40.0, 50.0)) {
                        val w = if (a % 10 == 0.0) 34f else if (a % 5 == 0.0) 22f else 11f
                        val n = if (a % 10 == 0.0) a.toInt().toString() else null
                        for (s in listOf(a, -a)) {
                            val y = ADI_CY - s.toFloat() * PX_DEG
                            fLine(ADI_CX - w, y, ADI_CX + w, y, Color.White, if (n != null) 1.6f else 1.1f)
                            if (n != null) {
                                fText(n, ADI_CX - w - 5, y, 8f, Color.White, TextAnchor.END)
                                fText(n, ADI_CX + w + 5, y, 8f, Color.White, TextAnchor.START)
                            }
                        }
                    }
                }
                // bank scale rides with the roll axis
                for (a in listOf(0, 10, -10, 20, -20, 30, -30, 45, -45, 60, -60)) {
                    val big = a % 30 == 0
                    fLine(pol(ADI_CX, ADI_CY, ADI_R - (if (big) 14f else 9f), a.toDouble()), pol(ADI_CX, ADI_CY, ADI_R - 1, a.toDouble()), Color.White, if (big) 2.2f else 1.3f)
                }
                fPoly(ADI_CX, ADI_CY - ADI_R + 15, ADI_CX - 5, ADI_CY - ADI_R + 5, ADI_CX + 5, ADI_CY - ADI_R + 5, fill = Color.White)
            }
            // shade
            val d = ADI_R * 2
            drawCircle(
                Brush.radialGradient(
                    0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.5f),
                    center = Offset(ADI_CX, ADI_CY - ADI_R + 0.42f * d), radius = 0.58f * d,
                ),
                ADI_R, Offset(ADI_CX, ADI_CY),
            )
        }
        // fixed sky pointer
        fPoly(ADI_CX, ADI_CY - ADI_R - 1, ADI_CX - 6, ADI_CY - ADI_R - 12, ADI_CX + 6, ADI_CY - ADI_R - 12, fill = FaceAmber, stroke = Ink, sw = 0.8f)
        // flight-director V-bars
        if (fdShown && fd != null) {
            fClipCircle(ADI_CX, ADI_CY, ADI_R) {
                withTransform({
                    rotate(fd.bank.toFloat(), Offset(ADI_CX, ADI_CY))
                    translate(0f, (-fd.pitch * PX_DEG).toFloat())
                }) {
                    fPoly(ADI_CX, ADI_CY + 4, ADI_CX - 64, ADI_CY - 15, ADI_CX - 64, ADI_CY - 7, ADI_CX, ADI_CY + 12, fill = FaceAmber, stroke = Ink, sw = 1.3f)
                    fPoly(ADI_CX, ADI_CY + 4, ADI_CX + 64, ADI_CY - 15, ADI_CX + 64, ADI_CY - 7, ADI_CX, ADI_CY + 12, fill = FaceAmber, stroke = Ink, sw = 1.3f)
                }
            }
        }
        // inclinometer
        val y0 = ADI_CY + ADI_R - 4
        val tube = Path().apply {
            moveTo(ADI_CX - 34, y0)
            quadraticBezierTo(ADI_CX, ADI_CY + ADI_R + 4, ADI_CX + 34, y0)
        }
        drawPath(tube, Color(0xFF0A0C0F), style = Stroke(13f, cap = StrokeCap.Round))
        drawPath(tube, Color(0xFF2A3036), style = Stroke(10f, cap = StrokeCap.Round))
        fLine(ADI_CX - 6, ADI_CY + ADI_R - 8, ADI_CX - 6, ADI_CY + ADI_R + 4, FaceWhite, 1.2f)
        fLine(ADI_CX + 6, ADI_CY + ADI_R - 8, ADI_CX + 6, ADI_CY + ADI_R + 4, FaceWhite, 1.2f)
        run {
            val sx = clampF(slip, -1.0, 1.0) * 26f
            val t = (sx + 34f) / 68f
            fCircle(ADI_CX + sx, y0 + 16f * t * (1 - t), 4.6f, Color(0xFFE9E6D8), Color(0xFF555555), 0.6f)
        }
        // fixed aircraft symbol
        val acft = Path().apply {
            moveTo(ADI_CX - 66, ADI_CY); lineTo(ADI_CX - 28, ADI_CY); lineTo(ADI_CX - 17, ADI_CY + 10)
            moveTo(ADI_CX + 66, ADI_CY); lineTo(ADI_CX + 28, ADI_CY); lineTo(ADI_CX + 17, ADI_CY + 10)
        }
        drawPath(acft, Color(0xFF1A1200), style = Stroke(7f, join = StrokeJoin.Round))
        drawPath(acft, FaceAmber, style = Stroke(4f, join = StrokeJoin.Round))
        fCircle(ADI_CX, ADI_CY, 4.5f, FaceAmber, Color(0xFF1A1200), 1.2f)

        // localizer scale (bottom)
        for (i in listOf(-2f, -1f, 1f, 2f)) fCircle(dot(i), 236f, 3.4f, null, FaceWhite, 1.4f)
        fLine(ADI_CX, 228f, ADI_CX, 244f, FaceWhite, 1.4f)
        if (locOn) fRect(dot(R.adiLocX(nav.locDev).toFloat()) - 3.5f, 227f, 7f, 18f, Yellow, Ink, 1f, 1.2f)
        // glideslope scale (right)
        for (i in listOf(-2f, -1f, 1f, 2f)) fCircle(226f, ADI_CY + i * 26f, 3.4f, null, FaceWhite, 1.4f)
        fLine(218f, ADI_CY, 234f, ADI_CY, FaceWhite, 1.4f)
        if (gsOn) fRect(217f, ADI_CY + R.adiGsY(nav.gsErr).toFloat() * 26f - 3.5f, 18f, 7f, Yellow, Ink, 1f, 1.2f)
        // speed-command legend (no speed command computer fitted)
        fText("F", 12f, ADI_CY - 24, 15f, Color.White, TextAnchor.START, bold = true, central = false)
        fText("S", 12f, ADI_CY + 40, 15f, Color.White, TextAnchor.START, bold = true, central = false)

        if (attBad) fFlag(34f, 36f, "ATT", 34f, rot = -32f)
        if (!powered || fail.fd || fd?.flag == true) fFlag(208f, 36f, "FD", 26f, rot = 32f)
        if (gsFlag) fFlag(228f, ADI_CY - 52, "GS", 26f, rot = 90f)
        if (!powered || fail.spd) fFlag(14f, ADI_CY + 8, "SPD", 30f, rot = 90f)
        if (!powered || fail.ht) fFlag(206f, 208f, "HT", 24f, rot = -40f)
        if (locFlag) fFlag(ADI_CX, 214f, "LOC", 30f)
    }
}

/* ═════════════════════════════ HSI ═════════════════════════════ */

private const val HSI_CX = 116f
private const val HSI_CY = 108f
private const val HSI_R = 80f

/** Horizontal situation indicator, viewBox 232 x 202. [tuned] is "VOR" or "ILS". */
@Composable
fun HsiFace(
    powered: Boolean,
    fail: MipLocal.MipFail,
    hdg: Double,
    tas: Double,
    gs: Double,
    crs: Double,
    bug: Double,
    nav: RadioNav.RadioNavData?,
    navOn: Boolean,
    tuned: String,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    FaceCanvas(232f, 202f, modifier) {
        val isVor = tuned == "VOR"
        val isIls = tuned == "ILS"
        val valid = nav != null && (if (isVor) nav.vorValid else if (isIls) nav.locValid else false) && !fail.loc
        val v = if (nav != null) RadioNav.vorDevOf(nav.radial, crs) else RadioNav.VorDeviation(0.0, false)
        val dev = R.hsiDev(isVor, v.dev, nav?.locDev ?: 0.0)
        val hdgUp = R.norm360(hdg)
        val gsOn = powered && navOn && isIls && nav?.gsValid == true && !fail.gs

        fun win(x: Float, y: Float, w: Float, head: String, txt: String, color: Color = Color(0xFF7DFF7D)) {
            fRect(x, y, w, 22f, Color.Black, Color(0xFF555A60), 1f, 2f)
            fText(head, x + w / 2, y + 6.5f, 6.5f, FaceWhite, bold = true, mono = true)
            fText(if (powered) txt else "", x + w / 2, y + 15.5f, 10f, color, bold = true, mono = true)
        }

        fRect(0f, 0f, 232f, 202f, Color(0xFF0A0C0F), rx = 8f)
        fCircle(HSI_CX, HSI_CY, HSI_R + 6, Color(0xFF0B0C0E), Color(0xFF3A4048), 1.5f)
        fRotate(-hdgUp.toFloat(), HSI_CX, HSI_CY) {
            for (d in 0 until 360 step 5) {
                val long = d % 10 == 0
                fLine(pol(HSI_CX, HSI_CY, HSI_R - (if (long) 9f else 5f), d.toDouble()), pol(HSI_CX, HSI_CY, HSI_R, d.toDouble()), FaceWhite, if (long) 1.4f else 1f)
            }
            for (d in 0 until 360 step 30) {
                val p = pol(HSI_CX, HSI_CY, HSI_R - 20, d.toDouble())
                val cardinal = when (d) { 0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"; else -> null }
                fRotate(d.toFloat(), p.x, p.y) { fText(cardinal ?: (d / 10).toString(), p.x, p.y, 13f, FaceWhite, bold = cardinal != null) }
            }
            if (powered) {
                // heading bug
                fRotate(bug.toFloat(), HSI_CX, HSI_CY) {
                    fPoly(HSI_CX - 8, HSI_CY - HSI_R - 1, HSI_CX + 8, HSI_CY - HSI_R - 1, HSI_CX + 5, HSI_CY - HSI_R + 8, HSI_CX - 5, HSI_CY - HSI_R + 8, fill = Color(0xFFFF9A1A), stroke = Color(0xFF3A2200), sw = 1f)
                }
                // course arrow, deviation bar and TO/FROM
                fRotate(crs.toFloat(), HSI_CX, HSI_CY) {
                    fPoly(HSI_CX, HSI_CY - 56, HSI_CX - 7, HSI_CY - 42, HSI_CX + 7, HSI_CY - 42, fill = FaceWhite)
                    fLine(HSI_CX, HSI_CY - 42, HSI_CX, HSI_CY - 28, FaceWhite, 3f)
                    fLine(HSI_CX, HSI_CY + 28, HSI_CX, HSI_CY + 58, FaceWhite, 3f)
                    for (i in listOf(-2f, -1f, 1f, 2f)) fCircle(HSI_CX + i * 20, HSI_CY, 3.2f, null, FaceWhite, 1.3f)
                    if (navOn && valid) {
                        withTransform({ translate(clampF(dev, -1.1, 1.1) * 40f, 0f) }) {
                            fLine(HSI_CX, HSI_CY - 28, HSI_CX, HSI_CY + 28, Color(0xFFFFCF3A), 4.5f)
                        }
                    }
                    if (navOn && valid && isVor) {
                        if (v.to) fPoly(HSI_CX + 14, HSI_CY - 20, HSI_CX + 22, HSI_CY - 8, HSI_CX + 6, HSI_CY - 8, fill = FaceWhite)
                        else fPoly(HSI_CX + 14, HSI_CY + 20, HSI_CX + 22, HSI_CY + 8, HSI_CX + 6, HSI_CY + 8, fill = FaceWhite)
                    }
                }
            }
        }
        // fixed lubber line, 45-degree marks and aircraft symbol
        fPoly(HSI_CX, HSI_CY - HSI_R - 7, HSI_CX - 6, HSI_CY - HSI_R - 18, HSI_CX + 6, HSI_CY - HSI_R - 18, fill = FaceAmber, stroke = Ink, sw = 0.8f)
        for (a in listOf(45, 90, 135, 180, 225, 270, 315)) {
            fLine(pol(HSI_CX, HSI_CY, HSI_R + 8, a.toDouble()), pol(HSI_CX, HSI_CY, HSI_R + 2, a.toDouble()), FaceWhite, 1.5f)
        }
        val acft = Path().apply {
            moveTo(HSI_CX, HSI_CY - 20); lineTo(HSI_CX, HSI_CY + 24)
            moveTo(HSI_CX - 16, HSI_CY + 2); lineTo(HSI_CX + 16, HSI_CY + 2)
            moveTo(HSI_CX - 7, HSI_CY + 22); lineTo(HSI_CX + 7, HSI_CY + 22)
        }
        drawPath(acft, FaceWhite, style = Stroke(3.6f, cap = StrokeCap.Round))
        // glideslope scale (left)
        if (powered && navOn && isIls) {
            for (i in listOf(-2f, -1f, 1f, 2f)) fCircle(14f, HSI_CY + i * 20, 3f, null, FaceWhite, 1.3f)
            fLine(8f, HSI_CY, 20f, HSI_CY, FaceWhite, 1.3f)
            if (gsOn) {
                val y = HSI_CY + R.adiGsY(nav?.gsErr ?: 0.0).toFloat() * 20f
                fPoly(6f, y, 22f, y - 6, 22f, y + 6, fill = Color(0xFFFFCF3A), stroke = Ink, sw = 0.8f)
            }
        }
        win(6f, 4f, 64f, "TAS", R.pad(R.clamp(tas, 0.0, 999.0), 3))
        win(162f, 4f, 64f, "GND SPD", R.pad(R.clamp(gs, 0.0, 999.0), 3))
        win(6f, 176f, 52f, "CRS", R.hsiWindowDeg(crs), Cream)
        win(174f, 176f, 52f, "HDG", R.hsiWindowDeg(bug), Color(0xFFFF9A1A))
        if (!powered || fail.hdg) fFlag(HSI_CX, 30f, "HDG", 32f, size = 9f)
        if (powered && navOn && !valid) fFlag(HSI_CX, 190f, if (isVor) "VOR" else "NAV", 30f, size = 9f)
    }
}

/* ═════════════════════════════ RMI / DME ═════════════════════════════ */

private const val RMI_CX = 77f
private const val RMI_CY = 108f
private const val RMI_R = 64f

private fun DrawScope.dmeWindow(x: Float, label: String, nm: Double?) {
    fRect(x, 3f, 68f, 28f, Color.Black, Color(0xFF555A60), 1f, 2f)
    if (nm != null) {
        fText(R.dmeText(nm), x + 34, 15f, 14f, Color(0xFFE8F5E8), bold = true, mono = true)
    } else {
        // orange / white hatching (web pattern rotated 45 degrees, 4 + 4 px stripes)
        withTransform({ clipRect(x + 2, 5f, x + 66, 17f) }) {
            fRotate(45f, x + 34, 11f) {
                for (k in -12..12) {
                    drawRect(Orange, Offset(x + 34 + k * 8f, -20f), androidx.compose.ui.geometry.Size(4f, 60f))
                    drawRect(Cream, Offset(x + 34 + k * 8f + 4f, -20f), androidx.compose.ui.geometry.Size(4f, 60f))
                }
            }
        }
    }
    fText(label, x + 34, 25f, 7f, FaceWhite, bold = true, mono = true)
}

private fun DrawScope.rmiPointer(rel: Double, kind: Int, live: Boolean) {
    val col = if (live) (if (kind == 1) Yellow else Color(0xFF8CF08C)) else Color(0xFF7A8088)
    val top = RMI_CY - RMI_R + 7
    fRotate(rel.toFloat(), RMI_CX, RMI_CY) {
        if (kind == 1) {
            fLine(RMI_CX, top + 8, RMI_CX, RMI_CY + RMI_R - 12, col, 2.4f)
            fPoly(RMI_CX, top - 2, RMI_CX - 6, top + 14, RMI_CX + 6, top + 14, fill = col)
            fLine(RMI_CX - 7, RMI_CY + RMI_R - 12, RMI_CX + 7, RMI_CY + RMI_R - 12, col, 2.4f)
        } else {
            fLine(RMI_CX - 2.4f, top + 10, RMI_CX - 2.4f, RMI_CY + RMI_R - 12, col, 1.6f)
            fLine(RMI_CX + 2.4f, top + 10, RMI_CX + 2.4f, RMI_CY + RMI_R - 12, col, 1.6f)
            fPolyline(listOf(Offset(RMI_CX - 8, top + 15), Offset(RMI_CX, top - 1), Offset(RMI_CX + 8, top + 15)), col, 1.8f)
            fLine(RMI_CX - 7, RMI_CY + RMI_R - 12, RMI_CX + 7, RMI_CY + RMI_R - 12, col, 1.8f)
        }
    }
}

/**
 * Radio magnetic indicator with DME-1 / DME-2 windows, viewBox 154 x 176. [brg1] / [brg2] are relative bearings (degrees clockwise
 * from the nose); null parks the pointer at 3 o'clock.
 */
@Composable
fun RmiFace(
    powered: Boolean,
    fail: MipLocal.MipFail,
    hdg: Double,
    dme1Nm: Double?,
    dme2Nm: Double?,
    brg1: Double?,
    brg2: Double?,
    label1: String = "",
    label2: String = "",
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    FaceCanvas(154f, 176f, modifier) {
        val h = R.norm360(hdg)
        fun ptr(b: Double?): Pair<Boolean, Double> = if (powered && !fail.adf && b != null) true to R.norm360(b) else false to 90.0
        val p1 = ptr(brg1)
        val p2 = ptr(brg2)
        fRect(0f, 0f, 154f, 176f, Color(0xFF0A0C0F), rx = 8f)
        dmeWindow(5f, "DME-1", if (powered && !fail.dme1) dme1Nm else null)
        dmeWindow(81f, "DME-2", if (powered && !fail.dme2) dme2Nm else null)
        fCircle(RMI_CX, RMI_CY, RMI_R + 5, Color(0xFF0B0C0E), Color(0xFF3A4048), 1.5f)
        fRotate(-h.toFloat(), RMI_CX, RMI_CY) {
            for (d in 0 until 360 step 5) {
                val long = d % 10 == 0
                fLine(pol(RMI_CX, RMI_CY, RMI_R - (if (long) 8f else 4f), d.toDouble()), pol(RMI_CX, RMI_CY, RMI_R, d.toDouble()), FaceWhite, if (long) 1.3f else 0.9f)
            }
            for (d in 0 until 360 step 30) {
                val p = pol(RMI_CX, RMI_CY, RMI_R - 17, d.toDouble())
                val cardinal = when (d) { 0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"; else -> null }
                fRotate(d.toFloat(), p.x, p.y) { fText(cardinal ?: (d / 10).toString(), p.x, p.y, 11f, FaceWhite, bold = cardinal != null) }
            }
        }
        rmiPointer(p2.second, 2, p2.first)
        rmiPointer(p1.second, 1, p1.first)
        fCircle(RMI_CX, RMI_CY, 5f, Color(0xFF23282D), Color(0xFF555D64), 1f)
        fPoly(RMI_CX, RMI_CY - RMI_R - 6, RMI_CX - 5, RMI_CY - RMI_R - 15, RMI_CX + 5, RMI_CY - RMI_R - 15, fill = FaceAmber, stroke = Ink, sw = 0.7f)
        if (!powered || fail.hdg) fFlag(RMI_CX, RMI_CY - 28, "HDG", 30f, size = 9f)
        fun src(x: Float, i: Int, live: Boolean, label: String) {
            fRect(x, 150f, 40f, 22f, Color(0xFF15181C), Color(0xFF666D74), 1f, 3f)
            fText(if (i == 0) "POINTER 1" else "POINTER 2", x + 20, 157f, 6f, Muted, mono = true)
            fText(label.ifEmpty { "PTR ${i + 1}" }, x + 20, 166f, 9f, if (live) (if (i == 0) Yellow else Color(0xFF8CF08C)) else Color(0xFFFF9A5A), bold = true, mono = true)
        }
        src(2f, 0, p1.first, label1)
        src(112f, 1, p2.first, label2)
    }
}

/* ═════════════════════════════ ASI ═════════════════════════════ */

private fun DrawScope.orangeFlag(x: Float, y: Float, w: Float, h: Float, text: String, size: Float, tx: Float, ty: Float) {
    fRect(x, y, w, h, Orange, OrangeEdge, 1f, 2f)
    fText(text, tx, ty, size, OrangeText, bold = true, mono = true)
}

/** 737-200 airspeed indicator, viewBox 200 x 200. Airspeed, Mach and the barber pole come from this side's CADC. */
@Composable
fun AsiFace(data: PitotStatic.SideAirData, powered: Boolean, bugKt: Double, modifier: Modifier = Modifier.fillMaxSize()) {
    FaceCanvas(200f, 200f, modifier) {
        val cadcOk = powered && data.adc.valid
        val limit = PitotStatic.vmoMmoLimitKt(data.staticPressureHpa)
        val vmoA = R.asiAng(limit)
        fun polc(r: Float, a: Double) = pol(100f, 100f, r, a)
        roundInstrumentBase()
        fCircle(100f, 100f, 96f, Color(0xFF0B0C0E))
        val ticks = (60..200 step 10).toList() + (250..400 step 50).toList()
        for (k in ticks) {
            val a = R.asiAng(k.toDouble())
            val big = k % 20 == 0 || k > 200
            fLine(polc(if (big) 78f else 84f, a), polc(94f, a), Color(0xFFF2F2F2), if (big) 2f else 1f)
        }
        for (k in listOf(60, 80, 100, 120, 140, 160, 180, 200, 250, 300, 350, 400)) {
            val p = polc(67f, R.asiAng(k.toDouble()))
            fText(k.toString(), p.x, p.y, 12f, Color(0xFFF2F2F2))
        }
        fText("MACH", 85f, 74f, 8f, Color(0xFF4AA8E8), TextAnchor.END, mono = true)
        fText("KNOTS", 100f, 152f, 8f, Color(0xFF4AA8E8), mono = true)
        // Mach window: blank below M 0.40, MACH flag when the CADC cannot supply it
        fRect(88f, 60f, 38f, 22f, Color(0xFF050607), Color(0xFF6D747A), 1f, 2f)
        if (cadcOk) {
            R.machText(data.mach)?.let { fText(it, 107f, 71f, 11f, Color(0xFFF2F2F2), mono = true) }
        } else {
            orangeFlag(64f, 62f, 50f, 16f, "MACH", 10f, 89f, 70f)
        }
        if (!powered) orangeFlag(132f, 94f, 38f, 16f, "IN OP", 8f, 154f, 102f)
        // speed bug setting
        fRect(76f, 118f, 52f, 22f, Color(0xFF050607), Color(0xFF6D747A), 1f, 2f)
        fText(if (powered) R.pad(R.clamp(bugKt, 60.0, 400.0), 3) else "", 118f, 129f, 13f, Color(0xFF7DFF7D), TextAnchor.END, mono = true)
        fRect(58f, 122f, 34f, 14f, if (powered) Color(0xFF2A2218) else Orange, OrangeEdge, 0.8f, 2f)
        fText("A/S", 75f, 129f, 8f, if (powered) Color(0xFF9A8A6A) else OrangeText, bold = true, mono = true)
        // VMO / MMO barber pole
        if (cadcOk) {
            fRotate(vmoA.toFloat(), 100f, 100f) {
                fLine(100f, 100f, 100f, 14f, Color.White, 5f)
                drawLine(Color(0xFFD6332A), Offset(100f, 100f), Offset(100f, 14f), strokeWidth = 5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
        } else {
            val v = polc(60f, R.asiAng(340.0) - 6)
            fRect(v.x - 15, v.y - 6, 30f, 12f, Orange, OrangeEdge, 0.6f, 2f)
            fText("VMO", v.x, v.y, 8f, OrangeText, bold = true, mono = true)
        }
        // speed bug and IAS pointer
        if (powered) {
            val a = R.asiAng(bugKt)
            val b = polc(94f, a)
            fRotate((a - 90).toFloat(), b.x, b.y) {
                fPoly(b.x, b.y, b.x + 9, b.y - 5, b.x + 9, b.y + 5, fill = Color(0xFFFF9A1A))
            }
        }
        fRotate(R.asiAng(data.iasKt).toFloat(), 100f, 100f) {
            fPoly(100f, 16f, 96f, 100f, 104f, 100f, fill = Cream)
            fPoly(100f, 16f, 98f, 40f, 102f, 40f, fill = Color(0xFF9DFF3A))
        }
        fCircle(100f, 100f, 7f, Color(0xFF23282D), Color(0xFF555D64), 1f)
    }
}

/* ═════════════════════════════ ALTIMETER ═════════════════════════════ */

/** Three-pointer altimeter with OFF flag, 5-digit counter and MB / IN HG windows, viewBox 200 x 200. [mb] hPa, [hg] inHg. */
@Composable
fun AltFace(altFt: Double, powered: Boolean, mb: Double, hg: Double, small: Boolean = false, modifier: Modifier = Modifier.fillMaxSize()) {
    FaceCanvas(200f, 200f, modifier) {
        val digits = R.altDigits(altFt)
        val (tenK, oneK) = R.altHandAngles(altFt)
        roundInstrumentBase()
        fCircle(100f, 100f, 96f, Color(0xFF0B0C0E))
        for (i in 0 until 50) {
            val a = i * 7.2
            val five = i % 5 == 0
            fLine(pol(100f, 100f, if (five) 80f else 88f, a), pol(100f, 100f, 94f, a), Color(0xFFF2F2F2), if (five) 2f else 1f)
        }
        for (i in 0 until 10) {
            val p = pol(100f, 100f, 68f, i * 36.0)
            fText(i.toString(), p.x, p.y, 17f, Color(0xFFF2F2F2))
        }
        fText("100 FEET", 100f, 42f, 8f, Color(0xFF4AA8E8), mono = true, central = false)
        fText("ALT", 100f, 140f, 11f, Color(0xFFF2F2F2), mono = true, central = false)
        fRect(34f, 82f, 132f, 32f, Color.Black, Color(0xFF8A9096), 1f, 2f)
        digits.forEachIndexed { i, c ->
            fText(if (powered || i > 1) c.toString() else "", 92f + i * 17f, 99f, 22f, Color(0xFFF2F2F2), mono = true)
        }
        if (!powered) orangeFlag(38f, 85f, 48f, 26f, "OFF", 16f, 62f, 98f)
        if (powered && altFt < 0) {
            fRect(38f, 85f, 48f, 26f, Cream, Color(0xFF8A9096), 1f, 2f)
            fText("NEG", 62f, 98f, 14f, Color(0xFF111111), bold = true, mono = true)
        }
        if (!small) {
            fText("IN HG", 100f, 128f, 7f, Color(0xFF9A968A), mono = true, central = false)
            fRect(72f, 131f, 56f, 16f, Color.Black, Color(0xFF6D747A), 1f, 2f)
            fText(R.hgText(hg), 100f, 139f, 12f, Color(0xFFF2F2F2), mono = true)
        }
        fText("MB", 50f, 148f, 7f, Color(0xFF9A968A), mono = true, central = false)
        fRect(26f, 150f, 46f, 14f, Color.Black, Color(0xFF6D747A), 1f, 2f)
        fText(Math.floor(mb + 0.5).toLong().toString(), 49f, 157f, 10f, Color(0xFFF2F2F2), mono = true)
        fPoly(100f, 6f, 94f, -2f, 106f, -2f, fill = Color(0xFFFFC21A))
        fRotate(tenK.toFloat(), 100f, 100f) { fPoly(100f, 50f, 96f, 100f, 104f, 100f, fill = Cream) }
        fRotate(oneK.toFloat(), 100f, 100f) { fPoly(100f, 12f, 95f, 100f, 105f, 100f, 100f, 112f, fill = Cream) }
        fCircle(100f, 100f, 6f, Color(0xFF23282D), Color(0xFF555D64), 1f)
    }
}

/* ═════════════════════ SixPack faces used on the panel ═════════════════════ */

private val SixText = Color(0xFFF2F2F2)

private fun DrawScope.sixPointer(deg: Double, len: Float, w: Float = 3f, color: Color = SixText, tail: Float = 14f) {
    fRotate(deg.toFloat(), 100f, 100f) {
        fPoly(100f, 100 - len, 100 - w, 100f, 100 - w * 0.6f, 100 + tail, 100 + w * 0.6f, 100 + tail, 100 + w, 100f, fill = color)
    }
}

private fun DrawScope.sixGaugeFrame() {
    roundInstrumentBase(ring = Color(0xFF15181C), border = Color(0xFF5B6168), bg = Color(0xFF0B0C0E))
}

/** SixPack VerticalSpeed: 0 at 9 o'clock, square-root scale; OFF flag when unpowered or failed. */
@Composable
fun VsiFace(vsiFpm: Double, powered: Boolean, failVsi: Boolean, modifier: Modifier = Modifier.fillMaxSize()) {
    FaceCanvas(200f, 200f, modifier) {
        sixGaugeFrame()
        fCircle(100f, 100f, 96f, Color(0xFF0B0C0E))
        for (f in listOf(0, 500, 1000, 1500, 2000, 4000, 6000)) {
            for (g in if (f == 0) listOf(1) else listOf(1, -1)) {
                val a = R.vsiAngle((f * g).toDouble())
                val big = f == 0 || f >= 1000
                fLine(pol(100f, 100f, if (big) 78f else 84f, a), pol(100f, 100f, 94f, a), SixText, 2f)
                if (big) {
                    val p = pol(100f, 100f, 66f, a)
                    val v = f / 1000.0
                    fText(if (v == Math.floor(v)) v.toInt().toString() else v.toString(), p.x, p.y, 11f, SixText)
                }
            }
        }
        fText("UP", 100f, 58f, 8f, SixText, mono = true, central = false)
        fText("DOWN", 100f, 146f, 8f, SixText, mono = true, central = false)
        fText("1000 FT/MIN", 100f, 160f, 7f, Muted, mono = true, central = false)
        fText(R.vsiText(vsiFpm), 140f, 112f, 10f, SixText, mono = true, central = false)
        sixPointer(R.vsiAngle(vsiFpm), 84f)
        fCircle(100f, 100f, 6f, Color(0xFF23282D), Color(0xFF555D64), 1f)
        if (!powered || failVsi) fFlag(31f, 96f, "OFF", 30f, 16f, size = 12f)
    }
}

/** SixPack Attitude, used for the standby horizon (viewBox 200 x 200). [flagX] draws the "X" flag when the standby AC source is lost. */
@Composable
fun StandbyAttitudeFace(roll: Double, pitch: Double, flagX: Boolean, modifier: Modifier = Modifier.fillMaxSize()) {
    FaceCanvas(200f, 200f, modifier) {
        sixGaugeFrame()
        fCircle(100f, 100f, 96f, Color(0xFF5B656C))
        fClipCircle(100f, 100f, 92f) {
            withTransform({
                rotate((-roll).toFloat(), Offset(100f, 100f))
                translate(0f, (pitch * 3).toFloat())
            }) {
                drawRect(Color(0xFF2A9FD0), Offset(-200f, -400f), androidx.compose.ui.geometry.Size(600f, 500f))
                drawRect(Color(0xFF14171A), Offset(-200f, 100f), androidx.compose.ui.geometry.Size(600f, 500f))
                fLine(-200f, 100f, 400f, 100f, Color.White, 2.5f)
                for (p in listOf(-30, -20, -10, 10, 20, 30)) {
                    val y = 100f - p * 3f
                    val w = if (abs(p) == 10) 18f else if (abs(p) == 20) 24f else 30f
                    fLine(100 - w, y, 100 + w, y, Color.White, 1.5f)
                    if (abs(p) % 20 == 0) fText(abs(p).toString(), 100 + w + 7, y, 9f, Color.White, TextAnchor.START, mono = true)
                }
                for (p in listOf(-25, -15, -5, 5, 15, 25)) {
                    val y = 100f - p * 3f
                    fLine(92f, y, 108f, y, Color.White, 1f)
                }
            }
        }
        // roll scale (moves with bank)
        fRotate((-roll).toFloat(), 100f, 100f) {
            for (a in listOf(-60, -45, -30, -20, -10, 0, 10, 20, 30, 45, 60)) {
                fLine(pol(100f, 100f, if (a == 0) 78f else 82f, a.toDouble()), pol(100f, 100f, 92f, a.toDouble()), Color.White, if (a == 0 || a % 30 == 0) 2.2f else 1.2f)
            }
            fPoly(100f, 88f, 94f, 78f, 106f, 78f, fill = Color.White)
        }
        fPoly(100f, 16f, 94f, 7f, 106f, 7f, fill = Color(0xFFFFD23A))
        val acft = Path().apply {
            moveTo(42f, 100f); relativeLineTo(36f, 0f); relativeLineTo(8f, 9f)
            moveTo(158f, 100f); relativeLineTo(-36f, 0f); relativeLineTo(-8f, 9f)
        }
        drawPath(acft, Color(0xFFFFD23A), style = Stroke(4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        fCircle(100f, 100f, 4.5f, Color(0xFFFFD23A), Ink, 0.8f)
        if (flagX) fFlag(176f, 26f, "X", 22f, 20f, size = 15f)
    }
}
