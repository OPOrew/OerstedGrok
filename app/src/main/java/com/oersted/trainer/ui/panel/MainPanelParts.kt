package com.oersted.trainer.ui.panel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.ChronometerLogic
import com.oersted.trainer.sim.MainPanelRules
import com.oersted.trainer.sim.MarkerBeacons
import com.oersted.trainer.sim.MarkerLamps
import com.oersted.trainer.sim.PedestalTopDeckRules
import com.oersted.trainer.sim.SelectorLogic
import com.oersted.trainer.ui.cockpit.CockpitModel
import com.oersted.trainer.ui.instruments.ChronometerFace
import com.oersted.trainer.ui.pedestal.Annun
import com.oersted.trainer.ui.pedestal.AnnunTone
import com.oersted.trainer.ui.pedestal.Knob
import com.oersted.trainer.ui.pedestal.PedestalLegend
import com.oersted.trainer.ui.pedestal.PushButton
import kotlinx.coroutines.delay
import java.time.ZoneOffset
import java.time.ZonedDateTime

/*
 * Layout parts of components/panel/MainInstrumentPanel.tsx: the S / C / Bezel / Tag / Placard / Lens / Cap helpers, the
 * AnalogClock, Sel3 selector, MarkerLamp and the DH / BUG / BARO knob blocks. Positions are the web's design px, used as dp
 * inside the scaled 3036 x 720 canvas (see MainInstrumentPanel.kt). Compose only; verified on CI, not compiled locally.
 */

internal val PanelBlue = Color(0xFF4B5463)
internal val BezelTone = Color(0xFF2F3744)
internal val CentreTone = Color(0xFF39414D)
internal val CreamTone = Color(0xFFD9D2BA)
internal val PanelGreen = Color(0xFF2FC27A)
internal val PanelRed = Color(0xFFE24B4B)
internal val PanelAmber = Color(0xFFE0A84A)

/** web S: absolutely placed box (design px). */
@Composable
internal fun MpS(x: Int, y: Int, w: Int, h: Int, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.offset(x.dp, y.dp).size(w.dp, h.dp), content = content)
}

/** web C: a round instrument of radius [r] centred on ([cx], [cy]); the content fills the circle's box. */
@Composable
internal fun MpC(cx: Int, cy: Int, r: Int, content: @Composable BoxScope.() -> Unit) = MpS(cx - r, cy - r, r * 2, r * 2, content = content)

/** web Bezel: dark rounded plate with four screws. */
@Composable
internal fun MpBezel(x: Int, y: Int, w: Int, h: Int, tone: Color = BezelTone) {
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.offset(x.dp, y.dp).size(w.dp, h.dp).background(tone, shape).border(1.dp, Color(0xFF1B2028), shape)) {
        for ((sx, sy) in listOf(6 to 6, w - 12 to 6, 6 to h - 12, w - 12 to h - 12)) {
            Box(
                Modifier.offset(sx.dp, sy.dp).size(6.dp).background(Color(0xFF171B21), CircleShape).border(1.dp, Color(0xFF556666), CircleShape),
            )
        }
    }
}

/** web plain panel (the CENTRE / CREAM plates): rounded 3, thin border. */
@Composable
internal fun MpPlate(x: Int, y: Int, w: Int, h: Int, fill: Color, border: Color = Color(0xFF1B2028)) {
    val shape = RoundedCornerShape(3.dp)
    Box(Modifier.offset(x.dp, y.dp).size(w.dp, h.dp).background(fill, shape).border(1.dp, border, shape))
}

/** web Tag: engraved legend centred on [x], top at [y]. */
@Composable
internal fun MpTag(x: Int, y: Int, w: Int = 120, text: String) {
    Box(Modifier.offset((x - w / 2).dp, y.dp).size(w.dp, 12.dp), contentAlignment = Alignment.TopCenter) {
        PedestalLegend(text, size = 8, textAlign = TextAlign.Center)
    }
}

