package com.oersted.trainer.ui.glareshield

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.GlareshieldDrag
import kotlin.math.atan2

/** Pointer angle about the centre of the control, in degrees, as CourseKnob measures it (0 = up, clockwise positive). */
private fun angleAbout(x: Float, y: Float, size: IntSize): Double {
    val cx = size.width / 2.0
    val cy = size.height / 2.0
    return Math.toDegrees(atan2(x - cx, -(y - cy)).toDouble())
}

/**
 * Selected course knob (CourseKnob in GlareshieldPanel.tsx). Dragging around the knob steps the course 6 degrees at a time;
 * [onStep] receives the signed step count. The digits show the course, normalised and zero-padded. Rules are in
 * sim/GlareshieldDrag.kt. Not compiled in the sandbox (needs the Android SDK).
 */
@Composable
fun CourseKnob(
    value: Double,
    powered: Boolean,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier.size(96.dp),
) {
    Box(
        modifier = modifier
            .background(
                Brush.radialGradient(listOf(Color(0xFF2A2D31), Color(0xFF08090A))),
                CircleShape,
            )
            .border(2.dp, Color(0xFF2B2E33), CircleShape)
            .pointerInput(Unit) {
                var last = 0.0
                var acc = 0.0
                detectDragGestures(
                    onDragStart = { p ->
                        last = angleAbout(p.x, p.y, size)
                        acc = 0.0
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val a = angleAbout(change.position.x, change.position.y, size)
                        val d = GlareshieldDrag.wrapAngleDelta(a, last)
                        last = a
                        val (steps, rest) = GlareshieldDrag.courseSteps(acc, d)
                        acc = rest
                        if (steps != 0) onStep(steps)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = GlareshieldDrag.headingText(value),
            color = if (powered) Color(0xFFF4F1E6) else Color(0xFF2A2C30),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
        )
    }
}

/**
 * HDG SEL push-button (HdgButton in GlareshieldPanel.tsx). A tap toggles the mode; dragging up or down sets the heading.
 * The lens is lit red when engaged and powered. Rules are in sim/GlareshieldDrag.kt. Not compiled in the sandbox.
 */
@Composable
fun HdgSelButton(
    hdg: Double,
    on: Boolean,
    powered: Boolean,
    onToggle: () -> Unit,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier.size(64.dp),
) {
    val lit = powered && on
    Box(
        modifier = modifier
            .background(Brush.verticalGradient(listOf(Color(0xFF34373B), Color(0xFF17191C))), RoundedCornerShape(4.dp))
            .border(2.dp, Color(0xFF050607), RoundedCornerShape(4.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var st = GlareshieldDrag.HdgDrag.start(down.position.y.toDouble())
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        if (!change.pressed) {
                            if (GlareshieldDrag.isTap(st)) onToggle()
                            break
                        }
                        val (next, steps) = GlareshieldDrag.hdgMove(st, change.position.y.toDouble())
                        st = next
                        if (steps != 0) onStep(steps)
                        change.consume()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 46.dp)
                .background(
                    if (lit) Brush.radialGradient(listOf(Color(0xFFFF5A4A), Color(0xFFB3140F)))
                    else Brush.radialGradient(listOf(Color(0xFF4A1210), Color(0xFF210807))),
                    RoundedCornerShape(3.dp),
                )
                .border(2.dp, Color(0xFF0A0303), RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = GlareshieldDrag.headingText(hdg),
                color = if (lit) Color.White else Color(0xFF8A3B36),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
        }
    }
}
