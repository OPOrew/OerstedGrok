package com.oersted.trainer.ui.cockpit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oersted.trainer.ui.panel.MainInstrumentPanel
import com.oersted.trainer.ui.pedestal.CenterPedestal
import com.oersted.trainer.ui.pedestal.ThrustLinkButton

/**
 * Flight deck: only the primary aircraft panels (aligned with web flight-deck).
 * - Main Instrument Panel
 * - Center Pedestal
 * Training + log remain as side tools (same as web).
 * Full P5 interactive overhead (web Overhead / OverheadP5) is not ported yet.
 */
@Composable
fun CockpitScreen(model: CockpitModel, audio: com.oersted.trainer.platform.AudioEngine) {
    val log by model.log.collectAsState()
    val simState by model.simState.collectAsState()
    val derived by model.derived.collectAsState()
    var trainingOpen by remember { mutableStateOf(false) }
    var logOpen by remember { mutableStateOf(false) }
    var panelScale by remember { mutableStateOf(0.5f) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "MAIN INSTRUMENT PANEL",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { panelScale = (panelScale - 0.1f).coerceAtLeast(0.3f) },
                    enabled = panelScale > 0.31f,
                ) { Text("-") }
                Text("${Math.round(panelScale * 100)}%", style = MaterialTheme.typography.labelSmall)
                OutlinedButton(
                    onClick = { panelScale = (panelScale + 0.1f).coerceAtMost(1.0f) },
                    enabled = panelScale < 0.99f,
                ) { Text("+") }
            }
            Spacer(Modifier.height(8.dp))
            MainInstrumentPanel(
                cockpit = model,
                scale = panelScale,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "CENTER PEDESTAL",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                ThrustLinkButton(cockpit = model)
            }
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                CenterPedestal(cockpit = model, width = maxWidth)
            }

            Spacer(Modifier.height(24.dp))
        }

        LogDrawerView(
            entries = log,
            open = logOpen,
            onToggle = { logOpen = !logOpen },
            onClose = { logOpen = false },
            onClear = { model.clearLog() },
        )

        if (trainingOpen) {
            TrainingConsoleView(
                s = simState,
                d = derived,
                change = model::applySim,
                onClose = { trainingOpen = false },
            )
        } else {
            OutlinedButton(
                onClick = { trainingOpen = true },
                shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp),
            ) { Text("TRAINING") }
        }
    }
}
