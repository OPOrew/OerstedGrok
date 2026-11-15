package com.oersted.trainer.ui.glareshield

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.GlareshieldLogic
import com.oersted.trainer.sim.KnobPick

/**
 * One flight director controller (FdController in GlareshieldPanel.tsx): MODE SEL knob, ON / OFF / ALT HOLD switch and
 * PITCH CMD knob on a dark plate. Coordinates are the web's, relative to the plate (plate origin 348, 212; 314 x 276).
 * The caller supplies the state and the setters; the rules are in sim/GlareshieldLogic.kt and sim/KnobPick.kt.
 * Not compiled in the sandbox (needs the Android SDK).
 */
@Composable
fun FdController(
    who: String,
    mode: Int,
    altHold: Boolean,
    pitch: Double,
    powered: Boolean,
    fdUp: Boolean,
    onMode: (Int) -> Unit,
    onAltHold: (Boolean) -> Unit,
    onPitch: (Double) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 314.dp, height = 276.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF1A1D21), Color(0xFF0E1013))), RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF2B2E33), RoundedCornerShape(4.dp)),
    ) {
        // MODE SEL knob: centre (92, 163) in plate coordinates
        DetentKnob(
            value = mode,
            markAngles = KnobPick.FD_MODE_ANGLES,
            labels = listOf("GA", "OFF", "HDG", "VOR\nLOC", "AUTO\nAPP", "MAN\nGS"),
            onChange = onMode,
            modifier = Modifier.offset(x = 48.dp, y = 119.dp).size(88.dp),
        )
        Text(
            text = "MODE SEL",
            color = Color(0xFFE8E4D8),
            fontFamily = FontFamily.SansSerif,
            fontSize = 11.sp,
            modifier = Modifier.offset(x = 110.dp, y = 245.dp),
        )
        Text(
            text = "ON",
            color = Color(0xFFE8E4D8),
            fontFamily = FontFamily.SansSerif,
            fontSize = 11.sp,
            modifier = Modifier.offset(x = 176.dp, y = 65.dp),
        )
        AltHoldSwitch(
            on = altHold,
            powered = powered,
            fdUp = fdUp,
            fdMode = mode,
            onToggle = onAltHold,
            modifier = Modifier.offset(x = 154.dp, y = 82.dp).size(56.dp),
        )
        Text(
            text = "OFF",
            color = Color(0xFFE8E4D8),
            fontFamily = FontFamily.SansSerif,
            fontSize = 11.sp,
            modifier = Modifier.offset(x = 176.dp, y = 158.dp),
        )
        Text(
            text = "ALT HOLD",
            color = Color(0xFFE8E4D8),
            fontFamily = FontFamily.SansSerif,
            fontSize = 11.sp,
            modifier = Modifier.offset(x = 170.dp, y = 184.dp),
        )
        // PITCH CMD knob: centre (262, 158), yellow pointer
        DetentKnob(
            value = GlareshieldLogic.pitchKnobValue(pitch).toInt(),
            markAngles = KnobPick.PITCH_CMD_ANGLES,
            labels = emptyList(),
            onChange = { onPitch(GlareshieldLogic.pitchFromKnob(it.toDouble())) },
            modifier = Modifier.offset(x = 216.dp, y = 112.dp).size(92.dp),
            yellowPointer = true,
        )
        Text(
            text = "PITCH CMD",
            color = Color(0xFFE8E4D8),
            fontFamily = FontFamily.SansSerif,
            fontSize = 10.sp,
            modifier = Modifier.offset(x = 192.dp, y = 245.dp),
        )
    }
}
