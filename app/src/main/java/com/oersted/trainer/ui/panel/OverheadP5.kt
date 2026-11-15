package com.oersted.trainer.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.Derived
import com.oersted.trainer.sim.SimState
import com.oersted.trainer.ui.cockpit.CockpitModel
import com.oersted.trainer.ui.pedestal.Annun
import com.oersted.trainer.ui.pedestal.AnnunTone
import com.oersted.trainer.ui.pedestal.PedestalLegend
import com.oersted.trainer.ui.pedestal.Plate
import com.oersted.trainer.ui.pedestal.PushButton
import com.oersted.trainer.ui.pedestal.Selector3
import com.oersted.trainer.ui.pedestal.Toggle

/**
 * P5 forward overhead — visual design matched to web OverheadP5 / reference screenshots.
 * Uses the same Plate / Toggle / Annun / Selector3 as the center pedestal (web PedestalParts).
 * Column widths mirror web: 216 · 224 · 98 · 217 · 217 (DESIGN_W = 1008).
 */
private const val DESIGN_W = 1008f
private val ColW = listOf(216f, 224f, 98f, 217f, 217f)

@Composable
fun OverheadP5(cockpit: CockpitModel, modifier: Modifier = Modifier) {
    val s by cockpit.simState.collectAsState()
    val d by cockpit.derived.collectAsState()
    val edit: (SimState.() -> Unit) -> Unit = { b -> cockpit.patchSim(b) }
    val pw = d.dcPower || d.batteryPower

    Column(modifier.fillMaxWidth()) {
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
            val scale = (maxWidth.value / DESIGN_W).coerceIn(0.30f, 1f)
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
                        .clip(RoundedCornerShape(10.dp))
                        .border(2.dp, Color(0xFF202225), RoundedCornerShape(10.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF5E6266), Color(0xFF55585C), Color(0xFF4A4E52)),
                            ),
                        )
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // ── Top row ──
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Col(0) {
                            Plate("ELT") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Annun("ELT", pw && d.eltTransmitting, AnnunTone.AMBER, small = true)
                                    Selector3(listOf("OFF", "ARM", "ON"), s.eltMode.ifEmpty { "OFF" }, { edit { eltMode = it } }, label = "ELT")
                                }
                            }
                            Plate("LE DEVICES") {
                                PedestalLegend("FLAPS · SLATS", size = 6)
                                PushButton("TEST")
                            }
                        }
                        Col(1) {
                            Plate("CARGO FIRE") {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Annun("FWD FIRE", false, AnnunTone.RED, small = true)
                                    Annun("AFT FIRE", false, AnnunTone.RED, small = true)
                                }
                                Selector3(listOf("OFF", "ARM", "AFT"), "OFF", {})
                            }
                        }
                        Col(2) {
                            Plate("SERVICE INTERPHONE") {
                                Toggle(false, {}, label = "ON", upLabel = "OFF", downLabel = "ON")
                                Annun("CALL", false, AnnunTone.BLUE, small = true)
                            }
                        }
                        Col(3) {
                            Plate("ENGINE") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Annun("REV 1", pw && d.thrustReverser1Active, AnnunTone.AMBER, small = true)
                                    Annun("REV 2", pw && d.thrustReverser2Active, AnnunTone.AMBER, small = true)
                                }
                            }
                            Plate("CREW OXYGEN") {
                                PedestalLegend("PSI", size = 6)
                            }
                        }
                        Col(4) {
                            Plate("FLIGHT RECORDER") {
                                Selector3(listOf("TEST", "NORMAL"), "NORMAL", {})
                            }
                            Plate("STALL WARNING TEST") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PushButton("NO. 1")
                                    PushButton("NO. 2")
                                }
                            }
                        }
                    }

                    // ── INDEX · TO LOCK band ──
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .background(Color(0xFF2A2C30), RoundedCornerShape(2.dp))
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        PedestalLegend("INDEX · TO LOCK", size = 6)
                        PedestalLegend("DOME WHITE", size = 7)
                        PedestalLegend("INDEX · TO LOCK", size = 6)
                    }

                    // ── Main systems ──
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Col A — FLT CONTROL + FUEL
                        Col(0) {
                            Plate("FLT CONTROL") {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Toggle(s.fltCtrlA, { edit { fltCtrlA = it } }, label = "A", upLabel = "ON", downLabel = "OFF", guarded = true)
                                    Toggle(s.fltCtrlB, { edit { fltCtrlB = it } }, label = "B", upLabel = "ON", downLabel = "OFF", guarded = true)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Annun("LOW PRESS", pw && !d.flightControlAAvailable, AnnunTone.AMBER, small = true)
                                    Annun("LOW PRESS", pw && !d.flightControlBAvailable, AnnunTone.AMBER, small = true)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Toggle(s.spoilerA, { edit { spoilerA = it } }, label = "SPL A", upLabel = "ON", downLabel = "OFF")
                                    Toggle(s.spoilerB, { edit { spoilerB = it } }, label = "SPL B", upLabel = "ON", downLabel = "OFF")
                                }
                                Annun("FEEL DIFF PRESS", pw && d.feelDiff, AnnunTone.AMBER, small = true)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Annun("YAW DAMPER", pw && s.yawDamper && !d.yawDamperEffective, AnnunTone.AMBER, small = true)
                                    Toggle(s.yawDamper, { edit { yawDamper = it } }, upLabel = "ON", downLabel = "OFF")
                                }
                            }
                            Plate("FUEL") {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Annun("ENG VALVE CLOSED", pw && !d.fuelShutoff1Open, AnnunTone.BLUE, small = true)
                                    Annun("ENG VALVE CLOSED", pw && !d.fuelShutoff2Open, AnnunTone.BLUE, small = true)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Annun("HEAT 1", d.fuelHeatValveOpen, AnnunTone.BLUE, small = true)
                                    Annun("HEAT 2", d.fuelHeatValve2Open, AnnunTone.BLUE, small = true)
                                    Annun("CROSSFEED OPEN", pw && s.fuelCrossfeed, AnnunTone.BLUE, small = true)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Toggle(s.fuelHeat, { edit { fuelHeat = it } }, label = "1 HEAT", upLabel = "ON", downLabel = "OFF")
                                    Toggle(s.fuelCrossfeed, { edit { fuelCrossfeed = it } }, label = "XFEED", upLabel = "OPEN", downLabel = "CLOSE")
                                    Toggle(s.fuelHeat2, { edit { fuelHeat2 = it } }, label = "2 HEAT", upLabel = "ON", downLabel = "OFF")
                                }
                                PedestalLegend("FUEL PUMPS", size = 6)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Toggle(s.fuelPumpCenter1, { edit { fuelPumpCenter1 = it } }, label = "CTR L", upLabel = "ON", downLabel = "OFF")
                                    Toggle(s.fuelPumpCenter2, { edit { fuelPumpCenter2 = it } }, label = "CTR R", upLabel = "ON", downLabel = "OFF")
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Toggle(s.fuelPump1Aft, { edit { fuelPump1Aft = it } }, label = "1 AFT", upLabel = "ON", downLabel = "OFF")
                                    Toggle(s.fuelPump1Fwd, { edit { fuelPump1Fwd = it } }, label = "1 FWD", upLabel = "ON", downLabel = "OFF")
                                    Toggle(s.fuelPump2Fwd, { edit { fuelPump2Fwd = it } }, label = "2 FWD", upLabel = "ON", downLabel = "OFF")
                                    Toggle(s.fuelPump2Aft, { edit { fuelPump2Aft = it } }, label = "2 AFT", upLabel = "ON", downLabel = "OFF")
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Annun("LOW PRESS", d.fuelPump1AftLowPressure, small = true)
                                    Annun("LOW PRESS", d.fuelPump1FwdLowPressure, small = true)
                                    Annun("LOW PRESS", d.fuelPump2FwdLowPressure, small = true)
                                    Annun("LOW PRESS", d.fuelPump2AftLowPressure, small = true)
                                }
                            }
                        }
                        // Col B — meters / gen / bus
                        Col(1) {
                            Plate("STANDBY POWER") {
                                Selector3(listOf("BAT", "OFF", "AUTO"), s.standbyPower.ifEmpty { "OFF" }, { edit { standbyPower = it } })
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Toggle(s.battery, { edit { battery = it } }, label = "BAT", upLabel = "ON", downLabel = "OFF")
                                    Toggle(s.groundPowerOn, {
                                        edit { groundPowerOn = if (d.groundPowerAvailable) it else false }
                                    }, label = "GRD PWR", upLabel = "ON", downLabel = "OFF")
                                }
                                Annun("GRD POWER AVAILABLE", pw && d.groundPowerAvailable, AnnunTone.BLUE, small = true)
                            }
                            Plate("BUS TRANS") {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Annun("TRANSFER BUS OFF", pw && !d.transferBus1Powered, small = true)
                                    Annun("TRANSFER BUS OFF", pw && !d.transferBus2Powered, small = true)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Annun("GEN OFF BUS", pw && d.gen1OffBus, small = true)
                                    Annun("APU GEN OFF BUS", pw && d.apuGenOffBus, small = true)
                                    Annun("GEN OFF BUS", pw && d.gen2OffBus, small = true)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Toggle(s.gen1, { edit { gen1 = it } }, label = "GEN 1", upLabel = "ON", downLabel = "OFF")
                                    Toggle(s.apuGen, { edit { apuGen = it } }, label = "APU GEN", upLabel = "ON", downLabel = "OFF")
                                    Toggle(s.gen2, { edit { gen2 = it } }, label = "GEN 2", upLabel = "ON", downLabel = "OFF")
                                }
                                Toggle(s.busTie, { edit { busTie = it } }, label = "BUS TIE", upLabel = "AUTO", downLabel = "OFF")
                                PedestalLegend("AC ${d.electricalSource1.ifEmpty { "NONE" }}", size = 7)
                            }
                            Plate("APU") {
                                Selector3(listOf("OFF", "ON", "START"), s.apuMaster.ifEmpty { "OFF" }, { edit { apuMaster = it } }, label = "MASTER")
                                Toggle(s.apuBleed, { edit { apuBleed = it } }, label = "BLEED", upLabel = "ON", downLabel = "OFF")
                                Annun("APU AVAIL", pw && d.apuAvailable, AnnunTone.GREEN, small = true)
                            }
                        }
                        // Col C — equip / signs
                        Col(2) {
                            Plate("EQUIP COOLING") {
                                Selector3(listOf("NORM", "ALT"), "NORM", {})
                            }
                            Plate("EMER EXIT LIGHTS") {
                                Selector3(listOf("OFF", "ARMED", "ON"), "OFF", {})
                            }
                            Plate("PASS SIGNS") {
                                Selector3(listOf("OFF", "AUTO", "ON"), s.ltNoSmoking.ifEmpty { "OFF" }, { edit { ltNoSmoking = it } }, label = "NO SMOKING")
                                Selector3(listOf("OFF", "AUTO", "ON"), s.ltSeatBelt.ifEmpty { "OFF" }, { edit { ltSeatBelt = it } }, label = "FASTEN BELTS")
                            }
                            Plate {
                                PushButton("ATTEND", onPress = { edit { cabinCallActive = true } }, onRelease = { edit { cabinCallActive = false } })
                                Annun("CALL", pw && s.cabinCallActive, AnnunTone.BLUE, small = true)
                            }
                        }
                        // Col D — ice + hyd
                        Col(3) {
                            Plate("WINDOW HEAT") {
                                PedestalLegend("L · R", size = 6)
                            }
                            Plate("ANTI-ICE") {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Toggle(s.wingAntiIceL, { edit { wingAntiIceL = it } }, label = "WING L", upLabel = "ON", downLabel = "OFF")
                                    Toggle(s.wingAntiIceR, { edit { wingAntiIceR = it } }, label = "WING R", upLabel = "ON", downLabel = "OFF")
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Toggle(s.antiIce1, { edit { antiIce1 = it } }, label = "ENG 1", upLabel = "ON", downLabel = "OFF")
                                    Toggle(s.antiIce2, { edit { antiIce2 = it } }, label = "ENG 2", upLabel = "ON", downLabel = "OFF")
                                }
                            }
                            Plate("HYD PUMPS") {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Toggle(s.engHyd1, { edit { engHyd1 = it } }, label = "ENG 1", upLabel = "ON", downLabel = "OFF")
                                    Toggle(s.elecHyd2, { edit { elecHyd2 = it } }, label = "ELEC 2", upLabel = "ON", downLabel = "OFF")
                                    Toggle(s.elecHyd1, { edit { elecHyd1 = it } }, label = "ELEC 1", upLabel = "ON", downLabel = "OFF")
                                    Toggle(s.engHyd2, { edit { engHyd2 = it } }, label = "ENG 2", upLabel = "ON", downLabel = "OFF")
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Annun("LOW PRESS", pw && d.hydALow, small = true)
                                    Annun("LOW PRESS", pw && d.hydBLow, small = true)
                                }
                            }
                        }
                        // Col E — bleed / press
                        Col(4) {
                            Plate("BLEED / PACKS") {
                                Annun("DUAL BLEED", pw && d.dualBleed, AnnunTone.AMBER, small = true)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Toggle(s.packL, { edit { packL = it } }, label = "L PACK", upLabel = "ON", downLabel = "OFF")
                                    Selector3(listOf("CLOSE", "AUTO", "OPEN"), s.isolation.ifEmpty { "AUTO" }, { edit { isolation = it } }, label = "ISOL")
                                    Toggle(s.packR, { edit { packR = it } }, label = "R PACK", upLabel = "ON", downLabel = "OFF")
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Toggle(s.bleed1, { edit { bleed1 = it } }, label = "BLEED 1", upLabel = "ON", downLabel = "OFF")
                                    Toggle(s.apuBleed, { edit { apuBleed = it } }, label = "APU", upLabel = "ON", downLabel = "OFF")
                                    Toggle(s.bleed2, { edit { bleed2 = it } }, label = "BLEED 2", upLabel = "ON", downLabel = "OFF")
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Annun("PACK TRIP OFF", pw && s.packTripL, small = true)
                                    Annun("PACK TRIP OFF", pw && s.packTripR, small = true)
                                }
                            }
                            Plate("PRESSURIZATION") {
                                Selector3(listOf("AUTO", "STBY", "MAN"), s.pressMode.ifEmpty { "AUTO" }, { edit { pressMode = it } })
                                PedestalLegend("ΔP ${"%.1f".format(s.cabinDiff)}  OFV ${"%.0f".format(s.outflow * 100)}%", size = 7)
                            }
                        }
                    }

                    // ── Bottom strip ──
                    Plate {
                        PedestalLegend("LANDING / TAXI / EXTERIOR", size = 7)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Selector3(listOf("RETR", "EXT", "ON"), normRet(s.ltRetL), { edit { ltRetL = it } }, label = "LDG L")
                            Selector3(listOf("RETR", "EXT", "ON"), normRet(s.ltRetR), { edit { ltRetR = it } }, label = "LDG R")
                            Toggle(s.ltFixedL, { edit { ltFixedL = it } }, label = "FIXED L", upLabel = "ON", downLabel = "OFF")
                            Toggle(s.ltFixedR, { edit { ltFixedR = it } }, label = "FIXED R", upLabel = "ON", downLabel = "OFF")
                            Toggle(s.ltTaxi, { edit { ltTaxi = it } }, label = "TAXI", upLabel = "ON", downLabel = "OFF")
                            Toggle(s.ltAntiCol, { edit { ltAntiCol = it } }, label = "ANTI COL", upLabel = "ON", downLabel = "OFF")
                            Toggle(s.ltLogo, { edit { ltLogo = it } }, label = "LOGO", upLabel = "ON", downLabel = "OFF")
                        }
                    }
                    Plate {
                        PedestalLegend("ENGINE START", size = 7)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Selector3(listOf("GRD", "OFF", "CONT", "FLT"), s.start1.ifEmpty { "OFF" }, { edit { start1 = it } }, label = "1")
                            Selector3(listOf("GRD", "OFF", "CONT", "FLT"), s.start2.ifEmpty { "OFF" }, { edit { start2 = it } }, label = "2")
                        }
                    }

                    Text(
                        "Lights need DC — BATTERY ON. Design matched to web P5 plates (PedestalParts).",
                        fontSize = 9.sp,
                        color = Color(0xFF8A8578),
                    )
                }
            }
        }
    }
}

@Composable
private fun Col(index: Int, content: @Composable () -> Unit) {
    Column(
        Modifier.width(ColW[index].dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

private fun normRet(v: String): String = when {
    v.startsWith("RETR", true) -> "RETR"
    v.equals("EXT", true) -> "EXT"
    v.equals("ON", true) -> "ON"
    else -> "RETR"
}
