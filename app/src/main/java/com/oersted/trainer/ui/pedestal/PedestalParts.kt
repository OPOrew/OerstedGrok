package com.oersted.trainer.ui.pedestal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.Pedestal
import com.oersted.trainer.sim.PedestalPartsRules
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Port of components/panel/PedestalParts.tsx: the shared pedestal look (grey plates, black displays, cream legends)
 * and the controls built on it. The pure rules (CDU pages and keys, knob geometry) are in sim/PedestalPartsRules.kt.
 * Compose only; verified on CI, not compiled locally. CSS inset shadows and glows are left out.
 */

/** web PLATE colours */
val PlateColor = Color(0xFF6A6D71)
val PlateBorder = Color(0xFF2C2F33)

/** web LEGEND colour (cream) */
val LegendColor = Color(0xFFE6E1D2)

/** Cream legend text, the web's LEGEND class (mono 8px bold uppercase, tracking .1em). */
@Composable
fun PedestalLegend(text: String, modifier: Modifier = Modifier, size: Int = 8, color: Color = LegendColor, textAlign: TextAlign? = null) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        lineHeight = (size + 1).sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.1.em,
        textAlign = textAlign,
    )
}

/** web Screws: two or four dark screw heads in the corners of a plate. */
@Composable
fun BoxScope.Screws(n: Int = 4) {
    val head = Modifier.size(8.dp).background(Color(0xFF4D5054), CircleShape).border(1.dp, Color(0xFF2A2D31), CircleShape)
    Box(head.align(Alignment.TopStart).padding(1.dp))
    Box(head.align(Alignment.TopEnd).padding(1.dp))
    if (n == 4) {
        Box(head.align(Alignment.BottomStart).padding(1.dp))
        Box(head.align(Alignment.BottomEnd).padding(1.dp))
    }
}

/** web Plate: a grey pedestal plate with screws and an optional centred title. */
@Composable
fun Plate(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(3.dp)
    Box(modifier.background(PlateColor, shape).border(1.dp, PlateBorder, shape).padding(8.dp)) {
        Screws()
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (title != null) PedestalLegend(title, Modifier.padding(bottom = 4.dp), textAlign = TextAlign.Center)
            content()
        }
    }
}

