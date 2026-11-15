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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.PedestalLeversRules
import com.oersted.trainer.sim.PedestalPartsRules
import com.oersted.trainer.sim.PedestalStandRules as Rules
import com.oersted.trainer.ui.cockpit.CockpitModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/*
 * Port of the CONTROL STAND of components/panel/CenterPedestal.tsx (lines 475-852): reverse and thrust levers with the
 * A/T DISENGAGE switches, start levers, the flap quadrant and speed brake lever, stabilizer trim wheels and scales, the
 * parking brake handle, the horn cutout and the park brake / stab trim lights. The rules are in sim/PedestalStandRules.kt.
 *
 * Every drag is read in a stationary container (never on the moving handle), so the pointer deltas stay in one frame.
 * Positions are design px at 1 px = 1 dp. Compose only; verified on CI.
 */

private val Steel: Brush get() = Brush.horizontalGradient(listOf(Color(0xFF7D848B), Color(0xFFD0D4D8), Color(0xFF7D848B)))
private val Gold: Brush get() = Brush.horizontalGradient(listOf(Color(0xFFB98A2A), Color(0xFFF0C050), Color(0xFFB98A2A)))
private val SteelV: Brush get() = Brush.verticalGradient(listOf(Color(0xFFC9CED2), Color(0xFF5C6064), Color(0xFFC9CED2)))
private val CreamCard: Brush get() = Brush.verticalGradient(listOf(Color(0xFFE9E5CF), Color(0xFFBDB89C)))

/** web clip-path of the bone-shaped thrust lever handle */
private val BoneShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(0f, h * 0.06f)
    lineTo(w * 0.16f, 0f)
    lineTo(w * 0.34f, h * 0.16f)
    lineTo(w * 0.66f, h * 0.16f)
    lineTo(w * 0.84f, 0f)
    lineTo(w, h * 0.06f)
    lineTo(w, h * 0.94f)
    lineTo(w * 0.84f, h)
    lineTo(w * 0.66f, h * 0.84f)
    lineTo(w * 0.34f, h * 0.84f)
    lineTo(w * 0.16f, h)
    lineTo(0f, h * 0.94f)
    close()
}

/** A drag or tap in design px from the top of this (stationary) box: the pointer y at touch down and while it moves. */
private fun Modifier.pickY(key: Any?, onPick: (Double) -> Unit): Modifier = this
    .pointerInput(key) { detectTapGestures(onPress = { onPick(it.y / density.toDouble()) }) }
    .pointerInput(key) { detectDragGestures(onDragStart = { onPick(it.y / density.toDouble()) }, onDrag = { change, _ -> onPick(change.position.y / density.toDouble()) }) }

/** web Detent2: two-position lever in a vertical slot (drag or tap); [value] true is the upper position. */
@Composable
private fun Detent2(value: Boolean, onChange: (Boolean) -> Unit, w: Int, h: Int, hw: Int, hh: Int, handle: @Composable () -> Unit) {
    val top by animateDpAsState(Rules.detent2HandleTop(value, h.toDouble(), hh.toDouble()).dp, tween(150), label = "detent2")
    val shape = RoundedCornerShape(3.dp)
    Box(
        Modifier
            .size(w.dp, h.dp)
            .background(Color(0xFF0B0C0E), shape)
            .border(1.dp, Color(0xFF050607), shape)
            .pointerInput(value) { detectTapGestures(onTap = { onChange(!value) }) }
            .pointerInput(value) {
                detectDragGestures { change, _ -> Rules.detent2Pick(change.position.y / density.toDouble(), h.toDouble(), value)?.let(onChange) }
            },
    ) {
        Box(Modifier.align(Alignment.TopCenter).padding(vertical = 4.dp).width(5.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().background(Color(0xFF020303), RoundedCornerShape(50)))
        }
        Box(Modifier.align(Alignment.TopCenter).offset(y = top).size(hw.dp, hh.dp)) { handle() }
    }
}

