package com.oersted.trainer.ui.instruments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.ui.cockpit.ApproachProgressView
import com.oersted.trainer.ui.cockpit.CockpitModel
import com.oersted.trainer.sim.WarningReducer

/*
 * Glareshield-strip annunciators from components/panel/MainInstrumentPanel.tsx: TopStrip (the caption strip with the
 * MASTER CAUTION lamps and RECALL), AnnPanel (F/D & A/P, A/T and PDC annunciators with the P/RST buttons and the TEST
 * toggle) and AlertLenses (WINDSHEAR, approach progress, ALTITUDE ALERT and the captain's GPWS lenses).
 * The web takes PushButton, Toggle and Annun from PedestalParts, which is not ported yet; the small versions here
 * are private and should be replaced when PedestalParts lands. Not compiled in the sandbox (needs the Android SDK).
 */

private val LegendColour = Color(0xFFE8E4D6)
private val Amber = Color(0xFFE9A93A)
private val AmberRim = Color(0xFFFFD27A)
private val LampDark = Color(0xFF16191C)
private val LampDarkRim = Color(0xFF1D2023)

/** Caption strip for one side: HYD / ELEC (or PACK / BLEED), MASTER CAUTION, RECALL, MASTER CAUTION, FUEL / ENG (or A/SKID / DOORS). */
@Composable
fun TopStrip(cockpit: CockpitModel, left: Boolean) {
    val s by cockpit.simState.collectAsState()
    val d by cockpit.derived.collectAsState()
    val m by cockpit.mipState.collectAsState()

    val pw = d.dcPower
    val caution = d.masterCaution && !m.masterCautionAck
    // The web clears the caution acknowledge once the master caution has gone out.
    LaunchedEffect(d.masterCaution, m.masterCautionAck) {
        if (!d.masterCaution && m.masterCautionAck) cockpit.updateMip { WarningReducer.syncCautionAck(it, d.masterCaution) }
    }
    val test = pw && (s.ltTest || m.recall || m.lightKey == 2)
    fun sys(on: Boolean): Boolean = pw && (on || test)
    val mcLit = pw && (caution || m.recall)
    val ackMc = { cockpit.updateMip { WarningReducer.ackMasterCaution(it) } }

    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(Color(0xFF262C35), RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF1B2028), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(Modifier.width(46.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (left) {
                SmallLamp("HYD", sys(d.hydALow || d.hydBLow))
                SmallLamp("ELEC", sys(!d.generator1BusPowered || !d.generator2BusPowered))
            } else {
                SmallLamp("PACK", sys(s.packTripL || s.packTripR))
                SmallLamp("BLEED", sys(s.bleedTrip1 || s.bleedTrip2))
            }
        }
        MasterCautionLamp(lit = mcLit, onPress = ackMc)
        RecallButton(cockpit)
        MasterCautionLamp(lit = mcLit, onPress = ackMc)
        Column(Modifier.width(46.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (left) {
                SmallLamp("FUEL", sys(d.fuelLowPressure1 || d.fuelLowPressure2))
                SmallLamp("ENG", sys(d.eng1Running && d.oilPressure1 < 13.0))
            } else {
                SmallLamp("A/SKID", sys(!d.antiskidInbdOperational || !d.antiskidOutbdOperational))
                SmallLamp("DOORS", sys(false))
            }
        }
    }
}

@Composable
private fun SmallLamp(label: String, on: Boolean) {
    PanelLamp(label = label, on = on, red = false, modifier = Modifier.height(17.dp))
}

@Composable
private fun MasterCautionLamp(lit: Boolean, onPress: () -> Unit) {
    // PanelLamp fills its parent's width, so the fixed width comes from the wrapper.
    Box(Modifier.width(72.dp)) {
        PanelLamp(
            label = "MASTER\nCAUTION",
            on = lit,
            red = false,
            onClick = onPress,
            modifier = Modifier.height(34.dp),
        )
    }
}

/** RECALL: held down, it lights the lamps (press and hold, as the web's mouse down / up). */
@Composable
private fun RecallButton(cockpit: CockpitModel) {
    Box(
        Modifier
            .width(44.dp)
            .height(34.dp)
            .background(Color(0xFF39414D), RoundedCornerShape(3.dp))
            .border(1.dp, Color(0xFF1B2028), RoundedCornerShape(3.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        cockpit.updateMip { it.copy(recall = true) }
                        tryAwaitRelease()
                        cockpit.updateMip { it.copy(recall = false) }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("RECALL", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = LegendColour, textAlign = TextAlign.Center)
            Text("PUSH TEST", fontSize = 6.sp, color = Color(0xFFAAAABB), textAlign = TextAlign.Center)
        }
    }
}

/** F/D & A/P, A/T and PDC annunciator groups, the A/P P/RST and A/T P/RST buttons and the annunciator TEST toggle. */
@Composable
fun AnnPanel(cockpit: CockpitModel, left: Boolean) {
    val s by cockpit.simState.collectAsState()
    val d by cockpit.derived.collectAsState()
    val m by cockpit.mipState.collectAsState()
    val lit = d.dcPower && (s.ltTest || m.annTest == 1 || m.lightKey == 2)

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
            AnnGroup("F/D & A/P", lit)
            AnnGroup("A/T", lit)
            AnnGroup("PDC", lit)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LegendText("A/P STATUS")
                Row(
                    Modifier
                        .background(Color.Black, RoundedCornerShape(2.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    repeat(2) { Cell(lit) }
                }
            }
            PushButton("A/P P/RST") { cockpit.updateMip { it.copy(masterCautionAck = false) } }
            if (left) {
                // The captain panel's A/T socket is empty: a blanking cap.
                Box(
                    Modifier
                        .width(34.dp)
                        .height(26.dp)
                        .background(Color(0xFFC9C5B4), RoundedCornerShape(2.dp))
                        .border(1.dp, Color(0xFF555A60), RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(10.dp).background(Color.Black, RoundedCornerShape(5.dp)))
                }
            } else {
                PushButton("A/T P/RST") { cockpit.updateMip { it.copy(masterCautionAck = false) } }
            }
            AnnunciatorTestToggle(on = m.annTest == 1) { v ->
                cockpit.updateMip { it.copy(annTest = if (v) 1 else 0) }
            }
        }
    }
}

@Composable
private fun AnnGroup(title: String, lit: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LegendText(title)
        Column(
            Modifier
                .background(Color.Black, RoundedCornerShape(2.dp))
                .padding(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            repeat(4) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(2) { Cell(lit) }
                }
            }
        }
    }
}