/** web Readout: black segment-style numeric display; unlit it shows dim eights. */
@Composable
fun Readout(value: String, lit: Boolean = true, modifier: Modifier = Modifier) {
    Box(
        modifier.background(Color.Black, RoundedCornerShape(2.dp)).border(1.dp, Color(0xFF111111), RoundedCornerShape(2.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (lit) value else value.replace(Regex("[0-9]"), "8"),
            color = if (lit) Color(0xFFF1F1EA) else Color(0xFF1B1B1B),
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.05.em,
        )
    }
}

/** web TONES of Annun and Lamp */
enum class AnnunTone { AMBER, RED, GREEN, BLUE, WHITE }

private data class ToneColors(val border: Color, val fill: Color, val text: Color, val offText: Color)

private fun tone(t: AnnunTone) = when (t) {
    AnnunTone.AMBER -> ToneColors(Color(0xFFA8741A), Color(0xFFE9A93A), Color(0xFF2A1B00), Color(0xFFD9A22B))
    AnnunTone.RED -> ToneColors(Color(0xFF8C1C1C), Color(0xFFE03A3A), Color.White, Color(0xFFD83A3A))
    AnnunTone.GREEN -> ToneColors(Color(0xFF1D7A45), Color(0xFF38D27A), Color(0xFF03230F), Color(0xFF3AA66A))
    AnnunTone.BLUE -> ToneColors(Color(0xFF2C5DA8), Color(0xFF5B9BFF), Color(0xFF02112B), Color(0xFF5B8FE0))
    AnnunTone.WHITE -> ToneColors(Color(0xFFAAAAAA), Color(0xFFF4F1E4), Color(0xFF222222), Color(0xFFD8D4C4))
}

/** web Annun: annunciator light; with [onClick] it is a push-button light. */
@Composable
fun Annun(label: String, on: Boolean, tone: AnnunTone = AnnunTone.AMBER, modifier: Modifier = Modifier, small: Boolean = false, onClick: (() -> Unit)? = null) {
    val c = tone(tone)
    val shape = RoundedCornerShape(2.dp)
    val base = modifier
        .height(if (small) 16.dp else 20.dp)
        .background(if (on) c.fill else Color(0xFF0B0C0D), shape)
        .border(1.dp, if (on) c.border else Color(0xFF55585C), shape)
        .let { if (onClick != null) it.clickable { onClick() } else it }
        .padding(horizontal = 4.dp)
    Box(base, contentAlignment = Alignment.Center) {
        Text(
            text = label.uppercase(),
            color = if (on) c.text else c.offText,
            fontSize = (if (small) 6 else 7).sp,
            lineHeight = (if (small) 6.5f else 7.5f).sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

/** web Lamp: round indicator lamp (green extinguisher-test lights, XPNDR FAULT, audio-panel LEDs). */
@Composable
fun Lamp(on: Boolean, tone: AnnunTone = AnnunTone.GREEN, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    val lit = when (tone) {
        AnnunTone.AMBER -> Color(0xFFF2B230)
        AnnunTone.RED -> Color(0xFFFF3B3B)
        else -> Color(0xFF38D27A)
    }
    Box(modifier.size(size).background(if (on) lit else Color(0xFF1F3A2A), CircleShape).border(1.dp, Color(0xFF0A0B0C), CircleShape))
}

/** web PushButton: momentary button with a legend. [onPress] on touch down, [onRelease] on lift or cancel. */
@Composable
fun PushButton(label: String, modifier: Modifier = Modifier, active: Boolean = false, onRelease: (() -> Unit)? = null, onPress: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier
            .background(Brush.verticalGradient(listOf(Color(0xFF3D4146), Color(0xFF1D2024))), shape)
            .border(1.dp, if (active) Color(0xFF88AAAA) else Color(0xFF15171A), shape)
            .pointerInput(onPress, onRelease) {
                detectTapGestures(onPress = {
                    onPress?.invoke()
                    tryAwaitRelease()
                    onRelease?.invoke()
                })
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        PedestalLegend(label, color = if (active) Color.White else LegendColor, textAlign = TextAlign.Center)
    }
}

private val LegendLit = Color.White

/** web Toggle: two-position latched bat-handle toggle, the active legend lights up. */
@Composable
fun Toggle(
    on: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    upLabel: String? = null,
    downLabel: String? = null,
    guarded: Boolean = false,
    disabled: Boolean = false,
) {
    @Composable
    fun leg(txt: String?, active: Boolean, target: Boolean) {
        if (txt != null) {
            PedestalLegend(
                txt, size = 6,
                color = if (active) LegendLit else LegendColor.copy(alpha = 0.45f),
                modifier = Modifier.clickable(enabled = !disabled) { if (on != target) onChange(target) },
            )
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        leg(upLabel, on, true)
        val shape = RoundedCornerShape(3.dp)
        Box(
            Modifier
                .size(width = 28.dp, height = 48.dp)
                .background(Color(0xFF14161A), shape)
                .border(1.dp, if (guarded) Color(0xFFC03A2A) else Color(0xFF15171A), shape)
                .clickable(enabled = !disabled) { onChange(!on) },
        ) {
            Canvas(Modifier.size(width = 28.dp, height = 48.dp)) {
                val c = Offset(size.width / 2f, size.height / 2f)
                // the lever leans fully down (off) or fully up (on) about the collar
                val len = 19.dp.toPx() * (if (on) -1f else 1f)
                drawLine(Color(0xFFC4C7CA), c, Offset(c.x, c.y + len), strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFB9BDC2), Color(0xFF4A4E53)), center = c, radius = 6.dp.toPx()), radius = 5.5.dp.toPx(), center = c)
            }
        }
        leg(downLabel, !on, false)
        if (label != null) PedestalLegend(label, size = 6)
    }
}

/** web SpringSwitch: held UP or DOWN while pressed, returns to centre on release. [value] is "UP", "MID" or "DOWN". */
@Composable
fun SpringSwitch(up: String, down: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, label: String? = null) {
    fun Modifier.hold(v: String) = pointerInput(v) {
        detectTapGestures(onPress = {
            onChange(v)
            tryAwaitRelease()
            onChange("MID")
        })
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PedestalLegend(up, size = 6, color = if (value == "UP") LegendLit else LegendColor, modifier = Modifier.hold("UP").padding(horizontal = 4.dp))
        val shape = RoundedCornerShape(3.dp)
        Box(Modifier.size(width = 24.dp, height = 44.dp).background(Color(0xFF14161A), shape).border(1.dp, Color(0xFF15171A), shape)) {
            // web: top 3px (UP), 13px (MID), bottom 3px (DOWN); the handle is 18px tall
            val y = when (value) { "UP" -> 3.dp; "DOWN" -> 44.dp - 3.dp - 18.dp - 2.dp; else -> 13.dp }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = y)
                    .size(width = 10.dp, height = 18.dp)
                    .background(Brush.horizontalGradient(listOf(Color(0xFF8A8E93), Color(0xFF3B3F44), Color(0xFF8A8E93))), RoundedCornerShape(2.dp))
                    .border(1.dp, Color(0xFF050607), RoundedCornerShape(2.dp)),
            )
        }
        PedestalLegend(down, size = 6, color = if (value == "DOWN") LegendLit else LegendColor, modifier = Modifier.hold("DOWN").padding(horizontal = 4.dp))
        if (label != null) PedestalLegend(label, size = 6, color = LegendColor.copy(alpha = 0.8f))
    }
}

