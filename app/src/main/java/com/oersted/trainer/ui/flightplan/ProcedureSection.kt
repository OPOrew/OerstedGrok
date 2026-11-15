package com.oersted.trainer.ui.flightplan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oersted.trainer.sim.FlightPlanDraft
import com.oersted.trainer.sim.FlightPlanEdits
import com.oersted.trainer.sim.FlightPlanProcedures

/**
 * Procedures and planning fields of the PLAN screen (flight-plan.tsx): SID apply, vectored ILS arrival, time compression,
 * the top-of-climb and descent distances. Rules are in sim/FlightPlanProcedures.kt and sim/FlightPlanEdits.kt.
 * [onResult] receives the new draft and the notice text. Not compiled in the sandbox (needs the Android SDK).
 */
@Composable
fun ProcedureSection(draft: FlightPlanDraft, onResult: (FlightPlanDraft, String) -> Unit) {
    var sidBase by remember { mutableStateOf("VERDE") }
    Text("Procedures", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = sidBase,
        onValueChange = { sidBase = it.uppercase() },
        label = { Text("SID (e.g. VERDE, BETEL, BUCAL)") },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    OutlinedButton(
        onClick = {
            when (val r = FlightPlanProcedures.applySid(draft, sidBase.trim(), draft.departureRunway.trim())) {
                is FlightPlanProcedures.Result.Applied -> onResult(r.draft, r.notice)
                is FlightPlanProcedures.Result.Refused -> onResult(draft, r.reason)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Apply SID for departure runway") }
    Spacer(Modifier.height(6.dp))
    OutlinedButton(
        onClick = {
            when (val r = FlightPlanProcedures.applyVectorArrival(draft)) {
                is FlightPlanProcedures.Result.Applied -> onResult(r.draft, r.notice)
                is FlightPlanProcedures.Result.Refused -> onResult(draft, r.reason)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Add vectored ILS arrival (WSSS 02L)") }
    Spacer(Modifier.height(10.dp))
    Text("En-route time compression", style = MaterialTheme.typography.labelMedium)
    Row(Modifier.fillMaxWidth()) {
        FlightPlanEdits.TIME_COMPRESSION_OPTIONS.forEach { v ->
            FilterChip(
                selected = draft.timeCompression == v,
                onClick = { onResult(FlightPlanEdits.withTimeCompression(draft, v), "Time compression ${v.toInt()}x.") },
                label = { Text("${v.toInt()}x") },
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = "%.0f".format(draft.cruiseTopOfClimbNm),
        onValueChange = { onResult(draft.copy(cruiseTopOfClimbNm = FlightPlanEdits.nmFromText(it)), "") },
        label = { Text("Top of climb from departure (NM)") },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = "%.0f".format(draft.descentStartNm),
        onValueChange = { onResult(draft.copy(descentStartNm = FlightPlanEdits.nmFromText(it)), "") },
        label = { Text("Descent start before destination (NM)") },
        modifier = Modifier.fillMaxWidth(),
    )
}
