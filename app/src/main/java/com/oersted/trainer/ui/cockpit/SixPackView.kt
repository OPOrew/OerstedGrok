package com.oersted.trainer.ui.cockpit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.oersted.trainer.sim.SixPackData

/**
 * Primary flight instruments: an attitude ball plus the readouts from [SixPackData] (the data pass is in sim/SixPackData.kt).
 * The roll sign (horizon rotates opposite to bank) and the 5 px per degree pitch scale are a first pass; check them against the web panel.
 * Not compiled in the sandbox (needs the Android SDK).
 */
@Composable
fun SixPackView(d: SixPackData) {
    Column(Modifier.fillMaxWidth()) {
        AttitudeBall(roll = d.roll, pitch = d.pitch)
        Spacer(Modifier.height(8.dp))
        Text("HDG ${"%.0f".format(d.heading)}   YAW ${"%.1f".format(d.yawRate)} °/s", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("CAPT", style = MaterialTheme.typography.labelSmall)
                Text(if (d.captPowered) "IAS ${"%.0f".format(d.captIasKt)}" else "IAS --", style = MaterialTheme.typography.bodySmall)
                Text(if (d.captPowered) "ALT ${"%.0f".format(d.captAltFt)}" else "ALT --", style = MaterialTheme.typography.bodySmall)
                Text(if (d.captPowered) "VSI ${"%.0f".format(d.captVsiFpm)}" else "VSI --", style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.weight(1f)) {
                Text("F/O", style = MaterialTheme.typography.labelSmall)
                Text(if (d.foPowered) "IAS ${"%.0f".format(d.foIasKt)}" else "IAS --", style = MaterialTheme.typography.bodySmall)
                Text(if (d.foPowered) "ALT ${"%.0f".format(d.foAltFt)}" else "ALT --", style = MaterialTheme.typography.bodySmall)
                Text(if (d.foPowered) "VSI ${"%.0f".format(d.foVsiFpm)}" else "VSI --", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AttitudeBall(roll: Double, pitch: Double) {
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        val w = size.width
        val h = size.height
        val c = Offset(w / 2f, h / 2f)
        val horizonY = c.y + pitch.toFloat() * (h / 60f)
        rotate(degrees = -roll.toFloat(), pivot = c) {
            // sky above the horizon, ground below (drawn larger than the window so the rotation never shows an edge)
            drawRect(Color(0xFF3B7DD8), topLeft = Offset(-w, -2 * h), size = Size(3 * w, horizonY + 2 * h))
            drawRect(Color(0xFF8B5A2B), topLeft = Offset(-w, horizonY), size = Size(3 * w, 3 * h))
        }
        // fixed aircraft symbol
        drawLine(Color.Yellow, Offset(c.x - 40f, c.y), Offset(c.x - 12f, c.y), strokeWidth = 4f)
        drawLine(Color.Yellow, Offset(c.x + 12f, c.y), Offset(c.x + 40f, c.y), strokeWidth = 4f)
        drawCircle(Color.Yellow, radius = 4f, center = c)
    }
}
