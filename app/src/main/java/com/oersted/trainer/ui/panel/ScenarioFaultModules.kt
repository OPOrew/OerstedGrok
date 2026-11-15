@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.oersted.trainer.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.Derived
import com.oersted.trainer.sim.FaultInjectionRules
import com.oersted.trainer.sim.SimActions
import com.oersted.trainer.sim.SimState
import com.oersted.trainer.ui.cockpit.CockpitModel
import com.oersted.trainer.ui.common.Classic737Palette

/**
 * ScenarioModule and FaultModule from components/panel/Overhead.tsx.
 * Button ids are the same keys the web passes to toggleFault / runScenario.
 * Log notes are not written yet.
 */

private val WarnBorder = Classic737Palette.Warn.copy(alpha = 0.4f)
private val WarnFill = Classic737Palette.Warn.copy(alpha = 0.1f)
private val DangerBorder = Color(0xFFEF4444).copy(alpha = 0.4f)
private val DangerFill = Color(0xFFEF4444).copy(alpha = 0.1f)

/** Full-width training scenario buttons plus the adverse-yaw readout. */
@Composable
fun ScenarioModule(cockpit: CockpitModel, s: SimState, d: Derived) {
    val now = { System.currentTimeMillis().toDouble() }
    fun run(name: String) = cockpit.applySim { SimActions.runScenario(it, name, now()) }

    PanelModule("Scenarios · training console") {
        ScenarioButton("TR 1 FAILURE → TR 3 BACKUP", WarnBorder, WarnFill) { run("TR3_BACKUP") }
        ScenarioButton("TE FLAP HYD MOTOR FAILURE → ALT POWER", WarnBorder, WarnFill) { run("ALT_FLAP_ALTERNATE") }
        ScenarioButton("ANTISKID + AUTOBRAKE LANDING TEST", Classic737Palette.Ok.copy(alpha = 0.4f), Classic737Palette.Ok.copy(alpha = 0.1f)) { run("AUTOBRAKE_ANTISKID") }
        ScenarioButton("ADVERSE YAW — COORDINATED TURN", Classic737Palette.Accent.copy(alpha = 0.4f), Classic737Palette.Accent.copy(alpha = 0.1f)) { run("ADVERSE_YAW") }
        ScenarioButton("RTO — 105 KT REJECT", WarnBorder, WarnFill) { run("AUTOBRAKE_RTO") }
        ReadoutBox(
            listOf(
                Triple(
                    "ADVERSE YAW CUE",
                    if (s.adverseYawScenario) "${Math.round(d.adverseYawCue * 100)}% ${d.adverseYawDirection}" else "OFF",
                    s.adverseYawScenario,
                ),
            ) + if (s.adverseYawScenario) {
                listOf(
                    Triple(
                        "",
                        if (d.adverseYawCorrected) "COORDINATED — CUE REDUCED" else "ADD RUDDER IN TURN DIRECTION",
                        d.adverseYawCorrected,
                    ),
                )
            } else {
                emptyList()
            },
        )
        Text(
            "The scenarios inject the failure and establish the required training power source. The response still uses the " +
                "real cockpit controls: ALT FLAPS must be armed, then the alternate position switch must be commanded.",
            fontSize = 10.sp,
            color = Classic737Palette.Subtle,
        )
    }
}

@Composable
private fun ScenarioButton(label: String, border: Color, fill: Color, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = fill),
    ) {
        Text(label, fontSize = 10.sp)
    }
}

