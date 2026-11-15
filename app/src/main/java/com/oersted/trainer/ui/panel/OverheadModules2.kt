@file:OptIn(ExperimentalLayoutApi::class)

package com.oersted.trainer.ui.panel

import androidx.compose.foundation.layout.ExperimentalLayoutApi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.Derived
import com.oersted.trainer.sim.SimState
import com.oersted.trainer.ui.common.Classic737Palette

/** Second half of components/panel/Overhead.tsx: Bleed/packs, Fuel, Environment, Anti-ice + window heat, Start, Lights. */

@Composable
fun BleedModule(s: SimState, d: Derived, edit: SimEdit) {
    PanelModule("Bleed / packs · P5-10") {
        PanelRocker("BLEED 1", s.bleed1, onChange = { v -> edit { bleed1 = v; bleedTrip1 = if (v) bleedTrip1 else false } }, lit = d.bleed1Available)
        PanelRocker("BLEED 2", s.bleed2, onChange = { v -> edit { bleed2 = v; bleedTrip2 = if (v) bleedTrip2 else false } }, lit = d.bleed2Available)
        PanelThreeToggle(
            label = "ISOLATION",
            value = s.isolation,
            options = listOf(PanelOption("OPEN", "OPEN"), PanelOption("AUTO", "AUTO"), PanelOption("CLOSE", "CLOSE")),
            onChange = { id -> edit { isolation = id } },
        )
        PanelRocker("L PACK", s.packL, onChange = { v -> edit { packL = v; packTripL = if (v) packTripL else false } }, lit = d.acPower && d.packLFlow)
        PanelRocker("R PACK", s.packR, onChange = { v -> edit { packR = v; packTripR = if (v) packTripR else false } }, lit = d.acPower && d.packRFlow)
        ResetPair(
            leftLabel = "L PACK TRIP RESET",
            leftEnabled = s.packTripL && !(d.packLDuctOverheat || d.packLCompressorOverheat || d.packLTurbineOverheat || d.packLPackTrip250),
            onLeft = { edit { packTripL = false } },
            rightLabel = "R PACK TRIP RESET",
            rightEnabled = s.packTripR && !(d.packRDuctOverheat || d.packRCompressorOverheat || d.packRTurbineOverheat || d.packRPackTrip250),
            onRight = { edit { packTripR = false } },
        )
        ResetPair(
            leftLabel = "BLEED 1 RESET",
            leftEnabled = s.bleedTrip1 && !s.faults.bleedOverheat1,
            onLeft = { edit { bleedTrip1 = false } },
            rightLabel = "BLEED 2 RESET",
            rightEnabled = s.bleedTrip2 && !s.faults.bleedOverheat2,
            onRight = { edit { bleedTrip2 = false } },
        )
        PanelRocker("RECIRC", s.recirc, onChange = { v -> edit { recirc = v } }, lit = d.acPower && s.recirc)
        PanelRocker(
            "GROUND AIR", s.groundPneumaticConnected,
            onChange = { v -> edit { groundPneumaticConnected = v; groundPneumaticPsi = if (v) 40.0 else 0.0 } },
            lit = s.groundPneumaticConnected && s.groundPneumaticPsi >= 30,
        )
        PanelRocker(
            "GROUND COND AIR", s.groundConditionedAirConnected,
            onChange = { v -> edit { groundConditionedAirConnected = v; groundConditionedAirPsi = if (v) 25.0 else 0.0 } },
            lit = d.groundConditionedAirActive,
        )
        PanelRocker("WATER CART", s.waterServiceConnected, onChange = { v -> edit { waterServiceConnected = v } })
        PanelRocker("WASTE CART", s.wasteServiceConnected, onChange = { v -> edit { wasteServiceConnected = v } })
        Column(
            Modifier.fillMaxWidth().background(Classic737Palette.Inset, RoundedCornerShape(6.dp)).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelCaptionLight("L AC SUPPLY", d.acSupplyLeft, LightTone.ELEC)
                PanelCaptionLight("R AC SUPPLY", d.acSupplyRight, LightTone.ELEC)
            }
        }
        Text(
            "L/R AC SUPPLY indicates actual conditioned-air flow to the respective distribution system. With one pack off, " +
                "the remaining pack supplies both systems; GROUND COND AIR is the separate ATA 21 service-cart connection used " +
                "when aircraft packs are off.",
            fontSize = 9.sp,
            color = Classic737Palette.Subtle,
        )
    }
}

