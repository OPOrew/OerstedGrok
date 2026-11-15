@file:OptIn(ExperimentalLayoutApi::class)

package com.oersted.trainer.ui.panel

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.oersted.trainer.sim.LightsTrainerRules
import com.oersted.trainer.ui.cockpit.CockpitModel
import com.oersted.trainer.ui.common.Classic737Palette

/**
 * Port of components/panel/Overhead.tsx (the module grid and the lighting modules).
 * `focus` picks one group, as the web does; null or "all" shows everything.
 * Not ported yet: the Landing gear, Brake, Fire, Fault, Scenario, Electrical distribution and Ground modules that
 * the web file also contains (they belong to their own section views).
 */
@Composable
fun Overhead(
    cockpit: CockpitModel,
    focus: String? = null,
    noPedestal: Boolean = false,
) {
    val s by cockpit.simState.collectAsState()
    val d by cockpit.derived.collectAsState()
    val edit: SimEdit = { block -> cockpit.patchSim(block) }

    fun show(k: String) = focus == null || focus == "all" || focus == k
    val all = focus == null || focus == "all"

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnnunciatorBar(s, d)
        if (show("flt")) FlightModule(s, d, edit, noPedestal)
        if (show("hyd")) HydModule(s, d, edit)
        if (show("elec")) ElecModule(s, d, edit)
        if (show("apu")) ApuModule(s, d, edit)
        if (show("bleed")) BleedModule(s, d, edit)
        if (show("start")) StartModule(s, d, edit)
        if (all) FuelModule(s, d, edit)
        if (all) EnvironmentModule(s, edit)
        if (all) AntiIceModule(s, d, edit)
        if (show("lights")) ExteriorLightsModule(s, d, edit)
        if (show("lights")) FlightDeckLightsModule(s, d, edit)
        if (show("lights")) SignsEmergencyModule(s, d, edit, onAllOff = { cockpit.applySim { LightsTrainerRules.resetSwitches(it) } })
    }
}

private fun lit(d: com.oersted.trainer.sim.Derived, id: String): Boolean =
    d.lights.firstOrNull { it.id == id }?.lit ?: false

private val RET_OPTS = listOf(PanelOption("RETRACT", "RETR"), PanelOption("EXT", "EXT"), PanelOption("ON", "ON"))

private fun triOpts(a: String, b: String, c: String) = listOf(PanelOption(a, a), PanelOption(b, b), PanelOption(c, c))

@Composable
fun ExteriorLightsModule(s: com.oersted.trainer.sim.SimState, d: com.oersted.trainer.sim.Derived, edit: SimEdit) {
    PanelModule("Exterior lights · P5") {
        PanelThreeToggle("LDG L RETR", s.ltRetL, RET_OPTS, onChange = { id -> edit { ltRetL = id } })
        PanelThreeToggle("LDG R RETR", s.ltRetR, RET_OPTS, onChange = { id -> edit { ltRetR = id } })
        PanelRocker("FIXED L", s.ltFixedL, onChange = { v -> edit { ltFixedL = v } }, lit = lit(d, "ldg-fix-l"))
        PanelRocker("FIXED R", s.ltFixedR, onChange = { v -> edit { ltFixedR = v } }, lit = lit(d, "ldg-fix-r"))
        PanelRocker("RWY TURNOFF L", s.ltTurnoffL, onChange = { v -> edit { ltTurnoffL = v } }, lit = lit(d, "rwy-l"))
        PanelRocker("RWY TURNOFF R", s.ltTurnoffR, onChange = { v -> edit { ltTurnoffR = v } }, lit = lit(d, "rwy-r"))
        PanelRocker("TAXI", s.ltTaxi, onChange = { v -> edit { ltTaxi = v } }, lit = lit(d, "taxi"))
        PanelRocker("WING", s.ltWing, onChange = { v -> edit { ltWing = v } }, lit = lit(d, "wing"))
        PanelRocker("LOGO", s.ltLogo, onChange = { v -> edit { ltLogo = v } }, lit = lit(d, "logo"))
        PanelRocker("WHEEL WELL", s.ltWheelWell, onChange = { v -> edit { ltWheelWell = v } }, lit = lit(d, "wheelwell"))
        PanelRocker("ANTI COLL", s.ltAntiCol, onChange = { v -> edit { ltAntiCol = v } }, lit = lit(d, "beacon-top") || lit(d, "beacon-bot"))
        PanelThreeToggle(
            "POSITION", s.ltPosition,
            listOf(PanelOption("OFF", "OFF"), PanelOption("STEADY", "STEADY"), PanelOption("STROBE_STEADY", "STRB+S")),
            onChange = { id -> edit { ltPosition = id } },
        )
    }
}

