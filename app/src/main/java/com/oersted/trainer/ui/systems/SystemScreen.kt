package com.oersted.trainer.ui.systems

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.oersted.trainer.data.Part
import com.oersted.trainer.data.SystemDef
import com.oersted.trainer.sim.SimActions
import com.oersted.trainer.ui.aircraft.AircraftPlan
import com.oersted.trainer.ui.cockpit.CockpitModel
import com.oersted.trainer.ui.common.Classic737Palette

/**
 * Port of routes/systems.$id.tsx: header, system chips, the aircraft plan with hotspots, the selected part,
 * the fault buttons and the part list.
 * Not ported yet: the Overhead and GroundModule panels the web page shows under the plan.
 */
@Composable
fun SystemScreen(
    system: SystemDef,
    systems: List<SystemDef>,
    allParts: List<Part>,
    cockpit: CockpitModel,
    onOpenSystem: (String) -> Unit,
    onBack: () -> Unit,
) {
    val sim by cockpit.simState.collectAsState()
    val derived by cockpit.derived.collectAsState()
    val parts = allParts.filter { it.system == system.id }
    var selectedId by rememberSaveable(system.id) { mutableStateOf(parts.firstOrNull()?.id) }
    val part = parts.firstOrNull { it.id == selectedId } ?: parts.firstOrNull()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedButton(onClick = onBack) { Text("← Hangar") }
        }
        item {
            Column {
                Text("ATA ${system.ata} · ${system.panel}", style = MaterialTheme.typography.labelSmall)
                Text(system.title, style = MaterialTheme.typography.headlineMedium)
                Text(
                    system.blurb,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Power: ${system.power}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                systems.forEach { item ->
                    FilterChip(
                        selected = item.id == system.id,
                        onClick = { onOpenSystem(item.id) },
                        label = { Text(item.short) },
                    )
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FaultButton("Door jam", "apuDoorJam", sim.faults.apuDoorJam, cockpit)
                FaultButton("Hyd A leak", "hydALeak", sim.faults.hydALeak, cockpit)
                FaultButton("Pack overheat (L)", "packOverheat", sim.faults.packOverheat, cockpit)
                FaultButton("Pack overheat (R)", "packOverheatR", sim.faults.packOverheatR, cockpit)
                FaultButton("Bleed overheat (1)", "bleedOverheat1", sim.faults.bleedOverheat1, cockpit)
                FaultButton("Bleed overheat (2)", "bleedOverheat2", sim.faults.bleedOverheat2, cockpit)
                FaultButton("APU fire detected", "apuFireDetected", sim.faults.apuFireDetected, cockpit)
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Box(Modifier.padding(8.dp)) {
                    AircraftPlan(
                        sim = sim,
                        derived = derived,
                        allParts = allParts,
                        activeSystem = system.id,
                        selectedId = part?.id,
                        onSelect = { selectedId = it },
                    )
                }
            }
        }
        item {
            part?.let { PartDetail(it) }
        }
        items(parts, key = { it.id }) { p ->
            PartCard(p, selected = p.id == part?.id, onClick = { selectedId = p.id })
        }
    }
}

@Composable
private fun FaultButton(label: String, key: String, active: Boolean, cockpit: CockpitModel) {
    OutlinedButton(
        onClick = {
            cockpit.applySim { s -> SimActions.toggleFault(s, key, System.currentTimeMillis().toDouble()) }
        },
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (active) Classic737Palette.Warn.copy(alpha = 0.25f) else Color.Transparent,
        ),
    ) {
        Text(label)
    }
}

@Composable
private fun PartCard(part: Part, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("ATA ${part.ata} · ${part.group}", style = MaterialTheme.typography.labelMedium)
            Text(part.name, style = MaterialTheme.typography.titleSmall)
            Text(part.function, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PartDetail(part: Part) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("ATA ${part.ata} · ${part.group}", style = MaterialTheme.typography.labelMedium)
            Text(part.name, style = MaterialTheme.typography.titleMedium)
            Text(part.function, style = MaterialTheme.typography.bodyMedium)
            Text("Flow: ${part.flow}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            Text("Relationships: ${part.relationships}", style = MaterialTheme.typography.bodySmall)
            if (part.specs.isNotEmpty()) {
                Text("Specs: ${part.specs.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall)
            }
            Text("Controls: ${part.controls}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
