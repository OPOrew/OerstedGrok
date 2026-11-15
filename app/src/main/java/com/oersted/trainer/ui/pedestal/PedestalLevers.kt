package com.oersted.trainer.ui.pedestal

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.PedestalLeversRules as Rules

/*
 * Port of components/panel/PedestalLevers.tsx: the centre-pedestal FLAP lever and SPEED BRAKE lever, after the 737-200
 * photos. Both are dragged (or tapped on a detent) and snap to the nearest gate. The gate rules are in
 * sim/PedestalLeversRules.kt. Compose only; verified on CI, not compiled locally.
 * The web logs a note with each selection (flapNote / speedBrakeNote); the callers here get the value only.
 */

private val Cream = Color(0xFFCFC9B6)

/** Vertical placement: the child's centre sits [y] below the top of the parent (web `-translate-y-1/2` at `top: y`). */
internal fun Modifier.centeredAtY(y: Dp): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) { p.place(0, y.roundToPx() - p.height / 2) }
}

/**
 * web DetentGate: engraved scale plate with a notch at every gate, a toothed rail, and a travel slot with the handle.
 * [height] is the track height in dp; [index] the selected gate.
 */
@Composable
fun DetentGate(
    labels: List<String>,
    index: Int,
    onSelect: (Int) -> Unit,
    height: Int,
    modifier: Modifier = Modifier,
    plateWidth: Dp = 44.dp,
    labelSize: Int = 11,
    handle: @Composable () -> Unit,
) {
    val n = labels.size
    fun y(i: Int) = Rules.gateY(i, n, height.toDouble()).toFloat()
    val handleY by animateDpAsState(targetValue = y(index).dp, animationSpec = tween(200), label = "gate")
    Row(modifier.height(height.dp)) {
        // engraved scale plate
        Box(
            Modifier
                .width(plateWidth)
                .fillMaxHeight()
                .background(Color(0xFF0C0D10), RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp))
                .border(1.dp, Color(0xFF0A0B0D), RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp)),
        ) {
            labels.forEachIndexed { i, l ->
                Text(
                    text = l,
                    color = if (i == index) Color.White else Cream,
                    fontSize = labelSize.sp,
                    lineHeight = labelSize.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.025.em,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 8.dp)
                        .centeredAtY(y(i).dp)
                        .clickable { onSelect(i) },
                )
            }
            labels.forEachIndexed { i, _ ->
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = y(i).dp)
                        .size(width = 6.dp, height = 1.dp)
                        .background(Cream.copy(alpha = 0.7f)),
                )
            }
        }
        // toothed gate rail
        Canvas(Modifier.width(12.dp).fillMaxHeight()) {
            drawRect(Color(0xFF5A5F64), size = size)
            var t = 6.dp.toPx()
            while (t < size.height) {
                drawRect(Color(0x73000000), topLeft = Offset(0f, t), size = Size(size.width, 2.dp.toPx()))
                t += 8.dp.toPx()
            }
        }
        // travel slot with the handle
        Box(
            Modifier
                .width(96.dp)
                .fillMaxHeight()
                .background(Color(0xFF16181C), RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                .border(1.dp, Color(0xFF0A0B0D), RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                .pointerInput(index, n, height) {
                    fun pick(py: Float) {
                        Rules.pickGate((py / density).toDouble(), n, height.toDouble(), index)?.let { onSelect(it) }
                    }
                    detectTapGestures(onPress = { pick(it.y) })
                }
                .pointerInput(index, n, height) {
                    fun pick(py: Float) {
                        Rules.pickGate((py / density).toDouble(), n, height.toDouble(), index)?.let { onSelect(it) }
                    }
                    detectDragGestures(onDragStart = { pick(it.y) }, onDrag = { change, _ -> pick(change.position.y) })
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .fillMaxHeight()
                    .padding(vertical = 12.dp)
                    .width(8.dp)
                    .background(Color(0xFF050608), RoundedCornerShape(50)),
            )
            Box(Modifier.align(Alignment.TopStart).centeredAtY(handleY)) { handle() }
        }
    }
}

