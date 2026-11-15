package com.oersted.trainer.ui.pedestal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.Pedestal
import com.oersted.trainer.sim.PedestalRadioRules as Radio
import com.oersted.trainer.sim.PedestalTopDeckRules
import com.oersted.trainer.ui.cockpit.CockpitModel

/** Design-px bottom of the radio stack (ADF / DATA row: y 1172 + 124). */
const val PEDESTAL_RADIOS_BOTTOM = 1296

/**
 * Port of Radios (components/panel/CenterPedestal.tsx 982 to 1245): VHF COMM L/R, VHF NAV L/R (with DME mode and test switches), the ADF
 * control head, the two HF units and the DATA display. Rules are in sim/PedestalRadioRules.kt (tested).
 *
 * As on the web, the HF units sit in the ADF box's normal flow, one below the other, i.e. at y 1296 and 1420 behind the lower plate. They
 * are drawn here at the same stage position and stay hidden until the lower plate is ported over them. Compose only; verified on CI.
 */
@Composable
fun PedestalRadios(cockpit: CockpitModel) {
    Box(Modifier.stageAt(262, 920, 272, 124)) { PedestalVhfUnit(cockpit, 0) }
    Box(Modifier.stageAt(540, 920, 282, 124)) { PedestalVhfUnit(cockpit, 1) }
    Box(Modifier.stageAt(262, 1046, 272, 124)) { PedestalNavUnit(cockpit, 0) }
    Box(Modifier.stageAt(540, 1046, 282, 124)) { PedestalNavUnit(cockpit, 1) }
    Box(Modifier.stageAt(258, 1172, 276, 124)) { PedestalAdfUnit(cockpit) }
    Box(Modifier.stageAt(258, 1172 + 124, 276, 124)) { PedestalHfUnit(cockpit, 0) }
    Box(Modifier.stageAt(258, 1172 + 248, 276, 124)) { PedestalHfUnit(cockpit, 1) }
    Box(Modifier.stageAt(540, 1172, 282, 124)) { PedestalDataDisplay(cockpit) }
}

/** web RadioFrame: painted grey-green radio face with rivets. */
@Composable
private fun RadioFrame(content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(2.dp)
    PaintedPanel(Modifier.fillMaxSize().shadow(3.dp, shape)) {
        Rivets()
        content()
    }
}

/** web ROW: a centred column with a 5 px gap. */
@Composable
private fun RowCol(content: @Composable ColumnScope.() -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp), content = content)
}

/** One tuning half: legend, drum window and the dual knob. */
@Composable
private fun ColumnScope.TuneHalf(label: String, value: String, lit: Boolean, winW: Dp, knob: Dp, onOuter: (Int) -> Unit, onInner: (Int) -> Unit) {
    Tiny(label)
    DrumWin(value, lit, w = winW)
    DualTune(onOuter = onOuter, onInner = onInner, size = knob)
}

@Composable
fun PedestalVhfUnit(cockpit: CockpitModel, idx: Int) {
    val p by cockpit.pedestalState.collectAsState()
    val d by cockpit.derived.collectAsState()
    val pw = d.dcPower
    val st = if (idx == 0) p.commL else p.commR
    fun write(f: Pedestal.Freq) = cockpit.updatePedestal { if (idx == 0) it.copy(commL = f) else it.copy(commR = f) }
    fun tune(stby: Boolean, delta: Int) {
        val cur = if (idx == 0) cockpit.pedestalState.value.commL else cockpit.pedestalState.value.commR
        write(if (stby) cur.copy(stby = Radio.tuneVhf(cur.stby, delta)) else cur.copy(act = Radio.tuneVhf(cur.act, delta)))
    }
    RadioFrame {
        Chips()
        UnitGrid(
            left = { TuneHalf("STBY", PedestalTopDeckRules.vhfWin(st.stby), pw, 72.dp, 46.dp, { tune(true, it * 1000) }, { tune(true, it * 25) }) },
            right = { TuneHalf("ACTIVE", PedestalTopDeckRules.vhfWin(st.act), pw, 72.dp, 46.dp, { tune(false, it * 1000) }, { tune(false, it * 25) }) },
            mid = {
                RowCol {
                    Bat(on = false, onChange = { val c = if (idx == 0) cockpit.pedestalState.value.commL else cockpit.pedestalState.value.commR; write(Pedestal.Freq(act = c.stby, stby = c.act)) })
                    Tiny("TFR")
                }
                RowCol {
                    Bat(on = p.commTest, onChange = { v -> cockpit.updatePedestal { it.copy(commTest = v) } })
                    Tiny("COMM")
                    Tiny("TEST")
                }
            },
        )
    }
}

