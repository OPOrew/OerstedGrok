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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.Derived
import com.oersted.trainer.sim.SimState
import com.oersted.trainer.ui.cockpit.CockpitModel
import com.oersted.trainer.ui.common.Classic737Palette

/**
 * Port of components/panel/OverheadP5.tsx — interactive 737-200 forward overhead (P5).
 * 5-column layout scaled to container width. Systems controls wire to [CockpitModel.patchSim];
 * lights need DC (battery). Full pixel parity with the web panel is iterative; this is the live panel shell.
 */
private const val DESIGN_W = 1008f

private val PlateBg = Color(0xFF6A6E72)
private val PlateBorder = Color(0xFF2A2E32)
private val Metal = Color(0xFF4A4E52)
private val Legend = Color(0xFFE8E0D0)
private val DimLegend = Color(0xFF9A9588)
private val Face = Color(0xFF2C3034)
private val PanelBg = Color(0xFF55585C)

@Composable
fun OverheadP5(cockpit: CockpitModel, modifier: Modifier = Modifier) {
    val s by cockpit.simState.collectAsState()
    val d by cockpit.derived.collectAsState()
    val edit: (SimState.() -> Unit) -> Unit = { block -> cockpit.patchSim(block) }
    val pw = d.dcPower || d.batteryPower

    Column(modifier.fillMaxWidth()) {
        Text(
            "P5 · FORWARD OVERHEAD · 737-200",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = Color(0xFFB9AF98),
            letterSpacing = 1.5.sp,
        )
        Text(
            "Interactive overhead — every control is live",
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = Classic737Palette.Muted,
        )
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF343A42), RoundedCornerShape(10.dp))
                .background(Color(0xFF121417))
                .padding(8.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            val scale = (maxWidth.value / DESIGN_W).coerceAtMost(1f).coerceAtLeast(0.35f)
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
                            Brush.verticalGradient(listOf(Color(0xFF5C6064), PanelBg, Color(0xFF4A4E52))),
                        )
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Main systems grid (compressed 3×2 of the web's 5-col layout for density)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FltCtrlPlate(s, d, edit, pw)
                            FuelPlate(s, d, edit, pw)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ElecPlate(s, d, edit, pw)
                            GenPlate(s, d, edit, pw)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            HydPlate(s, d, edit, pw)
                            IcePlate(s, d, edit, pw)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            BleedPlate(s, d, edit, pw)
                            AirCondPlate(s, d, edit, pw)
                        }
                    }
                    BottomLightsStrip(s, d, edit, pw)
                    StartStrip(s, d, edit, pw)
                    Text(
                        "Lights need DC — BATTERY ON. Tap switches to move them.",
                        fontSize = 9.sp,
                        color = DimLegend,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OvPlate(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, PlateBorder, RoundedCornerShape(4.dp))
            .background(PlateBg)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            title,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = Legend,
            letterSpacing = 0.8.sp,
        )
        content()
    }
}

@Composable
private fun OvAnnun(label: String, on: Boolean, tone: Color = Color(0xFFFBBF24)) {
    Box(
        Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(if (on) tone else Color(0xFF2A2410))
            .border(1.dp, Color(0xFF1A1A18), RoundedCornerShape(2.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            color = if (on) Color.Black else Color(0xFF5A5030),
            textAlign = TextAlign.Center,
            lineHeight = 8.sp,
        )
    }
}

@Composable
private fun OvRocker(
    label: String,
    on: Boolean,
    onChange: (Boolean) -> Unit,
    lit: Boolean = on,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(2.dp)) {
        Text(label, fontSize = 7.sp, color = Legend, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 8.sp)
        Box(
            Modifier
                .size(width = 36.dp, height = 44.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Face)
                .border(1.dp, Metal, RoundedCornerShape(3.dp))
                .clickable { onChange(!on) },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 8.dp, height = 22.dp)
                    .graphicsLayer { rotationZ = if (on) 20f else -20f }
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFD4D7DA)),
            )
        }
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (lit) Classic737Palette.Ok else Color(0xFF3A3E42)),
        )
    }
}