/** Two small buttons side by side, as the web `grid-cols-2` reset row. */
@Composable
private fun ResetPair(
    leftLabel: String,
    leftEnabled: Boolean,
    onLeft: () -> Unit,
    rightLabel: String,
    rightEnabled: Boolean,
    onRight: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ResetButton(leftLabel, leftEnabled, onLeft, Modifier.weight(1f))
        ResetButton(rightLabel, rightEnabled, onRight, Modifier.weight(1f))
    }
}

@Composable
private fun ResetButton(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .background(Classic737Palette.Inset, RoundedCornerShape(4.dp))
            .border(1.dp, Classic737Palette.Border, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 8.sp,
            color = if (enabled) Classic737Palette.Fg else Classic737Palette.Subtle.copy(alpha = 0.4f),
        )
    }
}

@Composable
fun FuelModule(s: SimState, d: Derived, edit: SimEdit) {
    PanelModule("Fuel · P5") {
        PanelRocker("TANK 1 FWD BOOST", s.fuelPump1Fwd, onChange = { v -> edit { fuelPump1Fwd = v } }, lit = d.fuelPump1FwdOn)
        PanelRocker("TANK 1 AFT BOOST", s.fuelPump1Aft, onChange = { v -> edit { fuelPump1Aft = v } }, lit = d.fuelPump1AftOn)
        PanelRocker("CENTER LEFT BOOST", s.fuelPumpCenter1, onChange = { v -> edit { fuelPumpCenter1 = v } }, lit = d.fuelCenterPump1On)
        PanelRocker("CENTER RIGHT BOOST", s.fuelPumpCenter2, onChange = { v -> edit { fuelPumpCenter2 = v } }, lit = d.fuelCenterPump2On)
        PanelRocker("TANK 2 FWD BOOST", s.fuelPump2Fwd, onChange = { v -> edit { fuelPump2Fwd = v } }, lit = d.fuelPump2FwdOn)
        PanelRocker("TANK 2 AFT BOOST", s.fuelPump2Aft, onChange = { v -> edit { fuelPump2Aft = v } }, lit = d.fuelPump2AftOn)
        PanelRocker("CROSSFEED", s.fuelCrossfeed, onChange = { v -> edit { fuelCrossfeed = v } }, lit = d.acPower && s.fuelCrossfeed)
        FlowRow(
            Modifier.fillMaxWidth().background(Classic737Palette.Inset, RoundedCornerShape(6.dp)).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PanelCaptionLight("T1 ${Math.round(s.fuelTank1)}%", d.fuelPump1On, LightTone.ELEC)
            PanelCaptionLight("CTR ${Math.round(s.fuelTankCenter)}%", d.fuelCenterPumpOn, LightTone.ELEC)
            PanelCaptionLight("T2 ${Math.round(s.fuelTank2)}%", d.fuelPump2On, LightTone.ELEC)
        }
    }
}

private fun tempModeOf(s: SimState, side: String): String =
    if (side == "L") (s.tempModeL ?: "AUTO") else (s.tempModeR ?: "AUTO")

private fun tempSelectOf(s: SimState, side: String): Double =
    if (side == "L") (s.tempSelectL ?: 75.0) else (s.tempSelectR ?: 75.0)

private val TEMP_OPTIONS = listOf(
    PanelOption("AUTO", "AUTO"),
    PanelOption("MANUAL_COOL", "COOL"),
    PanelOption("MANUAL_WARM", "WARM"),
    PanelOption("MANUAL", "MAN"),
    PanelOption("OFF", "OFF"),
)