@Composable
private fun Cell(lit: Boolean) {
    Box(
        Modifier
            .size(30.dp, 9.dp)
            .background(if (lit) Amber else LampDark, RoundedCornerShape(1.dp))
            .border(1.dp, if (lit) AmberRim else LampDarkRim, RoundedCornerShape(1.dp)),
    )
}

/** Alert lenses beside the F/D & A/P annunciators. The approach progress lamps come from the autopilot model. */
@Composable
fun AlertLenses(cockpit: CockpitModel, left: Boolean) {
    val s by cockpit.simState.collectAsState()
    val d by cockpit.derived.collectAsState()
    val m by cockpit.mipState.collectAsState()
    val pw = d.dcPower
    val test = pw && (s.ltTest || m.annTest == 1 || m.lightKey == 2)

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PanelLamp(label = "F/D\nWINDSHEAR", on = test, red = false, modifier = Modifier.height(34.dp))
        ApproachProgressView(
            vocLight = m.apAnn.voc,
            gsLight = m.apAnn.gs,
            powered = pw,
            modifier = Modifier.fillMaxWidth().height(40.dp),
        )
        PanelLamp(label = "ALTITUDE\nALERT", on = test, red = false, modifier = Modifier.height(34.dp))
        if (left) {
            PanelLamp(label = "GPWS", on = test, red = false, modifier = Modifier.height(24.dp))
            PanelLamp(label = "BELOW G/S\nP INHIBIT", on = test, red = false, modifier = Modifier.height(34.dp))
        }
    }
}

@Composable
private fun LegendText(text: String) {
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 7.sp,
        color = LegendColour,
        textAlign = TextAlign.Center,
    )
}

/** Black-bezel push button with a legend, the web's PushButton (PedestalParts) in a small form. */
@Composable
private fun PushButton(label: String, onPress: () -> Unit) {
    Box(
        Modifier
            .height(26.dp)
            .background(Color(0xFF4A5059), RoundedCornerShape(3.dp))
            .border(1.dp, Color(0xFF0B0C0E), RoundedCornerShape(3.dp))
            .clickable { onPress() }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = LegendColour)
    }
}

/** Two-position annunciator TEST toggle: up is "1", down is "2" (the web's Toggle with upLabel / downLabel). */
@Composable
private fun AnnunciatorTestToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LegendText("TEST")
        Box(
            Modifier
                .width(34.dp)
                .height(20.dp)
                .background(Color(0xFF101214), RoundedCornerShape(3.dp))
                .border(1.dp, Color(0xFF2C323D), RoundedCornerShape(3.dp))
                .clickable { onChange(!on) },
            contentAlignment = Alignment.Center,
        ) {
            Text(if (on) "1" else "2", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = LegendColour)
        }
    }
}
