package com.oersted.trainer.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.Derived
import com.oersted.trainer.sim.SimState
import com.oersted.trainer.ui.common.Classic737Palette

/**
 * Overhead modules from components/panel/Overhead.tsx. Each takes the current state, derived values and an `edit`
 * function (the Kotlin form of web `s.patch({...})`). Log notes from the web patch calls are not written yet.
 */
typealias SimEdit = (SimState.() -> Unit) -> Unit

/** Web `.rounded-md border bg-inset p-2 font-mono` readout box with label and coloured value rows. */
@Composable
fun ReadoutBox(rows: List<Triple<String, String, Boolean>>, onTone: Color = Classic737Palette.Ok) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Classic737Palette.Inset)
            .border(1.dp, Classic737Palette.Border, RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        rows.forEach { (label, value, on) ->
            Row {
                Text("$label: ", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Classic737Palette.Muted)
                Text(value, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = if (on) onTone else Classic737Palette.Subtle)
            }
        }
    }
}

@Composable
fun FlightModule(
    s: SimState,
    d: Derived,
    edit: SimEdit,
    noPedestal: Boolean,
) {
    PanelModule("Flight controls · P5-3") {
        PanelGuardedRocker("FLT CTRL A", s.fltCtrlA, { v -> edit { fltCtrlA = v } }, lit = d.flightControlAAvailable)
        PanelGuardedRocker("FLT CTRL B", s.fltCtrlB, { v -> edit { fltCtrlB = v } }, lit = d.flightControlBAvailable)
        PanelGuardedRocker("SPOILER A", s.spoilerA, { v -> edit { spoilerA = v } }, lit = d.flightControlAAvailable && s.spoilerA)
        PanelGuardedRocker("SPOILER B", s.spoilerB, { v -> edit { spoilerB = v } }, lit = d.flightControlBAvailable && s.spoilerB)
        PanelGuardedRocker(
            label = "ALT FLAPS",
            on = s.altFlapsArm,
            onLabel = "ARM",
            offLabel = "OFF",
            onChange = { v -> edit { altFlapsArm = v; altFlapsControl = if (v) altFlapsControl else "OFF" } },
            lit = d.alternateFlapArmed,
        )
        PanelThreeToggle(
            label = "ALT FLAP POS",
            value = s.altFlapsControl,
            options = listOf(PanelOption("UP", "UP"), PanelOption("OFF", "OFF"), PanelOption("DN", "DN")),
            onChange = { id -> edit { altFlapsControl = id } },
        )
        if (!noPedestal) {
            PanelRotary(
                label = "FLAP LEVER",
                value = s.flaps.toInt().toString(),
                options = listOf("0", "1", "2", "5", "10", "15", "25", "30", "40").map {
                    PanelOption(it, if (it == "0") "UP" else it)
                },
                onChange = { id -> edit { flaps = id.toDouble() } },
            )
            PanelRotary(
                label = "SPEED BRAKE",
                value = s.speedBrake,
                options = listOf(
                    PanelOption("DOWN", "DOWN"),
                    PanelOption("ARMED", "ARM"),
                    PanelOption("FLIGHT", "FLIGHT"),
                    PanelOption("UP", "UP"),
                ),
                onChange = { id -> edit { speedBrake = id } },
            )
        }
        ReadoutBox(
            listOf(
                Triple("TE NORMAL HYD", if (d.teFlapHydraulicDriveAvailable) "SYSTEM A AVAILABLE" else "UNAVAILABLE", d.teFlapHydraulicDriveAvailable),
                Triple("TE BYPASS VALVE", if (d.alternateFlapArmed) "BYPASS" else "NORMAL", d.alternateFlapArmed),
                Triple("TE ALT MOTOR", if (d.alternateFlapDrivePowered) "POWERED" else "OFF", d.alternateFlapDrivePowered),
            ),
        )
    }
}

@Composable
fun HydModule(s: SimState, d: Derived, edit: SimEdit) {
    PanelModule("Hydraulic power · P5") {
        PanelRocker("ENG 1 PUMP", s.engHyd1, onChange = { v -> edit { engHyd1 = v } }, lit = d.hydAPump1On)
        PanelRocker("ENG 2 PUMP", s.engHyd2, onChange = { v -> edit { engHyd2 = v } }, lit = d.hydAPump2On)
        PanelRocker("ELEC 1 PUMP", s.elecHyd1, onChange = { v -> edit { elecHyd1 = v } }, lit = d.hydBPump1On)
        PanelRocker("ELEC 2 PUMP", s.elecHyd2, onChange = { v -> edit { elecHyd2 = v } }, lit = d.hydBPump2On)
        PanelThreePos(
            label = "STANDBY",
            value = s.stbyHyd,
            options = listOf(PanelOption("AUTO", "AUTO"), PanelOption("ON", "ON"), PanelOption("OFF", "OFF")),
            onChange = { id -> edit { stbyHyd = id } },
        )
        PanelRocker("GND INTCONN", s.groundInterconnect, onChange = { v -> edit { groundInterconnect = v } })
    }
}