/** Fault table in the web's order. Each key goes to SimActions.toggleFault. */
private val FAULT_BUTTONS: List<Pair<String, String>> = listOf(
    "Engine 1 fire" to "engineFire1",
    "Engine 2 fire" to "engineFire2",
    "APU fire" to "apuFireDetected",
    "HYD A failure" to "hydALeak",
    "HYD B failure" to "hydBLeak",
    "TANK 1 FWD pump fault" to "fuelTank1FwdPumpFail",
    "TANK 1 AFT pump fault" to "fuelTank1AftPumpFail",
    "TANK 2 FWD pump fault" to "fuelTank2FwdPumpFail",
    "TANK 2 AFT pump fault" to "fuelTank2AftPumpFail",
    "CTR LEFT pump fault" to "fuelCenterPump1Fail",
    "TE FLAP ASYMMETRY FAULT" to "flapAsymmetry",
    "CTR RIGHT pump fault" to "fuelCenterPump2Fail",
    "TR 1 failure" to "tr1Failure",
    "TR 2 failure" to "tr2Failure",
    "GEN 1 failure → XFER" to "generator1Failure",
    "GEN 2 failure → XFER" to "generator2Failure",
    "XFR 1 relay failure" to "transferRelay1Failure",
    "XFR 2 relay failure" to "transferRelay2Failure",
    "BTB 1 failure" to "btb1Failure",
    "BTB 2 failure" to "btb2Failure",
    "APU GB failure" to "apuGeneratorBreakerFailure",
    "APU overspeed" to "apuOverspeed",
    "APU low oil" to "apuLowOilPressure",
    "APU high oil temp" to "apuHighOilTemperature",
    "EPC failure" to "externalPowerContactorFailure",
    "GEN 1 open phase" to "openPhase1",
    "GEN 2 open phase" to "openPhase2",
    "GEN 1 overfreq 425–435 Hz" to "generator1OverFrequency",
    "GEN 1 underfreq 360–370 Hz" to "generator1UnderFrequency",
    "GEN 1 overvoltage" to "generator1OverVoltage",
    "GEN 1 undervoltage" to "generator1UnderVoltage",
    "GEN 1 overcurrent" to "generator1OverCurrent",
    "GEN 1 differential" to "generator1Differential",
    "GEN 2 overfreq 425–435 Hz" to "generator2OverFrequency",
    "GEN 2 underfreq 360–370 Hz" to "generator2UnderFrequency",
    "GEN 2 overvoltage" to "generator2OverVoltage",
    "GEN 2 undervoltage" to "generator2UnderVoltage",
    "GEN 2 overcurrent" to "generator2OverCurrent",
    "GEN 2 differential" to "generator2Differential",
    "TR 3 failure" to "tr3Failure",
    "Battery failure" to "batteryFailure",
    "Static inverter failure" to "staticInverterFailure",
    "TE flap hyd motor" to "teFlapHydMotorFail",
    "Antiskid fault (both)" to "antiskidInop",
    "Antiskid inboard channel" to "antiskidInbdInop",
    "Antiskid outboard channel" to "antiskidOutbdInop",
    "GEN DRIVE 1 DISC fail" to "genDriveDisconnectFailure1",
    "GEN DRIVE 2 DISC fail" to "genDriveDisconnectFailure2",
    "GEN DRIVE 1 low oil" to "genDriveOilPressureLow1",
    "GEN DRIVE 2 low oil" to "genDriveOilPressureLow2",
    "GEN DRIVE 1 oil hot" to "genDriveOilOverheat1",
    "GEN DRIVE 2 oil hot" to "genDriveOilOverheat2",
    "Recirc fan failed" to "recircFanFailure",
    "Left mix valve jam" to "mixValveJamL",
    "Right mix valve jam" to "mixValveJamR",
    "Yaw damper failed" to "yawDamperFailure",
    "Equipment cooling failed" to "equipmentCoolingFailure",
    "Oxygen system failure" to "oxygenSystemFailure",
    "Door warning failed" to "doorWarningFailure",
    "Rain repellent failed" to "rainRepellentFailure",
    "Engine 1 fuel heater failed" to "fuelHeatFailure",
    "Engine 1 fuel filter icing" to "fuelFilterIcingFault1",
    "Engine 2 fuel filter icing" to "fuelFilterIcingFault2",
    "Fuel heater 2 failed" to "fuelHeatFailure2",
    "Left window heat failed" to "windowHeatFailureL",
    "Right window heat failed" to "windowHeatFailureR",
    "CVR failure" to "cvrFailure",
    "APU door jam" to "apuDoorJam",
)

