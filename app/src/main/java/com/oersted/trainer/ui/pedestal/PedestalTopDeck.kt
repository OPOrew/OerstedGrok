package com.oersted.trainer.ui.pedestal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.PedestalPartsRules
import com.oersted.trainer.sim.PedestalTopDeckRules as Rules
import com.oersted.trainer.ui.cockpit.CockpitModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Port of the TOP DECK of components/panel/CenterPedestal.tsx (lines 270-473): the FreeFlight GPS, VHF 3 COMM, cockpit
 * voice recorder, Primus 90 weather radar indicator and transponder, laid out at the photograph's coordinates.
 * The text and key rules are in sim/PedestalTopDeckRules.kt. Compose only; verified on CI.
 * Not ported yet: the radar scope picture itself (web WxrScope), drawn here as a dark placeholder.
 */

private val OrangeFill = Brush.verticalGradient(listOf(Color(0xFFE08A2E), Color(0xFFA85F14)))
private val OrangeBorder = Color(0xFF8A4A10)
private val OrangeText = Color(0xFF2A1200)

/** The orange GPS / CVR key: web `border-[#8a4a10] bg-[#e08a2e] text-[#2a1200]`. [onPress] fires on touch down, [onRelease] on lift. */
@Composable
private fun OrangeKey(label: String, modifier: Modifier = Modifier, lit: Boolean = false, size: Int = 7, onPress: (() -> Unit)? = null, onRelease: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier
            .background(OrangeFill, shape)
            .border(if (lit) 2.dp else 1.dp, if (lit) Color(0xB3FFFFFF) else OrangeBorder, shape)
            .pointerInput(onPress, onRelease) {
                detectTapGestures(onPress = {
                    onPress?.invoke()
                    tryAwaitRelease()
                    onRelease?.invoke()
                })
            }
            .padding(vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = OrangeText, fontSize = size.sp, lineHeight = (size + 1).sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
fun PedestalGpsUnit(cockpit: CockpitModel) {
    val m by cockpit.mipState.collectAsState()
    val p by cockpit.pedestalState.collectAsState()
    val plan by cockpit.activePlan.collectAsState()
    val nav by cockpit.navState.collectAsState()
    val page = Rules.gpsPage(p.gpsPage)
    PaintedPanel(Modifier.fillMaxSize(), dark = true) {
        Rivets()
        Text("FreeFlight", color = Color.White, fontSize = 12.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, modifier = Modifier.offset(12.dp, 4.dp))
        Column(
            Modifier
                .offset(12.dp, 18.dp)
                .size(width = 132.dp, height = 42.dp)
                .background(Color(0xFF1B1710), RoundedCornerShape(2.dp))
                .border(2.dp, Color(0xFF3A342A), RoundedCornerShape(2.dp))
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (m.gpsOn) {
                Rules.gpsLines(plan, nav, page).forEach { line ->
                    Text(line, color = Color(0xFFFFB21A), fontSize = 8.sp, lineHeight = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
        OrangeKey(
            "ENT", Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 14.dp).size(width = 34.dp, height = 18.dp), size = 8,
            onPress = { if (m.gpsOn) cockpit.updatePedestal { it.copy(gpsPage = "NAV") } },
        )
        Box(Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 36.dp)) {
            Knob(
                value = if (m.gpsOn) 1.0 else 0.0, onChange = { v -> cockpit.updateMip { it.copy(gpsOn = v > 0.5) } },
                min = 0.0, max = 1.0, step = 1.0, size = 26.dp, ticks = listOf("OFF", "ON"), compact = true,
            )
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Rules.GPS_KEYS.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { k ->
                        OrangeKey(k, Modifier.weight(1f), lit = m.gpsOn && page == k, onPress = { if (m.gpsOn) cockpit.updatePedestal { it.copy(gpsPage = k) } })
                    }
                }
            }
        }
    }
}

@Composable
private fun VhfWindow(text: String, hot: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier.background(if (hot) Color(0xFFE9E6DC) else Color.Black, shape).border(2.dp, Color(0xFF101316), shape).padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (hot) Color(0xFF111111) else Color(0xFFF1F1EA), fontSize = 15.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PedestalVhfCommTop(cockpit: CockpitModel) {
    val m by cockpit.mipState.collectAsState()
    val d by cockpit.derived.collectAsState()
    PaintedPanel(Modifier.fillMaxSize()) {
        Rivets()
        Tiny("VHF 3 · SIM ONLY", Modifier.offset(12.dp, 4.dp))
        Box(Modifier.align(Alignment.TopCenter).padding(top = 4.dp)) {
            Bat(on = false, onChange = { cockpit.updateMip { it.copy(comAct = it.comStby, comStby = it.comAct) } })
        }
        Tiny("TFR", Modifier.align(Alignment.TopCenter).padding(top = 36.dp))
        VhfWindow(Rules.vhfWindowText(m.comAct, d.dcPower), false, Modifier.offset(16.dp, 16.dp).width(70.dp))
        VhfWindow(Rules.vhfWindowText(m.comStby, d.dcPower), true, Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 16.dp).width(70.dp))
        TuneKnob(onStep = { dir -> cockpit.updateMip { it.copy(comAct = Rules.vhfStep(it.comAct, dir)) } }, size = 36.dp, modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 8.dp))
        TuneKnob(onStep = { dir -> cockpit.updateMip { it.copy(comStby = Rules.vhfStep(it.comStby, dir)) } }, size = 36.dp, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 8.dp))
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Tiny("COMM")
            PushButton(
                "TEST", active = m.comTest,
                onPress = { cockpit.updateMip { it.copy(comTest = true) } },
                onRelease = { cockpit.updateMip { it.copy(comTest = false) } },
            )
        }
    }
}

