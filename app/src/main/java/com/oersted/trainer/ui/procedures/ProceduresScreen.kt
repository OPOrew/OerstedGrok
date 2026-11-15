package com.oersted.trainer.ui.procedures

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.oersted.trainer.sim.Procedures
import com.oersted.trainer.ui.cockpit.CockpitModel
import com.oersted.trainer.ui.common.Classic737Palette

/** Port of routes/procedures.tsx: category switch, procedure chips, live step list and the reset / APU buttons. */
@Composable
fun ProceduresScreen(cockpit: CockpitModel) {
    val s by cockpit.simState.collectAsState()
    val d by cockpit.derived.collectAsState()

    var category by rememberSaveable { mutableStateOf(Procedures.ProcedureCategory.NORMAL.name) }
    val cat = Procedures.ProcedureCategory.valueOf(category)
    val visible = Procedures.PROCEDURES.filter { it.category == cat }
    var procId by rememberSaveable { mutableStateOf(Procedures.PROCEDURES.first().id) }
    val proc = visible.firstOrNull { it.id == procId } ?: visible.first()

    val doneFlags = proc.steps.map { it.done(s, d) }
    val current = doneFlags.indexOfFirst { !it }
    val complete = current == -1

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column {
                Text("GUIDED SEQUENCES", style = MaterialTheme.typography.labelSmall)
                Text("Procedures", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Each step is live against the same airplane. The next action is highlighted. Emergency / abnormal " +
                        "sequences are training simulations based on the supplied 737-200 manuals; use the approved " +
                        "aircraft checklist/QRH for real-world operation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = cat == Procedures.ProcedureCategory.NORMAL,
                    onClick = {
                        category = Procedures.ProcedureCategory.NORMAL.name
                        procId = Procedures.PROCEDURES.first { it.category == Procedures.ProcedureCategory.NORMAL }.id
                    },
                    label = { Text("Normal Procedures") },
                )
                FilterChip(
                    selected = cat == Procedures.ProcedureCategory.EMERGENCY,
                    onClick = {
                        category = Procedures.ProcedureCategory.EMERGENCY.name
                        procId = Procedures.PROCEDURES.first { it.category == Procedures.ProcedureCategory.EMERGENCY }.id
                    },
                    label = { Text("Emergency / Abnormal") },
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                visible.forEach { p ->
                    FilterChip(
                        selected = p.id == proc.id,
                        onClick = { procId = p.id },
                        label = { Text("ATA ${p.ata}  ${p.title}") },
                    )
                }
            }
        }
        itemsIndexed(proc.steps) { i, st ->
            val done = doneFlags[i]
            val active = i == current
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        done -> Classic737Palette.Ok.copy(alpha = 0.12f)
                        active -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surface
                    },
                ),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(if (done) Classic737Palette.Ok else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (done) "✓" else "${i + 1}", style = MaterialTheme.typography.labelMedium)
                    }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(st.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            st.why,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (active) {
                            Text(
                                st.hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(proc.title.uppercase(), style = MaterialTheme.typography.labelSmall)
                        Text(proc.summary, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        if (complete) "Complete" else "In progress",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (complete) Classic737Palette.Ok else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { cockpit.coldAndDark() }) { Text("Reset airplane") }
                OutlinedButton(onClick = { cockpit.readyApu() }) { Text("APU running preset") }
            }
        }
    }
}
