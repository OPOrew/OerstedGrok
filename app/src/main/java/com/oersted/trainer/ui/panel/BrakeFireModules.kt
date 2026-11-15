@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.oersted.trainer.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.Derived
import com.oersted.trainer.sim.FireProtectionRules
import com.oersted.trainer.sim.SimState
import com.oersted.trainer.sim.TrainingConsoleRules
import com.oersted.trainer.ui.cockpit.CockpitModel
import com.oersted.trainer.ui.common.Classic737Palette

/**
 * Brakes and fire modules from components/panel/Overhead.tsx (BrakeModule, BrakeWheelPanel, FireModule).
 * Fire handle and bottle actions go through the already-ported FireProtectionRules; autobrake through
 * TrainingConsoleRules.setAutobrake. Web log notes are not written yet.
 */

@Composable
fun BrakeModule(cockpit: CockpitModel, s: SimState, d: Derived) {
    val edit: SimEdit = { block -> cockpit.patchSim(block) }
    PanelModule("Brakes / antiskid · P2-2") {
        PanelRocker("INBD A/S", s.antiskidInbd, onChange = { v -> edit { antiskidInbd = v } })
        PanelRocker("OUTBD A/S", s.antiskidOutbd, onChange = { v -> edit { antiskidOutbd = v } })
        // The 737-200 AUTOBRAKE selector is five-position: RTO counterclockwise from OFF; MIN/MED/MAX clockwise.
        PanelRotary(
            label = "AUTOBRAKE",
            value = s.autobrake.toString(),
            options = listOf(
                PanelOption("4", "RTO"),
                PanelOption("0", "OFF"),
                PanelOption("1", "MIN"),
                PanelOption("2", "MED"),
                PanelOption("3", "MAX"),
            ),
            onChange = { id -> cockpit.applySim { TrainingConsoleRules.setAutobrake(it, id.toInt()) } },
        )
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelCaptionLight("A/S INBD", d.antiskidOperational && s.antiskidInbd, LightTone.OK)
            PanelCaptionLight("A/S OUTBD", d.antiskidOperational && s.antiskidOutbd, LightTone.OK)
            PanelCaptionLight("AUTOBRAKE DISARM", d.autobrakeDisarm, LightTone.WARN)
            PanelCaptionLight("AUTOBRAKE ACTIVE", d.autobrakeActive, LightTone.CAUTION)
        }
        ReadoutBox(
            listOf(
                Triple("TEST", if (d.autobrakeTestPassed) "PASS" else "WAIT", d.autobrakeTestPassed),
                Triple("STATUS", if (d.autobrakeArmed) "ARMED" else if (d.autobrakeActive) "ACTIVE" else "OFF", d.autobrakeArmed || d.autobrakeActive),
                Triple("BRAKE CMD", "${Math.round(d.autobrakePressure)} PSI", d.autobrakePressure > 0),
                Triple("A/S", if (d.antiskidModulating) "IN PATH" else "STANDBY", d.antiskidModulating),
            ),
        )
        Text(
            "AUTOBRAKE pressure is routed through the antiskid valves. Either antiskid channel OFF/faulted removes automatic braking.",
            fontSize = 9.sp,
            color = Classic737Palette.Subtle,
        )
        PanelControlSlider("L BRAKE PEDAL", s.leftBrakePedal, { v -> edit { leftBrakePedal = v } }, min = 0.0, max = 1.0)
        PanelControlSlider("R BRAKE PEDAL", s.rightBrakePedal, { v -> edit { rightBrakePedal = v } }, min = 0.0, max = 1.0)
        BrakeWheelReadout(d)
    }
}

