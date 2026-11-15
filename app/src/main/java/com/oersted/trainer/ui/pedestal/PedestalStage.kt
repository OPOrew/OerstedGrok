package com.oersted.trainer.ui.pedestal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.PedestalPartsRules
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.gestures.detectDragGestures

/*
 * Local helpers of components/panel/CenterPedestal.tsx (lines 1-267): the stage coordinates, paint, and the small
 * controls the pedestal units are built from. The photograph's own pixel coordinates are kept (x minus OX, y as is),
 * as in the web, so each unit sits where it does on the real pedestal. Compose only; verified on CI.
 */

/** web OX: photo x of the stage's left edge */
const val PEDESTAL_OX = 175

/** web STAGE_W, STAGE_H */
const val PEDESTAL_STAGE_W = 700
const val PEDESTAL_STAGE_H = 1642

/** web `Box`: places a child at the photo coordinates ([x] minus OX, [y]) with width [w] and optional height [h], inside the stage Box. */
fun Modifier.stageAt(x: Int, y: Int, w: Int, h: Int? = null): Modifier =
    offset((x - PEDESTAL_OX).dp, y.dp).let { m -> if (h != null) m.size(w.dp, h.dp) else m.width(w.dp) }

/** web PL: the painted grey-green panel */
val PanelPaint: Brush get() = Brush.linearGradient(listOf(Color(0xFF6B7871), Color(0xFF58655F), Color(0xFF4B5752)))

/** web PL_DARK */
val PanelDarkPaint: Brush get() = Brush.linearGradient(listOf(Color(0xFF323941), Color(0xFF1D2126)))

/** web CREAM */
val CreamPaint: Brush get() = Brush.verticalGradient(listOf(Color(0xFFF4EEDC), Color(0xFFCFC8AC)))

/** A painted panel: the web PL class (border #1c2220). */
@Composable
fun PaintedPanel(modifier: Modifier = Modifier, dark: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier.background(if (dark) PanelDarkPaint else PanelPaint, shape).border(1.dp, if (dark) Color(0xFF101316) else Color(0xFF1C2220), shape),
        content = content,
    )
}

/** web Rivets: four copper rivets in the corners. */
@Composable
fun BoxScope.Rivets(inset: Dp = 3.dp) {
    val rivet = Modifier.size(6.dp).background(Brush.radialGradient(listOf(Color(0xFFDC9150), Color(0xFF7A3F14))), CircleShape).border(1.dp, Color(0xFF3A1F0A), CircleShape)
    Box(Modifier.align(Alignment.TopStart).padding(start = inset, top = inset).then(rivet))
    Box(Modifier.align(Alignment.TopEnd).padding(end = inset, top = inset).then(rivet))
    Box(Modifier.align(Alignment.BottomStart).padding(start = inset, bottom = inset).then(rivet))
    Box(Modifier.align(Alignment.BottomEnd).padding(end = inset, bottom = inset).then(rivet))
}

/** web Chips: chipped lime paint blobs, as on the real panels. */
@Composable
fun BoxScope.Chips() {
    Canvas(Modifier.matchParentSize()) {
        val c = Color(0x80C4D280)
        val w = size.width
        val h = size.height
        drawCircle(c, 6.dp.toPx(), Offset(w * 0.12f, h * 0.18f))
        drawCircle(c, 5.dp.toPx(), Offset(w * 0.84f, h * 0.72f))
        drawCircle(c, 3.dp.toPx(), Offset(w * 0.38f, h * 0.90f))
        drawCircle(c, 3.dp.toPx(), Offset(w * 0.93f, h * 0.10f))
        drawCircle(Color(0x2E967850), 9.dp.toPx(), Offset(w * 0.60f, h * 0.30f))
    }
}

/** web Tiny: tiny engraved legend. */
@Composable
fun Tiny(text: String, modifier: Modifier = Modifier, dim: Boolean = false) {
    PedestalLegend(text, modifier, size = 6, color = LegendColor.copy(alpha = if (dim) 0.5f else 1f))
}

/** web RepeatButton: fires [onTick] on press and then every [ms] while held. */
@Composable
fun RepeatButton(label: String, onTick: () -> Unit, modifier: Modifier = Modifier, ms: Long = 70) {
    val cb by rememberUpdatedState(onTick)
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier
            .background(Brush.verticalGradient(listOf(Color(0xFF3D4146), Color(0xFF1D2024))), shape)
            .border(1.dp, Color(0xFF15171A), shape)
            .pointerInput(ms) {
                detectTapGestures(onPress = {
                    cb()
                    val job = scope.launch {
                        while (true) {
                            delay(ms)
                            cb()
                        }
                    }
                    try {
                        tryAwaitRelease()
                    } finally {
                        job.cancel()
                    }
                })
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        PedestalLegend(label, textAlign = TextAlign.Center)
    }
}

