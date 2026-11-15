package com.oersted.trainer.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.LightTestLogic
import com.oersted.trainer.sim.PedestalTopDeckRules
import com.oersted.trainer.ui.cockpit.CockpitModel
import com.oersted.trainer.ui.instruments.DialBand
import com.oersted.trainer.ui.instruments.DialWin
import com.oersted.trainer.ui.instruments.EprCounter
import com.oersted.trainer.ui.instruments.FlapDial
import com.oersted.trainer.ui.instruments.GearLever
import com.oersted.trainer.ui.instruments.RoundDial
import com.oersted.trainer.ui.instruments.YawIndicator
import com.oersted.trainer.ui.pedestal.Annun
import com.oersted.trainer.ui.pedestal.AnnunTone
import com.oersted.trainer.ui.pedestal.Knob
import com.oersted.trainer.ui.pedestal.PedestalLegend
import com.oersted.trainer.ui.pedestal.Toggle

/*
 * Port of `EnginePanels` and `GearColumn` in components/panel/MainInstrumentPanel.tsx (lines 388-565): the cream engine strip
 * (brake test, EPR, N1, N2, fuel flow), the dark systems panel (yaw damper and oil quantity test, annunciators, oil / EGT / hydraulic
 * dials, flap gauge, LE flap and anti-skid lights, auto brake, placards) and the gear lights + lever column. Placed inside the
 * 3036 x 720 canvas of MainInstrumentPanel.kt. Compose only; verified on CI, not compiled locally.
 */

private const val X_CREAM = 1300
private const val X_DARK = 1510
private const val X_GEAR = 1912
private const val KG_TO_LB = 2.2046
private val AB = listOf("OFF", "MIN", "MED", "MAX", "RTO")

private val BANDS_N = listOf(DialBand(0.0, 100.0, PanelGreen), DialBand(100.0, 110.0, PanelRed))
private val BANDS_EGT = listOf(DialBand(0.0, 620.0, PanelGreen), DialBand(620.0, 700.0, PanelAmber), DialBand(700.0, 900.0, PanelRed))
private val BANDS_OIL = listOf(DialBand(0.0, 13.0, PanelRed), DialBand(40.0, 90.0, PanelGreen))
private val BANDS_OIL_T = listOf(DialBand(0.0, 40.0, Color(0xFF4A90D9)), DialBand(40.0, 140.0, PanelGreen), DialBand(140.0, 160.0, PanelRed))
private val BANDS_HYD = listOf(DialBand(2800.0, 3200.0, PanelGreen))

private fun f1(v: Double) = PedestalTopDeckRules.toFixed(v, 1)
private fun rnd(v: Double) = PedestalTopDeckRules.jsRound(v).toString()

/** One engine's readings, as the web's `e(n)`. */
private class Eng(
    val epr: Double, val n1: Double, val egt: Double, val n2: Double, val ff: Double, val oil: Double, val qty: Double,
    val run: Boolean, val rev: Boolean, val sv: Boolean, val lowOil: Boolean,
)

