package com.oersted.trainer.ui.pedestal

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.PedestalLowerRules as Lower
import com.oersted.trainer.ui.cockpit.CockpitModel

private fun Modifier.at(x: Int, y: Int, w: Int? = null, h: Int? = null): Modifier =
    offset(x.dp, y.dp).let { m -> if (w != null && h != null) m.size(w.dp, h.dp) else m }

private val Cream = Color(0xFFECE7D6)

/** web `slot`: a recessed dark-green slot with an inset shadow. */
@Composable
private fun Slot(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {}) {
    val shape = RoundedCornerShape(40.dp)
    Box(
        modifier
            .background(Brush.verticalGradient(listOf(Color(0xFF2A322E), Color(0xFF3C4640))), shape)
            .border(1.dp, Color(0xFF0A0C0B), shape),
    ) {
        Chips()
        content()
    }
}

/**
 * Port of LowerPlate (components/panel/CenterPedestal.tsx 1258 to 1313): VHF COMM jack, PANEL / FLOOD BRIGHT knobs, the rudder trim knob,
 * the screwed access cover and the STAB BRAKE RELEASE pull knob. Placed at stage (250, 1304), 585 x 253. Rules in sim/PedestalLowerRules.kt.
 */
@Composable
fun PedestalLowerPlate(cockpit: CockpitModel) {
    val s by cockpit.simState.collectAsState()
    val p by cockpit.pedestalState.collectAsState()
    var pull by remember { mutableStateOf(false) }
    var coverOpen by remember { mutableStateOf(false) }
    fun release(v: Boolean) = cockpit.updatePedestal { it.copy(sw = it.sw + (Lower.STAB_BRAKE_REL to v)) }
    val cover by animateFloatAsState(if (coverOpen) 1f else 0f, tween(200), label = "cover")

    PaintedPanel(Modifier.stageAt(250, 1304, 585, 253)) {
        Rivets()
        Chips()
        // VHF COMM jack
        Tiny("VHF COMM", Modifier.at(56, 8))
        val jack = RoundedCornerShape(2.dp)
        Column(
            Modifier.at(56, 22).background(Color(0xFFD8D2BC), jack).border(1.dp, Color(0xFF6A5A3A), jack).padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(2) {
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(2) { Box(Modifier.size(12.dp).background(Color(0xFF2A2418), jack)) }
                }
            }
        }
        // PANEL / FLOOD BRIGHT knobs
        Box(Modifier.at(34, 84)) { Knob(value = s.ltPanelPed, onChange = { v -> cockpit.patchSim { ltPanelPed = Lower.panelBright(v) } }, size = 46.dp) }
        Box(Modifier.at(34, 168)) { Knob(value = p.flood, onChange = { v -> cockpit.updatePedestal { it.copy(flood = v) } }, size = 46.dp) }
        Tiny("PANEL\nBRIGHT", Modifier.at(88, 96))
        Tiny("FLOOD\nBRIGHT", Modifier.at(88, 180))
        // rudder trim: dished scalloped knob
        Box(Modifier.at(126, 14, 228, 228)) {
            Box(Modifier.at(10, 10)) {
                BlackKnob(
                    size = 208.dp, min = -1.0, max = 1.0, step = 1.0 / 15.0, value = p.rudderTrim,
                    onChange = { v -> cockpit.updatePedestal { it.copy(rudderTrim = Lower.rudderValue(v)) } },
                    onDouble = { cockpit.updatePedestal { it.copy(rudderTrim = 0.0) } },
                    ticks = listOf("15", "10", "5", "0", "5", "10", "15"),
                )
            }
            Text(
                Lower.rudderLabel(p.rudderTrim),
                Modifier.align(Alignment.TopCenter).padding(top = 118.dp),
                color = Cream, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            )
            Tiny("NOSE LEFT", Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 2.dp))
            Tiny("NOSE RIGHT", Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 2.dp))
        }
        // access recess closed by a screwed cover plate (tap to open / close)
        Box(Modifier.at(395, 36, 135, 55).clickable { coverOpen = !coverOpen }) {
            Slot(Modifier.fillMaxSize()) {
                Box(
                    Modifier.offset(10.dp, 14.dp).size(26.dp)
                        .shadow(2.dp, CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFFF0F0EE), Color(0xFF8A8E93))), CircleShape)
                        .border(1.dp, Color(0xFF3A3D41), CircleShape),
                )
            }
            val plate = RoundedCornerShape(30.dp)
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        scaleX = 1f - 0.62f * cover
                        translationX = -0.62f * size.width * cover
                    }
                    .shadow(2.dp, plate)
                    .background(Brush.linearGradient(listOf(Color(0xFF7A8780), Color(0xFF5F6C66), Color(0xFF505C57))), plate)
                    .border(1.dp, Color(0xFF1C2220), plate),
                contentAlignment = Alignment.Center,
            ) {
                for ((x, y) in listOf(14 to 11, 121 to 11, 14 to 44, 121 to 44)) {
                    Box(
                        Modifier.offset((x - 4).dp, (y - 4).dp).size(8.dp).align(Alignment.TopStart)
                            .background(Brush.radialGradient(listOf(Color(0xFFAEB5B1), Color(0xFF555C59))), CircleShape)
                            .border(1.dp, Color(0xFF1A1D1C), CircleShape),
                    )
                }
                Tiny("ACCESS")
            }
        }
        // STAB BRAKE RELEASE: recessed round slot, rope and the pull knob (hold)
        Slot(Modifier.at(458, 126, 92, 92))
        Box(Modifier.at(466, 134, 76, 130)) {
            Box(
                Modifier.align(Alignment.TopCenter).offset(y = 70.dp).size(3.dp, 44.dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        scaleY = if (pull) 1.6f else 1f
                    }
                    .background(Color(0xFFB9A47A)),
            )
            Box(
                Modifier.size(74.dp)
                    .offset(y = if (pull) 12.dp else 0.dp)
                    .shadow(5.dp, CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF3A3D41), Color(0xFF0E1012))), CircleShape)
                    .border(2.dp, Color(0xFF050607), CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            pull = true
                            release(true)
                            try {
                                tryAwaitRelease()
                            } finally {
                                pull = false
                                release(false)
                            }
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "STAB\nBRAKE\nRELEASE\nPULL", color = Cream, fontSize = 8.sp, lineHeight = 9.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Port of AileronDrum (CenterPedestal.tsx 1315 to 1342): drag the striped drum sideways, 0.004 of full travel per px. Stage (555, 1549), 202 x 78. */
@Composable
fun PedestalAileronDrum(cockpit: CockpitModel) {
    val p by cockpit.pedestalState.collectAsState()
    val trim = p.aileronTrim
    Box(Modifier.stageAt(555, 1549, 202, 78)) {
        val body = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 40.dp, bottomEnd = 40.dp)
        Box(
            Modifier.fillMaxSize().padding(top = 18.dp)
                .shadow(6.dp, body)
                .background(Color(0xFF0A0B0C), body)
                .border(2.dp, Color(0xFF050607), body),
        )
        val drum = RoundedCornerShape(6.dp)
        Box(
            Modifier.at(6, 22, 190, 48)
                .clip(drum)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dx ->
                        change.consume()
                        if (dx != 0f) cockpit.updatePedestal { it.copy(aileronTrim = Lower.aileronDrag(it.aileronTrim, dx.toDouble())) }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color(0xFF08090A))
                val period = 9.dp.toPx()
                val shift = (Lower.aileronStripeShift(trim).toFloat().dp.toPx()) % period
                var x = shift - period
                while (x < size.width) {
                    drawRect(Color(0xFF2A2D31), Offset(x + 6.dp.toPx(), 0f), Size(3.dp.toPx(), size.height))
                    x += period
                }
                drawRect(
                    Brush.verticalGradient(0f to Color(0x1FFFFFFF), 0.4f to Color.Transparent, 1f to Color(0x66000000)),
                )
            }
        }
        PedestalLegend("AILERON TRIM", Modifier.align(Alignment.TopCenter).padding(top = 4.dp), size = 8)
        Text(
            Lower.aileronLabel(trim),
            Modifier.fillMaxWidth().padding(top = 36.dp),
            color = Cream, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            style = TextStyle(shadow = Shadow(Color.Black, Offset(0f, 1f), 2f)),
        )
    }
}
