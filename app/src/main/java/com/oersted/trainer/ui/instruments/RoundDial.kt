package com.oersted.trainer.ui.instruments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.PedestalTopDeckRules

/**
 * Port of `Dial` in components/panel/EngineInstrumentPanel.tsx: the round engine / systems gauge (EPR, N1, N2, EGT, fuel flow, oil, hydraulics,
 * HYD QTY). 270 degree sweep from -135 to +135, coloured bands, numbered majors, minor ticks, an optional digital window and a caption under
 * the dial. Compose only; verified on CI, not compiled locally.
 */
data class DialBand(val from: Double, val to: Double, val color: Color)

/** web `win`: in-dial counter window. [text] null = gauge not operating (blank orange window). */
data class DialWin(val text: String?, val x: Float? = null, val y: Float? = null, val w: Float? = null)

private const val A0 = -135.0
private const val A1 = 135.0

/** JS `Number(v.toFixed(1))` printed the way String() prints it. */
private fun scaleNumber(v: Double): String = PedestalTopDeckRules.jsNum(PedestalTopDeckRules.toFixed(v, 1).toDouble())

@Composable
fun RoundDial(
    label: String,
    value: Double,
    min: Double,
    max: Double,
    major: Double,
    modifier: Modifier = Modifier,
    unit: String? = null,
    minor: Int = 5,
    scale: Double = 1.0,
    bands: List<DialBand> = emptyList(),
    digits: Int = 0,
    size: Dp = 112.dp,
    caption: String? = null,
    dead: Boolean = false,
    win: DialWin? = null,
) {
    val span = max - min
    fun ang(v: Double) = A0 + ((v.coerceIn(min, max) - min) / span) * (A1 - A0)
    val majors = ArrayList<Double>()
    run {
        var v = min
        while (v <= max + 1e-6) { majors.add(v); v += major }
    }
    val needle = ang(if (dead) min else value)
    val small = size.value < 80
    Column(modifier.wrapContentSize(Alignment.TopCenter, unbounded = true), horizontalAlignment = Alignment.CenterHorizontally) {
        FaceCanvas(100f, 100f, Modifier.size(size)) {
            fCircle(50f, 50f, 49f, Color(0xFF15181C), Color(0xFF5B6168), 1.5f)
            fCircle(50f, 50f, 44f, Color(0xFF0B0C0E))
            for (b in bands) {
                val a0 = ang(b.from)
                val a1 = ang(b.to)
                drawArc(b.color, (a0 - 90).toFloat(), (a1 - a0).toFloat(), false, Offset(50f - 41f, 50f - 41f), Size(82f, 82f), style = Stroke(3.2f))
            }
            for (v in majors) {
                val a = ang(v)
                fLine(pol(50f, 50f, 44f, a), pol(50f, 50f, 37f, a), Color(0xFFE8E4D6), 1.4f)
                val t = pol(50f, 50f, 29f, a)
                fText(scaleNumber(v / scale), t.x, t.y, if (small) 10f else 8f, Color(0xFFE8E4D6), bold = true)
            }
            if (!small) {
                for (v in majors.dropLast(1)) {
                    for (k in 0 until minor - 1) {
                        val a = ang(v + ((k + 1) * major) / minor)
                        fLine(pol(50f, 50f, 44f, a), pol(50f, 50f, 40.5f, a), Color(0xFFBDB8A8), 0.7f)
                    }
                }
            }
            fText(label, 50f, if (small) 70f else 68f, if (small) 9f else 6.5f, Color(0xFFCFC9B6), central = false)
            if (unit != null && !small) fText(unit, 50f, 76f, 5f, Color(0xFF8F8B7D), mono = true, central = false)
            fRotate(needle.toFloat(), 50f, 50f) {
                fPoly(50f, 9f, 52f, 50f, 48f, 50f, fill = Color(0xFFF3EFE2), stroke = Color.Black, sw = 0.4f)
            }
            fCircle(50f, 50f, 4.5f, Color(0xFF2A2D31), Color(0xFF6B7078), 0.8f)
            if (win != null) {
                val w = win.w ?: 22f
                val x = win.x ?: (50f - w / 2)
                val y = win.y ?: 22f
                fRect(x, y, w, 9f, Color(0xFF050607), Color(0xFF2A2D31), 0.6f, 1f)
                if (win.text == null) fRect(x + 1.5f, y + 1.5f, w - 3f, 6f, Color(0xFFFF7A1A))
                else fText(win.text, x + w / 2, y + 4.7f, 6.5f, Color(0xFFF2F2F2), bold = true, mono = true)
            }
            if (dead) fRect(38f, 82f, 24f, 7f, Color(0xFF7A1F1F), rx = 1f)
        }
        Text(
            caption ?: (PedestalTopDeckRules.toFixed(value, digits) + (if (unit != null) " $unit" else "")),
            color = Color(0xFF9AA4AD), fontSize = 9.sp, fontFamily = FontFamily.Monospace,
        )
    }
}