/** web Bat: small bat-handle toggle (latched, 2 positions). */
@Composable
fun Bat(on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, up: String? = null, down: String? = null, tone: Color = Color(0xFFE2E4E6)) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        if (up != null) Tiny(up, dim = !on)
        val shape = RoundedCornerShape(2.dp)
        Box(Modifier.size(width = 17.dp, height = 30.dp).background(Color(0xFF14161A), shape).border(1.dp, Color(0xFF101316), shape).clickable { onChange(!on) }) {
            Canvas(Modifier.size(width = 17.dp, height = 30.dp)) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val len = 13.dp.toPx() * (if (on) -1f else 1f)
                drawLine(tone, c, Offset(c.x, c.y + len), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFB9BDC2), Color(0xFF4A4E53)), center = c, radius = 4.dp.toPx()), radius = 3.5.dp.toPx(), center = c)
            }
        }
        if (down != null) Tiny(down, dim = on)
    }
}

/** web Bat3: small 3-position tilt toggle (cycles through the options). */
@Composable
fun Bat3(options: List<String>, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, label: String? = null, labels: List<String>? = null) {
    val i = maxOf(0, options.indexOf(value))
    val rot = PedestalPartsRules.bat3Rotation(options, value)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(Modifier.width(60.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            options.forEachIndexed { k, o ->
                Tiny(labels?.getOrNull(k) ?: PedestalPartsRules.bat3Legend(o), Modifier.clickable { onChange(o) }, dim = k != i)
            }
        }
        val shape = RoundedCornerShape(2.dp)
        Box(Modifier.size(width = 34.dp, height = 24.dp).background(Color(0xFF14161A), shape).border(1.dp, Color(0xFF101316), shape).clickable { onChange(options[(i + 1) % options.size]) }) {
            Canvas(Modifier.size(width = 34.dp, height = 24.dp)) {
                val pivot = Offset(size.width / 2f, 3.dp.toPx() + 3.5.dp.toPx())
                val a = rot * PI / 180.0
                val len = 15.dp.toPx()
                // the lever hangs from the pivot and swings by [rot] degrees
                drawLine(Color(0xFFE2E4E6), pivot, Offset(pivot.x - (sin(a) * len).toFloat(), pivot.y + (cos(a) * len).toFloat()), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFB9BDC2), Color(0xFF4A4E53)), center = pivot, radius = 4.dp.toPx()), radius = 3.5.dp.toPx(), center = pivot)
            }
        }
        if (label != null) Tiny(label)
    }
}

/** web BlackKnob: black ribbed rotary knob with a white pointer, -135..135 degrees, optional printed legends. */
@Composable
fun BlackKnob(
    size: Dp,
    value: Double,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    min: Double = 0.0,
    max: Double = 1.0,
    step: Double = 0.0,
    ticks: List<String> = emptyList(),
    onDouble: (() -> Unit)? = null,
) {
    val angle = PedestalPartsRules.knobAngle(value, min, max)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .background(Brush.radialGradient(listOf(Color(0xFF4B4E53), Color(0xFF1B1D20), Color(0xFF0A0B0C))), CircleShape)
                .border(2.dp, Color(0xFF050607), CircleShape)
                .pointerInput(min, max, step) {
                    fun set(p: Offset) {
                        val a = PedestalPartsRules.angleFrom(this.size.width / 2.0, this.size.height / 2.0, p.x.toDouble(), p.y.toDouble())
                        onChange(PedestalPartsRules.knobValue(a, min, max, step))
                    }
                    detectTapGestures(onDoubleTap = { onDouble?.invoke() }, onPress = { set(it) })
                }
                .pointerInput(min, max, step) {
                    fun set(p: Offset) {
                        val a = PedestalPartsRules.angleFrom(this.size.width / 2.0, this.size.height / 2.0, p.x.toDouble(), p.y.toDouble())
                        onChange(PedestalPartsRules.knobValue(a, min, max, step))
                    }
                    detectDragGestures(onDragStart = { set(it) }, onDrag = { change, _ -> set(change.position) })
                },
        ) {
            // dished centre and the white pointer dot
            Canvas(Modifier.size(size)) {
                val c = Offset(this.size.width / 2f, this.size.height / 2f)
                val r = this.size.width / 2f
                drawCircle(Brush.radialGradient(listOf(Color(0xFF3A3D41), Color(0xFF16181B)), center = c, radius = r * 0.8f), radius = r * 0.8f, center = c)
                val a = angle * PI / 180.0
                val d = r - 5.dp.toPx() - 3.5.dp.toPx()
                drawCircle(Color(0xFFECE7D6), radius = 3.5.dp.toPx(), center = Offset(c.x + (sin(a) * d).toFloat(), c.y - (cos(a) * d).toFloat()))
            }
        }
        val n = ticks.size
        ticks.forEachIndexed { i, t ->
            val g = (-135.0 + (i.toDouble() / maxOf(1, n - 1)) * 270.0) * PI / 180.0
            val r = size.value / 2f + 10f
            Tiny(t, Modifier.anchoredAt((r * sin(g)).dp, (-r * cos(g)).dp, -0.5f, -0.5f))
        }
    }
}

/** web Lbl: black engraved legend plate. */
@Composable
fun Lbl(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier.background(Color(0xFF0B0C0E), shape).border(1.dp, Color(0xFF2B2E32), shape).padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), color = Color(0xFFD4D0C0), fontSize = 6.sp, lineHeight = 7.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

