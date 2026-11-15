package com.oersted.trainer.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.Derived
import com.oersted.trainer.sim.SimState
import com.oersted.trainer.ui.common.Classic737Palette

/**
 * Port of components/panel/RealisticFuelPanel.tsx — 737-200 fuel overhead layout.
 * ENG valve / filter / heat / crossfeed / centre + wing pumps + low-pressure lights.
 */
@Composable
fun RealisticFuelPanel(
    s: SimState,
    d: Derived,
    edit: SimEdit,
    editNote: ((String, SimState.() -> Unit) -> Unit)? = null,
) {
    val dcOn = d.dcPower
    val lampTest = s.ltTest && dcOn
    val panelBg = Brush.verticalGradient(
        listOf(Color(0xFF3E413B), Color(0xFF32352F), Color(0xFF2A2C28)),
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(2.dp, Color(0xFF1A1C18), RoundedCornerShape(6.dp))
            .background(panelBg),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color(0xE62E312C))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                "FUEL · P5",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE0D8C0),
                letterSpacing = 1.6.sp,
            )
        }

        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top annunciators + fuel temp
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PanelCaptionLight("ENG VALVE CLOSED", lampTest || (dcOn && !d.fuelShutoff1Open), LightTone.ELEC)
                    PanelCaptionLight("FILTER BYPASS", lampTest, LightTone.CAUTION)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("FUEL TEMP", fontSize = 8.sp, color = Classic737Palette.Muted)
                    Text(
                        "${s.fuelTempC.toInt()} °C",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (d.fuelTempIndicationPowered) Classic737Palette.Ok else Classic737Palette.Subtle,
                    )
                    PanelCaptionLight(
                        "VALVE OPEN",
                        lampTest || (dcOn && s.fuelCrossfeed && !s.faults.fuelCrossfeedValveFail),
                        LightTone.ELEC,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PanelCaptionLight("ENG VALVE CLOSED", lampTest || (dcOn && !d.fuelShutoff2Open), LightTone.ELEC)
                    PanelCaptionLight("FILTER BYPASS", lampTest, LightTone.CAUTION)
                }
            }

            // Heat + crossfeed
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PanelRocker(
                    label = "HEAT 1",
                    on = s.fuelHeat,
                    onChange = { v ->
                        edit {
                            fuelHeat = v
                            if (v) fuelHeatElapsedSec = 0.0
                        }
                    },
                    lit = d.fuelHeatValveOpen,
                )
                PanelThreePos(
                    label = "CROSS FEED",
                    value = if (s.fuelCrossfeed) "open" else "closed",
                    options = listOf(PanelOption("closed", "CLOSE"), PanelOption("open", "OPEN")),
                    onChange = { id ->
                        val open = id == "open"
                        if (open != s.fuelCrossfeed) {
                            val note = if (open) "FUEL CROSSFEED OPEN" else "FUEL CROSSFEED CLOSED"
                            if (editNote != null) editNote(note) { fuelCrossfeed = open }
                            else edit { fuelCrossfeed = open }
                        }
                    },
                )
                PanelRocker(
                    label = "HEAT 2",
                    on = s.fuelHeat2,
                    onChange = { v ->
                        edit {
                            fuelHeat2 = v
                            if (v) fuelHeat2ElapsedSec = 0.0
                        }
                    },
                    lit = d.fuelHeatValve2Open,
                )
            }

            // Centre pumps
            Text("FUEL PUMPS CTR", fontSize = 8.sp, color = Classic737Palette.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PanelCaptionLight("LOW PRESS", d.fuelCenterPump1LowPressure, LightTone.CAUTION)
                    PanelRocker(
                        label = "L",
                        on = s.fuelPumpCenter1,
                        onChange = { v -> edit { fuelPumpCenter1 = v } },
                        lit = d.fuelCenterPump1On,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PanelCaptionLight("LOW PRESS", d.fuelCenterPump2LowPressure, LightTone.CAUTION)
                    PanelRocker(
                        label = "R",
                        on = s.fuelPumpCenter2,
                        onChange = { v -> edit { fuelPumpCenter2 = v } },
                        lit = d.fuelCenterPump2On,
                    )
                }
            }

            // Wing pumps 1 and 2
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("FUEL PUMPS 1", fontSize = 8.sp, color = Classic737Palette.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PanelCaptionLight("LOW", d.fuelPump1AftLowPressure, LightTone.CAUTION)
                            PanelRocker(
                                label = "AFT",
                                on = s.fuelPump1Aft,
                                onChange = { v -> edit { fuelPump1Aft = v } },
                                lit = d.fuelPump1AftOn,
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PanelCaptionLight("LOW", d.fuelPump1FwdLowPressure, LightTone.CAUTION)
                            PanelRocker(
                                label = "FWD",
                                on = s.fuelPump1Fwd,
                                onChange = { v -> edit { fuelPump1Fwd = v } },
                                lit = d.fuelPump1FwdOn,
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("FUEL PUMPS 2", fontSize = 8.sp, color = Classic737Palette.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PanelCaptionLight("LOW", d.fuelPump2FwdLowPressure, LightTone.CAUTION)
                            PanelRocker(
                                label = "FWD",
                                on = s.fuelPump2Fwd,
                                onChange = { v -> edit { fuelPump2Fwd = v } },
                                lit = d.fuelPump2FwdOn,
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PanelCaptionLight("LOW", d.fuelPump2AftLowPressure, LightTone.CAUTION)
                            PanelRocker(
                                label = "AFT",
                                on = s.fuelPump2Aft,
                                onChange = { v -> edit { fuelPump2Aft = v } },
                                lit = d.fuelPump2AftOn,
                            )
                        }
                    }
                }
            }

            // Tank quantity readout
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xCC1C1E1A))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                listOf(
                    "TK1" to s.fuelTank1,
                    "CTR" to s.fuelTankCenter,
                    "TK2" to s.fuelTank2,
                ).forEach { (label, qty) ->
                    Text(
                        "$label ${qty.toInt()}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = Classic737Palette.Muted,
                    )
                }
            }
        }
    }
}
