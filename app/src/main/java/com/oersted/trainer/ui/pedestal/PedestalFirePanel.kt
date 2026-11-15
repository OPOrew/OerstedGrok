package com.oersted.trainer.ui.pedestal

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.FirePanelRules
import com.oersted.trainer.sim.Pedestal
import com.oersted.trainer.ui.cockpit.CockpitModel

/** Design-px size of the FIRE / OVERHEAT panel (web `<Box x={270} y={772} w={560} h={146}>`). */
const val FIRE_PANEL_X = 270
const val FIRE_PANEL_Y = 772
const val FIRE_PANEL_W = 560
const val FIRE_PANEL_H = 146

private fun Modifier.at(x: Int, y: Int, w: Int? = null, h: Int? = null): Modifier =
    offset(x.dp, y.dp).let { m -> if (w != null && h != null) m.size(w.dp, h.dp) else m }

/**
 * Port of FireHandle + FirePanel (components/panel/CenterPedestal.tsx 854 to 957): the ENG 1 / ENG 2 / APU fire handles (locked until a
 * fire warning or the FIRE SWITCHES LOCK OVERRIDE; pull to isolate, then rotate L / R to fire a bottle), overheat detector selectors and
 * lenses, the TEST spring switch, the bell cutout, and the hold-to-test ENGINES EXT TEST button with its squib-continuity sockets.
 * Rules are in sim/FirePanelRules.kt (tested). Compose only; verified on CI, not compiled locally.
 */
