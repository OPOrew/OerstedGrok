package com.oersted.trainer.ui.cockpit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oersted.trainer.sim.CockpitReadout
import com.oersted.trainer.ui.glareshield.GlareshieldPanel
import com.oersted.trainer.ui.instruments.AlertLenses
import com.oersted.trainer.ui.instruments.AnnPanel
import com.oersted.trainer.ui.instruments.TopStrip
import com.oersted.trainer.ui.panel.MainInstrumentPanel
import com.oersted.trainer.ui.panel.Overhead
import com.oersted.trainer.ui.pedestal.CenterPedestal
import com.oersted.trainer.ui.pedestal.PedestalOtherFunctions
import com.oersted.trainer.ui.pedestal.ThrustLinkButton
import androidx.compose.foundation.layout.BoxWithConstraints

/** Cockpit tab: phase, hint, the key readouts and the phase buttons (each gated as the web's canStart). Not compiled in the sandbox. */
@Composable
fun CockpitScreen(model: CockpitModel, audio: com.oersted.trainer.platform.AudioEngine) {
    val r by model.readout.collectAsState()
    val sixPack by model.sixPack.collectAsState()
    val log by model.log.collectAsState()
    val simState by model.simState.collectAsState()
    val derived by model.derived.collectAsState()
    var trainingOpen by remember { mutableStateOf(false) }
    var logOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("COCKPIT", style = MaterialTheme.typography.labelSmall)
        Text(r.phaseLabel, style = MaterialTheme.typography.headlineMedium)
        SoundControls(audio)
        Text(r.hint, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        SixPackView(sixPack)
        Spacer(Modifier.height(12.dp))
        Text("ALT ${"%.0f".format(r.altFt)} FT   CAS ${"%.0f".format(r.casKt)} KT   VS ${"%.0f".format(r.vsFpm)} FPM")
        Text("HDG ${"%.0f".format(r.headingDeg)}   THR ${"%.0f".format(r.throttle1 * 100)} %")
        Spacer(Modifier.height(16.dp))
        CockpitReadout.BUTTON_PHASES.forEach { p ->
            val gate = r.gates[p]
            Row(Modifier.fillMaxWidth()) {
                Button(
                    onClick = { model.startPhase(p) },
                    enabled = gate?.ok == true,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(p.label) }
            }
            if (gate != null && !gate.ok) Text(gate.reason, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("GLARESHIELD", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            GlareshieldPanel(cockpit = model, width = maxWidth)
        }
        Spacer(Modifier.height(12.dp))
        Text("CAPTAIN CAPTION STRIP", style = MaterialTheme.typography.labelSmall)
        TopStrip(cockpit = model, left = true)
        Spacer(Modifier.height(8.dp))
        Text("FIRST OFFICER CAPTION STRIP", style = MaterialTheme.typography.labelSmall)
        TopStrip(cockpit = model, left = false)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).fillMaxSize()) { AlertLenses(cockpit = model, left = true) }
            Box(Modifier.weight(2f).fillMaxSize()) { AnnPanel(cockpit = model, left = true) }
        }
        Spacer(Modifier.height(16.dp))
        // The main panel is a 3036 x 720 design canvas: it scrolls sideways at a readable zoom. Zoom steps of 10 % from 30 % to 100 %.
        var panelScale by remember { mutableStateOf(0.5f) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("MAIN INSTRUMENT PANEL", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { panelScale = (panelScale - 0.1f).coerceAtLeast(0.3f) }, enabled = panelScale > 0.31f) { Text("-") }
            Text("${Math.round(panelScale * 100)}%", style = MaterialTheme.typography.labelSmall)
            OutlinedButton(onClick = { panelScale = (panelScale + 0.1f).coerceAtMost(1.0f) }, enabled = panelScale < 0.99f) { Text("+") }
        }
        Spacer(Modifier.height(8.dp))
        MainInstrumentPanel(cockpit = model, scale = panelScale, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("CENTER PEDESTAL", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            ThrustLinkButton(cockpit = model)
        }
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            CenterPedestal(cockpit = model, width = maxWidth)
        }
        Spacer(Modifier.height(8.dp))
        var otherOpen by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { otherOpen = !otherOpen }, modifier = Modifier.fillMaxWidth()) {
            Text("${if (otherOpen) "▼" else "▶"} OTHER PEDESTAL FUNCTIONS", style = MaterialTheme.typography.labelSmall)
        }
        if (otherOpen) {
            Spacer(Modifier.height(8.dp))
            PedestalOtherFunctions(cockpit = model)
        }
        Spacer(Modifier.height(16.dp))
        Text("OVERHEAD PANEL", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(8.dp))
        Overhead(cockpit = model)
    }
    LogDrawerView(
        entries = log,
        open = logOpen,
        onToggle = { logOpen = !logOpen },
        onClose = { logOpen = false },
        onClear = { model.clearLog() },
    )
    if (trainingOpen) {
        TrainingConsoleView(s = simState, d = derived, change = model::applySim, onClose = { trainingOpen = false })
    } else {
        OutlinedButton(
            onClick = { trainingOpen = true },
            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp),
        ) { Text("TRAINING") }
    }
    }
}