@Composable
private fun OvThree(
    label: String,
    value: String,
    options: List<String>,
    onChange: (String) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 7.sp, color = Legend)
        Column(
            Modifier
                .clip(RoundedCornerShape(2.dp))
                .border(1.dp, Metal, RoundedCornerShape(2.dp))
                .background(Face),
        ) {
            options.forEach { opt ->
                val active = value == opt
                Text(
                    opt,
                    modifier = Modifier
                        .width(56.dp)
                        .background(if (active) Legend else Color.Transparent)
                        .clickable { onChange(opt) }
                        .padding(vertical = 3.dp),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (active) Face else DimLegend,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun FltCtrlPlate(s: SimState, d: Derived, edit: (SimState.() -> Unit) -> Unit, pw: Boolean) {
    OvPlate("FLT CONTROL") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OvRocker("A", s.fltCtrlA, { v -> edit { fltCtrlA = v } }, lit = pw && d.flightControlAAvailable)
            OvRocker("B", s.fltCtrlB, { v -> edit { fltCtrlB = v } }, lit = pw && d.flightControlBAvailable)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OvRocker("SPL A", s.spoilerA, { v -> edit { spoilerA = v } })
            OvRocker("SPL B", s.spoilerB, { v -> edit { spoilerB = v } })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OvAnnun("FEEL DIFF", pw && d.feelDiff)
            OvRocker("YAW DMP", s.yawDamper, { v -> edit { yawDamper = v } }, lit = d.yawDamperEffective)
        }
    }
}

@Composable
private fun FuelPlate(s: SimState, d: Derived, edit: (SimState.() -> Unit) -> Unit, pw: Boolean) {
    OvPlate("FUEL") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OvRocker("1 FWD", s.fuelPump1Fwd, { v -> edit { fuelPump1Fwd = v } }, lit = d.fuelPump1FwdOn)
            OvRocker("1 AFT", s.fuelPump1Aft, { v -> edit { fuelPump1Aft = v } }, lit = d.fuelPump1AftOn)
            OvRocker("CTR L", s.fuelPumpCenter1, { v -> edit { fuelPumpCenter1 = v } }, lit = d.fuelCenterPump1On)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OvRocker("CTR R", s.fuelPumpCenter2, { v -> edit { fuelPumpCenter2 = v } }, lit = d.fuelCenterPump2On)
            OvRocker("2 FWD", s.fuelPump2Fwd, { v -> edit { fuelPump2Fwd = v } }, lit = d.fuelPump2FwdOn)
            OvRocker("2 AFT", s.fuelPump2Aft, { v -> edit { fuelPump2Aft = v } }, lit = d.fuelPump2AftOn)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OvRocker("X-FEED", s.fuelCrossfeed, { v -> edit { fuelCrossfeed = v } })
            OvAnnun("LOW 1", pw && d.fuelPump1FwdLowPressure)
            OvAnnun("LOW 2", pw && d.fuelPump2FwdLowPressure)
        }
    }
}

@Composable
private fun ElecPlate(s: SimState, d: Derived, edit: (SimState.() -> Unit) -> Unit, pw: Boolean) {
    OvPlate("ELECTRICAL") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OvRocker("BAT", s.battery, { v -> edit { battery = v } }, lit = d.dcPower)
            OvRocker("GRD PWR", s.groundPowerOn, { v ->
                edit { groundPowerOn = if (d.groundPowerAvailable) v else false }
            }, lit = d.groundPowerAvailable && s.groundPowerOn)
            OvThree(
                "STBY",
                s.standbyPower.ifEmpty { "OFF" },
                listOf("OFF", "AUTO", "BAT"),
            ) { id -> edit { standbyPower = id } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OvAnnun("BAT DISCH", pw && d.batteryPower && !d.acPower)
            OvAnnun("GRD AVAIL", pw && d.groundPowerAvailable, Color(0xFF60A5FA))
        }
    }
}

@Composable
private fun GenPlate(s: SimState, d: Derived, edit: (SimState.() -> Unit) -> Unit, pw: Boolean) {
    OvPlate("GENERATORS") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OvRocker("GEN 1", s.gen1, { v -> edit { gen1 = v } }, lit = d.eng1Running && s.gen1)
            OvRocker("APU GEN", s.apuGen, { v -> edit { apuGen = v } }, lit = d.apuAvailable && s.apuGen)
            OvRocker("GEN 2", s.gen2, { v -> edit { gen2 = v } }, lit = d.eng2Running && s.gen2)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OvRocker("BUS TIE", s.busTie, { v -> edit { busTie = v } }, lit = d.acPower && d.transferBus1Powered)
            OvAnnun("G1 OFF", pw && d.gen1OffBus)
            OvAnnun("G2 OFF", pw && d.gen2OffBus)
            OvAnnun("APU OFF", pw && d.apuGenOffBus)
        }
        Text(
            "AC ${d.electricalSource1.ifEmpty { "NONE" }}",
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            color = if (d.acPower) Classic737Palette.Ok else DimLegend,
        )
    }
}

@Composable
private fun HydPlate(s: SimState, d: Derived, edit: (SimState.() -> Unit) -> Unit, pw: Boolean) {
    OvPlate("HYDRAULIC") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OvRocker("ENG 1", s.engHyd1, { v -> edit { engHyd1 = v } })
            OvRocker("ENG 2", s.engHyd2, { v -> edit { engHyd2 = v } })
            OvRocker("ELEC 1", s.elecHyd1, { v -> edit { elecHyd1 = v } })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OvRocker("ELEC 2", s.elecHyd2, { v -> edit { elecHyd2 = v } })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OvAnnun("A LOW", pw && d.hydALow)
            OvAnnun("B LOW", pw && d.hydBLow)
        }
    }
}