/** Web BrakeWheelPanel: wheel and modulation readouts, as the text rows (the wheel dynamics graphic is not ported). */
@Composable
fun BrakeWheelReadout(d: Derived) {
    ReadoutBox(
        listOf(
            Triple("GROUND SPEED", "${Math.round(d.wheelSpeeds.li)} KT", false),
            Triple("TARGET DECEL", "${Math.round(d.targetDecel)} /s", false),
            Triple("ACTUAL DECEL", "${"%.1f".format(d.actualDecel)} /s", false),
            Triple("AB PRESS CMD", "${Math.round(d.autobrakePressure)} PSI", false),
            Triple("MANUAL OVERRIDE", if (d.manualBrakeOverride) "YES" else "NO", d.manualBrakeOverride),
            Triple("A/S MODULATION", if (d.antiskidModulating) "ACTIVE" else "STANDBY", d.antiskidModulating),
            Triple("NOSE STEERING", "${Math.round(d.noseWheelSteeringAngle)}°", false),
            Triple(
                "BRAKE SOURCES",
                "LI ${d.brakeSource.li} · LO ${d.brakeSource.lo} · RI ${d.brakeSource.ri} · RO ${d.brakeSource.ro}",
                false,
            ),
        ),
    )
}

@Composable
fun FireModule(cockpit: CockpitModel, s: SimState, d: Derived) {
    val edit: SimEdit = { block -> cockpit.patchSim(block) }

    fun applyOutcome(f: (SimState) -> FireProtectionRules.Outcome?) {
        cockpit.applySim { st -> f(st)?.state ?: st }
    }

    PanelModule("Fire protection · P8 center pedestal") {
        PanelFireHandle(
            label = "ENG 1 FIRE",
            pulled = s.engine1FirePulled,
            onToggle = { applyOutcome { FireProtectionRules.pullEngineHandle(it, 1, it.faults.engineFire1) } },
            onRotate = { dir -> applyOutcome { FireProtectionRules.dischargeEngineBottle(it, 1, dir == "L", stubborn = false) } },
        )
        PanelFireHandle(
            label = "ENG 2 FIRE",
            pulled = s.engine2FirePulled,
            onToggle = { applyOutcome { FireProtectionRules.pullEngineHandle(it, 2, it.faults.engineFire2) } },
            onRotate = { dir -> applyOutcome { FireProtectionRules.dischargeEngineBottle(it, 2, dir == "L", stubborn = false) } },
        )
        PanelFireHandle(
            label = "APU FIRE",
            pulled = s.apuFirePulled,
            onToggle = { applyOutcome { FireProtectionRules.pullApuHandle(it) } },
        )
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelCaptionLight("ENG 1 FIRE", d.engine1FireWarn, LightTone.WARN)
            PanelCaptionLight("ENG 2 FIRE", d.engine2FireWarn, LightTone.WARN)
            PanelCaptionLight("APU FIRE", d.apuFireWarn, LightTone.WARN)
        }
        Text(
            "Pull the red handle to isolate the engine and arm extinguishing. While pulled, use the L / R buttons beside " +
                "the handle to rotate it and discharge the corresponding bottle.",
            fontSize = 9.sp,
            color = Classic737Palette.Subtle,
            modifier = Modifier
                .fillMaxWidth()
                .background(Classic737Palette.Inset, RoundedCornerShape(6.dp))
                .padding(8.dp),
        )
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelCaptionLight("BOTTLE L DISCH", s.fireBottleLDischarged, LightTone.CAUTION)
            PanelCaptionLight("BOTTLE R DISCH", s.fireBottleRDischarged, LightTone.CAUTION)
        }
        val apuEnabled = s.apuFirePulled
        Box(
            Modifier
                .fillMaxWidth()
                .background(Classic737Palette.Inset, RoundedCornerShape(6.dp))
                .border(1.dp, Classic737Palette.Border, RoundedCornerShape(6.dp))
                .clickable(enabled = apuEnabled) { applyOutcome { FireProtectionRules.dischargeApuBottle(it) } }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "APU BOTTLE DISCHARGE",
                fontSize = 9.sp,
                color = if (apuEnabled) Classic737Palette.Fg else Classic737Palette.Subtle.copy(alpha = 0.4f),
            )
        }
    }
}