/** web Lens: black-glass annunciator lens, engraved text when dark, lit colour when on (amber, red or green). */
@Composable
fun Lens(label: String, on: Boolean, modifier: Modifier = Modifier, tone: AnnunTone = AnnunTone.AMBER) {
    val shape = RoundedCornerShape(2.dp)
    val lit = when (tone) {
        AnnunTone.RED -> Triple(Color(0xFFE03A3A), Color(0xFF8C1C1C), Color.White)
        AnnunTone.GREEN -> Triple(Color(0xFF38D27A), Color(0xFF1D7A45), Color(0xFF03230F))
        else -> Triple(Color(0xFFE9A93A), Color(0xFFA8741A), Color(0xFF2A1B00))
    }
    Box(
        modifier
            .background(if (on) SolidFill(lit.first) else Brush.verticalGradient(listOf(Color(0xFF16181B), Color(0xFF08090A))), shape)
            .border(1.dp, if (on) lit.second else Color(0xFF2A2D31), shape)
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.uppercase(), color = if (on) lit.third else Color(0xFF6A6F75), fontSize = 6.sp, lineHeight = 7.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

private fun SolidFill(c: Color): Brush = Brush.verticalGradient(listOf(c, c))

/** web DrumWin: rolling drum counter window (white digits on black); dashes when unlit. */
@Composable
fun DrumWin(value: String, lit: Boolean, modifier: Modifier = Modifier, w: Dp = 72.dp) {
    val shape = RoundedCornerShape(2.dp)
    Box(modifier.size(width = w, height = 24.dp).background(Color.Black, shape).border(2.dp, Color(0xFF0A0B0C), shape), contentAlignment = Alignment.Center) {
        Text(if (lit) value else "-----", color = Color(0xFFF1F1EA), fontSize = 15.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x99000000), 0.3f to Color.Transparent, 0.7f to Color.Transparent, 1f to Color(0x99000000))))
    }
}

/** web DualTune: concentric tuning knob, an outer ring and an inner knob, each emitting steps. */
@Composable
fun DualTune(onOuter: (Int) -> Unit, onInner: (Int) -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp, label: String? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            TuneKnob(onStep = onOuter, size = size)
            TuneKnob(onStep = onInner, size = (size.value * 0.52f).toInt().dp)
        }
        if (label != null) Tiny(label)
    }
}

/** web TestLamp: push-to-test lamp (black bezel, green when pressed and powered). */
@Composable
fun TestLamp(pw: Boolean, modifier: Modifier = Modifier, label: String? = null) {
    var on by remember { mutableStateOf(false) }
    val lit = pw && on
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            Modifier
                .size(13.dp)
                .background(if (lit) Color(0xFF38D27A) else Color(0xFF0D0F10), CircleShape)
                .border(2.dp, Color(0xFF050607), CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        on = true
                        try {
                            tryAwaitRelease()
                        } finally {
                            on = false
                        }
                    })
                },
        )
        if (label != null) Tiny(label)
    }
}

/** web MiniKnob: silver knob with a pointer, -135..135 degrees. */
@Composable
fun MiniKnob(value: Double, onChange: (Double) -> Unit, modifier: Modifier = Modifier, size: Dp = 26.dp, min: Double = 0.0, max: Double = 1.0, step: Double = 0.0) {
    val angle = PedestalPartsRules.knobAngle(value, min, max)
    Box(
        modifier
            .size(size)
            .background(Brush.radialGradient(listOf(Color(0xFFECECEC), Color(0xFFA9ABAE), Color(0xFF6C6E72))), CircleShape)
            .border(1.dp, Color(0xFF6C6E72), CircleShape)
            .pointerInput(min, max, step) {
                fun set(p: Offset) {
                    val a = PedestalPartsRules.angleFrom(this.size.width / 2.0, this.size.height / 2.0, p.x.toDouble(), p.y.toDouble())
                    onChange(PedestalPartsRules.knobValue(a, min, max, step))
                }
                detectTapGestures(onPress = { set(it) })
            }
            .pointerInput(min, max, step) {
                fun set(p: Offset) {
                    val a = PedestalPartsRules.angleFrom(this.size.width / 2.0, this.size.height / 2.0, p.x.toDouble(), p.y.toDouble())
                    onChange(PedestalPartsRules.knobValue(a, min, max, step))
                }
                detectDragGestures(onDragStart = { set(it) }, onDrag = { change, _ -> set(change.position) })
            },
    ) {
        Canvas(Modifier.size(size)) {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val a = angle * PI / 180.0
            val len = this.size.height * 0.42f
            drawLine(Color(0xFF1A1C1F), c, Offset(c.x + (sin(a) * len).toFloat(), c.y - (cos(a) * len).toFloat()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/** web UnitGrid: three-column radio face, [left half] [centre column of width [midW]] [right half]. */
@Composable
fun UnitGrid(
    left: @Composable ColumnScope.() -> Unit,
    mid: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    midW: Dp = 46.dp,
) {
    Row(modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 9.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp), content = left)
        Column(Modifier.width(midW).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween, content = mid)
        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp), content = right)
    }
}