/** web Placard: black engraved placard with cream text. */
@Composable
internal fun MpPlacard(text: String, size: Int = 7) {
    Box(
        Modifier.fillMaxSize().background(Color.Black, RoundedCornerShape(2.dp)).border(1.dp, Color(0xFF0B0D10), RoundedCornerShape(2.dp)).padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(), color = Color(0xFFE8E4D6), fontSize = size.sp, lineHeight = (size + 2).sp, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        )
    }
}

/** web Lens: dark-glass lens with embossed legend; lit variants use Annun. Fills its parent. */
@Composable
internal fun MpLens(text: String, on: Boolean = false, tone: AnnunTone = AnnunTone.AMBER) {
    Annun(text, on, tone, Modifier.fillMaxSize(), small = true)
}

/** web Cap: round blanking cap. */
@Composable
internal fun MpCap(size: Int, light: Boolean = false) {
    val brush = if (light) Brush.radialGradient(listOf(Color(0xFFF1F2F3), Color(0xFFB9BDC1))) else Brush.radialGradient(listOf(Color(0xFF4A4E53), Color(0xFF14161A)))
    Box(Modifier.size(size.dp).background(brush, CircleShape).border(1.dp, if (light) Color(0xFF9AA0A6) else Color(0xFF0B0C0E), CircleShape))
}

/** web Perf: perforated blanking plate where an instrument was removed. */
@Composable
internal fun MpPerf() {
    Box(Modifier.fillMaxSize().background(Color(0xFF101214), RoundedCornerShape(3.dp)).border(1.dp, Color(0xFF0B0D10), RoundedCornerShape(3.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 20.dp.toPx()
            var y = step / 2
            while (y < size.height) {
                var x = step / 2
                while (x < size.width) {
                    drawCircle(Color(0xFF555A60), step * 0.22f, Offset(x, y))
                    x += step
                }
                y += step
            }
        }
    }
}

/** web PlacardAc: aircraft data placard. */
@Composable
internal fun MpPlacardAc(compact: Boolean = false) {
    val lines = listOf(
        "THIS IS A/C RP-C8009", "B737-200", "MAX. TAXI WT. 117,500 LBS.", "MAX. T.O. WT. 117,000 LBS.", "MAX. ZERO FUEL 95,000 LBS.",
        "MAX. LDG. WT. 105,000 LBS.", "FUEL CAPACITY 34,500 LBS.", "MAX. OPER. ALT. 35,000 FT.",
    )
    val shape = RoundedCornerShape(2.dp)
    Column(Modifier.fillMaxSize().background(Color(0xFFD8D6CC), shape).border(1.dp, Color(0xFF8A8D90), shape).padding(4.dp)) {
        lines.forEachIndexed { i, l ->
            Text(
                l, color = Color(0xFF2A2A2A), fontSize = (if (compact) 6f else 7.5f).sp, lineHeight = (if (compact) 7.5f else 9.5f).sp,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                modifier = if (i == 2) Modifier.padding(top = 2.dp) else Modifier,
            )
        }
    }
}

/** web RegPlate */
@Composable
internal fun MpRegPlate() {
    Box(
        Modifier.fillMaxSize().background(Color(0xFF101214), RoundedCornerShape(2.dp)).border(1.dp, Color(0xFF2A2D31), RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("N267AU", color = Color(0xFFE8B98F), fontSize = 15.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 0.05.em)
    }
}

/** web Sel3: three-position instrument source selector with the legends printed at each detent. A press on the left half steps down, the right half up. */
@Composable
internal fun MpSel3(label: String, value: Int, onChange: (Int) -> Unit, legends: List<String>) {
    Box(Modifier.size(128.dp, 92.dp)) {
        @Composable
        fun leg(i: Int, mod: Modifier, align: TextAlign) {
            PedestalLegend(
                legends[i], modifier = mod.clickable { onChange(i) }, size = 7,
                color = if (value == i) Color.White else Color(0xFFE6E1D2).copy(alpha = 0.6f), textAlign = align,
            )
        }
        leg(0, Modifier.align(Alignment.TopStart).offset(y = 18.dp), TextAlign.Start)
        leg(1, Modifier.align(Alignment.TopCenter), TextAlign.Center)
        leg(2, Modifier.align(Alignment.TopEnd).offset(y = 18.dp), TextAlign.End)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = 34.dp)
                .size(44.dp)
                .background(Brush.radialGradient(listOf(Color(0xFFF3F0E6), Color(0xFFBDB8A6), Color(0xFF7C796C))), CircleShape)
                .border(1.dp, Color(0xFF8B8E92), CircleShape)
                .pointerInput(value) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        onChange(SelectorLogic.stepFromPress(value, down.position.x < size.width / 2f))
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                withTransform({ rotate(SelectorLogic.knobAngleDeg(value).toFloat(), Offset(size.width / 2, size.height / 2)) }) {
                    val w = 8.dp.toPx()
                    drawRect(Color(0xFFE9E5D6), Offset(size.width / 2 - w / 2, size.height / 2 - 22.dp.toPx()), androidx.compose.ui.geometry.Size(w, 22.dp.toPx()))
                }
            }
        }
        PedestalLegend(label, modifier = Modifier.align(Alignment.BottomCenter), size = 7)
    }
}

