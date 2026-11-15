package com.oersted.trainer.ui.cockpit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.ApproachProgressLamps
import com.oersted.trainer.sim.AutoFlight.Light

/**
 * Approach progress display (ApproachProgress.tsx): VOR/LOC and GLIDE SLOPE lamps. Press and hold to test (all lamps green).
 * Colours are the web values. Not compiled in the sandbox (needs the Android SDK); check against the web panel once CI runs.
 */
@Composable
fun ApproachProgressView(vocLight: Light, gsLight: Light, powered: Boolean, modifier: Modifier = Modifier) {
    var test by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0D10))
            .padding(2.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    test = true
                    waitForUpOrCancellation()
                    test = false
                }
            },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Lamp("VOR/LOC", ApproachProgressLamps.state(powered, test, vocLight), Modifier.weight(1f).fillMaxWidth())
        Lamp("GLIDE SLOPE", ApproachProgressLamps.state(powered, test, gsLight), Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun Lamp(label: String, state: Light, modifier: Modifier) {
    val (bg, fg, edge) = when (state) {
        Light.GREEN -> Triple(Color(0xFF27B84A), Color(0xFF031A08), Color(0xFF9DFFAE))
        Light.AMBER -> Triple(Color(0xFFE9A93A), Color(0xFF2A1B00), Color(0xFFFFD27A))
        Light.OFF -> Triple(Color(0xFF15181C), Color(0xFF4A4F57), Color(0xFF2A2F36))
    }
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier = modifier.border(1.dp, edge, shape).background(bg, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 7.sp,
        )
    }
}