@Composable
fun PedestalFirePanel(cockpit: CockpitModel) {
    val s by cockpit.simState.collectAsState()
    val d by cockpit.derived.collectAsState()
    val p by cockpit.pedestalState.collectAsState()
    val pw = d.dcPower
    // web `Date.now() < p.fireOverrideUntil`, plus the web's `setTimeout(() => usePedestal.setState({}), 12100)` repaint when it lapses
    var lapse by remember { mutableStateOf(0) }
    LaunchedEffect(p.fireOverrideUntil) {
        val left = p.fireOverrideUntil - System.currentTimeMillis()
        if (left > 0) {
            kotlinx.coroutines.delay(left.toLong() + 100L)
            lapse++
        }
    }
    @Suppress("UNUSED_VARIABLE") val repaint = lapse
    val override = System.currentTimeMillis() < p.fireOverrideUntil
    val testOv = pw && p.fireTest == Pedestal.FireTest.OVHT_FIRE
    val testFault = pw && p.fireTest == Pedestal.FireTest.FAULT
    val anyWarn = FirePanelRules.anyWarn(d)
    val extHeld = p.extTest != Pedestal.ExtTest.OFF

    // the bell cutout latches until every fire warning has cleared
    LaunchedEffect(anyWarn, p.bellSilenced) {
        if (FirePanelRules.bellShouldReset(anyWarn, p.bellSilenced)) cockpit.updatePedestal { it.copy(bellSilenced = false) }
    }

    fun act(a: FirePanelRules.Action?) {
        if (a != null) cockpit.patchSimNote(a.note, a.edit)
    }

    PaintedPanel(Modifier.stageAtFire()) {
        Rivets()
        Chips()
        // ENG 1 column: OVHT DET / ENG 1 OVERHEAT / TEST
        Bat3(
            options = listOf("A", "NORMAL", "B"),
            value = p.ovhtDet.first.name,
            onChange = { v -> cockpit.updatePedestal { it.copy(ovhtDet = Pair(Pedestal.OvhtDet.valueOf(v), it.ovhtDet.second)) } },
            modifier = Modifier.at(2, 8),
            label = "OVHT DET",
            labels = listOf("OVHT", "NORM", "FIRE"),
        )
        Lens("ENG 1\nOVERHEAT", FirePanelRules.overheatLit(pw, testOv, s, 1), Modifier.at(6, 64, 54, 34))
        SpringSwitch(
            up = "F/INOP", down = "OVHT",
            value = when (p.fireTest) { Pedestal.FireTest.FAULT -> "UP"; Pedestal.FireTest.OVHT_FIRE -> "DOWN"; else -> "MID" },
            onChange = { v ->
                cockpit.updatePedestal {
                    it.copy(fireTest = when (v) { "UP" -> Pedestal.FireTest.FAULT; "DOWN" -> Pedestal.FireTest.OVHT_FIRE; else -> Pedestal.FireTest.OFF })
                }
            },
            modifier = Modifier.at(143, 54),
            label = "TEST",
        )
        // DISCH 1 + WHEEL WELL
        FireHandle(
            "ENG 1", "1", red = true, pulled = s.engine1FirePulled, lit = pw && (d.engine1FireWarn || testOv),
            locked = FirePanelRules.engineLocked(s, 1, override), modifier = Modifier.at(72, 6),
            onPull = { act(FirePanelRules.pullEngine(s, 1, override)) },
            onRotate = { dir -> act(FirePanelRules.dischargeEngine(s, 1, dir[0])) },
        )
        Lens("WHEEL\nWELL", pw && (d.wheelWellFireWarn || testOv), Modifier.at(134, 8, 50, 34), AnnunTone.RED)
        // APU lenses, handle, bell cutout
        Lens("APU BOTTLE\nDISCHARGED", pw && s.apuBottleFired, Modifier.at(192, 44, 56, 34))
        Lens("APU DET\nINOP", testFault, Modifier.at(192, 82, 56, 34))
        Lbl("BELL CUTOUT", Modifier.at(254, 1, 70, 8))
        FireHandle(
            "APU", "APU", red = false, pulled = s.apuFirePulled, lit = pw && (d.apuFireWarn || testOv),
            locked = FirePanelRules.apuLocked(s, override), modifier = Modifier.at(264, 10),
            onPull = { act(FirePanelRules.pullApu(s, override)) },
            onRotate = { act(FirePanelRules.dischargeApu(s)) },
        )
        val apuWarn = pw && d.apuFireWarn
        Box(
            Modifier.at(236, 126, 14, 14).border(2.dp, Color(0xFF8A8E93), CircleShape).background(
                if (apuWarn) Brush.verticalGradient(listOf(Color(0xFFFFD27A), Color(0xFFFFD27A)))
                else Brush.radialGradient(listOf(Color(0xFFE6CFA0), Color(0xFFA8895A))),
                CircleShape,
            ),
        )
        Box(
            Modifier
                .at(324, 124, 18, 18)
                .border(2.dp, Color(0xFF8A8E93), CircleShape)
                .background(
                    if (p.bellSilenced) Brush.verticalGradient(listOf(Color(0xFF3A3D41), Color(0xFF3A3D41)))
                    else Brush.radialGradient(listOf(Color(0xFF3A3D41), Color(0xFF0C0D0E))),
                    CircleShape,
                )
                .clickable { cockpit.updatePedestal { it.copy(bellSilenced = true) } },
        )
        // ENG 2 column
        Bat3(
            options = listOf("A", "NORMAL", "B"),
            value = p.ovhtDet.second.name,
            onChange = { v -> cockpit.updatePedestal { it.copy(ovhtDet = Pair(it.ovhtDet.first, Pedestal.OvhtDet.valueOf(v))) } },
            modifier = Modifier.at(330, 8),
            label = "OVHT DET",
            labels = listOf("OVHT", "NORM", "FIRE"),
        )
        Lens("ENG 2\nOVERHEAT", FirePanelRules.overheatLit(pw, testOv, s, 2), Modifier.at(330, 64, 54, 34))
        Lens("L BOTTLE\nDISCHARGED", pw && s.fireBottleLDischarged, Modifier.at(392, 8, 50, 34))
        Lens("R BOTTLE\nDISCHARGED", pw && s.fireBottleRDischarged, Modifier.at(392, 46, 50, 34))
        FireHandle(
            "ENG 2", "2", red = true, pulled = s.engine2FirePulled, lit = pw && (d.engine2FireWarn || testOv),
            locked = FirePanelRules.engineLocked(s, 2, override), modifier = Modifier.at(448, 6),
            onPull = { act(FirePanelRules.pullEngine(s, 2, override)) },
            onRotate = { dir -> act(FirePanelRules.dischargeEngine(s, 2, dir[0])) },
        )
        // ENGINES EXT TEST: hold the button; each socket lights while its squib is intact
        Box(Modifier.at(506, 8, 50, 116)) {
            PedestalLegend("ENGINES TEST", Modifier.offset(0.dp, 0.dp), size = 6)
            Box(
                Modifier
                    .offset(0.dp, 14.dp)
                    .size(18.dp)
                    .border(3.dp, Color(0xFF1A1D20), CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF4A5058), Color(0xFF1A1D20))), CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            cockpit.updatePedestal { it.copy(extTest = Pedestal.ExtTest.ONE) }
                            tryAwaitRelease()
                            cockpit.updatePedestal { it.copy(extTest = Pedestal.ExtTest.OFF) }
                        })
                    },
            )
            Socket(24, 14, FirePanelRules.extLampLit(pw, extHeld, !s.fireBottleRDischarged), "R")
            Socket(12, 46, FirePanelRules.extLampLit(pw, extHeld, !s.fireBottleLDischarged), "L", below = true)
            Socket(36, 46, FirePanelRules.extLampLit(pw, extHeld, !s.apuBottleFired), "APU", below = true)
        }
    }
}