@Composable
fun EnvironmentModule(s: SimState, edit: SimEdit) {
    PanelModule("Air conditioning / pressurization · P5") {
        PanelRotary(
            label = "CABIN TEMP L",
            value = tempModeOf(s, "L"),
            options = TEMP_OPTIONS,
            onChange = { id -> edit { tempModeL = id } },
        )
        PanelControlSlider(
            label = "CABIN TEMP L · ${tempSelectOf(s, "L").toInt()}°F",
            value = tempSelectOf(s, "L"),
            onChange = { v -> edit { tempSelectL = Math.round(v).toDouble(); tempModeL = "MANUAL" } },
            min = 65.0,
            max = 85.0,
            disabled = tempModeOf(s, "L") == "OFF",
        )
        PanelRotary(
            label = "CABIN TEMP R",
            value = tempModeOf(s, "R"),
            options = TEMP_OPTIONS,
            onChange = { id -> edit { tempModeR = id } },
        )
        PanelControlSlider(
            label = "CABIN TEMP R · ${tempSelectOf(s, "R").toInt()}°F",
            value = tempSelectOf(s, "R"),
            onChange = { v -> edit { tempSelectR = Math.round(v).toDouble(); tempModeR = "MANUAL" } },
            min = 65.0,
            max = 85.0,
            disabled = tempModeOf(s, "R") == "OFF",
        )
        PanelRotary(
            label = "PRESSURIZATION",
            value = s.pressMode,
            options = listOf(
                PanelOption("AUTO", "AUTO"),
                PanelOption("STBY", "STBY"),
                PanelOption("MAN_AC", "MAN AC"),
                PanelOption("MAN_DC", "MAN DC"),
            ),
            onChange = { id -> edit { pressMode = id } },
        )
        val outflowLocked = s.pressMode == "AUTO" || s.pressMode == "STBY"
        ResetPair(
            leftLabel = "OUTFLOW OPEN",
            leftEnabled = !outflowLocked,
            onLeft = { edit { outflow = minOf(1.0, outflow + 0.1) } },
            rightLabel = "OUTFLOW CLOSE",
            rightEnabled = !outflowLocked,
            onRight = { edit { outflow = maxOf(0.0, outflow - 0.1) } },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PanelCaptionLight("CABIN ΔP ${"%.1f".format(s.cabinDiff)} PSI", s.cabinDiff > 0.2, LightTone.ELEC)
            PanelCaptionLight("OUTFLOW ${Math.round(s.outflow * 100)}%", s.outflow < 0.9, LightTone.ELEC)
        }
    }
}

@Composable
fun AntiIceModule(s: SimState, d: Derived, edit: SimEdit) {
    PanelModule("Anti-ice / engine fuel heat · P5") {
        PanelRocker("ENG 1 ANTI-ICE", s.antiIce1, onChange = { v -> edit { antiIce1 = v } }, lit = d.acPower && s.antiIce1)
        PanelRocker("ENG 2 ANTI-ICE", s.antiIce2, onChange = { v -> edit { antiIce2 = v } }, lit = d.acPower && s.antiIce2)
        PanelRocker("WING ANTI-ICE L", s.wingAntiIceL, onChange = { v -> edit { wingAntiIceL = v } }, lit = d.acPower && s.wingAntiIceL)
        PanelRocker("WING ANTI-ICE R", s.wingAntiIceR, onChange = { v -> edit { wingAntiIceR = v } }, lit = d.acPower && s.wingAntiIceR)
        PanelRocker(
            "ENG 1 FUEL HEAT", s.fuelHeat,
            onChange = { v -> edit { fuelHeat = v; if (v) fuelHeatElapsedSec = 0.0 } },
            lit = d.fuelHeatValveOpen,
        )
        PanelRocker(
            "ENG 2 FUEL HEAT", s.fuelHeat2,
            onChange = { v -> edit { fuelHeat2 = v; if (v) fuelHeat2ElapsedSec = 0.0 } },
            lit = d.fuelHeatValve2Open,
        )
        FlowRow(
            Modifier.fillMaxWidth().background(Classic737Palette.Inset, RoundedCornerShape(6.dp)).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PanelCaptionLight("ENG 1", d.acPower && s.antiIce1, LightTone.OK)
            PanelCaptionLight("WING", d.acPower && (s.wingAntiIceL || s.wingAntiIceR), LightTone.OK)
            PanelCaptionLight("FUEL HT", d.acPower && s.fuelHeat, LightTone.OK)
        }
    }
    WindowHeatModule(s, d, edit)
}

