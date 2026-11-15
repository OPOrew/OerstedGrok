package com.oersted.trainer.ui.instruments

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.sin

/*
 * Small SVG-like drawing layer for the instrument faces of components/panel/instruments.tsx and SixPack.tsx.
 * Every face is drawn in the web's viewBox units (e.g. 242 x 254 for the ADI) and scaled to fit the view, so the
 * coordinates below are copied from the web SVG unchanged. Compose only; verified on CI, not compiled locally.
 */

internal val FaceWhite = Color(0xFFF2F0E6)
internal val FaceAmber = Color(0xFFFFB81C)
internal val FlagFill = Color(0xFFFF5A2A)
internal val FlagEdge = Color(0xFFFF8A5A)
internal val FlagText = Color(0xFF5A1000)

/** Canvas that maps a [vw] x [vh] viewBox onto the view (SVG `meet`: uniform scale, centred). */
@Composable
internal fun FaceCanvas(vw: Float, vh: Float, modifier: Modifier = Modifier, draw: DrawScope.() -> Unit) {
    Canvas(modifier) {
        val s = minOf(size.width / vw, size.height / vh)
        val ox = (size.width - vw * s) / 2f
        val oy = (size.height - vh * s) / 2f
        withTransform({
            translate(ox, oy)
            scale(s, s, Offset.Zero)
        }) { draw() }
    }
}

/** web pol(): point at radius [r], clockwise angle [a] degrees from twelve o'clock, about ([cx], [cy]). */
internal fun pol(cx: Float, cy: Float, r: Float, a: Double): Offset {
    val t = Math.toRadians(a)
    return Offset(cx + r * sin(t).toFloat(), cy - r * cos(t).toFloat())
}

internal enum class TextAnchor { START, MIDDLE, END }

private val textPaint = Paint().apply { isAntiAlias = true }

/**
 * SVG `<text>`: [size] in viewBox units, anchored at ([x], [y]). [central] = `dominantBaseline="central"`; otherwise [y] is the
 * alphabetic baseline. [mono] picks the monospace family (web MONO), else sans (web FONT).
 */
internal fun DrawScope.fText(
    text: String, x: Float, y: Float, size: Float, color: Color,
    anchor: TextAnchor = TextAnchor.MIDDLE, bold: Boolean = false, mono: Boolean = false, central: Boolean = true,
) {
    if (text.isEmpty()) return
    drawIntoCanvas { canvas ->
        val p = textPaint
        p.color = color.toArgb()
        p.textSize = size
        p.typeface = Typeface.create(if (mono) Typeface.MONOSPACE else Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        p.textAlign = when (anchor) {
            TextAnchor.START -> Paint.Align.LEFT
            TextAnchor.MIDDLE -> Paint.Align.CENTER
            TextAnchor.END -> Paint.Align.RIGHT
        }
        val fm = p.fontMetrics
        val base = if (central) y - (fm.ascent + fm.descent) / 2f else y
        canvas.nativeCanvas.drawText(text, x, base, p)
    }
}

internal fun DrawScope.fLine(a: Offset, b: Offset, color: Color, w: Float, round: Boolean = false) {
    drawLine(color, a, b, strokeWidth = w, cap = if (round) StrokeCap.Round else StrokeCap.Butt)
}

internal fun DrawScope.fLine(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, w: Float) = fLine(Offset(x1, y1), Offset(x2, y2), color, w)

/** SVG `<polygon>`: filled, optionally outlined. */
internal fun DrawScope.fPoly(pts: List<Offset>, fill: Color?, stroke: Color? = null, sw: Float = 1f) {
    val path = Path().apply {
        pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        close()
    }
    if (fill != null) drawPath(path, fill)
    if (stroke != null) drawPath(path, stroke, style = Stroke(width = sw, join = StrokeJoin.Miter))
}

internal fun DrawScope.fPoly(vararg xy: Float, fill: Color?, stroke: Color? = null, sw: Float = 1f) {
    fPoly(List(xy.size / 2) { Offset(xy[it * 2], xy[it * 2 + 1]) }, fill, stroke, sw)
}

/** SVG `<polyline>` (open, unfilled). */
internal fun DrawScope.fPolyline(pts: List<Offset>, color: Color, w: Float) {
    val path = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
    drawPath(path, color, style = Stroke(width = w))
}

internal fun DrawScope.fRect(x: Float, y: Float, w: Float, h: Float, fill: Color?, stroke: Color? = null, sw: Float = 1f, rx: Float = 0f) {
    if (fill != null) drawRoundRect(fill, Offset(x, y), Size(w, h), CornerRadius(rx, rx))
    if (stroke != null) drawRoundRect(stroke, Offset(x, y), Size(w, h), CornerRadius(rx, rx), style = Stroke(width = sw))
}

internal fun DrawScope.fCircle(cx: Float, cy: Float, r: Float, fill: Color?, stroke: Color? = null, sw: Float = 1f) {
    if (fill != null) drawCircle(fill, r, Offset(cx, cy))
    if (stroke != null) drawCircle(stroke, r, Offset(cx, cy), style = Stroke(width = sw))
}

/** SVG `<g transform="rotate(deg cx cy)">` */
internal inline fun DrawScope.fRotate(deg: Float, cx: Float, cy: Float, block: DrawScope.() -> Unit) {
    withTransform({ rotate(deg, Offset(cx, cy)) }) { block() }
}

/** SVG `<g clipPath>` with a circle. */
internal inline fun DrawScope.fClipCircle(cx: Float, cy: Float, r: Float, block: DrawScope.() -> Unit) {
    val path = Path().apply { addOval(androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r)) }
    withTransform({ clipPath(path) }) { block() }
}

/** web Flag: orange warning flag centred on ([x], [y]), rotated by [rot]. */
internal fun DrawScope.fFlag(x: Float, y: Float, text: String, w: Float = 34f, h: Float = 14f, rot: Float = 0f, size: Float = 10f) {
    fRotate(rot, x, y) {
        fRect(x - w / 2, y - h / 2, w, h, FlagFill, FlagEdge, 0.8f, 2f)
        fText(text, x, y + 0.5f, size, FlagText, bold = true, mono = true)
    }
}

/** Vertical gradient between two viewBox y values (SVG linearGradient, objectBoundingBox on a tall rect). */
internal fun vGrad(y0: Float, y1: Float, top: Color, bottom: Color): Brush = Brush.verticalGradient(colors = listOf(top, bottom), startY = y0, endY = y1)

/** Round face inside a round bezel, as the web `aspect-square rounded-full border ... shadow-[inset_0_0_0_4px_#1b2026]` wrapper. */
internal fun DrawScope.roundInstrumentBase(ring: Color = Color(0xFF1B2026), border: Color = Color(0xFF5B656C), bg: Color = Color(0xFF05070A)) {
    fCircle(100f, 100f, 100f, bg)
    fCircle(100f, 100f, 98f, null, ring, 4f)
    fCircle(100f, 100f, 99.5f, null, border, 1f)
}