@Composable
fun ElecModule(s: SimState, d: Derived, edit: SimEdit) {
    val gpuConnected = d.groundPowerAvailable
    PanelModule("Electrical · P5") {
        PanelRocker("BATTERY", s.battery, onChange = { v -> edit { battery = v } }, lit = d.dcPower)
        PanelRocker(
            "APU CONTROL L", s.apuLeftControl,
            onChange = { v -> edit { apuLeftControl = v; apuGen = v || apuRightControl } },
            lit = d.apuGenOnBus,
        )
        PanelRocker(
            "APU CONTROL R", s.apuRightControl,
            onChange = { v -> edit { apuRightControl = v; apuGen = apuLeftControl || v } },
            lit = d.apuGenOnBus,
        )
        PanelRocker(
            "GRD PWR", s.groundPowerOn,
            onChange = { v -> edit { groundPowerOn = if (gpuConnected) v else false; groundServiceOn = if (v) false else groundServiceOn } },
            lit = gpuConnected,
        )
        PanelRocker("GEN 1", s.gen1, onChange = { v -> edit { gen1 = v } }, lit = d.eng1Running && s.gen1)
        PanelRocker("GEN 2", s.gen2, onChange = { v -> edit { gen2 = v } }, lit = d.eng2Running && s.gen2)
        PanelRocker("BUS TRANSFER AUTO", s.busTie, onChange = { v -> edit { busTie = v } }, lit = d.acPower && d.transferBus1Powered && d.transferBus2Powered)
        PanelThreeToggle(
            label = "STANDBY POWER",
            value = s.standbyPower,
            options = listOf(PanelOption("OFF", "OFF"), PanelOption("AUTO", "AUTO"), PanelOption("BAT", "BAT")),
            onChange = { id -> edit { standbyPower = id } },
        )
        PanelCaptionLight("TR 1", d.tr1Powered, LightTone.ELEC)
        PanelCaptionLight("TR 2", d.tr2Powered, LightTone.ELEC)
        PanelCaptionLight("TR 3", d.tr3Powered, LightTone.ELEC)
        ReadoutBox(
            listOf(
                Triple("AC SOURCE", d.electricalSource1, d.acPower),
                Triple("TRANSFER BUS 1", if (d.transferBus1Powered) "POWERED" else "OFF", d.transferBus1Powered),
                Triple("TRANSFER BUS 2", if (d.transferBus2Powered) "POWERED" else "OFF", d.transferBus2Powered),
            ),
        )
    }
}

@Composable
fun ApuModule(s: SimState, d: Derived, edit: SimEdit) {
    PanelModule("APU · P5") {
        PanelRotary(
            label = "MASTER",
            value = s.apuMaster,
            options = listOf(PanelOption("OFF", "OFF"), PanelOption("ON", "ON"), PanelOption("START", "START")),
            onChange = { id ->
                // Web: START is inhibited without 28V DC starter/control power.
                if (id == "START" && !(d.batteryPower || d.externalDcControl)) {
                    edit { apuMaster = "OFF" }
                } else {
                    edit { apuMaster = id }
                }
            },
        )
        PanelRocker(
            "APU BLEED", s.apuBleed,
            onChange = { v -> edit { apuBleed = v } },
            lit = d.dcPower && s.apuBleedValve > 0.7,
        )
        Text(
            "APU FIRE / DISCHARGE — P8 FIRE PROTECTION",
            modifier = Modifier
                .fillMaxWidth()
                .background(Classic737Palette.Inset, RoundedCornerShape(6.dp))
                .padding(8.dp),
            fontSize = 9.sp,
            color = Classic737Palette.Subtle,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PanelCaptionLight(if (d.apuAvailable) "APU AVAIL 95%" else "APU NOT AVAIL", d.apuAvailable, LightTone.OK)
            PanelCaptionLight(if (s.apuStarter) "STARTER ON" else "STARTER", s.apuStarter, LightTone.CAUTION)
        }
    }
}