/** The panel's slot on the stage. */
private fun Modifier.stageAtFire(): Modifier = stageAt(FIRE_PANEL_X, FIRE_PANEL_Y, FIRE_PANEL_W, FIRE_PANEL_H)

/** web `sock`: a squib-continuity socket (a 16 px ring, green when the bottle circuit is intact and the test is held) with its tag. */
@Composable
private fun Socket(x: Int, y: Int, on: Boolean, tag: String, below: Boolean = false) {
    Box(
        Modifier
            .offset(x.dp, y.dp)
            .size(16.dp)
            .background(if (on) Color(0xFF38D27A) else Color(0xFF0D0F10), CircleShape)
            .border(3.dp, Color(0xFF8A8E93), CircleShape),
    )
    val tx = if (below) x + 8f - tag.length * 2.4f else x + 18f
    val ty = if (below) y + 19 else y + 4
    PedestalLegend(tag, Modifier.offset(tx.dp, ty.dp), size = 6)
}

/**
 * web FireHandle: a 54 x 134 slot with the red T-handle. Pulling slides it up; once pulled, rotate buttons appear (L / R for an engine,
 * DISCH for the APU). A locked handle that is tapped wiggles for 250 ms. The tap target is the handle itself.
 */
@Composable
private fun FireHandle(
    label: String,
    num: String,
    red: Boolean,
    pulled: Boolean,
    lit: Boolean,
    locked: Boolean,
    modifier: Modifier = Modifier,
    onPull: () -> Unit,
    onRotate: (String) -> Unit,
) {
    var wiggle by remember { mutableStateOf(false) }
    LaunchedEffect(wiggle) {
        if (wiggle) {
            kotlinx.coroutines.delay(250)
            wiggle = false
        }
    }
    val top: Dp by animateDpAsState(if (pulled) 2.dp else 10.dp, tween(200), label = "fireHandle")
    val slot = RoundedCornerShape(3.dp)
    val handle = RoundedCornerShape(6.dp)
    val small = 6.sp
    val pale = Color(0xFFF6DEDE)
    Box(modifier.size(54.dp, 134.dp).background(Color(0xFF0A0B0D), slot).border(1.dp, Color(0xFF050607), slot)) {
        Box(
            Modifier
                .offset(if (wiggle) 6.dp else 4.dp, top)
                .size(44.dp, 118.dp)
                .background(
                    if (lit) Brush.verticalGradient(listOf(Color(0xFFFF3B3B), Color(0xFFFF3B3B)))
                    else Brush.verticalGradient(listOf(Color(0xFFD62B2B), Color(0xFF9A1717))),
                    handle,
                )
                .border(if (lit) 1.dp else 2.dp, if (lit) Color(0xFFFF9A9A) else Color(0xFF3A0D0D), handle)
                .clickable {
                    if (locked && !pulled) wiggle = true
                    onPull()
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            Text("DISCH", Modifier.padding(top = 4.dp), color = pale, fontSize = small, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text(
                if (red) "L ⟲ R" else "⟲", Modifier.padding(top = 13.dp), color = pale, fontSize = small,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            )
            Text(
                num, Modifier.padding(top = 22.dp), color = Color.White, fontSize = if (red) 44.sp else 32.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
        }
        if (pulled) {
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp)) {
                for (l in FirePanelRules.rotateLabels(red)) {
                    Box(
                        Modifier
                            .padding(horizontal = 1.5.dp)
                            .background(Color(0xFFF1D0D0), RoundedCornerShape(2.dp))
                            .border(1.dp, Color(0xFF3A0C0C), RoundedCornerShape(2.dp))
                            .clickable { onRotate(l) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Text(l, color = Color(0xFF4A0C0C), fontSize = small, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