/** A stage-placed ring light: a dark bezel ring of 3 dp around a lit or unlit lens, as the web box-shadow ring. */
@Composable
private fun RingLight(x: Int, y: Int, size: Int, lit: Boolean, litBrush: Brush, dimBrush: Brush, border: Color) {
    Box(Modifier.stageAt(x - 3, y - 3, size + 6, size + 6).border(3.dp, Color(0xFF8A8E93), CircleShape).padding(3.dp)) {
        Box(Modifier.fillMaxSize().background(if (lit) litBrush else dimBrush, CircleShape).border(2.dp, border, CircleShape))
    }
}

@Composable
private fun ThrustLever(cockpit: CockpitModel, n: Int, cx: Int) {
    val s by cockpit.simState.collectAsState()
    val d by cockpit.derived.collectAsState()
    val p by cockpit.pedestalState.collectAsState()
    val linked = PedestalPartsRules.swOf(p, "thrustLinked", true)
    val throttle = if (n == 1) s.throttle1 else s.throttle2
    val disc = if (n == 1) p.atDisc.first else p.atDisc.second
    val rev = if (n == 1) d.thrustReverser1Active else d.thrustReverser2Active
    val yc = Rules.thrustY(throttle)
    val forkY by animateDpAsState(Rules.reverserForkY(rev).dp, tween(200), label = "fork")
    var startV by remember { mutableStateOf(0.0) }
    var grabbed by remember { mutableStateOf(false) }
    var acc by remember { mutableStateOf(0.0) }

    fun applyThrottle(v: Double) {
        val t = Rules.thrustTargets(linked, n)
        cockpit.patchSim {
            if (t.first) throttle1 = v
            if (t.second) throttle2 = v
        }
    }

    fun toggleReverser() {
        val st = cockpit.simState.value
        val dd = cockpit.derived.value
        val commanded = if (n == 1) st.rev1 else st.rev2
        val running = if (n == 1) dd.eng1Running else dd.eng2Running
        val fire = if (n == 1) st.engine1FirePulled else st.engine2FirePulled
        val thr = if (n == 1) st.throttle1 else st.throttle2
        val dec = Rules.reverserToggle(n, commanded, thr, st.wow, running, fire, dd.hydA)
        when (dec.action) {
            Rules.ReverserAction.STOW -> cockpit.patchSimNote(dec.note) { if (n == 1) rev1 = false else rev2 = false }
            Rules.ReverserAction.DEPLOY -> cockpit.patchSimNote(dec.note) { if (n == 1) rev1 = true else rev2 = true }
            else -> cockpit.patchSimNote(dec.note) { }
        }
    }

    // travel slot
    val slot = RoundedCornerShape(3.dp)
    Box(Modifier.stageAt(cx - 27, 392, 54, 214).background(Color(0xFF07080A), slot).border(1.dp, Color(0xFF050607), slot))
    // reverse-lever fork (pull aft to deploy)
    Box(
        Modifier.offset((cx - 22 - PEDESTAL_OX).dp, forkY).size(44.dp, 72.dp).clickable { toggleReverser() },
    ) {
        val brush = if (rev) Gold else Steel
        val edge = Color(0xFF1A1D20)
        Box(Modifier.align(Alignment.TopStart).fillMaxHeight13().background(brush, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)).border(1.dp, edge, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)))
        Box(Modifier.align(Alignment.TopEnd).fillMaxHeight13().background(brush, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)).border(1.dp, edge, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)))
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(22.dp).background(brush, RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)).border(1.dp, edge, RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)))
    }
    // handle travel area: stationary, so the drag deltas never see the handle move
    val travelTop = (Rules.LEV_TOP - 39).toInt()
    Box(
        Modifier
            .stageAt(cx - 56, travelTop, 112, (Rules.LEV_IDLE - Rules.LEV_TOP + 78).toInt())
            .pointerInput(n, linked) {
                detectTapGestures(onTap = { off ->
                    val y = off.y / density + travelTop
                    if (abs(y - Rules.thrustY(cockpit.simState.value.let { if (n == 1) it.throttle1 else it.throttle2 })) <= 39) {
                        cockpit.updatePedestal { ps ->
                            val cur = if (n == 1) ps.atDisc.first else ps.atDisc.second
                            ps.copy(atDisc = if (n == 1) Pair(!cur, ps.atDisc.second) else Pair(ps.atDisc.first, !cur))
                        }
                    }
                })
            }
            .pointerInput(n, linked) {
                detectDragGestures(
                    onDragStart = { off ->
                        val st = cockpit.simState.value
                        val cur = if (n == 1) st.throttle1 else st.throttle2
                        val y = off.y / density + travelTop
                        grabbed = abs(y - Rules.thrustY(cur)) <= 39
                        startV = cur
                        acc = 0.0
                    },
                    onDragEnd = { grabbed = false },
                    onDragCancel = { grabbed = false },
                    onDrag = { _, amount ->
                        if (grabbed) {
                            acc += amount.y / density.toDouble()
                            applyThrottle(Rules.thrustDrag(startV, acc))
                        }
                    },
                )
            },
    ) {
        // bone-shaped handle with the A/T DISENGAGE switch
        Box(
            Modifier
                .offset(0.dp, (yc - 39 - travelTop).dp)
                .size(112.dp, 78.dp)
                .clip(BoneShape)
                .background(if (disc) Brush.verticalGradient(listOf(Color(0xFFF3C36A), Color(0xFFD79A2A))) else Brush.verticalGradient(listOf(Color(0xFFE9E6D2), Color(0xFFC4C1A6)))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$n", color = Color(0xFF4A483A), fontSize = 22.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    (if (n == 1) "⇨ A/T" else "A/T ⇦") + "\nDISENGAGE", color = Color(0xFF4A483A), fontSize = 8.sp, lineHeight = 9.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** the 13 dp wide prong of the reverse fork */
private fun Modifier.fillMaxHeight13(): Modifier = this.width(13.dp).height(72.dp)

@Composable
private fun StartLevers(cockpit: CockpitModel) {
    val s by cockpit.simState.collectAsState()
    listOf(1 to 503, 2 to 545).forEach { (n, x) ->
        val idle = if (n == 1) s.lever1 else s.lever2
        Box(Modifier.stageAt(x, 702, 36, 64)) {
            Detent2(
                value = idle, w = 36, h = 64, hw = 28, hh = 26,
                onChange = { v -> cockpit.patchSimNote(Rules.startLeverNote(n, v)) { if (n == 1) lever1 = v else lever2 = v } },
            ) {
                val shape = RoundedCornerShape(4.dp)
                Box(Modifier.fillMaxSize().background(CreamPaint, shape).border(1.dp, Color(0xFF8E8A76), shape), contentAlignment = Alignment.Center) {
                    Text("$n", color = Color(0xFF37352B), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FlapQuadrant(cockpit: CockpitModel) {
    val s by cockpit.simState.collectAsState()
    val idx = PedestalLeversRules.flapIndex(s.flaps)
    val values = PedestalLeversRules.FLAP_VALUES
    val armY by animateDpAsState((Rules.FLAP_Y[idx] - Rules.FLAP_TOP).dp, tween(200), label = "flap")

    fun select(i: Int) {
        if (i != PedestalLeversRules.flapIndex(cockpit.simState.value.flaps)) {
            cockpit.patchSimNote(PedestalLeversRules.flapNote(values[i])) { flaps = values[i] }
        }
    }

    Box(Modifier.stageAt(632, Rules.FLAP_TOP, 160, 310).pickY(idx) { y -> select(Rules.flapGate(y)) }) {
        // navy scale plate
        Box(
            Modifier.offset(24.dp, 4.dp).size(40.dp, 300.dp)
                .background(Brush.horizontalGradient(listOf(Color(0xFF0D1522), Color(0xFF16233A))), RoundedCornerShape(3.dp))
                .border(1.dp, Color(0xFF050607), RoundedCornerShape(3.dp)),
        )
        Tiny("FLAP UP", Modifier.offset(26.dp, 6.dp).width(36.dp), dim = false)
        Tiny("FLAP DN", Modifier.offset(26.dp, (600 - Rules.FLAP_TOP).dp).width(36.dp))
        // serrated rack
        Canvas(Modifier.offset(64.dp, 8.dp).size(9.dp, 292.dp)) {
            var y = 0f
            while (y < size.height) {
                drawRect(Color(0xFF9AA0A5), topLeft = Offset(0f, y), size = Size(size.width, minOf(5.dp.toPx(), size.height - y)))
                y += 9.dp.toPx()
            }
            drawRect(Color(0xFF15181B), topLeft = Offset.Zero, size = Size(1.dp.toPx(), size.height))
            drawRect(Color(0xFF15181B), topLeft = Offset(size.width - 1.dp.toPx(), 0f), size = Size(1.dp.toPx(), size.height))
        }
        values.forEachIndexed { i, v ->
            Text(
                text = v.toInt().toString(),
                color = if (i == idx) Color.White else Color(0xFFD9D4C0),
                fontSize = 11.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.offset(26.dp, 0.dp).width(36.dp).centeredAtY((Rules.FLAP_Y[i] - Rules.FLAP_TOP).dp).clickable { select(i) },
            )
        }
        // bent steel arm + cream FLAP grip
        Row(Modifier.offset(62.dp, 0.dp).centeredAtY(armY), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp, 10.dp).background(SteelV))
            Box(Modifier.offset((-4).dp, (-10).dp).size(22.dp, 10.dp).rotate(-28f).background(SteelV))
            val shape = RoundedCornerShape(6.dp)
            Box(
                Modifier.offset((-4).dp, (-16).dp).size(58.dp, 44.dp).background(CreamCard, shape).border(1.dp, Color(0xFF9B9577), shape),
                contentAlignment = Alignment.Center,
            ) {
                Text("FLAP", color = Color(0xFF6A6650), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SpeedBrake(cockpit: CockpitModel) {
    val s by cockpit.simState.collectAsState()
    val idx = PedestalLeversRules.speedBrakeIndex(s.speedBrake)
    val values = PedestalLeversRules.SPEED_BRAKE_VALUES
    val leverY by animateDpAsState(Rules.SB_C[idx].dp, tween(200), label = "sb")

    // gate plate: DOWN / ARMED / FLIGHT DETENT / UP
    val plate = RoundedCornerShape(2.dp)
    Box(Modifier.stageAt(380, 498, 15, Rules.SB_H).background(Color(0xFF0C0D0F), plate).border(1.dp, Color(0xFF050607), plate)) {
        Rules.SB_NAME.forEachIndexed { i, nm ->
            val len = (nm.length * 4.4f).dp
            Box(Modifier.offset(3.dp, Rules.SB_Y[i].dp).size(7.dp, len), contentAlignment = Alignment.Center) {
                Text(
                    nm, color = if (i == idx) Color.White else LegendColor.copy(alpha = 0.6f), fontSize = 6.sp, lineHeight = 7.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
                    softWrap = false, modifier = Modifier.requiredWidth(len).rotate(90f), textAlign = TextAlign.Center,
                )
            }
        }
    }
    // lever: drag or tap along the gate, like the flap lever
    Box(
        Modifier.stageAt(270, 498, 125, Rules.SB_H).pickY(idx) { y ->
            val best = Rules.speedBrakeGate(y)
            if (best != PedestalLeversRules.speedBrakeIndex(cockpit.simState.value.speedBrake)) {
                cockpit.patchSimNote(PedestalLeversRules.speedBrakeNote(values[best])) { speedBrake = values[best] }
            }
        },
    ) {
        Rules.SB_C.forEach { y -> Box(Modifier.offset(120.dp, (y - 1).dp).size(6.dp, 2.dp).background(Color(0x999AA0A5))) }
        Row(Modifier.width(112.dp).centeredAtY(leverY), verticalAlignment = Alignment.CenterVertically) {
            val shape = RoundedCornerShape(6.dp)
            Box(Modifier.size(58.dp, 34.dp).background(CreamCard, shape).border(1.dp, Color(0xFF9B9577), shape), contentAlignment = Alignment.Center) {
                Text("SPEED\nBRAKE", color = Color(0xFF6A6650), fontSize = 7.sp, lineHeight = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
            Box(Modifier.offset((-4).dp, 0.dp).size(60.dp, 10.dp).background(SteelV))
        }
    }
}

@Composable
private fun TrimWheel(x: Int, y: Int, w: Int, h: Int, units: Double, stripes: Boolean, onTrim: (Double) -> Unit) {
    val off = Rules.trimWheelOffset(units).toFloat()
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .stageAt(x, y, w, h)
            .clip(shape)
            .background(Color(0xFF0C0D0F))
            .border(2.dp, Color(0xFF050607), shape)
            .pointerInput(Unit) { detectDragGestures { _, amount -> if (amount.y != 0f) onTrim(Rules.trimDragUnits(amount.y / density.toDouble())) } },
    ) {
        Canvas(Modifier.fillMaxSize().clipToBounds()) {
            val period = 10.dp.toPx()
            var t = (off % period) - period
            while (t < size.height) {
                drawRect(Color(0xFF23262A), topLeft = Offset(0f, t + 7.dp.toPx()), size = Size(size.width, 3.dp.toPx()))
                t += period
            }
            drawRect(Brush.horizontalGradient(0f to Color(0x14FFFFFF), 0.3f to Color.Transparent, 0.7f to Color.Transparent, 1f to Color(0x1AFFFFFF)), size = size)
            if (stripes) {
                val p2 = 150.dp.toPx()
                var u = (off % p2) - p2
                while (u < size.height) {
                    drawRect(Color(0xFFEBE7DA), topLeft = Offset(size.width * 0.34f, u), size = Size(size.width * 0.32f, 22.dp.toPx()))
                    u += p2
                }
            }
        }
    }
}

@Composable
private fun StabScale(x: Int, y: Int, value: Double, left: Boolean) {
    val line = Color(0xFFE6E1D2)
    Box(Modifier.stageAt(x, y, 24, 190)) {
        Canvas(Modifier.fillMaxSize()) {
            val k = 1.dp.toPx()
            fun yy(u: Double) = (Rules.stabScaleY(u) * k).toFloat()
            drawRoundRect(Color(0xFF07100C), Offset(k, k), Size(22 * k, 188 * k), CornerRadius(2 * k))
            drawRoundRect(Color(0xFF2A3A32), Offset(k, k), Size(22 * k, 188 * k), CornerRadius(2 * k), style = Stroke(k))
            drawRect(Color(0xFF2FA860), Offset((if (left) 8 else 4) * k, yy(3.5)), Size(9 * k, yy(8.0) - yy(3.5)))
            for (u in 0..15) {
                val x1 = (if (left) 17 else 4) * k
                val x2 = (if (left) (if (u % 5 != 0) 20 else 22) else (if (u % 5 != 0) 7 else 2)) * k
                drawLine(line, Offset(x1, yy(u.toDouble())), Offset(x2, yy(u.toDouble())), strokeWidth = 0.8f * k)
            }
            val px = (if (left) 0 else 24) * k
            val tipX = (if (left) 6 else 18) * k
            val v = yy(value)
            val path = Path().apply {
                moveTo(px, v - 4 * k)
                lineTo(tipX, v)
                lineTo(px, v + 4 * k)
                close()
            }
            drawPath(path, Color(0xFFF4EFD8))
        }
        listOf(5, 10, 15).forEach { u ->
            Text("$u", color = line, fontSize = 6.sp, lineHeight = 6.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.offset((if (left) 3 else 12).dp, (Rules.stabScaleY(u.toDouble()) - 4).dp))
        }
        Text("APL ND", color = Color(0xFFBDB5A2), fontSize = 4.sp, lineHeight = 4.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp))
        Text("APL NU", color = Color(0xFFBDB5A2), fontSize = 4.sp, lineHeight = 4.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.TopCenter).offset(y = 178.dp))
    }
}

@Composable
private fun ParkBrakeHandle(value: Boolean, onChange: (Boolean) -> Unit) {
    var live by remember { mutableStateOf<Double?>(null) } // 0 = stowed, 1 = fully pulled while dragging
    var moved by remember { mutableStateOf(false) }
    var acc by remember { mutableStateOf(0.0) }
    var base by remember { mutableStateOf(0.0) }
    val frac = live ?: (if (value) 1.0 else 0.0)
    val top by animateDpAsState(Rules.parkHandleTop(frac).dp, tween(if (live == null) 150 else 0), label = "park")
    val shape = RoundedCornerShape(3.dp)
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(Color(0xFF0B0C0E))
            .border(1.dp, Color(0xFF050607), shape)
            .pointerInput(value) { detectTapGestures(onTap = { onChange(!value) }) }
            .pointerInput(value) {
                detectDragGestures(
                    onDragStart = { base = if (value) 1.0 else 0.0; acc = 0.0; moved = false },
                    onDragEnd = {
                        val next = Rules.parkRelease(moved, live, value)
                        live = null
                        if (next != value) onChange(next)
                    },
                    onDragCancel = { live = null },
                    onDrag = { _, amount ->
                        acc += amount.y / density.toDouble()
                        if (Rules.parkMoved(acc)) moved = true
                        if (moved) live = Rules.parkDragFrac(base, acc)
                    },
                )
            },
    ) {
        Box(Modifier.align(Alignment.TopCenter).padding(vertical = 4.dp).width(5.dp).fillMaxSize().background(Color(0xFF020303), RoundedCornerShape(50)))
        Canvas(Modifier.align(Alignment.TopCenter).offset(y = top).size(34.dp, 70.dp)) {
            val k = 1.dp.toPx()
            drawRect(Color(0xFF8A8E93), Offset(14 * k, 12 * k), Size(6 * k, 58 * k))
            drawRect(Color(0xFF15181B), Offset(14 * k, 12 * k), Size(6 * k, 58 * k), style = Stroke(k))
            drawRect(Color(0xFFC9CED2), Offset(14 * k, 12 * k), Size(2 * k, 58 * k))
            drawRoundRect(Color(0xFFD0453A), Offset(2 * k, 2 * k), Size(30 * k, 12 * k), CornerRadius(5 * k))
            drawRoundRect(Color(0xFF4A0F0F), Offset(2 * k, 2 * k), Size(30 * k, 12 * k), CornerRadius(5 * k), style = Stroke(1.5f * k))
            drawRoundRect(Color(0x99FF9A8A), Offset(5 * k, 4 * k), Size(24 * k, 3 * k), CornerRadius(1.5f * k))
        }
    }
}

/** web ControlStand: the throttle quadrant body and everything on it. Place inside the stage Box. */
@Composable
fun PedestalControlStand(cockpit: CockpitModel) {
    val s by cockpit.simState.collectAsState()
    val d by cockpit.derived.collectAsState()
    val p by cockpit.pedestalState.collectAsState()
    val scope = rememberCoroutineScope()
    var hornPress by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var latch by remember { mutableStateOf(false) }
    var timer by remember { mutableStateOf<Job?>(null) }
    val lampTest = Rules.lampTest(d.dcPower, p.fireTest)

    fun trim(dU: Double) {
        val st = cockpit.simState.value
        if (Rules.trimCut(st.stabTrimCutoutA, st.stabTrimCutoutB, st.stabTrimOverride)) return
        cockpit.patchSimNote(Rules.NOTE_TRIM) { stabTrimUnits = Rules.trimNext(stabTrimUnits, dU) }
        moving = true
        timer?.cancel()
        timer = scope.launch {
            delay(Rules.TRIM_LIGHT_MS)
            moving = false
        }
    }

    // body
    val body = RoundedCornerShape(10.dp)
    Box(Modifier.stageAt(398, 333, 350, 437).background(PanelPaint, body).border(1.dp, Color(0xFF141A17), body))
    // reverse linkage and steel plates above the levers
    Canvas(Modifier.stageAt(398, 333, 350, 70)) {
        val k = 1.dp.toPx()
        listOf(Triple(48f, 40f, 17f), Triple(128f, 18f, 20f), Triple(188f, 36f, 15f), Triple(250f, 30f, 18f)).forEach { (cx, cy, r) ->
            drawCircle(Color(0xFF7B8389), r * k, Offset(cx * k, cy * k))
            drawCircle(Color(0xFF15181B), r * k, Offset(cx * k, cy * k), style = Stroke(2 * k))
            drawCircle(Color(0xFF2A2E32), r * 0.4f * k, Offset(cx * k, cy * k))
        }
        drawRect(Color(0xFF9AA0A5), Offset(60 * k, 10 * k), Size(190 * k, 9 * k))
        drawRect(Color(0xFF15181B), Offset(60 * k, 10 * k), Size(190 * k, 9 * k), style = Stroke(k))
        drawRect(Color(0xFF555B60), Offset(30 * k, 48 * k), Size(240 * k, 6 * k))
        val linkage = Path().apply {
            moveTo(70 * k, 60 * k); lineTo(96 * k, 20 * k); lineTo(120 * k, 20 * k); lineTo(100 * k, 60 * k); close()
            moveTo(170 * k, 60 * k); lineTo(196 * k, 24 * k); lineTo(216 * k, 24 * k); lineTo(196 * k, 60 * k); close()
        }
        drawPath(linkage, Color(0xFF6C7378))
        drawPath(linkage, Color(0xFF15181B), style = Stroke(k))
    }

    // INCREASE / DECREASE THRUST placard + rusty centre plate
    val plac = RoundedCornerShape(2.dp)
    Box(
        Modifier.stageAt(520, 420, 44, 120).background(Brush.verticalGradient(listOf(Color(0xFF101A2A), Color(0xFF0A111C))), plac).border(1.dp, Color(0xFF050607), plac),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "DECREASE THRUST ⊕ INCREASE THRUST", color = Color(0xFFDFE6EE), fontSize = 7.sp, lineHeight = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            softWrap = false, textAlign = TextAlign.Center, modifier = Modifier.requiredWidth(116.dp).rotate(-90f),
        )
    }
    val rust = RoundedCornerShape(2.dp)
    Box(Modifier.stageAt(516, 545, 52, 162).background(Brush.verticalGradient(0f to Color(0xFF7A7468), 0.6f to Color(0xFF5E5648), 1f to Color(0xFF6A5238)), rust).border(1.dp, Color(0xFF3A2A1A), rust)) {
        listOf(24, 80, 134).forEach { yy -> Box(Modifier.align(Alignment.TopCenter).offset(y = yy.dp).size(8.dp).background(Color(0xFF1A1612), CircleShape)) }
        Box(Modifier.offset(y = 44.dp).fillMaxWidth().height(26.dp).background(Color(0x4D8A4A1A)))
    }

    SpeedBrake(cockpit)
    StabScale(186, 500, s.stabTrimUnits, left = true)
    StabScale(792, 500, s.stabTrimUnits, left = false)
    ThrustLever(cockpit, 1, 475)
    ThrustLever(cockpit, 2, 600)
    StartLevers(cockpit)
    FlapQuadrant(cockpit)

    // stabilizer trim wheels
    TrimWheel(212, 500, 54, 250, s.stabTrimUnits, stripes = true, onTrim = { trim(it) })
    TrimWheel(818, 500, 54, 250, s.stabTrimUnits, stripes = true, onTrim = { trim(it) })

    // parking brake: placard + bent red wire handle
    val card = RoundedCornerShape(4.dp)
    Box(
        Modifier.stageAt(398, 628, 58, 38).background(Brush.verticalGradient(listOf(Color(0xFFE4E3DC), Color(0xFFB8B6AA))), card).border(1.dp, Color(0xFF8E8A76), card),
        contentAlignment = Alignment.Center,
    ) {
        Text("PARKING\nBRAKE\nPULL", color = Color(0xFF2A2A24), fontSize = 7.sp, lineHeight = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
    Box(Modifier.stageAt(398, 668, 40, 74)) {
        ParkBrakeHandle(value = s.parkingBrake, onChange = { v -> cockpit.patchSimNote(Rules.parkNote(v)) { parkingBrake = v } })
    }
    RingLight(
        451, 677, 34, Rules.parkBrakeLight(d.dcPower, s.parkingBrake),
        Brush.radialGradient(listOf(Color(0xFFFFD27A), Color(0xFFF08A1A))), Brush.radialGradient(listOf(Color(0xFFC9761A), Color(0xFF7A420A))), Color(0xFF5A3A0A),
    )
    Box(Modifier.stageAt(440, 716, 62, 28)) { Lbl("PARK\nBRAKE\nLIGHT", Modifier.fillMaxSize()) }

    // horn cutout
    Box(
        Modifier.stageAt(596, 614, 34, 34)
            .background(Brush.radialGradient(listOf(if (hornPress) Color(0xFF6A6E72) else Color(0xFF3A3D41), Color(0xFF0C0D0E))), CircleShape)
            .border(2.dp, Color(0xFF8A8E93), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    hornPress = true
                    cockpit.updateOverhead { it.copy(hornCutout = true) }
                    try {
                        tryAwaitRelease()
                    } finally {
                        hornPress = false
                        cockpit.updateOverhead { it.copy(hornCutout = false) }
                    }
                })
            },
    )
    Box(Modifier.stageAt(572, 652, 58, 22)) { Lbl("HORN\nCUTOUT", Modifier.fillMaxSize()) }

    // stab trim light + latch
    RingLight(
        650, 677, 34, Rules.stabTrimLight(d.dcPower, moving, lampTest),
        Brush.radialGradient(listOf(Color(0xFFFF9A9A), Color(0xFFE02020))), Brush.radialGradient(listOf(Color(0xFFB02A2A), Color(0xFF5A0A0A))), Color(0xFF4A0F0F),
    )
    Box(Modifier.stageAt(638, 716, 62, 28)) { Lbl("STAB\nTRIM\nLIGHT", Modifier.fillMaxSize()) }
    Box(Modifier.stageAt(706, 724, 34, 30).clickable { latch = !latch }) {
        val bar = Brush.horizontalGradient(listOf(Color(0xFF9AA0A5), Color(0xFF5A5F64)))
        Box(Modifier.offset(4.dp, 2.dp).size(6.dp, 26.dp).rotate(if (latch) 24f else 0f).background(bar, RoundedCornerShape(1.dp)))
        Box(Modifier.align(Alignment.TopEnd).padding(end = 4.dp, top = 2.dp).size(6.dp, 26.dp).rotate(if (latch) -24f else 0f).background(bar, RoundedCornerShape(1.dp)))
        Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 2.dp).fillMaxWidth().height(6.dp).background(Color(0xFF2A2D31), RoundedCornerShape(1.dp)))
    }
}
