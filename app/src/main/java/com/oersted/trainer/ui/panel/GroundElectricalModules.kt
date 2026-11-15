@file:OptIn(ExperimentalLayoutApi::class)

package com.oersted.trainer.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
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
import com.oersted.trainer.ui.cockpit.CockpitModel
import com.oersted.trainer.ui.common.Classic737Palette

/**
 * ElectricalDistribution and GroundModule from components/panel/Overhead.tsx.
 * Log notes from the web patch calls are not written yet.
 */

@Composable
fun ElectricalDistributionModule(s: SimState, d: Derived) {
    PanelModule("Electrical power distribution · source → bus → user") {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelCaptionLight("GEN 1 ${if (d.generator1BusPowered) "BUS" else "OFF"}", d.generator1BusPowered, LightTone.ELEC)
            PanelCaptionLight("GEN 2 ${if (d.generator2BusPowered) "BUS" else "OFF"}", d.generator2BusPowered, LightTone.ELEC)
            PanelCaptionLight("XFER 1 ${if (d.transferBus1Powered) d.electricalSource1 else "OFF"}", d.transferBus1Powered, LightTone.ELEC)
            PanelCaptionLight("XFER 2 ${if (d.transferBus2Powered) d.electricalSource2 else "OFF"}", d.transferBus2Powered, LightTone.ELEC)
            PanelCaptionLight("TR 1", d.tr1Powered, LightTone.ELEC)
            PanelCaptionLight("TR 2", d.tr2Powered, LightTone.ELEC)
            PanelCaptionLight("TR 3 BACKUP", d.tr3Powered, LightTone.ELEC)
        }
        val acSources = listOf(d.electricalSource1, d.electricalSource2)
            .filter { it != "NONE" }
            .distinct()
            .joinToString(" / ")
        ReadoutBox(
            listOf(
                Triple("DC BUS 1", if (d.dcBus1Powered) "POWERED" else "OFF", d.dcBus1Powered),
                Triple("DC BUS 2", if (d.dcBus2Powered) "POWERED" else "OFF", d.dcBus2Powered),
                Triple("BATTERY", if (s.battery) "ON" else "OFF", s.battery),
                Triple("AC SOURCE", if (d.acPower && acSources.isNotEmpty()) acSources else "NONE", d.acPower),
            ),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            d.electricalUsers.forEach { u ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (u.powered) Classic737Palette.Ok.copy(alpha = 0.05f) else Classic737Palette.Inset,
                            RoundedCornerShape(4.dp),
                        )
                        .border(
                            1.dp,
                            if (u.powered) Classic737Palette.Ok.copy(alpha = 0.4f) else Classic737Palette.Border,
                            RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(u.label, fontSize = 9.sp, modifier = Modifier.weight(1.45f))
                    Text(
                        if (u.powered) "POWERED" else "OFF",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = if (u.powered) Classic737Palette.Ok else Classic737Palette.Subtle,
                        modifier = Modifier.weight(0.55f),
                    )
                    Text(
                        u.source,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        color = Classic737Palette.Subtle,
                        modifier = Modifier.weight(0.7f),
                    )
                }
            }
        }
    }
}