@Composable
private fun IcePlate(s: SimState, d: Derived, edit: (SimState.() -> Unit) -> Unit, pw: Boolean) {
    OvPlate("ANTI-ICE") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OvRocker("ENG 1", s.antiIce1, { v -> edit { antiIce1 = v } })
            OvRocker("ENG 2", s.antiIce2, { v -> edit { antiIce2 = v } })
            OvRocker("WING L", s.wingAntiIceL, { v -> edit { wingAntiIceL = v } })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OvRocker("WING R", s.wingAntiIceR, { v -> edit { wingAntiIceR = v } })
            OvRocker("FUEL HT1", s.fuelHeat, { v -> edit { fuelHeat = v } }, lit = d.fuelHeatValveOpen)
            OvRocker("FUEL HT2", s.fuelHeat2, { v -> edit { fuelHeat2 = v } }, lit = d.fuelHeatValve2Open)
        }
    }
}

@Composable
private fun BleedPlate(s: SimState, d: Derived, edit: (SimState.() -> Unit) -> Unit, pw: Boolean) {
    OvPlate("BLEED / PACKS") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OvRocker("BLEED 1", s.bleed1, { v -> edit { bleed1 = v } })
            OvRocker("BLEED 2", s.bleed2, { v -> edit { bleed2 = v } })
            OvThree(
                "ISOL",
                s.isolation.ifEmpty { "AUTO" },
                listOf("CLOSE", "AUTO", "OPEN"),
            ) { id -> edit { isolation = id } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OvRocker("L PACK", s.packL, { v -> edit { packL = v } })
            OvRocker("R PACK", s.packR, { v -> edit { packR = v } })
            OvRocker("RECIRC", s.recirc, { v -> edit { recirc = v } })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OvAnnun("TRIP L", pw && s.packTripL)
            OvAnnun("TRIP R", pw && s.packTripR)
            OvAnnun("DUAL BLEED", pw && d.dualBleed)
        }
    }
}

@Composable
private fun AirCondPlate(s: SimState, d: Derived, edit: (SimState.() -> Unit) -> Unit, pw: Boolean) {
    OvPlate("AIR COND / PRESS") {
        // Cabin temp selectors: press mode covers pressurization; mix valves live on packs.
        Text("CABIN TEMP · use pack mix on web parity later", fontSize = 7.sp, color = DimLegend)
        OvThree(
            "PRESS",
            s.pressMode.ifEmpty { "AUTO" },
            listOf("AUTO", "STBY", "MAN AC", "MAN DC"),
        ) { id -> edit { pressMode = id } }
        Text(
            "ΔP ${"%.1f".format(s.cabinDiff)}  OUTFLOW ${"%.0f".format(s.outflow * 100)}%",
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            color = DimLegend,
        )
    }
}

@Composable
private fun BottomLightsStrip(s: SimState, d: Derived, edit: (SimState.() -> Unit) -> Unit, pw: Boolean) {
    OvPlate("EXTERIOR LIGHTS") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OvThree("LDG L", s.ltRetL.ifEmpty { "RETRACT" }, listOf("RETRACT", "EXT", "ON")) { id -> edit { ltRetL = id } }
            OvThree("LDG R", s.ltRetR.ifEmpty { "RETRACT" }, listOf("RETRACT", "EXT", "ON")) { id -> edit { ltRetR = id } }
            OvRocker("FIXED L", s.ltFixedL, { v -> edit { ltFixedL = v } })
            OvRocker("FIXED R", s.ltFixedR, { v -> edit { ltFixedR = v } })
            OvRocker("TAXI", s.ltTaxi, { v -> edit { ltTaxi = v } })
            OvRocker("LOGO", s.ltLogo, { v -> edit { ltLogo = v } })
            OvRocker("ANTI COL", s.ltAntiCol, { v -> edit { ltAntiCol = v } })
        }
    }
}

@Composable
private fun StartStrip(s: SimState, d: Derived, edit: (SimState.() -> Unit) -> Unit, pw: Boolean) {
    OvPlate("ENGINE START · APU") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OvThree(
                "START 1",
                s.start1.ifEmpty { "OFF" },
                listOf("GRD", "OFF", "L IGN", "R IGN", "FLT"),
            ) { id -> edit { start1 = id } }
            OvThree(
                "START 2",
                s.start2.ifEmpty { "OFF" },
                listOf("GRD", "OFF", "L IGN", "R IGN", "FLT"),
            ) { id -> edit { start2 = id } }
            OvThree(
                "APU",
                s.apuMaster.ifEmpty { "OFF" },
                listOf("OFF", "ON", "START"),
            ) { id -> edit { apuMaster = id } }
            OvRocker("APU BLD", s.apuBleed, { v -> edit { apuBleed = v } }, lit = d.apuAvailable && s.apuBleed)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OvAnnun("APU AVAIL", pw && d.apuAvailable, Classic737Palette.Ok)
            OvAnnun("START VLV1", pw && s.startValve1)
            OvAnnun("START VLV2", pw && s.startValve2)
        }
    }
}
