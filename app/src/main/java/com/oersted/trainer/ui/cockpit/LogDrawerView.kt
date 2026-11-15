package com.oersted.trainer.ui.cockpit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.oersted.trainer.sim.LogDrawerRules
import com.oersted.trainer.sim.LogEntry
import java.text.DateFormat
import java.util.Date

private val LogPanel = Color(0xFF151719)
private val LogRow = Color(0xFF1D2227)
private val LogLine = Color(0xFF343A42)
private val LogSubtle = Color(0xFF9DA7B1)

/**
 * Right-edge LOG drawer (routes/cockpit.tsx LogDrawer). [open] is owned by the caller; the vertical LOG tab opens it.
 * Row text comes from [LogDrawerRules]. Not compiled in the sandbox; verify on CI.
 */
@Composable
fun LogDrawerView(
    entries: List<LogEntry>,
    open: Boolean,
    onToggle: () -> Unit,
    onClose: () -> Unit,
    onClear: () -> Unit,
) {
    val timeFormat = DateFormat.getTimeInstance()
    Box(Modifier.fillMaxSize()) {
        OutlinedButton(
            onClick = onToggle,
            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) { Text("LOG") }

        if (open) {
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxSize(fraction = 0.92f)
                    .background(LogPanel)
                    .border(1.dp, LogLine)
                    .padding(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Separate event recorder", color = LogSubtle, style = MaterialTheme.typography.labelSmall)
                        Text("Simulator Log", style = MaterialTheme.typography.titleLarge)
                    }
                    OutlinedButton(onClick = onClose) { Text("Close") }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Control actions · component consequences · system events", color = LogSubtle, style = MaterialTheme.typography.labelSmall)
                    OutlinedButton(onClick = onClear) { Text("Clear") }
                }
                Spacer(Modifier.height(8.dp))
                if (LogDrawerRules.isEmpty(entries)) {
                    Text(
                        LogDrawerRules.EMPTY_TEXT,
                        color = LogSubtle,
                        modifier = Modifier.fillMaxWidth().background(LogRow).padding(12.dp),
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(entries) { e ->
                            Column(Modifier.fillMaxWidth().background(LogRow).padding(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(timeFormat.format(Date(e.t.toLong())), color = LogSubtle, style = MaterialTheme.typography.labelSmall)
                                    Text(LogDrawerRules.rowTitle(e.control), style = MaterialTheme.typography.labelMedium)
                                }
                                Text(e.text, style = MaterialTheme.typography.bodySmall)
                                LogDrawerRules.causeLine(e)?.let {
                                    Text(it, color = LogSubtle, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