@Composable
fun PedestalCvrUnit(cockpit: CockpitModel) {
    val s by cockpit.simState.collectAsState()
    val d by cockpit.derived.collectAsState()
    val scope = rememberCoroutineScope()
    var erase by remember { mutableStateOf(false) } // UI lamp for the modeled bulk-erase interval
    var hold by remember { mutableStateOf<Pair<Long, Boolean>?>(null) }
    var timer by remember { mutableStateOf<Job?>(null) }

    fun erasePress() {
        if (hold != null) return
        val ok = Rules.cvrEraseAllowed(d.cvrPowered, d.dcPower, s.wow, s.parkingBrake, s.faults.cvrFailure)
        hold = System.currentTimeMillis() to ok
        if (!ok) cockpit.patchSimNote(Rules.NOTE_INHIBITED) { cvrErase = false }
    }

    fun eraseRelease() {
        val h = hold
        hold = null
        if (h == null || !h.second) return
        when (Rules.cvrEraseOutcome(h.second, System.currentTimeMillis() - h.first)) {
            Rules.EraseOutcome.TOO_SHORT -> cockpit.patchSimNote(Rules.NOTE_TOO_SHORT) { cvrErase = false }
            Rules.EraseOutcome.START -> {
                erase = true
                cockpit.patchSimNote(Rules.NOTE_RUNNING) { cvrErase = true; cvrMemoryErased = false }
                timer?.cancel()
                timer = scope.launch {
                    delay(Rules.CVR_ERASE_RUN_MS)
                    erase = false
                    cockpit.patchSimNote(Rules.NOTE_DONE) { cvrErase = false }
                }
            }
            Rules.EraseOutcome.NOT_ALLOWED -> Unit
        }
    }

    PaintedPanel(Modifier.fillMaxSize()) {
        Rivets()
        // area microphone
        Canvas(Modifier.offset(16.dp, 12.dp).size(58.dp)) {
            drawCircle(Color(0xFF7A4A2A), radius = size.width / 2f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF8A5A38), Color(0xFF8A5A38), Color(0xFF3A2A20)), radius = size.width / 2f - 4.dp.toPx()), radius = size.width / 2f - 4.dp.toPx())
        }
        // monitor meter with its needle
        Canvas(Modifier.offset(86.dp, 16.dp).size(58.dp)) {
            drawCircle(Color(0xFFC9CCD0), radius = size.width / 2f)
            drawCircle(Color(0xFFD8D6CC), radius = size.width / 2f - 4.dp.toPx())
            val a = Rules.cvrNeedleDeg(d.cvrPowered, s.cvrTest) * PI / 180.0
            val pivot = Offset(size.width / 2f, size.height - 10.dp.toPx())
            val len = 24.dp.toPx()
            drawLine(Color(0xFF222222), pivot, Offset(pivot.x + (sin(a) * len).toFloat(), pivot.y - (cos(a) * len).toFloat()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(Color(0xFF333333), radius = 3.5.dp.toPx(), center = Offset(size.width / 2f, size.height - 6.dp.toPx() - 3.5.dp.toPx() + 3.5.dp.toPx()))
        }
        Column(Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Lamp(on = d.cvrPowered && s.cvrTest, tone = AnnunTone.GREEN, size = 8.dp)
                PushButton(
                    "TEST",
                    onPress = { cockpit.patchSimNote(Rules.NOTE_TEST) { cvrTest = true } },
                    onRelease = { cockpit.patchSim { cvrTest = false } },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Lamp(on = erase, tone = AnnunTone.AMBER, size = 8.dp)
                OrangeKey("ERASE", Modifier.width(42.dp), onPress = { erasePress() }, onRelease = { eraseRelease() })
            }
        }
        Tiny("COCKPIT VOICE RECORDER", Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 8.dp))
    }
}