@Composable
fun FaultModule(cockpit: CockpitModel, s: SimState) {
    val now = { System.currentTimeMillis().toDouble() }
    val gearActive = s.landingGearFault.isNotEmpty() && s.landingGearFault != "NONE"
    val gearLabel = when (s.landingGearFault) {
        "NOSE" -> "NOSE GEAR"
        "LEFT_MAIN" -> "LEFT MAIN GEAR"
        "RIGHT_MAIN" -> "RIGHT MAIN GEAR"
        else -> "NONE"
    }

    PanelModule("Fault injection · training") {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FAULT_BUTTONS.forEach { (label, key) ->
                OutlinedButton(
                    onClick = { cockpit.applySim { SimActions.toggleFault(it, key, now()) } },
                    modifier = Modifier.fillMaxWidth(0.48f),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = WarnFill),
                ) {
                    Text(label, fontSize = 9.sp)
                }
            }
            OutlinedButton(
                onClick = { cockpit.applySim { FaultInjectionRules.toggleCenterPumpAutoShutoff(it) } },
                modifier = Modifier.fillMaxWidth(0.48f),
            ) {
                Text(
                    "CENTER PUMP AUTO-SHUTOFF · ${FaultInjectionRules.centerPumpLabel(s.fuelCenterPumpAutoShutoffInstalled)}",
                    fontSize = 9.sp,
                )
            }
        }
        Text(
            "Electrical transfer tests: fail GEN 1/2 to observe automatic XFR BUS transfer; fail XFR relay to block the " +
                "transfer; fail BTB/EPC/APU GB to test alternate source isolation. Open-phase faults represent the manual's " +
                "three-phase fault condition. TR 3, battery and static-inverter faults test DC/standby backup paths.",
            fontSize = 9.sp,
            color = Classic737Palette.Subtle,
        )
        GearFaultBox(gearLabel = gearLabel, gearActive = gearActive, cockpit = cockpit, now = now)
        Text(
            "HYD A failure → standby hydraulic pressure can power the standby rudder/leading-edge system when AC power is " +
                "available and STBY HYD is AUTO/ON. Trailing-edge flaps use the alternate electric drive when ALTERNATE FLAPS is armed.",
            fontSize = 10.sp,
            color = Classic737Palette.Subtle,
        )
    }
}

/** The landing-gear position fault box from the web fault module. */
@Composable
private fun GearFaultBox(gearLabel: String, gearActive: Boolean, cockpit: CockpitModel, now: () -> Double) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(DangerFill, RoundedCornerShape(6.dp))
            .border(1.dp, DangerBorder, RoundedCornerShape(6.dp))
            .padding(8.dp),
    ) {
        Text("LANDING GEAR POSITION FAULT", fontSize = 9.sp, color = Color(0xFFFECACA))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { cockpit.applySim { SimActions.injectLandingGearFault(it, now()) } },
                enabled = !gearActive,
                modifier = Modifier.fillMaxWidth(0.48f),
            ) { Text("Inject random gear fault", fontSize = 10.sp) }
            OutlinedButton(
                onClick = { cockpit.applySim { SimActions.clearLandingGearFault(it, now()) } },
                enabled = gearActive,
                modifier = Modifier.fillMaxWidth(0.48f),
            ) { Text("Clear gear fault", fontSize = 10.sp) }
        }
        Text("Active: $gearLabel", fontSize = 8.sp, color = Classic737Palette.Subtle)
        Text(
            "Each injection randomly selects one gear and holds it from extending while the selector is DN, creating a " +
                "stable selector / position disagreement. A new injection is available after the current fault is cleared.",
            fontSize = 9.sp,
            color = Classic737Palette.Subtle,
        )
    }
}