@Composable
internal fun MainPanelEngines(cockpit: CockpitModel) {
    val s by cockpit.simState.collectAsState()
    val d by cockpit.derived.collectAsState()
    val m by cockpit.mipState.collectAsState()
    val pw = d.dcPower
    fun ppH(kg: Double) = Math.max(0.0, kg) * KG_TO_LB
    val e1 = Eng(d.epr1, s.n1_1, s.egt1, s.n2_1, s.ff1, s.oilPsi1, s.oilQty1, d.eng1Running, d.thrustReverser1Active, d.startValveOpen1, d.eng1Running && d.oilPressure1 < 13)
    val e2 = Eng(d.epr2, s.n1_2, s.egt2, s.n2_2, s.ff2, s.oilPsi2, s.oilQty2, d.eng2Running, d.thrustReverser2Active, d.startValveOpen2, d.eng2Running && d.oilPressure2 < 13)
    fun oilT(x: Eng) = if (d.dcPower || x.run) 30 + Math.min(100.0, x.n2) * 0.95 else 20.0
    fun qtyGal(x: Eng) = if (m.oilQtyTest) 4.0 else x.qty / 20
    val sz = 90.dp
    val xl = X_CREAM + 52
    val xr = X_CREAM + 150
    val rows = listOf(168, 270, 372, 474, 576)
    val d1 = X_DARK + 50
    val d2 = X_DARK + 150
    val rows2 = listOf(276, 374, 472, 570)
    val rc = X_DARK + 204

    /* ── cream strip: brake test, EPR, N1, two empty cut-outs, N2, fuel flow ── */
    MpPlate(X_CREAM, 14, 200, 626, CreamTone, Color(0xFF9B9380))
    MpS(X_CREAM + 8, 22, 184, 92) {
        Column(
            Modifier.fillMaxSize().background(Color(0xFFC9C3AD), RoundedCornerShape(3.dp)).padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("SPEED BRAKE TEST", color = Color(0xFF2A2A2A), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                for (n in 1..3) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("$n", color = Color(0xFF2A2A2A), fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Box(
                            Modifier.size(28.dp)
                                .background(Brush.radialGradient(listOf(Color(0xFF3A3D41), Color(0xFF0B0C0E))), CircleShape)
                                .border(if (m.brakeTest == n) 5.dp else 4.dp, if (m.brakeTest == n) PanelAmber else Color(0xFF9FA3A7), CircleShape)
                                .pointerInput(n) {
                                    detectTapGestures(onPress = {
                                        cockpit.updateMip { it.copy(brakeTest = n) }
                                        tryAwaitRelease()
                                        cockpit.updateMip { it.copy(brakeTest = 0) }
                                    })
                                },
                        )
                    }
                }
            }
        }
    }
    for ((cx, e) in listOf(xl to e1, xr to e2)) {
        MpC(cx, rows[0], 45) {
            RoundDial("EPR", e.epr, 1.0, 2.4, 0.2, minor = 2, digits = 2, size = sz, caption = "", win = DialWin(if (pw) "" else null, 52f, 76f, 26f), bands = listOf(DialBand(2.0, 2.4, PanelRed)))
        }
        MpS(cx - 26, rows[0] + 6 - 40, 52, 16) { EprCounter(e.epr, pw, Modifier.fillMaxSize()) }
        MpTag(cx, rows[0] + 50, 80, "PULL TO SET")
        MpC(cx, rows[1], 45) {
            RoundDial("N1", e.n1, 0.0, 110.0, 10.0, minor = 2, scale = 10.0, size = sz, bands = BANDS_N, win = DialWin(if (pw) f1(e.n1) else null, y = 24f), caption = "${f1(e.n1)}%")
        }
        MpC(cx, rows[2], 45) { MpCutout() }
        MpC(cx, rows[3], 45) {
            RoundDial("N2", e.n2, 0.0, 110.0, 10.0, minor = 2, scale = 10.0, size = sz, bands = BANDS_N, win = DialWin(if (pw) f1(e.n2) else null, y = 24f), caption = "${f1(e.n2)}%")
        }
        MpC(cx, rows[4], 45) {
            RoundDial("FUEL FLOW", ppH(e.ff), 0.0, 5000.0, 1000.0, minor = 5, scale = 1000.0, size = sz, caption = "${rnd(ppH(e.ff))} pph")
        }
    }

    /* ── dark panel ── */
    MpPlate(X_DARK, 14, 392, 626, CentreTone)
    MpS(X_DARK + 8, 20, 190, 24) {
        Row(
            Modifier.fillMaxSize().background(Color(0xFFCFC9B6), RoundedCornerShape(2.dp)),
            horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically,
        ) {
            val ink = Color(0xFF3A3A3A)
            Text("YAW DAMPER\nL  TEST  R", color = ink, fontSize = 8.sp, lineHeight = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Box(Modifier.size(1.dp, 16.dp).background(Color(0xFF7A7566)))
            Text("ENG OIL\nQTY TEST", color = ink, fontSize = 8.sp, lineHeight = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
    MpC(X_DARK + 44, 92, 32) { YawIndicator(m.yawTest, Modifier.fillMaxSize()) }
    MpS(X_DARK + 90, 78, 30, 30) {
        Box(
            Modifier.fillMaxSize()
                .background(Brush.radialGradient(listOf(Color(0xFFF1F2F3), Color(0xFFB9BDC1))), CircleShape)
                .border(1.dp, Color(0xFF9AA0A6), CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = { p ->
                        // left half = L test, right half = R test
                        cockpit.updateMip { it.copy(yawTest = if (p.x < size.width / 2f) 1 else 2) }
                        tryAwaitRelease()
                        cockpit.updateMip { it.copy(yawTest = 0) }
                    })
                },
        )
    }
    MpS(X_DARK + 142, 76, 34, 34) {
        Box(
            Modifier.fillMaxSize()
                .background(Brush.radialGradient(listOf(Color(0xFF6A85A8), Color(0xFF1D2A3C))), CircleShape)
                .border(4.dp, if (m.oilQtyTest) PanelAmber else Color(0xFF8A8F95), CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        cockpit.updateMip { it.copy(oilQtyTest = true) }
                        tryAwaitRelease()
                        cockpit.updateMip { it.copy(oilQtyTest = false) }
                    })
                },
        )
    }
    MpS(X_DARK + 18, 126, 26, 26) { MpCap(26, light = true) }
    MpS(X_DARK + 100, 126, 26, 26) { MpCap(26, light = true) }
    // annunciators: REVERSER UNLOCKED / START VALVE OPEN x2, blank / OIL FILTER BYPASS / LOW OIL PRESSURE / OIL FILTER BYPASS
    MpS(X_DARK + 6, 160, 194, 66) {
        Column(Modifier.fillMaxSize().background(Color.Black, RoundedCornerShape(2.dp)).padding(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            @Composable
            fun RowScope.cell(text: String, on: Boolean) {
                Box(Modifier.weight(1f).fillMaxHeight()) { Annun(text, on, AnnunTone.AMBER, Modifier.fillMaxSize(), small = true) }
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                cell("REVERSER\nUNLOCKED", pw && e1.rev)
                cell("START\nVALVE OPEN", pw && e1.sv)
                cell("REVERSER\nUNLOCKED", pw && e2.rev)
                cell("START\nVALVE OPEN", pw && e2.sv)
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                // blank lens: engine 1 low-oil lens not fitted on RP-C8009
                Box(Modifier.weight(1f).fillMaxSize().background(Color(0xFFE9E6DC), RoundedCornerShape(2.dp)).border(1.dp, Color(0xFF9AA0A6), RoundedCornerShape(2.dp)))
                cell("OIL FILTER\nBYPASS", false)
                cell("LOW OIL\nPRESSURE", pw && (e1.lowOil || e2.lowOil))
                cell("OIL FILTER\nBYPASS", false)
            }
        }
    }
    for ((cx, e) in listOf(d1 to e1, d2 to e2)) {
        MpC(cx, rows2[0], 45) { RoundDial("OIL PRESS", e.oil, 0.0, 100.0, 20.0, minor = 4, size = sz, bands = BANDS_OIL, caption = "${rnd(e.oil)} psi") }
    }
    MpC(d1, rows2[1], 45) { RoundDial("OIL TEMP", oilT(e1), 0.0, 160.0, 40.0, minor = 4, size = sz, bands = BANDS_OIL_T, caption = "${rnd(oilT(e1))}°C") }
    MpC(d2, rows2[1], 45) {
        RoundDial("EGT", e2.egt, 0.0, 900.0, 100.0, minor = 2, scale = 100.0, size = sz, bands = BANDS_EGT, win = DialWin(if (pw) rnd(e2.egt) else null, 56f, 26f), caption = "${rnd(e2.egt)}°C")
    }
    MpC(d1, rows2[2], 45) {
        RoundDial("EGT", e1.egt, 0.0, 900.0, 100.0, minor = 2, scale = 100.0, size = sz, bands = BANDS_EGT, win = DialWin(if (pw) rnd(e1.egt) else null, 56f, 26f), caption = "${rnd(e1.egt)}°C")
    }
    MpC(d2, rows2[2], 45) {
        RoundDial("OIL QUANTITY", qtyGal(e2), 0.0, 5.0, 1.0, minor = 2, digits = 1, size = sz, caption = "${f1(qtyGal(e2))} gal", bands = listOf(DialBand(0.0, 1.0, PanelRed)))
    }
    MpC(d1, rows2[3], 45) { RoundDial("HYD SYS PRESS", s.hydAPress, 0.0, 4000.0, 1000.0, minor = 5, scale = 1000.0, size = sz, bands = BANDS_HYD, caption = "${rnd(s.hydAPress)} psi") }
    MpC(d2, rows2[3], 45) { RoundDial("HYD BRAKE PRESS", s.hydBPress, 0.0, 4000.0, 1000.0, minor = 5, scale = 1000.0, size = sz, bands = BANDS_HYD, caption = "${rnd(s.hydBPress)} psi") }

    /* ── right column: flap gauge, LE flaps, antiskid, autobrake, placards ── */
    MpS(rc, 20, 182, 50) { MpPlacard("Landing flaps 40° prohibited except for emergency", 8) }
    MpS(rc + 45, 91, 92, 92) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF050607)).padding(4.dp)) { FlapDial(s.flapPos, Modifier.fillMaxSize()) }
    }
    MpS(rc, 202, 182, 34) {
        Row(Modifier.fillMaxSize().background(Color.Black, RoundedCornerShape(2.dp)).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.weight(1f).fillMaxSize()) { Annun("LE FLAPS\nTRANSIT", pw && d.leTransit, AnnunTone.AMBER, Modifier.fillMaxSize(), small = true) }
            Box(Modifier.weight(1f).fillMaxSize()) { Annun("LE FLAPS\nEXT", pw && d.leExtended && !d.leTransit, AnnunTone.GREEN, Modifier.fillMaxSize(), small = true) }
        }
    }
    MpS(rc, 242, 182, 34) {
        Row(Modifier.fillMaxSize().background(Color.Black, RoundedCornerShape(2.dp)).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.weight(1f).fillMaxSize()) { Annun("ANTI SKID\nINOP", pw && !d.antiskidInbdOperational, AnnunTone.AMBER, Modifier.fillMaxSize(), small = true) }
            Box(Modifier.weight(1f).fillMaxSize()) { Annun("ANTI SKID\nINOP", pw && !d.antiskidOutbdOperational, AnnunTone.AMBER, Modifier.fillMaxSize(), small = true) }
        }
    }
    MpS(rc, 284, 182, 116) {
        Column(Modifier.fillMaxSize().background(BezelTone, RoundedCornerShape(3.dp)).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PedestalLegend("ANTISKID", size = 9)
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                Toggle(
                    on = s.antiskidInbd, label = "INBD", upLabel = "ON", downLabel = "OFF",
                    onChange = { v -> cockpit.patchSimNote(if (v) "Inboard antiskid ON." else "Inboard antiskid OFF — autobrake unavailable.") { antiskidInbd = v } },
                )
                Toggle(
                    on = s.antiskidOutbd, label = "OUTBD", upLabel = "ON", downLabel = "OFF",
                    onChange = { v -> cockpit.patchSimNote(if (v) "Outboard antiskid ON." else "Outboard antiskid OFF — autobrake unavailable.") { antiskidOutbd = v } },
                )
            }
        }
    }
    MpS(rc, 408, 182, 122) {
        Column(
            Modifier.fillMaxSize().background(BezelTone, RoundedCornerShape(3.dp)).padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween,
        ) {
            PedestalLegend("AUTO BRAKE", size = 8)
            Knob(
                value = s.autobrake, min = 0.0, max = 4.0, step = 1.0, size = 46.dp, ticks = AB, compact = true,
                onChange = { v ->
                    val i = v.toInt()
                    cockpit.patchSimNote("AUTOBRAKE → ${AB[i]}") { autobrake = v; autobrakeTestTimer = 0.0; autobrakeRtoArmed = false }
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val label = if (d.autobrakeActive) "AUTO BRK" else if (d.autobrakeDisarm) "DISARM" else if (d.autobrakeArmed) "ARMED" else "AUTO BRAKE"
                Box(Modifier.weight(1f)) {
                    Annun(label, pw && (d.autobrakeActive || d.autobrakeDisarm || d.autobrakeArmed), if (d.autobrakeDisarm) AnnunTone.AMBER else AnnunTone.GREEN, Modifier.fillMaxWidth(), small = true)
                }
                Box(Modifier.weight(1f)) { Annun("PARK BRAKE", s.parkingBrake, AnnunTone.RED, Modifier.fillMaxWidth(), small = true) }
            }
        }
    }
    MpS(rc, 538, 182, 96) {
        val shape = RoundedCornerShape(2.dp)
        val ink = Color(0xFF2A2415)
        Column(Modifier.fillMaxSize().background(Color(0xFFB8A97A), shape).border(1.dp, Color(0xFF6D5F3A), shape).padding(4.dp)) {
            Text("LANDING GEAR LIMIT (IAS)", color = ink, fontSize = 6.sp, lineHeight = 7.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text("OPERATING · EXTEND 270K-.82M · RETRACT 235K · EXTENDED 320K-.82M", color = ink, fontSize = 6.sp, lineHeight = 7.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text("FLAPS LIMIT (IAS)", color = ink, fontSize = 6.sp, lineHeight = 7.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
            Text("1-230K 2-230K 5-225K 10-210K · 15-195K 25-190K 30-185K 40-170K · ALT FLAP EXT 310K", color = ink, fontSize = 6.sp, lineHeight = 7.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
    }

    /* ── gear lights + lever column ── */
    val gl = d.gearLights
    val test = LightTestLogic.gearTest(d.dcPower, s.ltTest, m.lightKey)
    MpPlate(X_GEAR - 4, 14, 148, 626, CentreTone)
    MpS(X_GEAR + 2, 22, 136, 118) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            GearPairMini("NOSE GEAR", test || gl.nose.red, test || gl.nose.green)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                GearPairMini("LEFT GEAR", test || gl.leftMain.red, test || gl.leftMain.green)
                GearPairMini("RIGHT GEAR", test || gl.rightMain.red, test || gl.rightMain.green)
            }
        }
    }
    MpS(X_GEAR, 152, 136, 304) {
        GearLever(
            gear = s.gear, wow = s.wow, gearLeverOverride = s.gearLeverOverride, modifier = Modifier.fillMaxSize(),
            onRequest = { sel ->
                if (sel.locked) cockpit.patchSimNote(sel.message ?: "") { gear = sel.gear } else cockpit.patchSim { gear = sel.gear }
            },
        )
    }
}

/** web GearPairMini: red lamp over green lamp, 64 wide. */
@Composable
private fun GearPairMini(text: String, red: Boolean, green: Boolean) {
    Column(Modifier.width(64.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Annun(text, red, AnnunTone.RED, Modifier.fillMaxWidth().height(26.dp), small = true)
        Annun(text, green, AnnunTone.GREEN, Modifier.fillMaxWidth().height(26.dp), small = true)
    }
}
