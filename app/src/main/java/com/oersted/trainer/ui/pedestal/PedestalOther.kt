package com.oersted.trainer.ui.pedestal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.Pedestal
import com.oersted.trainer.sim.PedestalLowerRules as Lower
import com.oersted.trainer.sim.PedestalPartsRules
import com.oersted.trainer.sim.PedestalTopDeckRules
import com.oersted.trainer.ui.cockpit.CockpitModel

/**
 * Port of OtherFunctions (components/panel/CenterPedestal.tsx 1463 to 1525): the functions that are not on the photographed pedestal
 * (STAB TRIM cutouts and NOSE UP / DN, FIRE SWITCHES LOCK OVRD, STAB TRIM OVERRIDE and CAB DOOR, CARGO FIRE, the two MIC / audio panels
 * and the DATA PRINTER). The web shows them in a grid under the pedestal; here they stack in one column. Rules in sim/PedestalLowerRules.kt.
 */
@Composable
fun PedestalOtherFunctions(cockpit: CockpitModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StabTrimPlate(cockpit)
        Plate("FIRE SWITCHES") {
            PushButton("LOCK OVRD", onPress = {
                cockpit.updatePedestal { it.copy(fireOverrideUntil = Lower.lockOverrideUntil(System.currentTimeMillis())) }
            })
        }
        StabOverrideCabDoor(cockpit)
        CargoFirePanel(cockpit)
        MicPanel(cockpit, "L")
        MicPanel(cockpit, "R")
        DataPrinter(cockpit)
    }
}

@Composable
private fun StabTrimPlate(cockpit: CockpitModel) {
    val s by cockpit.simState.collectAsState()
    fun trim(dir: Int) {
        val u = Lower.stabTrimPress(cockpit.simState.value, dir) ?: return
        cockpit.patchSimNote("STABILIZER TRIM INPUT") { stabTrimUnits = u }
    }
    Plate("STAB TRIM", Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            Toggle(
                on = s.stabTrimCutoutA, guarded = true, label = "CUTOUT A", upLabel = "CUTOUT",
                onChange = { v -> cockpit.patchSimNote(Lower.cutoutNote('A', v)) { stabTrimCutoutA = v } },
            )
            Toggle(
                on = s.stabTrimCutoutB, guarded = true, label = "CUTOUT B", upLabel = "CUTOUT",
                onChange = { v -> cockpit.patchSimNote(Lower.cutoutNote('B', v)) { stabTrimCutoutB = v } },
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RepeatButton("NOSE DN", onTick = { trim(-1) })
                RepeatButton("NOSE UP", onTick = { trim(1) })
                PedestalLegend("${PedestalTopDeckRules.toFixed(s.stabTrimUnits, 1)} U", size = 6, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun StabOverrideCabDoor(cockpit: CockpitModel) {
    val s by cockpit.simState.collectAsState()
    val p by cockpit.pedestalState.collectAsState()
    Plate(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Toggle(
                on = s.stabTrimOverride, label = "STAB TRIM", upLabel = "OVERRIDE", downLabel = "NORMAL", guarded = true,
                onChange = { v -> cockpit.patchSimNote(Lower.stabOverrideNote(v)) { stabTrimOverride = v } },
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PedestalLegend("CAB DOOR", size = 7)
                Annun("CAB DOOR\nUNLOCKED", p.cabDoorUnlocked, AnnunTone.AMBER, Modifier.width(64.dp), onClick = {
                    cockpit.updatePedestal { it.copy(cabDoorUnlocked = !it.cabDoorUnlocked) }
                })
            }
        }
    }
}

@Composable
private fun CargoFirePanel(cockpit: CockpitModel) {
    val s by cockpit.simState.collectAsState()
    val d by cockpit.derived.collectAsState()
    val p by cockpit.pedestalState.collectAsState()
    val pw = d.dcPower
    val ext = Lower.cargoExtLit(pw, s, p.cargoExtTest)
    val shape = RoundedCornerShape(3.dp)
    Column(
        Modifier.width(150.dp).background(Color(0xFF5B5E62), shape).border(1.dp, Color(0xFF2B2E32), shape).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PedestalLegend("CARGO FIRE", size = 7)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PedestalLegend("EXT", size = 6)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Lamp(ext, size = 12.dp)
                    Lamp(ext, size = 12.dp)
                }
                PushButton("TEST", onPress = { cockpit.updatePedestal { it.copy(cargoExtTest = true) } }, onRelease = { cockpit.updatePedestal { it.copy(cargoExtTest = false) } })
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PedestalLegend("DET SELECT", size = 6)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Selector3(
                        listOf("A", "NORM", "B"), p.cargoDetFwd.name,
                        onChange = { v -> cockpit.updatePedestal { it.copy(cargoDetFwd = Pedestal.CargoDet.valueOf(v)) } }, label = "FWD",
                    )
                    Selector3(
                        listOf("A", "NORM", "B"), p.cargoDetAft.name,
                        onChange = { v -> cockpit.updatePedestal { it.copy(cargoDetAft = Pedestal.CargoDet.valueOf(v)) } }, label = "AFT",
                    )
                }
            }
        }
        Annun("DETECTOR\nFAULT", Lower.cargoFault(pw, s, p), AnnunTone.AMBER, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Annun("ARMED\nFWD", pw && s.cargoArmed == "FWD", AnnunTone.AMBER, Modifier.weight(1f), onClick = {
                val a = Lower.cargoArm(cockpit.simState.value, "FWD")
                cockpit.patchSimNote(a.note, a.edit)
            })
            Annun("ARMED\nAFT", pw && s.cargoArmed == "AFT", AnnunTone.RED, Modifier.weight(1f), onClick = {
                val a = Lower.cargoArm(cockpit.simState.value, "AFT")
                cockpit.patchSimNote(a.note, a.edit)
            })
        }
        Annun("DISCH", pw && s.cargoBottleDischarged, AnnunTone.AMBER, Modifier.fillMaxWidth(), onClick = {
            val a = Lower.cargoDischarge(cockpit.simState.value)
            if (a != null) cockpit.patchSimNote(a.note, a.edit)
        })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Annun("CARGO FWD", pw && d.cargoFwdFireWarn, AnnunTone.RED, Modifier.weight(1f), small = true)
            Annun("CARGO AFT", pw && d.cargoAftFireWarn, AnnunTone.RED, Modifier.weight(1f), small = true)
        }
    }
}

