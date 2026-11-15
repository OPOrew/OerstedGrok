package com.oersted.trainer.ui.flightplan

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oersted.trainer.platform.PlanStore
import com.oersted.trainer.sim.FlightPlanDraft

/**
 * PLAN tab: edit the core flight-plan fields, save them on this device, and activate them into the cockpit.
 * Waypoints, the map picker, SID / arrival builders and procedure notes are not ported yet (see docs/HANDOFF.md).
 * Not compiled in the sandbox (needs the Android SDK).
 */
@Composable
fun FlightPlanScreen(store: PlanStore, activePlanLabel: String?, onActivate: (FlightPlanDraft) -> Unit) {
    var draft by remember { mutableStateOf(store.load()) }
    var notice by remember { mutableStateOf("Planning workspace ready. Nothing is loaded into the aircraft until you activate.") }
    LaunchedEffect(draft) { store.save(draft) }

    val cruiseAlt = draft.cruiseAltitudeFt
    val cruiseSpd = draft.cruiseSpeedKt
    val problem = draft.problem()

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("FLIGHT PLAN", style = MaterialTheme.typography.labelSmall)
        Text(activePlanLabel ?: "No plan active", style = MaterialTheme.typography.titleMedium)
        Text(notice, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(value = draft.callsign, onValueChange = { draft = draft.copy(callsign = it) }, label = { Text("Callsign") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = draft.departure, onValueChange = { draft = draft.copy(departure = it.uppercase()) }, label = { Text("Departure") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = draft.destination, onValueChange = { draft = draft.copy(destination = it.uppercase()) }, label = { Text("Destination") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = draft.alternate, onValueChange = { draft = draft.copy(alternate = it.uppercase()) }, label = { Text("Alternate") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = draft.departureRunway, onValueChange = { draft = draft.copy(departureRunway = it.uppercase()) }, label = { Text("Departure runway") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = draft.arrivalRunway, onValueChange = { draft = draft.copy(arrivalRunway = it.uppercase()) }, label = { Text("Arrival runway") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = "%.0f".format(cruiseAlt),
            onValueChange = { t -> t.toDoubleOrNull()?.let { draft = draft.copy(cruiseAltitudeFt = it) } },
            label = { Text("Cruise altitude (ft)") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = "%.0f".format(cruiseSpd),
            onValueChange = { t -> t.toDoubleOrNull()?.let { draft = draft.copy(cruiseSpeedKt = it) } },
            label = { Text("Cruise speed (kt)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        ProcedureSection(draft) { d, n -> draft = d; if (n.isNotEmpty()) notice = n }
        Spacer(Modifier.height(12.dp))

        if (problem != null) Text(problem, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    onActivate(draft)
                    notice = "Plan activated. The aircraft stays parked until TAKEOFF is pressed on the cockpit."
                },
                enabled = problem == null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Activate") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                // Built from the last fix, or the departure airport when the plan has no fixes (TS vectorArrivalFor).
                when (val r = com.oersted.trainer.sim.FlightPlanProcedures.applyVectorArrival(draft)) {
                    is com.oersted.trainer.sim.FlightPlanProcedures.Result.Applied -> { draft = r.draft; notice = r.notice }
                    is com.oersted.trainer.sim.FlightPlanProcedures.Result.Refused -> notice = r.reason
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add vectored ILS arrival (WSSS 02L)") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                draft = FlightPlanDraft()
                notice = "Reset to the default plan (not activated)."
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Reset to default") }
    }
}
