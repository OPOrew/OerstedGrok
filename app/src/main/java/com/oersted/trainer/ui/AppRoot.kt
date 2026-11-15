package com.oersted.trainer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.oersted.trainer.data.Part
import com.oersted.trainer.data.Repository
import com.oersted.trainer.data.SystemDef
import com.oersted.trainer.platform.PlanStore
import com.oersted.trainer.platform.AudioEngine
import com.oersted.trainer.ui.cockpit.CockpitModel
import com.oersted.trainer.ui.common.StatusStrip
import com.oersted.trainer.ui.procedures.ProceduresScreen
import com.oersted.trainer.ui.systems.SystemScreen
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import com.oersted.trainer.ui.flightplan.FlightPlanScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.oersted.trainer.ui.cockpit.CockpitScreen
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// Same order and labels as the web AppShell NAV (Hangar, Flight deck, Flight plan, Procedures, Library).
private enum class Tab(val label: String) {
    HANGAR("Hangar"),
    FLIGHT_DECK("Flight deck"),
    FLIGHT_PLAN("Flight plan"),
    PROCEDURES("Procedures"),
    LIBRARY("Library"),
}

@Composable
fun AppRoot(lightMode: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repository(context) }
    var tabIndex by rememberSaveable { androidx.compose.runtime.mutableStateOf(0) }
    // Which system the Hangar has opened (web: /systems/$id). Null shows the list.
    var openSystemId by rememberSaveable { mutableStateOf<String?>(null) }
    val tab = Tab.entries[tabIndex]
    // The sim and the phase controller run for the whole app, like the web's SimTicker, on the main thread.
    val cockpit = remember { CockpitModel(CoroutineScope(SupervisorJob() + Dispatchers.Main)) }
    val planStore = remember { PlanStore(context) }
    val cockpitPlanLabel by cockpit.activePlanLabel.collectAsState()
    val sim by cockpit.simState.collectAsState()
    val derived by cockpit.derived.collectAsState()
    DisposableEffect(cockpit) {
        cockpit.start()
        onDispose { cockpit.stop() }
    }
    // Sound (audio.ts): on while the app is in front unless SOUND was turned off. Reads the live sim through audioFeed and clicks on switch changes.
    val audio = remember { AudioEngine(context) }
    DisposableEffect(audio, cockpit) {
        cockpit.onSwitchClick = { audio.switchClick() }
        audio.start { cockpit.audioFeed() }
        val owner = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<androidx.lifecycle.LifecycleOwner>().firstOrNull()
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> audio.onForeground()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> audio.onBackground()
                else -> {}
            }
        }
        if (owner != null) owner.lifecycle.addObserver(observer) else audio.onForeground()
        onDispose {
            owner?.lifecycle?.removeObserver(observer)
            cockpit.onSwitchClick = null
            audio.release()
        }
    }

    Scaffold(
        topBar = {
            // Web AppShell header: title, "model · book Rev revision", theme toggle; StatusStrip below it.
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Classic 737 Line Lab", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${repo.amm.meta.model} · ${repo.amm.meta.book} Rev ${repo.amm.meta.revision}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    OutlinedButton(onClick = onToggleTheme) {
                        Text(if (lightMode) "Dark" else "Light")
                    }
                }
                StatusStrip(
                    s = sim,
                    d = derived,
                    onTimeScale = { cockpit.cycleTimeScale() },
                    onReadyApu = { cockpit.readyApu() },
                    onColdAndDark = { cockpit.coldAndDark() },
                )
            }
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = i == tabIndex,
                        onClick = { tabIndex = i },
                        icon = { Text(t.label.take(1)) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.HANGAR -> {
                    val open = openSystemId?.let { id -> repo.systems.firstOrNull { it.id == id } }
                    if (open != null) {
                        SystemScreen(
                            system = open,
                            systems = repo.systems,
                            allParts = repo.parts,
                            cockpit = cockpit,
                            onOpenSystem = { openSystemId = it },
                            onBack = { openSystemId = null },
                        )
                    } else {
                        SystemsScreen(repo.systems) { openSystemId = it }
                    }
                }
                Tab.FLIGHT_DECK -> CockpitScreen(cockpit, audio)
                Tab.FLIGHT_PLAN -> FlightPlanScreen(planStore, cockpitPlanLabel) { draft -> cockpit.activate(draft) }
                Tab.PROCEDURES -> ProceduresScreen(cockpit)
                Tab.LIBRARY -> LibraryScreen(repo.parts, repo.systems)
            }
        }
    }
}

@Composable
private fun Header(eyebrow: String, title: String) {
    Column(Modifier.padding(16.dp)) {
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall)
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun SystemsScreen(systems: List<SystemDef>, onOpen: (String) -> Unit) {
    Header("Airframe layout · 737-200", "Systems")
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(systems, key = { it.id }) { s ->
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onOpen(s.id) }) {
                Column(Modifier.padding(12.dp)) {
                    Text("ATA ${s.ata} · ${s.short}", style = MaterialTheme.typography.labelMedium)
                    Text(s.title, style = MaterialTheme.typography.titleMedium)
                    Text(s.blurb, style = MaterialTheme.typography.bodyMedium)
                    Text("Power: ${s.power} · Panel: ${s.panel}", style = MaterialTheme.typography.bodySmall)
                    // Web card links to /systems/{id}; this card opens the SystemScreen port.
                    Text(
                        "OPEN LAYOUT →",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(parts: List<Part>, systems: List<SystemDef>) {
    // Same filter as web routes/library.tsx: system chip + free-text match over name, function, ATA, group and controls.
    var query by rememberSaveable { mutableStateOf("") }
    var sysId by rememberSaveable { mutableStateOf("all") }
    val needle = query.trim().lowercase()
    val filtered = parts.filter { p ->
        (sysId == "all" || p.system == sysId) &&
            (needle.isEmpty() || "${p.name} ${p.function} ${p.ata} ${p.group} ${p.controls}".lowercase().contains(needle))
    }
    Header("Component library", "${filtered.size} parts")
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text("Search valves, actuators, lights…") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(selected = sysId == "all", onClick = { sysId = "all" }, label = { Text("All") })
        }
        items(systems, key = { it.id }) { s ->
            FilterChip(selected = sysId == s.id, onClick = { sysId = s.id }, label = { Text(s.short) })
        }
    }
    if (filtered.isEmpty()) {
        Text("No components match.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(filtered, key = { it.id }) { p ->
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("ATA ${p.ata} · ${p.group}", style = MaterialTheme.typography.labelMedium)
                    Text(p.name, style = MaterialTheme.typography.titleSmall)
                    Text(p.function, style = MaterialTheme.typography.bodyMedium)
                    Text("Flow: ${p.flow}", style = MaterialTheme.typography.bodySmall)
                    Text("Controls: ${p.controls}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun AmmScreen(repo: Repository) {
    val amm = repo.amm
    Header(amm.meta.source, amm.meta.book + " Rev " + amm.meta.revision)
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(amm.chapters, key = { it.ata }) { c ->
            Text(
                "ATA ${c.ata}  ${c.title}" + if (c.trainer) "  ●" else "",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}

