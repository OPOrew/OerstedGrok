package com.oersted.trainer.ui.instruments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.ChronometerLogic
import com.oersted.trainer.sim.SelectorLogic
import kotlin.math.cos
import kotlin.math.sin

/**
 * Chronometer face (AnalogClock in MainInstrumentPanel.tsx). Rules come from sim/ChronometerLogic.kt. The face draws the
 * UTC clock hands and the elapsed readout with its flag; [onToggle] and [onReset] are the lever actions, [onFaceTap] the dial tap.
 * Not compiled in the sandbox (needs the Android SDK).
 */
@Composable
fun ChronometerFace(
    utcHour: Int,
    utcMinute: Int,
    utcSecond: Int,
    state: ChronometerLogic.State,
    onFaceTap: () -> Unit,
    modifier: Modifier = Modifier.size(96.dp),
) {
    Box(modifier = modifier.pointerInput(Unit) {
        awaitEachGesture { awaitFirstDown(); onFaceTap() }
    }, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            val s = size.width / 100f
            withTransform({ scale(s, s, Offset.Zero) }) {
                drawCircle(Color(0xFF15181C), radius = 49f, center = Offset(50f, 50f))
                drawCircle(Color(0xFF5B6168), radius = 49f, center = Offset(50f, 50f), style = Stroke(width = 1.5f))
                drawCircle(Color(0xFF0B0C0E), radius = 44f, center = Offset(50f, 50f))
                for (i in 0 until 60) {
                    val a = i * 6.0
                    val r1 = if (i % 5 == 0) 36.0 else 40.0
                    drawLine(
                        Color(0xFFE8E4D6),
                        polar(44f, a), polar(r1.toFloat(), a),
                        strokeWidth = if (i % 5 == 0) 1.4f else 0.6f,
                    )
                }
                val h = ChronometerLogic.hands(utcHour, utcMinute, utcSecond)
                drawLine(Color(0xFFF1F1EA), Offset(50f, 50f), polar(20f, h.hourDeg), strokeWidth = 2.4f)
                drawLine(Color(0xFFF1F1EA), Offset(50f, 50f), polar(30f, h.minuteDeg), strokeWidth = 1.6f)
                drawLine(Color(0xFFFF9A3A), Offset(50f, 50f), polar(36f, h.secondDeg), strokeWidth = 0.8f)
                drawCircle(Color(0xFF2A2D31), radius = 2.6f, center = Offset(50f, 50f))
            }
        }
        // the elapsed readout and its flag sit on top of the face
        val flag = ChronometerLogic.flag(state)
        Text(
            text = if (flag == ChronometerLogic.Flag.RUNNING) "" else ChronometerLogic.elapsedText(state.elapsed),
            color = Color(0xFFF1F1EA),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 7.sp,
        )
    }
}

/** Three-position selector knob (Sel3 in MainInstrumentPanel.tsx). A press on the left half steps down, the right half up. */
@Composable
fun ThreePositionSelector(
    value: Int,
    legends: List<String>,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier.size(width = 128.dp, height = 92.dp),
) {
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .size(44.dp)
                .pointerInput(value) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val leftHalf = down.position.x < size.width / 2f
                        onChange(SelectorLogic.stepFromPress(value, leftHalf))
                    }
                },
        ) {
            val s = size.width / 100f
            withTransform({ scale(s, s, Offset.Zero) }) {
                drawCircle(Color(0xFFBDB8A6), radius = 50f, center = Offset(50f, 50f))
                // the pointer, rotated about the knob centre
                withTransform({ rotate(SelectorLogic.knobAngleDeg(value).toFloat(), Offset(50f, 50f)) }) {
                    drawLine(Color(0xFFE9E5D6), Offset(50f, 50f), Offset(50f, 14f), strokeWidth = 8f)
                }
            }
        }
        Text(
            text = legends.getOrElse(value) { "" },
            color = Color(0xFFF1F1EA),
            fontFamily = FontFamily.Monospace,
            fontSize = 7.sp,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** EPR counter: two decimals when powered, an amber bar when not (EprCounter in MainInstrumentPanel.tsx). */
@Composable
fun EprCounter(value: Double, powered: Boolean, modifier: Modifier = Modifier.size(width = 52.dp, height = 16.dp)) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val text = SelectorLogic.eprText(value, powered)
        if (text != null) {
            Text(text = text, color = Color(0xFFF2F2F2), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        } else {
            Box(Modifier.fillMaxWidth().size(height = 8.dp, width = 0.dp).background(Color(0xFFFF7A1A)))
        }
    }
}

private fun DrawScope.polar(r: Float, deg: Double): Offset {
    val rad = Math.toRadians(deg)
    return Offset(50f + r * sin(rad).toFloat(), 50f - r * cos(rad).toFloat())
}