/**
 * Places the child around the centre of its parent box: [dx], [dy] from the centre, then shifted by the fraction of the
 * child's own size ([fx], [fy]: 0 keeps the left/top edge there, -0.5 centres it, -1 puts the right/bottom edge there).
 * The web does this with left/top calc() and translate(); the parent must be a Box with the child centred.
 */
internal fun Modifier.anchoredAt(dx: Dp, dy: Dp, fx: Float, fy: Float): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) {
        p.place(
            (dx.roundToPx() + fx * p.width + p.width / 2f).roundToInt(),
            (dy.roundToPx() + fy * p.height + p.height / 2f).roundToInt(),
        )
    }
}

/** web `x > 0.3 ? 0 : x < -0.3 ? -1 : -0.5` horizontal anchor of a legend at pointer direction x */
private fun anchorX(x: Double): Float = if (x > 0.3) 0f else if (x < -0.3) -1f else -0.5f

private fun nudge(x: Double): Float = if (x > 0.3) 2f else if (x < -0.3) -2f else 0f

/** web Selector3: latched selector drawn as a pointer dial with each option printed around it at its own detent. */
@Composable
fun Selector3(options: List<String>, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, label: String? = null) {
    val n = options.size
    val idx = maxOf(0, options.indexOf(value))
    val dial = 36.dp
    val radius = dial.value / 2f + 8f
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.size(width = dial + 54.dp, height = dial + 26.dp), contentAlignment = Alignment.Center) {
            options.forEachIndexed { i, o ->
                val a = PedestalPartsRules.selectorAngle(i, n) * PI / 180.0
                val x = sin(a)
                val y = -cos(a)
                PedestalLegend(
                    o, size = 6,
                    color = if (i == idx) LegendLit else LegendColor.copy(alpha = 0.5f),
                    modifier = Modifier
                        .anchoredAt((radius * x + nudge(x)).dp, (radius * y + 3f).dp, anchorX(x), -0.5f)
                        .clickable { onChange(o) },
                )
            }
            Box(
                Modifier
                    .offset(y = 3.dp)
                    .size(dial)
                    .background(Brush.radialGradient(listOf(Color(0xFF33373B), Color(0xFF0F1114))), CircleShape)
                    .border(1.dp, Color(0xFF15171A), CircleShape)
                    .clickable { if (n > 0) onChange(options[(idx + 1) % n]) },
            ) {
                Canvas(Modifier.size(dial)) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val a = PedestalPartsRules.selectorAngle(idx, n) * PI / 180.0
                    val len = 18.dp.toPx()
                    drawLine(Color(0xFFD8DADC), c, Offset(c.x + (sin(a) * len).toFloat(), c.y - (cos(a) * len).toFloat()), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
        if (label != null) PedestalLegend(label, size = 6)
    }
}

/**
 * web Knob: absolute rotary knob (-135..135 degrees mapped onto min..max). Legends in [ticks] are printed around the
 * knob at their detents (first at -135, last at +135) with a tick mark each; the one the pointer is on lights up.
 */
@Composable
fun Knob(
    value: Double,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    min: Double = 0.0,
    max: Double = 1.0,
    step: Double = 0.0,
    size: Dp = 40.dp,
    ticks: List<String> = emptyList(),
    compact: Boolean = false,
) {
    val angle = PedestalPartsRules.knobAngle(value, min, max)
    val n = ticks.size
    val activeIdx = PedestalPartsRules.activeTick(n, angle)
    val radius = size.value / 2f + 9f
    val boxW = size + (if (compact) 30.dp else 56.dp)
    val boxH = size + 26.dp

    @Composable
    fun dial() {
        Box(
            Modifier
                .size(size)
                .background(Brush.radialGradient(listOf(Color(0xFFE8E8E8), Color(0xFFA9ABAE), Color(0xFF6C6E72))), CircleShape)
                .border(1.dp, Color(0xFF8B8E92), CircleShape)
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
                drawLine(Color(0xFF1A1C1F), c, Offset(c.x + (sin(a) * len).toFloat(), c.y - (cos(a) * len).toFloat()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (n > 0) {
            Box(Modifier.size(width = boxW, height = boxH), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(width = boxW, height = boxH)) {
                    val c = Offset(this.size.width / 2f, this.size.height / 2f)
                    for (i in 0 until n) {
                        val a = PedestalPartsRules.tickAngle(i, n) * PI / 180.0
                        val dx = sin(a).toFloat()
                        val dy = -cos(a).toFloat()
                        val r0 = (size.toPx() / 2f) + 1.dp.toPx()
                        val r1 = r0 + 5.dp.toPx()
                        drawLine(
                            if (i == activeIdx) Color.White else LegendColor.copy(alpha = 0.6f),
                            Offset(c.x + dx * r0, c.y + dy * r0), Offset(c.x + dx * r1, c.y + dy * r1), strokeWidth = 1.dp.toPx(),
                        )
                    }
                }
                ticks.forEachIndexed { i, t ->
                    val a = PedestalPartsRules.tickAngle(i, n) * PI / 180.0
                    val x = sin(a)
                    val y = -cos(a)
                    PedestalLegend(
                        t, size = 6,
                        color = if (i == activeIdx) LegendLit else LegendColor.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.anchoredAt((radius * x + nudge(x)).dp, (radius * y).dp, anchorX(x), -0.5f),
                    )
                }
                dial()
            }
        } else {
            dial()
        }
        if (label != null) PedestalLegend(label, size = 6)
    }
}

/** web TuneKnob: endless tuning knob; every ~20 degrees of rotation emits one +1 / -1 step. */
@Composable
fun TuneKnob(onStep: (Int) -> Unit, modifier: Modifier = Modifier, label: String? = null, size: Dp = 40.dp) {
    var rot by remember { mutableStateOf(0f) }
    val state = remember { doubleArrayOf(Double.NaN, 0.0) } // [last pointer angle, accumulator]
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier
                .size(size)
                .background(Brush.radialGradient(listOf(Color(0xFFE8E8E8), Color(0xFFA9ABAE), Color(0xFF6C6E72))), CircleShape)
                .border(1.dp, Color(0xFF8B8E92), CircleShape)
                .pointerInput(Unit) {
                    fun move(p: Offset) {
                        val a = PedestalPartsRules.angleFrom(this.size.width / 2.0, this.size.height / 2.0, p.x.toDouble(), p.y.toDouble())
                        if (state[0].isNaN()) { state[0] = a; return }
                        val d = PedestalPartsRules.tuneDelta(state[0], a)
                        state[0] = a
                        rot += d.toFloat()
                        val r = PedestalPartsRules.tuneAccumulate(state[1], d)
                        state[1] = r.acc
                        r.steps.forEach { onStep(it) }
                    }
                    detectDragGestures(
                        onDragStart = { state[0] = Double.NaN; state[1] = 0.0; move(it) },
                        onDragEnd = { state[0] = Double.NaN },
                        onDragCancel = { state[0] = Double.NaN },
                        onDrag = { change, _ -> move(change.position) },
                    )
                },
        ) {
            Canvas(Modifier.size(size).rotate(rot)) {
                val c = Offset(this.size.width / 2f, this.size.height / 2f)
                drawLine(Color(0xFF1A1C1F), Offset(c.x, 3.dp.toPx()), Offset(c.x, 3.dp.toPx() + this.size.height * 0.35f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                for (deg in listOf(0, 90, 180, 270)) {
                    val a = deg * PI / 180.0
                    val r = this.size.height * 0.28f
                    drawCircle(Color(0x8055585C), radius = 3.dp.toPx(), center = Offset(c.x + (sin(a) * r).toFloat(), c.y - (cos(a) * r).toFloat()))
                }
            }
        }
        if (label != null) PedestalLegend(label, size = 6)
    }
}

/**
 * web Cdu: the FMC control display unit. State lives in [Pedestal.CduSide] (the pedestal store); [update] applies a pure
 * change to it, as the web's setCdu. The key handling is in PedestalPartsRules.
 */
@Composable
fun Cdu(state: Pedestal.CduSide, power: Boolean, update: ((Pedestal.CduSide) -> Pedestal.CduSide) -> Unit, modifier: Modifier = Modifier, left: Boolean = true) {
    val R = PedestalPartsRules
    val page = R.pageOf(state)

    @Composable
    fun LskColumn(sd: Char) {
        Column(Modifier.width(12.dp).height(132.dp), verticalArrangement = Arrangement.SpaceAround) {
            for (r in 1..6) {
                val fld = page.fields.firstOrNull { it.side == sd && it.row == r }
                Box(
                    Modifier.fillMaxWidth().height(10.dp)
                        .background(Color(0xFF2B2E32), RoundedCornerShape(1.dp))
                        .border(1.dp, Color(0xFF222222), RoundedCornerShape(1.dp))
                        .clickable { if (fld != null) update { s -> R.lsk(s, power, fld) } },
                )
            }
        }
    }

    Plate(modifier = modifier.width(250.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LskColumn('L')
            Box(
                Modifier.weight(1f).height(132.dp)
                    .background(if (power) Color.Black else Color(0xFF07090A), RoundedCornerShape(3.dp))
                    .border(2.dp, Color(0xFF16181B), RoundedCornerShape(3.dp))
                    .padding(4.dp),
            ) {
                if (power) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Box(Modifier.size(1.dp))
                        CduText(page.title, Color.White)
                        CduText(page.pg ?: "", Color(0xFFD8FFE0))
                    }
                    for (r in 1..6) {
                        Row(Modifier.fillMaxWidth().offset(y = (8 + (r - 1) * 18).dp), verticalAlignment = Alignment.Bottom) {
                            for (sd in listOf('L', 'C', 'R')) {
                                val fld = page.fields.firstOrNull { it.side == sd && it.row == r }
                                val typedValue = fld != null && state.fields.containsKey("${state.page}:${fld.id}")
                                Column(
                                    Modifier.weight(1f),
                                    horizontalAlignment = when (sd) { 'R' -> Alignment.End; 'C' -> Alignment.CenterHorizontally; else -> Alignment.Start },
                                ) {
                                    CduText(fld?.label ?: "", Color(0xFF9EC8A8), 6)
                                    CduText(if (fld != null) R.fieldVal(state, fld) else "", if (typedValue) Color(0xFF77FFFF) else Color.White)
                                }
                            }
                        }
                    }
                    Box(Modifier.align(Alignment.BottomStart)) {
                        CduText(state.msg.ifEmpty { state.pad.ifEmpty { "□□□□□□□□□□□□" } }, Color.White)
                    }
                }
            }
            LskColumn('R')
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (row in R.FUNCS) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (fk in row) {
                            CduKey(R.FKEY_LABEL[fk] ?: fk, dark = true, lit = state.page == fk, modifier = Modifier.weight(1f)) {
                                update { s -> R.funcKey(s, power, fk) }
                            }
                        }
                        if (row.size < 5) {
                            // web: EXEC spans the last two columns of the final row
                            CduKey("EXEC", dark = true, lit = false, modifier = Modifier.weight((5 - row.size).toFloat())) {
                                update { s -> R.exec(s, power) }
                            }
                        }
                    }
                }
            }
            Column(Modifier.width(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.size(12.dp).background(if (power && (state.msg.isNotEmpty() || state.exec)) Color(0xFFF4EFD8) else Color(0xFF2E3033), CircleShape))
                PedestalLegend("MSG", size = 5)
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (row in R.KEYROWS) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (k in row) CduKey(k, dark = false, lit = false, modifier = Modifier.weight(1f)) { update { s -> R.key(s, power, k) } }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (k in listOf("1", "2", "3", "4", "5", "6")) CduKey(k, dark = false, lit = false, modifier = Modifier.weight(1f)) { update { s -> R.key(s, power, k) } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (k in listOf("7", "8", "9", "0", ".")) CduKey(k, dark = false, lit = false, modifier = Modifier.weight(1f)) { update { s -> R.key(s, power, k) } }
                CduKey("+/-", dark = false, lit = false, modifier = Modifier.weight(1f)) { update { s -> R.plusMinus(s, power) } }
            }
        }
    }
}

@Composable
private fun CduText(text: String, color: Color, size: Int = 8) {
    Text(text, color = color, fontSize = size.sp, lineHeight = (size + 2).sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
}

@Composable
private fun CduKey(label: String, dark: Boolean, lit: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(2.dp)
    val fill = if (dark) Brush.verticalGradient(listOf(Color(0xFF46494D), Color(0xFF25272A))) else Brush.verticalGradient(listOf(Color(0xFFD9D7CF), Color(0xFF9B9990)))
    Box(
        modifier.height(17.dp).background(fill, shape).border(1.dp, Color(0xFF1A1C1F), shape).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (dark) (if (lit) Color.White else LegendColor) else Color(0xFF222222),
            fontSize = (if (dark) 6 else 8).sp,
            lineHeight = (if (dark) 7 else 9).sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}
