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
 * Port of components/panel/RealisticElecPanel.tsx — fixed 737-200 P5 electrical layout.
 * Uses the shared Panel* widgets and the same SimEdit pattern as OverheadModules.
 */
@Composable
fun RealisticElecPanel(
    s: SimState,
    d: Derived,
    edit: SimEdit,
    editNote: ((String, SimState.() -> Unit) -> Unit)? = null,
) {
    val annBus = d.dcPower || d.batteryPower
    val gpuConnected = d.groundPowerAvailable || s.externalPowerConnected

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
        // Title bar
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color(0xE62E312C))
                .border(1.dp, Color(0xFF1E201C))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                "ELECTRICAL · P5",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE0D8C0),
                letterSpacing = 1.6.sp,
            )
        }

        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Caption lights row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                PanelCaptionLight("BAT DISCHARGE", annBus && d.batteryPower && !d.acPower, LightTone.CAUTION)
                PanelCaptionLight("GEN 1 OFF BUS", annBus && d.gen1OffBus, LightTone.CAUTION)
                PanelCaptionLight("GEN 2 OFF BUS", annBus && d.gen2OffBus, LightTone.CAUTION)
                PanelCaptionLight("APU GEN OFF BUS", annBus && d.apuGenOffBus, LightTone.CAUTION)
                PanelCaptionLight("GRD PWR AVAIL", annBus && gpuConnected, LightTone.ELEC)
            }

            // Row 1: BATTERY · GRD PWR · STANDBY
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top,
            ) {
                PanelRocker(
                    label = "BATTERY",
                    on = s.battery,
                    onChange = { v -> edit { battery = v } },
                    lit = d.dcPower,
                )
                PanelRocker(
                    label = "GRD PWR",
                    on = s.groundPowerOn,
                    onChange = { v ->
                        if (gpuConnected) {
                            edit {
                                groundPowerOn = v
                                if (v) groundServiceOn = false
                            }
                        } else if (s.groundPowerOn) {
                            edit { groundPowerOn = false }
                        } else {
                            editNote?.invoke(
                                "GRD PWR inhibited — connect the external ground power cart first.",
                                { /* no state change */ },
                            )
                        }
                    },
                    lit = gpuConnected && s.groundPowerOn,
                    disabled = !gpuConnected && !s.groundPowerOn,
                )
                PanelThreePos(
                    label = "STANDBY POWER",
                    value = s.standbyPower,
                    options = listOf(
                        PanelOption("OFF", "OFF"),
                        PanelOption("AUTO", "AUTO"),
                        PanelOption("BAT", "BAT"),
                    ),
                    onChange = { id -> edit { standbyPower = id } },
                )
            }

            // Row 2: GEN 1 · APU GEN · GEN 2
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = Color(0xB31E201C))
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top,
            ) {
                PanelRocker(
                    label = "GEN 1",
                    on = s.gen1,
                    onChange = { v -> edit { gen1 = v } },
                    lit = d.eng1Running && s.gen1,
                )
                PanelRocker(
                    label = "APU GEN",
                    on = s.apuGen,
                    onChange = { v -> edit { apuGen = v } },
                    lit = d.apuAvailable && s.apuGen,
                )
                PanelRocker(
                    label = "GEN 2",
                    on = s.gen2,
                    onChange = { v -> edit { gen2 = v } },
                    lit = d.eng2Running && s.gen2,
                )
            }

            // Row 3: BUS TIE
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = Color(0xB31E201C))
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                PanelRocker(
                    label = "BUS TIE",
                    on = s.busTie,
                    onChange = { v -> edit { busTie = v } },
                    lit = d.acPower && d.transferBus1Powered && d.transferBus2Powered,
                    onLabel = "AUTO",
                    offLabel = "OFF",
                )
            }

            // TR + bus status
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                PanelCaptionLight("TR 1", annBus && d.tr1Powered, LightTone.ELEC)
                PanelCaptionLight("TR 2", annBus && d.tr2Powered, LightTone.ELEC)
                PanelCaptionLight("TR 3", annBus && d.tr3Powered, LightTone.ELEC)
            }

            // Status readout
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFF1E201C), RoundedCornerShape(4.dp))
                    .background(Color(0xCC1C1E1A))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val muted = Color(0xFF5A5548)
                val ok = Classic737Palette.Ok
                Text(
                    buildString {
                        append("AC SRC ")
                        append(d.electricalSource1 ?: "NONE")
                        append("  ·  XFER 1 ")
                        append(if (d.transferBus1Powered) "PWR" else "OFF")
                        append("  ·  XFER 2 ")
                        append(if (d.transferBus2Powered) "PWR" else "OFF")
                        append("  ·  DC ")
                        append(if (d.dcPower) "PWR" else "OFF")
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    color = if (d.acPower || d.dcPower) ok else muted,
                )
            }
        }

        // Footer
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color(0xB3252722))
                .border(1.dp, Color(0xFF1E201C))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                "Fixed electrical layout · Battery · GRD PWR · Standby · GEN 1/2 · APU GEN · Bus tie · TR / transfer buses",
                fontFamily = FontFamily.Monospace,
                fontSize = 7.sp,
                color = Color(0xFF7A7568),
                lineHeight = 10.sp,
            )
        }
    }
}
