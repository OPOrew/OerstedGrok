package com.oersted.trainer.ui.cockpit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.oersted.trainer.sim.Derived
import com.oersted.trainer.sim.FuelAndGroundRules
import com.oersted.trainer.sim.SimActions
import com.oersted.trainer.sim.HydraulicTrainerRules
import com.oersted.trainer.sim.SimState
import com.oersted.trainer.sim.TrainingConsoleRules

private val ConsoleBg = Color(0xFF171816)

/**
 * Full-screen TRAINING console (routes/cockpit.tsx TrainingConsole). Shell and the sections whose rules are ported:
 * Flight Controls, Landing Gear, Brakes / Antiskid. Other sections show a placeholder until their panels are ported.
 * Switch state comes from [s]; every press goes through [change] with a pure rule from [TrainingConsoleRules].
 * Not compiled in the sandbox; verify on CI.
 */
@Composable
fun TrainingConsoleView(
    s: SimState,
    d: Derived,
    change: ((SimState) -> SimState) -> Unit,
    onClose: () -> Unit,
) {
    var section by remember { mutableStateOf(TrainingConsoleRules.DEFAULT_SECTION) }
    var sectionsCollapsed by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(ConsoleBg).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Separate training interface", color = Color(0xFFB7AB87), style = MaterialTheme.typography.labelSmall)
                Text("Training Console", style = MaterialTheme.typography.titleLarge)
            }
            OutlinedButton(onClick = onClose) { Text("Close") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { sectionsCollapsed = !sectionsCollapsed }) {
            Text(if (sectionsCollapsed) "Show sections" else "Hide sections")
        }
        if (!sectionsCollapsed) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TrainingConsoleRules.SECTIONS.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { name ->
                            OutlinedButton(
                                onClick = { section = name; sectionsCollapsed = true },
                                modifier = Modifier.weight(1f),
                            ) { Text(name, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        } else {
            Text("Section: $section", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            when (section) {
                "Flight Controls" -> FlightControlsSection(s, change)
                "Landing Gear" -> LandingGearSection(s, change)
                "Brakes / Antiskid" -> BrakesSection(s, change)
                "Hydraulics" -> HydraulicsSection(s, d, change)
                "Fuel" -> FuelSection(s, d, change)
                "Engine Start" -> EngineStartSection(s, d, change)
                else -> Text("$section: not ported yet.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SwitchButton(label: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text("$label · ${TrainingConsoleRules.onOff(on)}", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StateButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HydraulicsSection(s: SimState, d: Derived, change: ((SimState) -> SimState) -> Unit) {
    Text("Hydraulics", style = MaterialTheme.typography.titleMedium)
    SwitchButton("ENG 1 PUMP (SYSTEM A)", s.engHyd1) { change { HydraulicTrainerRules.toggle(it, "engHyd1") } }
    SwitchButton("ENG 2 PUMP (SYSTEM A)", s.engHyd2) { change { HydraulicTrainerRules.toggle(it, "engHyd2") } }
    SwitchButton("ELEC 1 PUMP (SYSTEM B)", s.elecHyd1, HydraulicTrainerRules.elecPumpEnabled(d, 1)) {
        change { HydraulicTrainerRules.toggle(it, "elecHyd1") }
    }
    SwitchButton("ELEC 2 PUMP (SYSTEM B)", s.elecHyd2, HydraulicTrainerRules.elecPumpEnabled(d, 2)) {
        change { HydraulicTrainerRules.toggle(it, "elecHyd2") }
    }
    Text("STBY HYD", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        HydraulicTrainerRules.STBY_MODES.forEach { mode ->
            val lit = s.stbyHyd == mode
            OutlinedButton(
                onClick = { change { st -> HydraulicTrainerRules.setStbyHyd(st, mode) } },
                modifier = Modifier.weight(1f),
            ) { Text(if (lit) "[$mode]" else mode, style = MaterialTheme.typography.labelSmall) }
        }
    }
    StateButton("GND INTERCONNECT · ${HydraulicTrainerRules.interconnectState(s.groundInterconnect)}") {
        change { HydraulicTrainerRules.toggle(it, "groundInterconnect") }
    }
    val onGround = HydraulicTrainerRules.groundServicingEnabled(s)
    SwitchButton("GND SERVICING", s.hydServicingConnected, onGround) { change { HydraulicTrainerRules.toggle(it, "hydServicingConnected") } }
    SwitchButton("GND HYD POWER", s.hydGroundPowerConnected, onGround) { change { HydraulicTrainerRules.toggle(it, "hydGroundPowerConnected") } }
    SwitchButton("GND AIR CART", s.groundPneumaticConnected, onGround) { change { HydraulicTrainerRules.toggleGroundAirCart(it) } }
    Text("Hydraulic flow drawing: not ported yet.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun FlightControlsSection(s: SimState, change: ((SimState) -> SimState) -> Unit) {
    Text("Flight Controls", style = MaterialTheme.typography.titleMedium)
    SwitchButton("FLT CTRL A", s.fltCtrlA) { change(TrainingConsoleRules::toggleFltCtrlA) }
    SwitchButton("FLT CTRL B", s.fltCtrlB) { change(TrainingConsoleRules::toggleFltCtrlB) }
    SwitchButton("SPOILER A", s.spoilerA) { change(TrainingConsoleRules::toggleSpoilerA) }
    SwitchButton("SPOILER B", s.spoilerB) { change(TrainingConsoleRules::toggleSpoilerB) }
    Button(onClick = { change(TrainingConsoleRules::centerFlightControls) }, modifier = Modifier.fillMaxWidth()) {
        Text("Center flight controls", style = MaterialTheme.typography.labelSmall)
    }
    Text("Servicing / Test", style = MaterialTheme.typography.labelLarge)
    SwitchButton("Ground-sensing test override", s.groundSenseTest) { change(TrainingConsoleRules::toggleGroundSenseTest) }
    SwitchButton("Airborne test override", s.airborneTest) { change(TrainingConsoleRules::toggleAirborneTest) }
    SwitchButton("Ground hydraulic servicing", s.hydServicingConnected) { change(TrainingConsoleRules::toggleHydServicingConnected) }
    SwitchButton("Ground power cart (GPU)", s.externalPowerConnected) { change(TrainingConsoleRules::toggleExternalPowerConnected) }
}

@Composable
private fun LandingGearSection(s: SimState, change: ((SimState) -> SimState) -> Unit) {
    Text("Landing Gear", style = MaterialTheme.typography.titleMedium)
    SwitchButton("PARKING BRAKE", s.parkingBrake) { change(TrainingConsoleRules::toggleParkingBrake) }
    Text("Servicing / Test", style = MaterialTheme.typography.labelLarge)
    SwitchButton("Ground hydraulic servicing", s.hydServicingConnected) { change(TrainingConsoleRules::toggleHydServicingConnected) }
    SwitchButton("Ground-sensing test override", s.groundSenseTest) { change(TrainingConsoleRules::toggleGroundSenseTest) }
    SwitchButton("Airborne test override", s.airborneTest) { change(TrainingConsoleRules::toggleAirborneTest) }
    SwitchButton("Ground power cart (GPU)", s.externalPowerConnected) { change(TrainingConsoleRules::toggleExternalPowerConnected) }
    Button(onClick = { change(TrainingConsoleRules::centerTillerAndPedals) }, modifier = Modifier.fillMaxWidth()) {
        Text("Center tiller & pedals", style = MaterialTheme.typography.labelSmall)
    }
    Text("Gear wheel and brake dynamics: not ported yet.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun BrakesSection(s: SimState, change: ((SimState) -> SimState) -> Unit) {
    Text("Brakes / Antiskid", style = MaterialTheme.typography.titleMedium)
    SwitchButton("INBD ANTISKID", s.antiskidInbd) { change(TrainingConsoleRules::toggleAntiskidInbd) }
    SwitchButton("OUTBD ANTISKID", s.antiskidOutbd) { change(TrainingConsoleRules::toggleAntiskidOutbd) }
    Text("AUTOBRAKE", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TrainingConsoleRules.AUTOBRAKE_LABELS.forEach { (level, label) ->
            val selected = s.autobrake.toInt() == level
            OutlinedButton(
                onClick = { change { st -> TrainingConsoleRules.setAutobrake(st, level) } },
                modifier = Modifier.weight(1f),
            ) { Text(if (selected) "[$label]" else label, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun FuelSection(s: SimState, d: Derived, change: ((SimState) -> SimState) -> Unit) {
    Text("Fuel", style = MaterialTheme.typography.titleMedium)
    val pct = { v: Double -> if (d.fuelQuantityIndicationPowered) "%.1f%%".format(v) else "NO IND" }
    Text("TANK 1 ${pct(s.fuelTank1)}   CTR ${pct(s.fuelTankCenter)}   TANK 2 ${pct(s.fuelTank2)}", style = MaterialTheme.typography.bodySmall)
    val labels = mapOf(
        "fuelPump1Fwd" to "TANK 1 FWD BOOST", "fuelPump1Aft" to "TANK 1 AFT BOOST",
        "fuelPumpCenter1" to "CTR LEFT BOOST", "fuelPumpCenter2" to "CTR RIGHT BOOST",
        "fuelPump2Fwd" to "TANK 2 FWD BOOST", "fuelPump2Aft" to "TANK 2 AFT BOOST",
    )
    fun pumpOn(k: String): Boolean = when (k) {
        "fuelPump1Fwd" -> s.fuelPump1Fwd
        "fuelPump1Aft" -> s.fuelPump1Aft
        "fuelPumpCenter1" -> s.fuelPumpCenter1
        "fuelPumpCenter2" -> s.fuelPumpCenter2
        "fuelPump2Fwd" -> s.fuelPump2Fwd
        else -> s.fuelPump2Aft
    }
    for (k in FuelAndGroundRules.BOOST_PUMPS) {
        StateButton("${labels[k]} · ${TrainingConsoleRules.onOff(pumpOn(k))}") {
            change { FuelAndGroundRules.toggleBoostPump(it, k) }
        }
    }
    val shutoffOpen = d.fuelShutoff1Open || d.fuelShutoff2Open
    StateButton("CROSSFEED VALVE · ${FuelAndGroundRules.crossfeedLabel(s.fuelCrossfeed, shutoffOpen)}") {
        change { FuelAndGroundRules.toggleCrossfeed(it) }
    }
    StateButton("SET LOW FUEL · 25%") { change { FuelAndGroundRules.setLowFuel(it) } }
    StateButton("RESET QUANTITY · 100%") { change { FuelAndGroundRules.resetQuantity(it) } }
}

@Composable
private fun EngineStartSection(s: SimState, d: Derived, change: ((SimState) -> SimState) -> Unit) {
    Text("Engine Start", style = MaterialTheme.typography.titleMedium)
    val nowMs = System.currentTimeMillis().toDouble()
    val engines = listOf(
        Triple(1, Triple(d.eng1Running, s.autoStart1, s.engine1FirePulled), s.n2_1),
        Triple(2, Triple(d.eng2Running, s.autoStart2, s.engine2FirePulled), s.n2_2),
    )
    for ((side, flags, n2) in engines) {
        val (running, auto, fire) = flags
        val status = when {
            running -> "RUNNING"
            fire -> "FIRE HANDLE PULLED"
            auto -> if (n2 < 20) "MOTORING ON STARTER" else "LIGHT-OFF / ACCELERATING"
            n2 > 1 -> "SPOOLING DOWN"
            else -> "SHUT DOWN"
        }
        Text("Engine $side · $status", style = MaterialTheme.typography.labelLarge)
        StateButton("START ENG $side", FuelAndGroundRules.startEnabled(running, auto, fire)) {
            change { SimActions.startEngine(it, side, nowMs) }
        }
        StateButton(
            "${FuelAndGroundRules.shutdownLabel(running, auto)} ENG $side",
            FuelAndGroundRules.shutdownEnabled(running, auto, n2),
        ) { change { SimActions.shutdownEngine(it, side, nowMs) } }
    }
}