@Composable
fun FlightDeckLightsModule(s: com.oersted.trainer.sim.SimState, d: com.oersted.trainer.sim.Derived, edit: SimEdit) {
    val test = s.ltTest && d.dcPower
    PanelModule("Flight deck lighting · P5") {
        PanelThreeToggle("DOME", s.ltDome, triOpts("BRT", "DIM", "OFF"), onChange = { id -> edit { ltDome = id } })
        PanelRocker(
            "MASTER DIM", s.ltMasterDim == "DIM", onLabel = "DIM", offLabel = "BRT",
            onChange = { v -> edit { ltMasterDim = if (v) "DIM" else "BRT" } },
        )
        PanelRocker(
            "ANNUN TEST", s.ltTest, onLabel = "TEST", offLabel = "OFF",
            onChange = { v -> edit { ltTest = v } }, lit = test,
        )
        PanelRocker("MAP L", s.ltMapCapt, onChange = { v -> edit { ltMapCapt = v } }, lit = lit(d, "map-capt"))
        PanelRocker("MAP R", s.ltMapFo, onChange = { v -> edit { ltMapFo = v } }, lit = lit(d, "map-fo"))
        PanelRocker("STORM", s.ltStorm, onChange = { v -> edit { ltStorm = v } }, lit = lit(d, "storm"))
        PanelRocker("COMPASS", s.ltCompass, onChange = { v -> edit { ltCompass = v } }, lit = lit(d, "compass"))
        PanelRocker("OBSERVER", s.ltObserver, onChange = { v -> edit { ltObserver = v } }, lit = lit(d, "observer"))
        PanelRocker("LTSHIELD EMER", s.ltShieldEmer, onChange = { v -> edit { ltShieldEmer = v } }, lit = lit(d, "shield-emer"))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelControlSlider("OVHD PANEL ${Math.round(s.ltPanelOvhd * 100)}%", s.ltPanelOvhd, { v -> edit { ltPanelOvhd = v } })
            PanelControlSlider("CAPT PANEL ${Math.round(s.ltPanelCapt * 100)}%", s.ltPanelCapt, { v -> edit { ltPanelCapt = v } })
            PanelControlSlider("F/O PANEL ${Math.round(s.ltPanelFo * 100)}%", s.ltPanelFo, { v -> edit { ltPanelFo = v } })
            PanelControlSlider("PEDESTAL ${Math.round(s.ltPanelPed * 100)}%", s.ltPanelPed, { v -> edit { ltPanelPed = v } })
        }
    }
}

@Composable
fun SignsEmergencyModule(
    s: com.oersted.trainer.sim.SimState,
    d: com.oersted.trainer.sim.Derived,
    edit: SimEdit,
    onAllOff: () -> Unit,
) {
    PanelModule("Passenger signs · emergency lights · P5") {
        PanelThreeToggle("NO SMOKING", s.ltNoSmoking, triOpts("ON", "AUTO", "OFF"), onChange = { id -> edit { ltNoSmoking = id } })
        PanelThreeToggle("SEAT BELTS", s.ltSeatBelt, triOpts("ON", "AUTO", "OFF"), onChange = { id -> edit { ltSeatBelt = id } })
        PanelThreeToggle("EMER LIGHTS", s.ltEmergency, triOpts("ON", "ARMED", "OFF"), onChange = { id -> edit { ltEmergency = id } })
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelCaptionLight("NO SMOKING", lit(d, "sign-ns"), LightTone.CAUTION)
            PanelCaptionLight("FASTEN BELTS", lit(d, "sign-sb"), LightTone.CAUTION)
            PanelCaptionLight("EMER LTS ON", lit(d, "emer-aisle"), LightTone.OK)
        }
        androidx.compose.material3.OutlinedButton(onClick = onAllOff, modifier = Modifier.fillMaxWidth()) {
            androidx.compose.material3.Text("All lights switches off", fontSize = 9.sp)
        }
    }
}

@Composable
fun AnnunciatorBar(s: com.oersted.trainer.sim.SimState, d: com.oersted.trainer.sim.Derived) {
    // MASTER DIM & TEST (P5): TEST lights every lamp (DC needed), DIM lowers brightness.
    val test = s.ltTest && d.dcPower
    FlowRow(
        Modifier
            .fillMaxWidth()
            .alpha(if (s.ltMasterDim == "DIM") 0.7f else 1f)
            .background(Classic737Palette.Inset, RoundedCornerShape(8.dp))
            .border(1.dp, Classic737Palette.Border, RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PanelCaptionLight("MASTER CAUTION", test || d.masterCaution, LightTone.CAUTION)
        PanelCaptionLight("FIRE WARN", test || d.fireWarn, LightTone.WARN)
        PanelCaptionLight("DUAL BLEED", test || d.dualBleed, LightTone.CAUTION)
        PanelCaptionLight("START VALVE 1", test || d.startValveOpen1, LightTone.CAUTION)
        PanelCaptionLight("START VALVE 2", test || d.startValveOpen2, LightTone.CAUTION)
        PanelCaptionLight("APU GEN OFF BUS", test || d.apuGenOffBus, LightTone.ELEC)
        PanelCaptionLight("APU LOW OIL PRESS", test || d.apuLowOil, LightTone.WARN)
        PanelCaptionLight("GRD POWER AVAILABLE", test || d.groundPowerAvailable, LightTone.ELEC)
        PanelCaptionLight("GEN 1 OFF BUS", test || d.gen1OffBus, LightTone.ELEC)
        PanelCaptionLight("GEN 2 OFF BUS", test || d.gen2OffBus, LightTone.ELEC)
        PanelCaptionLight("HYD A LOW PRESS", test || d.hydALow, LightTone.WARN)
        PanelCaptionLight("HYD B LOW PRESS", test || d.hydBLow, LightTone.WARN)
        PanelCaptionLight("STBY RUD ON", test || d.stbyOn, LightTone.CAUTION)
        PanelCaptionLight("PACK TRIP L", test || (d.dcPower && s.packTripL), LightTone.CAUTION)
        PanelCaptionLight("PACK TRIP R", test || (d.dcPower && s.packTripR), LightTone.CAUTION)
        PanelCaptionLight("BLEED TRIP 1", test || (d.dcPower && s.bleedTrip1), LightTone.CAUTION)
        PanelCaptionLight("BLEED TRIP 2", test || (d.dcPower && s.bleedTrip2), LightTone.CAUTION)
        PanelCaptionLight("SPEED BRK ARMED", test || (d.dcPower && d.speedBrakeArmed), LightTone.OK)
    }
}