@Composable
fun GroundModule(cockpit: CockpitModel, s: SimState, d: Derived) {
    val edit: SimEdit = { block -> cockpit.patchSim(block) }
    PanelModule("Gear / ground · center panel") {
        // WOW indication: GND and AIR lamps, the lit one follows weight-on-wheels.
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("WOW", fontSize = 9.sp, color = Classic737Palette.Subtle)
            Row(
                Modifier
                    .background(Classic737Palette.Inset, RoundedCornerShape(2.dp))
                    .border(1.dp, Classic737Palette.Border, RoundedCornerShape(2.dp)),
            ) {
                Text(
                    "GND",
                    fontSize = 9.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = if (s.battery && s.wow) Classic737Palette.AccentFg else Classic737Palette.Subtle,
                    modifier = Modifier
                        .background(if (s.battery && s.wow) Classic737Palette.Ok else Color.Transparent)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Text(
                    "AIR",
                    fontSize = 9.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = if (s.battery && !s.wow) Classic737Palette.AccentFg else Classic737Palette.Subtle,
                    modifier = Modifier
                        .background(if (s.battery && !s.wow) Classic737Palette.Ok else Color.Transparent)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        PanelRocker(
            "PARK BRK", s.parkingBrake,
            onChange = { v -> edit { parkingBrake = v } },
            lit = s.battery && s.parkingBrake,
        )
        PanelThreeToggle(
            label = "GEAR",
            value = s.gear,
            options = listOf(PanelOption("UP", "UP"), PanelOption("OFF", "OFF"), PanelOption("DN", "DN")),
            onChange = { id ->
                // Web: UP is inhibited on the ground (weight-on-wheels); the lever stays OFF.
                if (id == "UP" && s.wow) edit { gear = "OFF" } else edit { gear = id }
            },
        )
        PanelRotary(
            label = "FLAPS",
            value = s.flaps.toInt().toString(),
            options = listOf("0", "1", "2", "5", "10", "15", "25", "30", "40").map {
                PanelOption(it, if (it == "0") "UP" else it)
            },
            onChange = { id -> edit { flaps = id.toDouble() } },
        )
        PanelRotary(
            label = "SPD BRK",
            value = s.speedBrake,
            options = listOf(
                PanelOption("DOWN", "DOWN"),
                PanelOption("ARMED", "ARM"),
                PanelOption("FLIGHT", "FLT"),
                PanelOption("UP", "UP"),
            ),
            onChange = { id -> edit { speedBrake = id } },
        )
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelCaptionLight("TRANSIT", d.gearTransit && (d.dcPower || d.acPower), LightTone.WARN)
            PanelCaptionLight("DOWN / LOCKED", d.gearDown && (d.dcPower || d.acPower), LightTone.OK)
            PanelCaptionLight("WOW / GROUND", s.battery && s.wow, LightTone.OK)
            PanelCaptionLight("PARKING BRAKE", s.battery && s.parkingBrake, LightTone.WARN)
        }
        ReadoutBox(
            listOf(
                Triple("GPU", if (s.externalPowerConnected) "CONNECTED" else "DISCONNECTED", s.externalPowerConnected),
                Triple("GROUND AIR", "${Math.round(s.groundPneumaticPsi)} PSI", s.groundPneumaticPsi > 0),
                Triple("WATER", "${Math.round(s.potableWater)}%", false),
                Triple("WASTE", "${Math.round(s.wasteLevel)}%", false),
                Triple("HYD SERVICING", if (s.hydServicingConnected) "CONNECTED" else "OFF", s.hydServicingConnected),
                Triple("TE FLAP POS", "${"%.1f".format(s.flapPos)}", false),
                Triple("STBY RUD", if (d.standbyRudderPowered) "PRESSURIZED" else "OFF", d.standbyRudderPowered),
            ),
        )
        PanelControlSlider(
            label = "TRAINING GROUND SPEED · ${Math.round(s.groundSpeedKt)} KT",
            value = s.groundSpeedKt,
            onChange = { v -> edit { groundSpeedKt = Math.round(v).toDouble() } },
            min = 0.0,
            max = 160.0,
        )
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { edit { externalPowerConnected = !s.externalPowerConnected } }, modifier = Modifier.fillMaxWidth(0.48f)) {
                Text(if (s.externalPowerConnected) "GPU DISCONNECT" else "GPU CONNECT", fontSize = 10.sp)
            }
            OutlinedButton(
                onClick = {
                    val on = !s.groundPneumaticConnected
                    edit { groundPneumaticConnected = on; groundPneumaticPsi = if (on) 40.0 else 0.0 }
                },
                modifier = Modifier.fillMaxWidth(0.48f),
            ) {
                Text(if (s.groundPneumaticConnected) "AIR CART OFF" else "AIR CART ON", fontSize = 10.sp)
            }
            OutlinedButton(onClick = { edit { hydServicingConnected = !s.hydServicingConnected } }, modifier = Modifier.fillMaxWidth(0.48f)) {
                Text(if (s.hydServicingConnected) "HYD SERVICING DISCONNECT" else "HYD SERVICING CONNECT", fontSize = 10.sp)
            }
            OutlinedButton(
                onClick = {
                    val on = !s.hydReservoirAirConnected
                    edit { hydReservoirAirConnected = on; hydReservoirAirPsi = if (on) 42.0 else 0.0 }
                },
                modifier = Modifier.fillMaxWidth(0.48f),
            ) {
                Text(
                    if (s.hydReservoirAirConnected) "HYD RESERVOIR AIR DISCONNECT" else "HYD RESERVOIR AIR CONNECT",
                    fontSize = 10.sp,
                )
            }
        }
    }
}