/**
 * web AnalogClock: the UTC clock with the elapsed-time chronometer. Tap the dial or the left lever to run / stop, the right lever resets.
 * [drivesElapsed]: only one clock on the panel advances the elapsed seconds (the web's two clocks each add a second per second,
 * which doubles the chronometer; this port ticks it once).
 */
@Composable
internal fun MpClock(cockpit: CockpitModel, size: Int, drivesElapsed: Boolean) {
    val mip by cockpit.mipState.collectAsState()
    var now by remember { mutableStateOf(ZonedDateTime.now(ZoneOffset.UTC)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = ZonedDateTime.now(ZoneOffset.UTC)
            if (drivesElapsed && cockpit.mipState.value.clock.running) {
                cockpit.updateMip { it.copy(clock = it.clock.copy(elapsed = it.clock.elapsed + 1)) }
            }
        }
    }
    val state = ChronometerLogic.State(mip.clock.running, mip.clock.elapsed.toLong())
    fun toggle() = cockpit.updateMip { it.copy(clock = it.clock.copy(running = !it.clock.running)) }
    Box(Modifier.size(size.dp)) {
        ChronometerFace(now.hour % 12, now.minute, now.second, state, onFaceTap = { toggle() }, modifier = Modifier.size(size.dp))
        val lever = Brush.verticalGradient(listOf(Color(0xFFD8D8D2), Color(0xFF8A8E93)))
        val shape = RoundedCornerShape(2.dp)
        Box(
            Modifier.align(Alignment.BottomStart).offset((-12).dp, 8.dp).size(28.dp, 16.dp)
                .background(lever, shape).border(1.dp, if (mip.clock.running) PanelAmber else Color(0xFF0B0C0E), shape)
                .clickable { toggle() },
        )
        Box(
            Modifier.align(Alignment.BottomEnd).offset(12.dp, 8.dp).size(28.dp, 16.dp)
                .background(lever, shape).border(1.dp, Color(0xFF0B0C0E), shape)
                .clickable { cockpit.updateMip { it.copy(clock = MipClockReset) } },
        )
    }
}

private val MipClockReset = com.oersted.trainer.sim.MipLocal.ClockState(running = false, elapsed = 0.0)

