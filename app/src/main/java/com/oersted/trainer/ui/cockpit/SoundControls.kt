package com.oersted.trainer.ui.cockpit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.platform.AudioEngine

/**
 * Port of the SOUND ON / SOUND OFF button and volume slider of routes/cockpit.tsx (range 0..1, step 0.05, default 0.6).
 * Sound starts on (Android has no autoplay block) and the button turns it off and on. Compose only; verified on CI, not compiled locally.
 */
@Composable
fun SoundControls(audio: AudioEngine, modifier: Modifier = Modifier) {
    val on by audio.on.collectAsState()
    val vol by audio.volume.collectAsState()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val label: @Composable () -> Unit = {
            Text(if (on) "SOUND ON" else "SOUND OFF", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        if (on) Button(onClick = { audio.toggle() }) { label() } else OutlinedButton(onClick = { audio.toggle() }) { label() }
        Slider(value = vol, onValueChange = { audio.setVolume(it) }, valueRange = 0f..1f, steps = 19, modifier = Modifier.width(140.dp))
    }
}