@Composable
private fun TitleLegend(text: String) {
    Text(
        text, color = Color(0xFFD7D0BD), fontSize = 8.sp, fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold, letterSpacing = 0.14.em,
    )
}

@Composable
private fun StatusChip(text: String, border: Color, fill: Color, textColor: Color) {
    val shape = RoundedCornerShape(4.dp)
    Box(Modifier.background(fill, shape).border(1.dp, border, shape).padding(horizontal = 6.dp, vertical = 4.dp)) {
        Text(text.uppercase(), color = textColor, fontSize = 7.sp, lineHeight = 7.sp, fontFamily = FontFamily.Monospace)
    }
}

private val GripBrush get() = Brush.verticalGradient(listOf(Color(0xFFF1ECD8), Color(0xFFD3CCB2)))

/** web FlapLeverQuadrant: cream FLAP grip in a gated quadrant, detents 0 1 2 5 10 15 25 30 40. */
@Composable
fun FlapLeverQuadrant(flaps: Double, flapPos: Double, onSelect: (Double) -> Unit, modifier: Modifier = Modifier) {
    val values = Rules.FLAP_VALUES
    val idx = Rules.flapIndex(flaps)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TitleLegend("FLAP")
        DetentGate(
            height = 300,
            labels = values.map { it.toInt().toString() },
            index = idx,
            onSelect = { i -> onSelect(values[i]) },
            handle = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 20.dp, height = 12.dp).background(Brush.verticalGradient(listOf(Color(0xFF8D9297), Color(0xFF4A4E53), Color(0xFF8D9297)))))
                    val shape = RoundedCornerShape(6.dp)
                    Box(
                        Modifier.size(width = 67.dp, height = 48.dp).background(GripBrush, shape).border(1.dp, Color(0xFF9B9577), shape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("FLAP", color = Color(0xFF6D6850), fontSize = 11.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.em)
                    }
                }
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip("LEVER ${Rules.flapLeverText(values[idx])}", Color(0xFF5C654E), Color(0xFF1A2018), Color(0xFFB9D18D))
            StatusChip("TE ${Rules.trailingEdgeText(flapPos)}", Color(0xFF3D4248), Color(0xFF111317), Color(0xFFBDB5A2))
        }
    }
}

/** web SpeedBrakeLever: metal arm with a cream grip; DOWN / ARMED / FLIGHT DETENT / UP. [speedBrake] is the sim's value. */
@Composable
fun SpeedBrakeLever(speedBrake: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val values = Rules.SPEED_BRAKE_VALUES
    val idx = Rules.speedBrakeIndex(speedBrake)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TitleLegend("SPEED BRAKE")
        DetentGate(
            height = 220,
            labels = Rules.SPEED_BRAKE_NAMES,
            index = idx,
            onSelect = { i -> onSelect(values[i]) },
            plateWidth = 56.dp,
            labelSize = 8,
            handle = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 28.dp, height = 10.dp).background(Brush.verticalGradient(listOf(Color(0xFFB9BDC0), Color(0xFF6C7074), Color(0xFFB9BDC0)))))
                    val shape = RoundedCornerShape(5.dp)
                    Box(
                        Modifier.size(width = 58.dp, height = 36.dp).background(GripBrush, shape).border(1.dp, Color(0xFF9B9577), shape).padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "SPEED\nBRAKE", color = Color(0xFF5D5942), fontSize = 8.sp, lineHeight = 9.sp, fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold, letterSpacing = 0.025.em, textAlign = TextAlign.Center,
                        )
                    }
                }
            },
        )
        val text = Rules.speedBrakeStatus(speedBrake)
        when (Rules.speedBrakeTone(speedBrake)) {
            Rules.StatusTone.OK -> StatusChip(text, Color(0x9938D27A), Color(0xFF10241A), Color(0xFF38D27A))
            Rules.StatusTone.NEUTRAL -> StatusChip(text, Color(0xFF3D4248), Color(0xFF111317), Color(0xFFBDB5A2))
            Rules.StatusTone.CAUTION -> StatusChip(text, Color(0x99E9A93A), Color(0xFF2A2210), Color(0xFFE9A93A))
        }
    }
}