/** web MarkerLamp: lit by the lamp test, or flashing in step with the received marker tone. [annun] = the blue OUTER lamp, else a lens. */
@Composable
internal fun MpMarkerLamp(cockpit: CockpitModel, id: MarkerBeacons.MarkerId, test: Boolean, powered: Boolean, annun: Boolean, tone: AnnunTone = AnnunTone.AMBER, text: String) {
    val mip by cockpit.mipState.collectAsState()
    val received = when (id) {
        MarkerBeacons.MarkerId.OM -> mip.markers.om
        MarkerBeacons.MarkerId.MM -> mip.markers.mm
        MarkerBeacons.MarkerId.IM -> mip.markers.im
    }
    var t by remember { mutableStateOf(0.0) }
    LaunchedEffect(received, powered) {
        if (received && powered) {
            while (true) {
                t = System.nanoTime() / 1e9
                delay(MarkerLamps.REFRESH_MS)
            }
        }
    }
    val keyed = received && powered && MarkerBeacons.markerKeyed(id, t)
    val on = test || keyed
    if (annun) Annun(text, on, AnnunTone.BLUE, Modifier.fillMaxSize(), small = true) else Annun(text, on, tone, Modifier.fillMaxSize(), small = true)
}

/** web dhKnob: radio-altimeter decision height knob with its legend and TEST button. */
@Composable
internal fun MpDhKnob(x: Int, y: Int, raDh: Double, onDh: (Double) -> Unit, onTest: (Boolean) -> Unit) {
    MpS(x, y, 176, 30) {
        Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Knob(value = raDh, onChange = onDh, min = 0.0, max = 500.0, step = 10.0, size = 22.dp)
            PedestalLegend("DH ${MainPanelRules.pad(raDh, 3)}", size = 7)
            PushButton("TEST", onPress = { onTest(true) }, onRelease = { onTest(false) })
        }
    }
}

/** web bugKnob: ASI speed bug. */
@Composable
internal fun MpBugKnob(x: Int, y: Int, v: Double, set: (Double) -> Unit) {
    MpS(x, y, 90, 30) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Knob(value = v, onChange = { set(Math.floor(it + 0.5)) }, min = 60.0, max = 340.0, step = 1.0, size = 22.dp)
            PedestalLegend("BUG ${Math.floor(v + 0.5).toLong()}", size = 7)
        }
    }
}

/** web baroKnob: altimeter baro setting in inHg. */
@Composable
internal fun MpBaroKnob(x: Int, y: Int, v: Double, set: (Double) -> Unit) {
    MpS(x, y, 90, 30) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Knob(value = v, onChange = { set(PedestalTopDeckRules.toFixed(it, 2).toDouble()) }, min = 28.5, max = 31.0, step = 0.01, size = 22.dp)
            PedestalLegend("BARO ${PedestalTopDeckRules.toFixed(v, 2)}", size = 7)
        }
    }
}

/** A circular glass-bezelled face wrapper is drawn by the faces themselves; this clips a composable to a circle (empty instrument cut-outs). */
@Composable
internal fun MpCutout() {
    Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.Black))
}

/** web radial lighting overlay of one zone; see MainPanelRules.lightOverlay. Does not take touches. */
@Composable
internal fun MpLightZone(x: Int, w: Int, h: Int, o: MainPanelRules.LightOverlay) {
    Box(
        Modifier.offset(x.dp, 0.dp).size(w.dp, h.dp).drawBehind {
            drawRect(Color(3f / 255, 5f / 255, 9f / 255, o.dark.toFloat()))
            val r = kotlin.math.sqrt((size.width / 2) * (size.width / 2) + size.height * size.height)
            drawRect(
                Brush.radialGradient(
                    0f to Color(1f, 214f / 255, 150f / 255, o.floodOuter.toFloat()),
                    0.55f to Color(1f, 200f / 255, 120f / 255, o.floodMid.toFloat()),
                    0.85f to Color(1f, 200f / 255, 120f / 255, 0f),
                    center = Offset(size.width / 2, 0f), radius = r,
                ),
            )
        },
    )
}