/** web WxrKey: latched pushbutton with a lit legend (Primus 90 indicator keys). */
@Composable
private fun WxrKey(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier
            .height(18.dp)
            .background(
                if (on) Brush.verticalGradient(listOf(Color(0xFF2A2D31), Color(0xFF14161A))) else Brush.verticalGradient(listOf(Color(0xFF4A4E53), Color(0xFF2A2D31))),
                shape,
            )
            .border(1.dp, Color(0xFF15171A), shape)
            .pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center,
    ) {
        Text(label.uppercase(), color = if (on) Color(0xFF7FE8A0) else Color(0xFFE6E1D2), fontSize = 7.sp, lineHeight = 7.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PedestalRadarUnit(cockpit: CockpitModel) {
    val m by cockpit.mipState.collectAsState()
    PaintedPanel(Modifier.fillMaxSize()) {
        Rivets()
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Knob(value = m.radarRange.toDouble(), onChange = { v -> cockpit.updateMip { it.copy(radarRange = v.toInt()) } }, min = 0.0, max = 6.0, step = 1.0, size = 24.dp, ticks = Rules.WXR_RANGES, label = "RANGE", compact = true)
            Knob(value = m.radarTilt, onChange = { v -> cockpit.updateMip { it.copy(radarTilt = v) } }, min = -15.0, max = 15.0, step = 1.0, size = 24.dp, label = Rules.radarTiltLabel(m.radarTilt), compact = true)
            Knob(value = m.radarGain, onChange = { v -> cockpit.updateMip { it.copy(radarGain = Rules.radarGain(v)) } }, min = 0.0, max = 1.0, size = 24.dp, ticks = listOf("PRESET", "MAX"), label = "GAIN", compact = true)
            Knob(value = m.radarInt, onChange = { v -> cockpit.updateMip { it.copy(radarInt = v) } }, min = 0.0, max = 1.0, size = 24.dp, label = "INT", compact = true)
        }
        // placeholder for the web WxrScope picture
        Box(
            Modifier
                .offset(22.dp, 62.dp)
                .size(width = 196.dp, height = 150.dp)
                .background(Color.Black, RoundedCornerShape(10.dp))
                .border(5.dp, Color(0xFF2A323A), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("WX RADAR", color = Color(0xFF2F6B3F), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
        Row(Modifier.offset(14.dp, 218.dp).width(212.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Rules.RADAR_MODE_KEYS.forEach { (l, i) ->
                WxrKey(l, m.radarMode == i, { cockpit.updateMip { it.copy(radarMode = Rules.radarModeToggle(it.radarMode, i)) } }, Modifier.weight(1f))
            }
        }
        Row(Modifier.offset(14.dp, 240.dp).width(212.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WxrKey("FRZ", m.radarFrz, { cockpit.updateMip { it.copy(radarFrz = !it.radarFrz) } }, Modifier.weight(1f))
            WxrKey("AZIM", m.radarAzim, { cockpit.updateMip { it.copy(radarAzim = !it.radarAzim) } }, Modifier.weight(1f))
            WxrKey("SEC SCAN", m.radarSector, { cockpit.updateMip { it.copy(radarSector = !it.radarSector) } }, Modifier.weight(1f))
            WxrKey("TGT", m.radarTgt, { cockpit.updateMip { it.copy(radarTgt = !it.radarTgt) } }, Modifier.weight(1f))
            WxrKey("STAB", m.radarStab, { cockpit.updateMip { it.copy(radarStab = !it.radarStab) } }, Modifier.weight(1f))
        }
    }
}

@Composable
fun PedestalTransponderUnit(cockpit: CockpitModel) {
    val m by cockpit.mipState.collectAsState()
    val d by cockpit.derived.collectAsState()
    val scope = rememberCoroutineScope()
    var ident by remember { mutableStateOf(false) }
    val fail = Rules.xpdrFail(d.dcPower, m.xpdrMode)
    PaintedPanel(Modifier.fillMaxSize()) {
        Rivets()
        Box(Modifier.offset(12.dp, 12.dp)) {
            Knob(value = m.xpdrMode.toDouble(), onChange = { v -> cockpit.updateMip { it.copy(xpdrMode = v.toInt()) } }, min = 0.0, max = 3.0, step = 1.0, size = 28.dp, ticks = Rules.XPDR_MODES, compact = true)
        }
        val failShape = RoundedCornerShape(3.dp)
        Box(
            Modifier.offset(88.dp, 8.dp).size(width = 56.dp, height = 34.dp)
                .background(if (fail) Color(0xFFE03A3A) else Color(0xFF5A1410), failShape).border(2.dp, Color(0xFF3A0F0C), failShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("ATC\nFAIL", color = if (fail) Color.White else Color(0xFFA84A40), fontSize = 7.sp, lineHeight = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        val codeShape = RoundedCornerShape(3.dp)
        Box(
            Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 8.dp)
                .background(Color(0xFF2A0C0A), codeShape).border(2.dp, Color(0xFF101316), codeShape).padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Text(if (d.dcPower) m.xpdrCode else "----", color = Color(0xFFFF7A5A), fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
        TuneKnob(onStep = { dir -> cockpit.updateMip { it.copy(xpdrCode = Rules.xpdrPair(it.xpdrCode, 0, dir)) } }, size = 30.dp, modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 12.dp))
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) {
            PushButton("IDENT", active = ident, onPress = {
                scope.launch {
                    ident = true
                    delay(3000)
                    ident = false
                }
            })
        }
        TuneKnob(onStep = { dir -> cockpit.updateMip { it.copy(xpdrCode = Rules.xpdrPair(it.xpdrCode, 2, dir)) } }, size = 30.dp, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 12.dp))
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 58.dp, bottom = 12.dp)) {
            Bat(on = m.atcSel == 1, onChange = { v -> cockpit.updateMip { it.copy(atcSel = if (v) 1 else 2) } }, up = "1", down = "2")
        }
    }
}

@Composable
fun PedestalBlankPlate() {
    PaintedPanel(Modifier.fillMaxSize()) {
        Rivets()
        Chips()
    }
}

/** web TopDeck: the upper deck of the pedestal, at the photograph's coordinates. Place it inside the stage Box. */
@Composable
fun PedestalTopDeck(cockpit: CockpitModel) {
    val deck = RoundedCornerShape(4.dp)
    Box(
        Modifier.stageAt(185, 8, 675, 522)
            .background(Brush.verticalGradient(listOf(Color(0xFF3B434B), Color(0xFF2A3037))), deck)
            .border(1.dp, Color(0xFF0D1013), deck),
    )
    Box(Modifier.stageAt(200, 45, 210, 135)) { PedestalGpsUnit(cockpit) }
    Box(Modifier.stageAt(195, 186, 215, 66)) { PedestalBlankPlate() }
    Box(Modifier.stageAt(195, 256, 197, 114)) { PedestalVhfCommTop(cockpit) }
    Box(Modifier.stageAt(190, 374, 202, 96)) { PedestalCvrUnit(cockpit) }
    Box(Modifier.stageAt(420, 18, 240, 282)) { PedestalRadarUnit(cockpit) }
    Box(Modifier.stageAt(665, 15, 190, 100)) { PedestalBlankPlate() }
    Box(Modifier.stageAt(665, 121, 190, 100)) { PedestalBlankPlate() }
    Box(Modifier.stageAt(665, 227, 190, 102)) { PedestalTransponderUnit(cockpit) }
}
