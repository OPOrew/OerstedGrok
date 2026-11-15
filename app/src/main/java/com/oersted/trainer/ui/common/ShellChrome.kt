package com.oersted.trainer.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.oersted.trainer.sim.Derived
import com.oersted.trainer.sim.SimState

/** Colors from the web styles.css tokens (@theme for dark, html.light-mode for light). */
object Classic737Palette {
    val Bg = Color(0xFF0A0C0E)
    val Surface = Color(0xFF12151A)
    val Panel = Color(0xFF181C24)
    val Raised = Color(0xFF222833)
    val Inset = Color(0xFF0E1116)
    val Fg = Color(0xFFE7E2D6)
    val Muted = Color(0xFF8D929C)
    val Subtle = Color(0xFF5C616C)
    val Border = Color(0xFF2C323D)
    val Accent = Color(0xFF8BA4B8)
    val AccentFg = Color(0xFF0A0C0E)
    val Ok = Color(0xFF3DD68C)
    val Caution = Color(0xFFE0A84A)
    val Warn = Color(0xFFE24B4B)
    val Pneumatic = Color(0xFFD4A574)
    val HydA = Color(0xFF5B8FD4)
    val HydB = Color(0xFF3DD68C)
    val Elec = Color(0xFF7EC8E3)
}

/** Light-mode values from html.light-mode in styles.css. */
private object Classic737LightPalette {
    val Bg = Color(0xFFF3F5F7)
    val Surface = Color(0xFFFFFFFF)
    val Panel = Color(0xFFE8EDF1)
    val Raised = Color(0xFFDCE3E8)
    val Fg = Color(0xFF18212A)
    val Muted = Color(0xFF56616C)
    val Border = Color(0xFFB9C3CC)
    val Accent = Color(0xFF496B86)
    val AccentFg = Color(0xFFFFFFFF)
}

fun classic737Scheme(light: Boolean): ColorScheme =
    if (light) {
        lightColorScheme(
            background = Classic737LightPalette.Bg,
            surface = Classic737LightPalette.Surface,
            surfaceVariant = Classic737LightPalette.Panel,
            surfaceContainerHighest = Classic737LightPalette.Raised,
            onBackground = Classic737LightPalette.Fg,
            onSurface = Classic737LightPalette.Fg,
            onSurfaceVariant = Classic737LightPalette.Muted,
            primary = Classic737LightPalette.Accent,
            onPrimary = Classic737LightPalette.AccentFg,
            outline = Classic737LightPalette.Border,
        )
    } else {
        darkColorScheme(
            background = Classic737Palette.Bg,
            surface = Classic737Palette.Surface,
            surfaceVariant = Classic737Palette.Panel,
            surfaceContainerHighest = Classic737Palette.Raised,
            onBackground = Classic737Palette.Fg,
            onSurface = Classic737Palette.Fg,
            onSurfaceVariant = Classic737Palette.Muted,
            primary = Classic737Palette.Accent,
            onPrimary = Classic737Palette.AccentFg,
            outline = Classic737Palette.Border,
        )
    }

/** Web StatusStrip: caption lights, tapes, and the time / APU / cold-and-dark buttons. */
@Composable
fun StatusStrip(
    s: SimState,
    d: Derived,
    onTimeScale: () -> Unit,
    onReadyApu: () -> Unit,
    onColdAndDark: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CaptionLight("MASTER CAUTION", d.masterCaution, Classic737Palette.Caution)
            CaptionLight("FIRE", d.fireWarn, Classic737Palette.Warn)
            CaptionLight(if (s.wow) "GND" else "AIR", s.battery, Classic737Palette.Elec)
            Tape("APU", "${"%.0f".format(s.apuRpm)} %", Classic737Palette.Ok)
            Tape("N2-1", "${"%.0f".format(s.n2_1)} %", Classic737Palette.Caution)
            Tape("N2-2", "${"%.0f".format(s.n2_2)} %", Classic737Palette.Caution)
            Tape("DUCT", "${"%.0f".format(d.ductPsi)} psi", Classic737Palette.Pneumatic)
            Tape("HYD A", "${"%.0f".format(s.hydAPress)} psi", Classic737Palette.HydA)
            Tape("HYD B", "${"%.0f".format(s.hydBPress)} psi", Classic737Palette.HydB)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onTimeScale) { Text("${s.timeScale.toInt()}× time") }
            OutlinedButton(onClick = onReadyApu) { Text("Set APU running") }
            OutlinedButton(onClick = onColdAndDark) { Text("Cold & dark") }
        }
    }
}

@Composable
fun CaptionLight(label: String, on: Boolean, tone: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(if (on) tone else Classic737Palette.Subtle, CircleShape),
        )
        Text(
            label,
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (on) tone else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun Tape(label: String, value: String, tone: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, color = tone)
    }
}
