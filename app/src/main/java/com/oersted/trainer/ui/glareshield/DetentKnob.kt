package com.oersted.trainer.ui.glareshield

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.KnobPick
import kotlin.math.cos
import kotlin.math.sin

/**
 * Detent knob (Knob in GlareshieldPanel.tsx): a knurled dial with marks at fixed angles and a pointer at the current
 * mark. A press picks the nearest mark and calls [onChange] when it differs. [labels] are the legends, one per mark (null
 * for a plain tick). Rules are in sim/KnobPick.kt. Not compiled in the sandbox (needs the Android SDK).
 */
@Composable
fun DetentKnob(
    value: Int,
    markAngles: List<Double>,
    labels: List<String?>,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier.size(88.dp),
    yellowPointer: Boolean = false,
) {
    val legendRadius = 62.dp
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // legends around the dial
        markAngles.forEachIndexed { i, a ->
            val text = labels.getOrNull(i)
            if (text != null) {
                val rad = Math.toRadians(a)
                val dx = (legendRadius.value * sin(rad)).dp
                val dy = (-legendRadius.value * cos(rad)).dp
                Text(
                    text = text,
                    color = if (i == value) Color.White else Color(0xFFE8E4D8),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp,
                    modifier = Modifier.offset(x = dx, y = dy),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(size = 88.dp)
                .background(Brush.sweepGradient(listOf(Color(0xFF2A2D31), Color(0xFF08090A), Color(0xFF2A2D31))), CircleShape)
                .border(2.dp, Color(0xFF2B2E33), CircleShape)
                .pointerInput(value) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val dx = down.position.x - size.width / 2f
                        val dy = down.position.y - size.height / 2f
                        val angle = KnobPick.pointerAngleDeg(dx.toDouble(), dy.toDouble())
                        KnobPick.pressedValue(value, angle, markAngles)?.let { onChange(it) }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 40.dp)
                    .rotate(KnobPick.pointerAngleFor(value, markAngles).toFloat())
                    .background(if (yellowPointer) Color(0xFFF2C200) else Color(0xFFF1EFE6), CircleShape),
            )
        }
    }
}
