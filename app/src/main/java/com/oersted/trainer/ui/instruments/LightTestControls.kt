package com.oersted.trainer.ui.instruments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.oersted.trainer.sim.LightTestLogic
import com.oersted.trainer.ui.pedestal.PedestalLegend

/**
 * LightTestKey (MainInstrumentPanel.tsx): a dark knob whose pointer shows BRT / DIM / TEST; a tap cycles the position.
 * Rules come from sim/LightTestLogic.kt. Not compiled in the sandbox (needs the Android SDK).
 */
@Composable
fun LightTestKey(lightKey: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier.size(width = 96.dp, height = 40.dp)) {
    Box(
        modifier = modifier
            .background(Color(0xFFCFCBBE), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF8A8D90), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.CenterStart,
    ) {
        // engraved legends: the lit position is printed black, as the web's text-black
        val ink = Color(0xFF33362F)
        PedestalLegend("LIGHT TEST", Modifier.align(Alignment.TopCenter).padding(top = 3.dp), 8, if (lightKey == 2) Color.Black else ink)
        PedestalLegend("BRT", Modifier.align(Alignment.CenterEnd).padding(end = 4.dp), 8, if (lightKey == 0) Color.Black else ink)
        PedestalLegend("DIM", Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp), 8, if (lightKey == 1) Color.Black else ink)
        Box(
            modifier = Modifier
                .padding(start = 24.dp)
                .size(34.dp)
                .background(Color(0xFF0B0C0D), CircleShape)
                .border(2.dp, Color(0xFF6A6D70), CircleShape)
                .clickable { onChange(LightTestLogic.nextLightKey(lightKey)) },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                withTransform({ rotate(LightTestLogic.keyAngleDeg(lightKey).toFloat(), Offset(size.width / 2, size.height / 2)) }) {
                    drawRect(
                        Color(0xFFE2E4E6),
                        topLeft = Offset(size.width / 2 - 2f, size.height / 2 - 17f),
                        size = Size(4f, 17f),
                    )
                }
            }
        }
    }
}
