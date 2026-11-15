package com.oersted.trainer.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.Derived
import com.oersted.trainer.sim.SimState
import com.oersted.trainer.ui.cockpit.CockpitModel
import com.oersted.trainer.ui.common.Classic737Palette

/**
 * P5 forward overhead — layout matched to web OverheadP5.tsx / reference photo:
 * 5 columns (216/224/98/217/217), INDEX·TO·LOCK band, main systems row, bottom light/start strip.
 * DESIGN_W = 1008 like the web. Systems wired to sim; expand remaining local-only plates iteratively.
 */
private const val DESIGN_W = 1008f

private val PanelShell = Color(0xFF55585C)
private val Plate = Color(0xFF6B6F73)
private val PlateLight = Color(0xFF9B9EA2)
private val Face = Color(0xFF2C2F33)
private val Border = Color(0xFF202225)
private val Legend = Color(0xFFE6E1D2)
private val LegendDark = Color(0xFF1A1C1F)
private val Dim = Color(0xFF8A8578)
private val Screw = Color(0xFF3A3E42)

@Composable
fun OverheadP5(cockpit: CockpitModel, modifier: Modifier = Modifier) {
    val s by cockpit.simState.collectAsState()
    val d by cockpit.derived.collectAsState()
    val edit: (SimState.() -> Unit) -> Unit = { b -> cockpit.patchSim(b) }
    val pw = d.dcPower || d.batteryPower

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    "P5 · FORWARD OVERHEAD · 737-200",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color(0xFFB9AF98),
                    letterSpacing = 1.6.sp,
                )
                Text(
                    "Interactive overhead — every control is live",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color(0xFFE8E4DC),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF343A42), RoundedCornerShape(12.dp))
                .background(Color(0xFF121417))
                .padding(8.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            val scale = (maxWidth.value / DESIGN_W).coerceIn(0.32f, 1f)
            Box(
                Modifier
                    .width((DESIGN_W * scale).dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
            ) {
                Column(
                    Modifier
                        .width(DESIGN_W.dp)
                        .shadow(12.dp, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .border(2.dp, Border, RoundedCornerShape(10.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF5E6266), PanelShell, Color(0xFF4A4E52)),
                            ),
                        )
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // ── Top row: 5 cols ──
                    FiveCols {
                        ColPlate {
                            Cap("ELT"); MiniToggle("ARM", false) {}
                            Cap("LE DEVICES"); MiniToggle("TEST", false) {}
                        }
                        ColPlate {
                            Cap("CARGO FIRE")
                            Row {
                                Ann("FWD", false); Ann("AFT", false)
                            }
                            MiniToggle("ARM", false) {}
                        }
                        ColPlate {
                            Cap("SERVICE")
                            Cap("INTERPHONE")
                            MiniToggle("ON", false) {}
                        }
                        ColPlate {
                            Cap("AUDIO")
                            Row { Ann("VHF", false); Ann("HF", false) }
                            Cap("ENGINE")
                            Row {
                                Ann("REV 1", pw && d.thrustReverser1Active)
                                Ann("REV 2", pw && d.thrustReverser2Active)
                            }
                            Cap("OXYGEN")
                            MiniToggle("PASS ON", false) {}
                        }
                        ColPlate {
                            Cap("FLIGHT RECORDER")
                            MiniToggle("NORMAL", true) {}
                            Cap("STALL WARNING")
                            Row { Ann("1", false); Ann("2", false) }
                        }
                    }

                    // ── INDEX · TO LOCK + DOME WHITE ──
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF3A3E42)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("  INDEX · TO LOCK", fontSize = 8.sp, color = Legend, fontFamily = FontFamily.Monospace)
                        Text("DOME WHITE", fontSize = 8.sp, color = Legend, fontFamily = FontFamily.Monospace)
                        Text("INDEX · TO LOCK  ", fontSize = 8.sp, color = Legend, fontFamily = FontFamily.Monospace)
                    }

                    // ── Main systems row: 5 cols ──
                    FiveCols {
                        // Col A: FLT CONTROL + FUEL
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ColPlate {
                                Cap("FLT CONTROL")
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Bat("A", s.fltCtrlA, { edit { fltCtrlA = it } }, pw && d.flightControlAAvailable)
                                    Bat("B", s.fltCtrlB, { edit { fltCtrlB = it } }, pw && d.flightControlBAvailable)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Bat("SPL A", s.spoilerA) { edit { spoilerA = it } }
                                    Bat("SPL B", s.spoilerB) { edit { spoilerB = it } }
                                }
                                Ann("FEEL DIFF", pw && d.feelDiff)
                                Bat("YAW DMP", s.yawDamper, { edit { yawDamper = it } }, d.yawDamperEffective)
                            }
                            ColPlate(light = true) {
                                Cap("FUEL", dark = true)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Bat("1 FWD", s.fuelPump1Fwd, { edit { fuelPump1Fwd = it } }, d.fuelPump1FwdOn)
                                    Bat("1 AFT", s.fuelPump1Aft, { edit { fuelPump1Aft = it } }, d.fuelPump1AftOn)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Bat("CTR L", s.fuelPumpCenter1, { edit { fuelPumpCenter1 = it } }, d.fuelCenterPump1On)
                                    Bat("CTR R", s.fuelPumpCenter2, { edit { fuelPumpCenter2 = it } }, d.fuelCenterPump2On)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Bat("2 FWD", s.fuelPump2Fwd, { edit { fuelPump2Fwd = it } }, d.fuelPump2FwdOn)
                                    Bat("2 AFT", s.fuelPump2Aft, { edit { fuelPump2Aft = it } }, d.fuelPump2AftOn)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Bat("XFEED", s.fuelCrossfeed) { edit { fuelCrossfeed = it } }
                                    Ann("LOW", pw && d.fuelPump1FwdLowPressure)
                                    Ann("LOW", pw && d.fuelPump2FwdLowPressure)
                                }
                            }
                        }
                        // Col B: STANDBY POWER + GENERATORS
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ColPlate {
                                Cap("STANDBY POWER")
                                Tri(
                                    listOf("OFF", "AUTO", "BAT"),
                                    s.standbyPower.ifEmpty { "OFF" },
                                ) { edit { standbyPower = it } }
                                Cap("BATTERY")
                                Bat("BAT", s.battery, { edit { battery = it } }, d.dcPower)
                                Bat("GRD PWR", s.groundPowerOn, {
                                    edit { groundPowerOn = if (d.groundPowerAvailable) it else false }
                                }, d.groundPowerAvailable && s.groundPowerOn)
                                Ann("GRD AVAIL", pw && d.groundPowerAvailable, Color(0xFF60A5FA))
                            }
                            ColPlate {
                                Cap("GENERATORS")
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Bat("GEN 1", s.gen1, { edit { gen1 = it } }, d.eng1Running && s.gen1)
                                    Bat("APU", s.apuGen, { edit { apuGen = it } }, d.apuAvailable && s.apuGen)
                                    Bat("GEN 2", s.gen2, { edit { gen2 = it } }, d.eng2Running && s.gen2)
                                }
                                Bat("BUS TIE", s.busTie, { edit { busTie = it } }, d.acPower)
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Ann("G1 OFF", pw && d.gen1OffBus)
                                    Ann("G2 OFF", pw && d.gen2OffBus)
                                    Ann("APU OFF", pw && d.apuGenOffBus)
                                }
                                Text(
                                    "AC ${d.electricalSource1.ifEmpty { "NONE" }}",
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (d.acPower) Classic737Palette.Ok else Dim,
                                )
                            }
                        }
                        // Col C: CABIN / EQUIP
                        ColPlate {
                            Cap("EQUIP COOLING")
                            Tri(listOf("NORM", "ALT"), "NORM") {}
                            Cap("CABIN SIGNS")
                            Tri(
                                listOf("OFF", "AUTO", "ON"),
                                s.ltSeatBelt.ifEmpty { "OFF" },
                            ) { edit { ltSeatBelt = it } }
                            Tri(
                                listOf("OFF", "AUTO", "ON"),
                                s.ltNoSmoking.ifEmpty { "OFF" },
                            ) { edit { ltNoSmoking = it } }
                            Cap("ATTEND / GRD")
                            Ann("CALL", pw && s.cabinCallActive)
                        }
                        // Col D: ICE + HYD
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ColPlate {
                                Cap("ANTI-ICE")
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Bat("ENG1", s.antiIce1) { edit { antiIce1 = it } }
                                    Bat("ENG2", s.antiIce2) { edit { antiIce2 = it } }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Bat("WING L", s.wingAntiIceL) { edit { wingAntiIceL = it } }
                                    Bat("WING R", s.wingAntiIceR) { edit { wingAntiIceR = it } }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Bat("HEAT1", s.fuelHeat, { edit { fuelHeat = it } }, d.fuelHeatValveOpen)
                                    Bat("HEAT2", s.fuelHeat2, { edit { fuelHeat2 = it } }, d.fuelHeatValve2Open)
                                }
                            }
                            ColPlate {
                                Cap("HYDRAULIC")
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Bat("ENG1", s.engHyd1, { edit { engHyd1 = it } }, d.hydAPump1On)
                                    Bat("ENG2", s.engHyd2, { edit { engHyd2 = it } }, d.hydAPump2On)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Bat("ELEC1", s.elecHyd1, { edit { elecHyd1 = it } }, d.hydBPump1On)
                                    Bat("ELEC2", s.elecHyd2, { edit { elecHyd2 = it } }, d.hydBPump2On)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Ann("A LOW", pw && d.hydALow)
                                    Ann("B LOW", pw && d.hydBLow)
                                }
                            }
                        }
                        // Col E: AIR COND + BLEED + PRESS
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ColPlate {
                                Cap("BLEED / PACKS")
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Bat("BLD1", s.bleed1) { edit { bleed1 = it } }
                                    Bat("BLD2", s.bleed2) { edit { bleed2 = it } }
                                }
                                Tri(
                                    listOf("CLOSE", "AUTO", "OPEN"),
                                    s.isolation.ifEmpty { "AUTO" },
                                ) { edit { isolation = it } }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Bat("PACK L", s.packL) { edit { packL = it } }
                                    Bat("PACK R", s.packR) { edit { packR = it } }
                                }
                                Bat("RECIRC", s.recirc) { edit { recirc = it } }
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Ann("TRIP L", pw && s.packTripL)
                                    Ann("TRIP R", pw && s.packTripR)
                                    Ann("DUAL", pw && d.dualBleed)
                                }
                            }
                            ColPlate {
                                Cap("PRESSURIZATION")
                                Tri(
                                    listOf("AUTO", "STBY", "MAN"),
                                    s.pressMode.ifEmpty { "AUTO" },
                                ) { edit { pressMode = it } }
                                Text(
                                    "ΔP ${"%.1f".format(s.cabinDiff)}  OFV ${"%.0f".format(s.outflow * 100)}%",
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Dim,
                                )
                            }
                        }
                    }

                    // ── Bottom strip: landing lights + engine start ──
                    ColPlate {
                        Cap("LANDING / EXTERIOR LIGHTS")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Tri(listOf("RETR", "EXT", "ON"), s.ltRetL.ifEmpty { "RETR" }) { edit { ltRetL = it } }
                            Tri(listOf("RETR", "EXT", "ON"), s.ltRetR.ifEmpty { "RETR" }) { edit { ltRetR = it } }
                            Bat("FIX L", s.ltFixedL) { edit { ltFixedL = it } }
                            Bat("FIX R", s.ltFixedR) { edit { ltFixedR = it } }
                            Bat("TAXI", s.ltTaxi) { edit { ltTaxi = it } }
                            Bat("LOGO", s.ltLogo) { edit { ltLogo = it } }
                            Bat("A/COL", s.ltAntiCol) { edit { ltAntiCol = it } }
                        }
                    }
                    ColPlate {
                        Cap("ENGINE START · APU")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Cap("START 1")
                                Tri(
                                    listOf("GRD", "OFF", "CONT", "FLT"),
                                    s.start1.ifEmpty { "OFF" },
                                ) { edit { start1 = it } }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Cap("START 2")
                                Tri(
                                    listOf("GRD", "OFF", "CONT", "FLT"),
                                    s.start2.ifEmpty { "OFF" },
                                ) { edit { start2 = it } }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Cap("APU MASTER")
                                Tri(
                                    listOf("OFF", "ON", "START"),
                                    s.apuMaster.ifEmpty { "OFF" },
                                ) { edit { apuMaster = it } }
                            }
                            Bat("APU BLD", s.apuBleed, { edit { apuBleed = it } }, d.apuAvailable && s.apuBleed)
                            Ann("APU AVAIL", pw && d.apuAvailable, Classic737Palette.Ok)
                        }
                    }

                    Text(
                        "Lights and displays need DC power — flip BAT first. Tap a switch to move it.",
                        fontSize = 9.sp,
                        color = Dim,
                    )
                }
            }
        }
    }
}