@Composable
fun PedestalNavUnit(cockpit: CockpitModel, idx: Int) {
    val p by cockpit.pedestalState.collectAsState()
    val d by cockpit.derived.collectAsState()
    val st = if (idx == 0) p.navL else p.navR
    val mode = if (idx == 0) p.dmeMode.first else p.dmeMode.second
    val lit = Radio.navLit(d.dcPower, mode)
    fun write(f: Pedestal.Freq) = cockpit.updatePedestal { if (idx == 0) it.copy(navL = f) else it.copy(navR = f) }
    fun cur() = if (idx == 0) cockpit.pedestalState.value.navL else cockpit.pedestalState.value.navR
    fun tune(stby: Boolean, delta: Int) {
        val c = cur()
        write(if (stby) c.copy(stby = Radio.tuneNav(c.stby, delta)) else c.copy(act = Radio.tuneNav(c.act, delta)))
    }
    RadioFrame {
        Chips()
        UnitGrid(
            midW = 62.dp,
            left = { TuneHalf("STBY", Radio.navWin(st.stby), lit, 72.dp, 46.dp, { tune(true, it * 1000) }, { tune(true, it * 50) }) },
            right = { TuneHalf("ACTIVE", Radio.navWin(st.act), lit, 72.dp, 46.dp, { tune(false, it * 1000) }, { tune(false, it * 50) }) },
            mid = {
                RowCol {
                    Bat(on = false, onChange = { val c = cur(); write(Pedestal.Freq(act = c.stby, stby = c.act)) })
                    Tiny("TFR")
                }
                Bat3(
                    options = Radio.DME_MODES,
                    value = Radio.DME_MODES.getOrElse(mode) { "NORM" },
                    onChange = { v -> cockpit.updatePedestal { it.copy(dmeMode = Radio.setPair(it.dmeMode, idx, Radio.DME_MODES.indexOf(v))) } },
                    labels = Radio.DME_MODES,
                )
                // wider than the centre column, as on the web (flex overflow)
                Row(Modifier.wrapContentWidth(Alignment.CenterHorizontally, unbounded = true), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val dmeTest = if (idx == 0) p.dmeTest.first else p.dmeTest.second
                    val navTest = if (idx == 0) p.navTest.first else p.navTest.second
                    Bat(on = dmeTest, onChange = { v -> cockpit.updatePedestal { it.copy(dmeTest = Radio.setPair(it.dmeTest, idx, v)) } })
                    Tiny("DME TST")
                    Bat(on = navTest, onChange = { v -> cockpit.updatePedestal { it.copy(navTest = Radio.setPair(it.navTest, idx, v)) } })
                    Tiny("NAV TST")
                    TestLamp(pw = lit, label = "VOR")
                }
            },
        )
    }
}

@Composable
fun PedestalAdfUnit(cockpit: CockpitModel) {
    val p by cockpit.pedestalState.collectAsState()
    val d by cockpit.derived.collectAsState()
    val pw = d.dcPower
    val g1 = p.adfMode.first
    val g2 = p.adfMode.second
    fun adj(left: Boolean, delta: Int) = cockpit.updatePedestal {
        it.copy(adf = if (left) it.adf.copy(l = Radio.adjAdf(it.adf.l, delta)) else it.adf.copy(r = Radio.adjAdf(it.adf.r, delta)))
    }
    fun setMode(side: Int, v: Int) = cockpit.updatePedestal {
        val (modes, ant) = Radio.adfSetMode(it.adfMode, it.adfAnt, side, v)
        it.copy(adfMode = modes, adfAnt = ant)
    }
    @Composable
    fun gain(side: Int, g: Int) = RowCol {
        MiniKnob(value = g.toDouble(), onChange = { v -> setMode(side, v.toInt()) }, size = 26.dp, min = 0.0, max = 4.0, step = 1.0)
        Tiny(Radio.ADF_MODES.getOrElse(g) { "" })
    }
    RadioFrame {
        Chips()
        UnitGrid(
            midW = 62.dp,
            left = { TuneHalf("ADF 1", Radio.adfText(p.adf.l, g1), Radio.adfLit(pw, g1), 80.dp, 46.dp, { adj(true, it * 10) }, { adj(true, it) }) },
            right = { TuneHalf("ADF 2", Radio.adfText(p.adf.r, g2), Radio.adfLit(pw, g2), 80.dp, 46.dp, { adj(false, it * 10) }, { adj(false, it) }) },
            mid = {
                gain(0, g1)
                RowCol { Bat(on = p.adfTone, onChange = { v -> cockpit.updatePedestal { it.copy(adfTone = v) } }, up = "TONE") }
                gain(1, g2)
            },
        )
    }
}

