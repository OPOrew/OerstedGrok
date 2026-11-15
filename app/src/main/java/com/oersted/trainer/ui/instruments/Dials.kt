package com.oersted.trainer.ui.instruments

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import com.oersted.trainer.sim.DialGeometry
import kotlin.math.cos
import kotlin.math.sin

/**
 * Radio altimeter ("HEIGHT x100 FEET") and flap position dials, drawn from the web SVGs in MainInstrumentPanel.tsx.
 * Drawn in the web's 100-unit design space and scaled to the view. Angles come from [DialGeometry].
 * The red sector on the height dial is not drawn yet. Not compiled in the sandbox (needs the Android SDK).
 */
@Composable
fun HeightDial(ft: Double, dh: Double, powered: Boolean, modifier: Modifier = Modifier.fillMaxWidth()) {
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val s = size.width / 100f
        withTransform({ scale(s, s, Offset.Zero) }) {
            drawCircle(Color(0xFF15181C), radius = 48f, center = Offset(50f, 50f))
            drawCircle(Color(0xFF5B6168), radius = 48f, center = Offset(50f, 50f), style = Stroke(width = 1.5f))
            val tickValues = listOf(0, 1, 2, 3, 4, 5, 10, 15, 20, 25)
            tickValues.forEach { n ->
                val a = DialGeometry.raAngleDeg(n.toDouble())
                val p1 = polar(45f, a)
                val p2 = polar(38f, a)
                drawLine(Color(0xFFE8E4D6), p1, p2, strokeWidth = 1.4f)
                drawLabel(n.toString(), polar(30f, a), 9f, Color(0xFFE8E4D6).toArgb())
            }
            drawLabel("HEIGHT", Offset(50f, 62f), 5f, Color(0xFF9A968A).toArgb())
            drawLabel("x100 FEET", Offset(50f, 68f), 4f, Color(0xFF9A968A).toArgb())
            if (powered) {
                // decision-height bug, at the outer rim
                val a = DialGeometry.raAngleDeg(dh / 100.0)
                drawCircle(Color(0xFFFFB24A), radius = 2.5f, center = polar(46f, a))
            }
            val valid = powered && ft >= 0 && ft <= 2500
            if (powered && valid) {
                val a = DialGeometry.raAngleDeg(ft / 100.0)
                drawLine(Color(0xFFF4F1E4), Offset(50f, 50f), polar(38f, a), strokeWidth = 2.2f)
            }
            drawCircle(Color(0xFF2A2D31), radius = 3f, center = Offset(50f, 50f))
        }
    }
}

/** Flap position gauge: UP-1-2-5-10-15-25-30-40 on the non-linear scale; the needle points to the current setting. */
@Composable
fun FlapDial(units: Double, modifier: Modifier = Modifier.fillMaxWidth()) {
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val s = size.width / 100f
        withTransform({ scale(s, s, Offset.Zero) }) {
            drawCircle(Color(0xFF15181C), radius = 49f, center = Offset(50f, 50f))
            drawCircle(Color(0xFF5B6168), radius = 49f, center = Offset(50f, 50f), style = Stroke(width = 1.5f))
            drawCircle(Color(0xFF0B0C0E), radius = 44f, center = Offset(50f, 50f))
            val labels = listOf(0 to "UP", 1 to "1", 2 to "2", 5 to "5", 10 to "10", 15 to "15", 25 to "25", 30 to "30", 40 to "40")
            val angles = listOf(250.0, 292.0, 336.0, 20.0, 62.0, 96.0, 134.0, 162.0, 192.0)
            labels.zip(angles).forEach { (lab, a) ->
                drawLine(Color(0xFFF0B27A), polar(44f, a), polar(37f, a), strokeWidth = 2.4f)
                drawLabel(lab.second, polar(29f, a), 8f, Color(0xFFF0B27A).toArgb())
            }
            drawLabel("FLAPS", Offset(26f, 78f), 6.5f, Color(0xFFF0B27A).toArgb())
            val ang = DialGeometry.flapAngleDeg(units)
            // needle: a triangle pointing up, rotated about the centre
            withTransform({ rotate(ang.toFloat(), Offset(50f, 50f)) }) {
                drawLine(Color(0xFFF3EFE2), Offset(50f, 12f), Offset(50f, 50f), strokeWidth = 4f)
            }
            drawCircle(Color(0xFFD8D2BC), radius = 6f, center = Offset(50f, 50f))
            drawCircle(Color(0xFF4A4D52), radius = 2.4f, center = Offset(50f, 50f))
        }
    }
}

/** A point on the dial, [r] from the centre at clockwise angle [deg] from twelve o'clock. */
private fun DrawScope.polar(r: Float, deg: Double): Offset {
    val rad = Math.toRadians(deg)
    return Offset(50f + r * sin(rad).toFloat(), 50f - r * cos(rad).toFloat())
}

/** Draws a centred label in the 100-unit space. */
private fun DrawScope.drawLabel(text: String, at: Offset, size: Float, argb: Int) {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            color = argb
            textSize = size
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.nativeCanvas.drawText(text, at.x, at.y + size * 0.35f, paint)
    }
}