/* ──── layout helpers matching web plate language ──── */

@Composable
private fun FiveCols(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun RowScope.Slot(content: @Composable () -> Unit) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        content()
    }
}

@Composable
private fun ColPlate(light: Boolean = false, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, Color(0xFF2A2E32), RoundedCornerShape(3.dp))
            .background(if (light) PlateLight else Plate)
            .padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun Cap(text: String, dark: Boolean = false) {
    Text(
        text,
        fontFamily = FontFamily.Monospace,
        fontSize = 7.sp,
        fontWeight = FontWeight.Bold,
        color = if (dark) LegendDark else Legend,
        letterSpacing = 0.6.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Ann(label: String, on: Boolean, tone: Color = Color(0xFFFBBF24)) {
    Box(
        Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(if (on) tone else Color(0xFF2A2410))
            .border(1.dp, Color(0xFF151515), RoundedCornerShape(2.dp))
            .padding(horizontal = 3.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            fontSize = 6.sp,
            fontWeight = FontWeight.Bold,
            color = if (on) Color.Black else Color(0xFF5A5030),
            textAlign = TextAlign.Center,
            lineHeight = 7.sp,
        )
    }
}

@Composable
private fun Bat(
    label: String,
    on: Boolean,
    onChange: (Boolean) -> Unit = {},
    lit: Boolean = on,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(1.dp),
    ) {
        Text(label, fontSize = 6.sp, color = Legend, textAlign = TextAlign.Center, maxLines = 1)
        Box(
            Modifier
                .size(width = 28.dp, height = 36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Face)
                .border(1.dp, Color(0xFF15171A), RoundedCornerShape(2.dp))
                .clickable { onChange(!on) },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 6.dp, height = 18.dp)
                    .graphicsLayer { rotationZ = if (on) 18f else -18f }
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFF9A9EA3), Color(0xFF464A4F), Color(0xFF9A9EA3))),
                    ),
            )
        }
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (lit) Classic737Palette.Ok else Screw),
        )
    }
}

@Composable
private fun Tri(options: List<String>, value: String, onChange: (String) -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, Color(0xFF15171A), RoundedCornerShape(2.dp))
            .background(Face),
    ) {
        options.forEach { opt ->
            val active = value.equals(opt, ignoreCase = true) ||
                (opt == "RETR" && value.startsWith("RETR", true))
            Text(
                opt,
                modifier = Modifier
                    .width(48.dp)
                    .background(if (active) Legend else Color.Transparent)
                    .clickable { onChange(opt) }
                    .padding(vertical = 2.dp),
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) Face else Dim,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MiniToggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(if (on) Classic737Palette.Ok else Face)
            .border(1.dp, Border, RoundedCornerShape(2.dp))
            .clickable { onChange(!on) }
            .padding(horizontal = 6.dp, vertical = 3.dp),
        fontSize = 7.sp,
        fontWeight = FontWeight.Bold,
        color = if (on) Color.Black else Legend,
    )
}