@Composable
fun PedestalHfUnit(cockpit: CockpitModel, idx: Int) {
    val p by cockpit.pedestalState.collectAsState()
    val d by cockpit.derived.collectAsState()
    val s by cockpit.simState.collectAsState()
    val st = if (idx == 0) p.hfL else p.hfR
    val lit = Radio.hfLit(d.dcPower, s.circuitBreakers[if (idx == 0) "hf-1" else "hf-2"], st.mode)
    fun write(f: Pedestal.Hf) = cockpit.updatePedestal { if (idx == 0) it.copy(hfL = f) else it.copy(hfR = f) }
    fun cur() = if (idx == 0) cockpit.pedestalState.value.hfL else cockpit.pedestalState.value.hfR
    fun tune(stby: Boolean, delta: Int) {
        val c = cur()
        write(if (stby) c.copy(stby = Radio.tuneHf(c.stby, delta)) else c.copy(act = Radio.tuneHf(c.act, delta)))
    }
    val side = if (idx == 0) "L" else "R"
    RadioFrame {
        Chips()
        UnitGrid(
            midW = 58.dp,
            left = { TuneHalf("STBY", st.stby.toString(), lit, 72.dp, 42.dp, { tune(true, it * 100) }, { tune(true, it) }) },
            right = { TuneHalf("ACTIVE", st.act.toString(), lit, 72.dp, 42.dp, { tune(false, it * 100) }, { tune(false, it) }) },
            mid = {
                RowCol {
                    Bat(on = false, onChange = { val c = cur(); write(c.copy(act = c.stby, stby = c.act)) })
                    Tiny("TFR")
                }
                Bat3(
                    options = Radio.HF_MODES,
                    value = Radio.HF_MODES.getOrElse(st.mode) { "OFF" },
                    onChange = { v -> write(cur().copy(mode = Radio.HF_MODES.indexOf(v))) },
                    labels = Radio.HF_MODES,
                )
                RowCol {
                    Bat(on = false, onChange = { cockpit.updatePedestal { it.copy(hfTuneReq = Radio.setPair(it.hfTuneReq, idx, true)) } })
                    Tiny("TUNE")
                }
                Tiny("HF $side")
            },
        )
    }
}

@Composable
fun PedestalDataDisplay(cockpit: CockpitModel) {
    val p by cockpit.pedestalState.collectAsState()
    val d by cockpit.derived.collectAsState()
    val m by cockpit.mipState.collectAsState()
    val lines = Radio.dataLines(p, m.comAct)
    val shape = RoundedCornerShape(3.dp)
    PaintedPanel(Modifier.fillMaxSize()) {
        Rivets()
        Chips()
        Box(
            Modifier
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .fillMaxSize()
                .clip(shape)
                .background(Color(0xFF020303), shape),
        ) {
            if (d.dcPower) {
                Text(
                    lines.joinToString("\n"),
                    modifier = Modifier.padding(start = 9.dp + 8.dp, top = 9.dp + 3.dp),
                    style = TextStyle(
                        color = Color(0xFFFFB21A),
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        shadow = Shadow(Color(0xB3FFB21A), Offset.Zero, 5f),
                    ),
                    softWrap = false,
                )
            }
            // scan lines + glass glare, then the 9 px bezel
            Canvas(Modifier.fillMaxSize()) {
                val step = 3.dp.toPx()
                var y = 0f
                while (y < size.height) {
                    drawRect(Color(0x09FFFFFF), Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
                    y += step
                }
                drawRect(Brush.linearGradient(listOf(Color(0x1AFFFFFF), Color.Transparent), start = Offset.Zero, end = Offset(size.width * 0.38f, size.height * 0.62f)))
            }
            Box(Modifier.fillMaxSize().border(9.dp, Color(0xFF6D7C78), shape))
        }
    }
}