/** P5-9 window heat module (ATA 30-41). Cycling a window switch OFF clears its latched overheat. */
@Composable
fun WindowHeatModule(s: SimState, d: Derived, edit: SimEdit) {
    fun whOn(on: Boolean, ovht: Boolean) = d.acPower && on && !ovht

    PanelModule("Window heat · P5-9") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                Modifier
                    .weight(1f)
                    .background(Classic737Palette.Inset, RoundedCornerShape(6.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("LEFT", fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = Classic737Palette.Subtle)
                PanelRocker("L FWD", s.windowHeatLFwd, onChange = { v -> edit { windowHeatLFwd = v; if (!v) windowHeatOvhtLFwd = false } }, lit = whOn(s.windowHeatLFwd, s.windowHeatOvhtLFwd))
                PanelRocker("L SIDE", s.windowHeatLSide, onChange = { v -> edit { windowHeatLSide = v; if (!v) windowHeatOvhtLSide = false } }, lit = whOn(s.windowHeatLSide, s.windowHeatOvhtLSide))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PanelCaptionLight("L FWD ON", whOn(s.windowHeatLFwd, s.windowHeatOvhtLFwd), LightTone.OK)
                    PanelCaptionLight("L FWD OVHT", s.windowHeatOvhtLFwd || (s.windowHeatTest == "OVHT" && s.windowHeatLFwd), LightTone.CAUTION)
                    PanelCaptionLight("L SIDE ON", whOn(s.windowHeatLSide, s.windowHeatOvhtLSide), LightTone.OK)
                    PanelCaptionLight("L SIDE OVHT", s.windowHeatOvhtLSide || (s.windowHeatTest == "OVHT" && s.windowHeatLSide), LightTone.CAUTION)
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .background(Classic737Palette.Inset, RoundedCornerShape(6.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("RIGHT", fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = Classic737Palette.Subtle)
                PanelRocker("R FWD", s.windowHeatRFwd, onChange = { v -> edit { windowHeatRFwd = v; if (!v) windowHeatOvhtRFwd = false } }, lit = whOn(s.windowHeatRFwd, s.windowHeatOvhtRFwd))
                PanelRocker("R SIDE", s.windowHeatRSide, onChange = { v -> edit { windowHeatRSide = v; if (!v) windowHeatOvhtRSide = false } }, lit = whOn(s.windowHeatRSide, s.windowHeatOvhtRSide))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PanelCaptionLight("R FWD ON", whOn(s.windowHeatRFwd, s.windowHeatOvhtRFwd), LightTone.OK)
                    PanelCaptionLight("R FWD OVHT", s.windowHeatOvhtRFwd || (s.windowHeatTest == "OVHT" && s.windowHeatRFwd), LightTone.CAUTION)
                    PanelCaptionLight("R SIDE ON", whOn(s.windowHeatRSide, s.windowHeatOvhtRSide), LightTone.OK)
                    PanelCaptionLight("R SIDE OVHT", s.windowHeatOvhtRSide || (s.windowHeatTest == "OVHT" && s.windowHeatRSide), LightTone.CAUTION)
                }
            }
        }
        PanelThreeToggle(
            label = "WINDOW HEAT TEST",
            value = s.windowHeatTest,
            options = listOf(PanelOption("PWR", "PWR"), PanelOption("OFF", "OFF"), PanelOption("OVHT", "OVHT")),
            onChange = { id -> edit { windowHeatTest = id } },
        )
        Text(
            "ATA 30-41 · P5-9. FWD = No.1 windshield; SIDE = No.2/4/5. Green ON = heat applied (AC available, not overheat). " +
                "Amber OVHT = overheat or OVHT test. Cycle the window switch OFF to clear a latched overheat. Test is spring-centered on the aircraft.",
            fontSize = 8.sp,
            color = Classic737Palette.Subtle,
        )
    }
}

@Composable
fun StartModule(s: SimState, d: Derived, edit: SimEdit) {
    val options = listOf(
        PanelOption("GRD", "GRD"),
        PanelOption("OFF", "OFF"),
        PanelOption("IGN_L", "L IGN"),
        PanelOption("IGN_R", "R IGN"),
        PanelOption("FLT", "FLT"),
    )
    PanelModule("Engine start · P5 forward overhead") {
        PanelRotary("ENGINE START 1", s.start1, options, onChange = { id -> edit { start1 = id } })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PanelCaptionLight("START VLV 1", d.startValveOpen1, LightTone.CAUTION)
            PanelCaptionLight("IGN 1", d.ignition1On, LightTone.CAUTION)
        }
        PanelRotary("ENGINE START 2", s.start2, options, onChange = { id -> edit { start2 = id } })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PanelCaptionLight("START VLV 2", d.startValveOpen2, LightTone.CAUTION)
            PanelCaptionLight("IGN 2", d.ignition2On, LightTone.CAUTION)
        }
        Text(
            "737-200 arrangement: rotary engine-start selectors remain on the forward overhead; thrust, fuel cutoff and " +
                "reverse levers are on the center control stand.",
            fontSize = 7.sp,
            color = Classic737Palette.Subtle,
        )
    }
}