/** web LampToggle: a status lamp over a small latched toggle with an optional legend. */
@Composable
private fun LampToggle(on: Boolean, pw: Boolean, onChange: () -> Unit, label: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Lamp(on && pw, size = 5.dp)
        val shape = RoundedCornerShape(2.dp)
        Box(Modifier.size(width = 14.dp, height = 22.dp).background(Color(0xFF14161A), shape).border(1.dp, Color(0xFF15171A), shape).clickable { onChange() }) {
            Canvas(Modifier.size(width = 14.dp, height = 22.dp)) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val len = 10.dp.toPx() * (if (on) -1f else 1f)
                drawLine(Color(0xFFE2E4E6), c, Offset(c.x, c.y + len), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(Color(0xFF2A2D31), radius = 2.5.dp.toPx(), center = c)
            }
        }
        if (label != null) PedestalLegend(label, size = 5)
    }
}

@Composable
private fun MicPanel(cockpit: CockpitModel, side: String) {
    val p by cockpit.pedestalState.collectAsState()
    val d by cockpit.derived.collectAsState()
    val pw = d.dcPower
    val left = side == "L"
    fun k(n: String) = Lower.key(side, n)
    val tx = Lower.transmitting(p.mic, side)
    val rtIc = if (left) p.micRtIc.l else p.micRtIc.r
    val mask = if (left) p.micMask.l else p.micMask.r
    val vbr = if (left) p.micVbr.l else p.micVbr.r
    val alt = if (left) p.micAlt.l else p.micAlt.r
    fun <T> pair(cur: Pedestal.Pair2<T>, v: T) = if (left) cur.copy(l = v) else cur.copy(r = v)
    Plate(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (r in Lower.RX) {
                val armed = p.mic[k("tx:$r")] == true
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    PedestalLegend(r, size = 5)
                    val shape = RoundedCornerShape(2.dp)
                    val lit = armed && pw
                    Column(
                        Modifier.size(width = 18.dp, height = 28.dp)
                            .background(if (lit) Color(0xFFF4EFD8) else Color(0xFF26282B), shape)
                            .border(1.dp, Color(0xFF15171A), shape)
                            .clickable { cockpit.updatePedestal { it.copy(mic = Lower.selectTx(it.mic, side, r)) } },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val ink = if (lit) Color.Black else Color(0xFFE6E1D2)
                        Text("≡", color = ink, fontSize = 7.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text("C", color = ink, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    LampToggle(p.mic[k("vol:$r")] == true, pw, { cockpit.updatePedestal { it.copy(mic = Lower.flip(it.mic, k("vol:$r"))) } })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, start = 14.dp, end = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            for (key in Lower.AUD) {
                LampToggle(p.mic[k("rx:$key")] == true, pw, { cockpit.updatePedestal { it.copy(mic = Lower.flip(it.mic, k("rx:$key"))) } }, key)
            }
        }
        PedestalLegend("1 – NAV – 2", Modifier.fillMaxWidth().alpha(0.7f), size = 5, textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Toggle(on = !rtIc, upLabel = "R/T", downLabel = "I/C", onChange = { v -> cockpit.updatePedestal { it.copy(micRtIc = pair(it.micRtIc, !v)) } })
            Toggle(
                on = mask == Pedestal.MicMask.MASK, upLabel = "MASK", downLabel = "BOOM",
                onChange = { v -> cockpit.updatePedestal { it.copy(micMask = pair(it.micMask, if (v) Pedestal.MicMask.MASK else Pedestal.MicMask.BOOM)) } },
            )
            Selector3(listOf("V", "B", "R"), vbr, onChange = { v -> cockpit.updatePedestal { it.copy(micVbr = pair(it.micVbr, v)) } }, label = "V · B · R")
            Toggle(on = alt, upLabel = "ALT", downLabel = "NORM", onChange = { v -> cockpit.updatePedestal { it.copy(micAlt = pair(it.micAlt, v)) } })
        }
        val active = Lower.pttActive(pw, p.ptt, tx)
        val keyShape = RoundedCornerShape(4.dp)
        Box(
            Modifier.padding(top = 4.dp)
                .background(if (active) Color(0x4DF59E0B) else Color(0xFF26282B), keyShape)
                .border(1.dp, if (active) Color(0xFFFBBF24) else Color(0xFF555A60), keyShape)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        cockpit.updatePedestal { it.copy(ptt = true) }
                        try {
                            tryAwaitRelease()
                        } finally {
                            cockpit.updatePedestal { it.copy(ptt = false) }
                        }
                    })
                }
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text("PTT", color = if (active) Color(0xFFFEF3C7) else Color(0xFFE6E1D2), fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
        PedestalLegend(Lower.micStatus(pw, p.ptt, tx), Modifier.fillMaxWidth().padding(top = 4.dp), size = 6, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DataPrinter(cockpit: CockpitModel) {
    val d by cockpit.derived.collectAsState()
    val pw = d.dcPower
    var paper by remember { mutableStateOf(true) }
    var test by remember { mutableStateOf(false) }
    Plate(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until 3) {
                Box(Modifier.size(width = 24.dp, height = 12.dp).background(if (i < 2) Color(0xFF7A8F62) else Color(0xFF394B39), RoundedCornerShape(2.dp)).border(1.dp, Color(0xFF15171A), RoundedCornerShape(2.dp)))
            }
            PushButton("TEST", onPress = { test = true }, onRelease = { test = false })
            Annun("MSG", Lower.printerMsgLit(pw, test), AnnunTone.BLUE, Modifier.width(32.dp), small = true)
            Annun("PAPER", Lower.printerPaperLit(pw, test, paper), AnnunTone.AMBER, Modifier.width(32.dp), small = true)
            Annun("FAIL", Lower.printerMsgLit(pw, test), AnnunTone.AMBER, Modifier.width(32.dp), small = true)
        }
        val body = RoundedCornerShape(6.dp)
        Box(
            Modifier.background(Color(0xFF4D5054), body).border(1.dp, Color(0xFF2B2E32), body).padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("DATA PRINTER", color = Color(0xFFE6E1D2), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val tray = RoundedCornerShape(2.dp)
            Column(
                Modifier.size(width = 48.dp, height = 56.dp).background(Color(0xFFB9B6AA), tray).border(1.dp, Color(0xFF15171A), tray)
                    .clickable { paper = !paper }.padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.SpaceAround,
            ) {
                val ink = Color(0xFF2A2A24)
                Text("FULL", color = ink, fontSize = 6.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Box(Modifier.fillMaxWidth().height(1.dp).background(if (paper) Color.Black else Color.Transparent))
                Text("EMPTY", color = ink, fontSize = 6.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text("PAPER", color = ink, fontSize = 5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            PedestalLegend(Lower.paperText(paper), size = 7)
        }
    }
}

/** Thrust-lever coupling button of the web CenterPedestal header: COUPLED / SPLIT plus both throttle percentages (switch `thrustLinked`). */
@Composable
fun ThrustLinkButton(cockpit: CockpitModel, modifier: Modifier = Modifier) {
    val p by cockpit.pedestalState.collectAsState()
    val s by cockpit.simState.collectAsState()
    val linked = PedestalPartsRules.swOf(p, "thrustLinked", true)
    val pct1 = Math.round(s.throttle1 * 100)
    val pct2 = Math.round(s.throttle2 * 100)
    val shape = RoundedCornerShape(4.dp)
    val tone = if (linked) Color(0xFF38BDF8) else Color(0xFF8A9099)
    Box(
        modifier
            .background(if (linked) Color(0x1A38BDF8) else Color(0xFF16191D), shape)
            .border(1.dp, if (linked) Color(0xB338BDF8) else Color(0xFF343A42), shape)
            .clickable { cockpit.updatePedestal { PedestalPartsRules.withSw(it, "thrustLinked", !linked) } }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            "THRUST LEVERS ${if (linked) "COUPLED" else "SPLIT"} · $pct1/$pct2%",
            color = tone, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
        )
    }
}
