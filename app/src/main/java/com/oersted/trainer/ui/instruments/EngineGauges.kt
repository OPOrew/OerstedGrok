package com.oersted.trainer.ui.instruments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.EngineGaugeRules

/** Colour for a band tone name from sim/EngineGaugeRules.kt. */
private fun toneColour(tone: String): Color = when (tone) {
    "green" -> Color(0xFF3FBF5A)
    "amber" -> Color(0xFFE9A93A)
    "red" -> Color(0xFFE03A3A)
    "blue" -> Color(0xFF4A90D9)
    else -> Color.Gray
}

/**
 * Linear band gauge for one engine reading (EnginePanels in MainInstrumentPanel.tsx): the bands drawn as coloured
 * segments along the bar, a marker at [value], and the numeric readout. Bands and limits come from EngineGaugeRules.
 * Not compiled in the sandbox (needs the Android SDK).
 */
@Composable
fun EngineBandGauge(
    label: String,
    value: Double,
    min: Double,
    max: Double,
    bands: List<EngineGaugeRules.Band>,
    readout: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Column(modifier = modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = Color(0xFFE8E4D8),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
        )
        Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
            val span = (max - min).coerceAtLeast(1e-9)
            fun x(v: Double): Float = (((v.coerceIn(min, max)) - min) / span * size.width).toFloat()
            drawRoundRect(Color(0xFF0B0C0E), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f))
            for (b in bands) {
                val x0 = x(b.from)
                val x1 = x(b.to)
                drawRect(toneColour(b.tone), topLeft = Offset(x0, 2f), size = Size((x1 - x0).coerceAtLeast(0f), size.height - 4f))
            }
            val mx = x(value)
            drawRect(Color.White, topLeft = Offset(mx - 1.5f, 0f), size = Size(3f, size.height))
        }
        Text(
            text = readout,
            color = Color(0xFFF2F2F2),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
    }
}

/** A readout-only box, for oil quantity and fuel flow (values come from EngineGaugeRules). */
@Composable
fun EngineReadoutBox(label: String, readout: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    Box(
        modifier = modifier
            .background(Color(0xFF0B0C0E), RoundedCornerShape(3.dp))
            .border(1.dp, Color(0xFF555A60), RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Column {
            Text(text = label, color = Color(0xFFBDB8A6), fontFamily = FontFamily.Monospace, fontSize = 8.sp)
            Text(text = readout, color = Color(0xFFF2F2F2), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}
