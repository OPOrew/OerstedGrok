package com.oersted.trainer.ui.instruments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.InstrumentReadouts

/**
 * Panel strips and small readouts from MainInstrumentPanel.tsx: IasStrip, QtyLed, YawIndicator and the annunciator Lamp.
 * Text comes from sim/InstrumentReadouts.kt. Colours and sizes follow the web classes. Not compiled in the sandbox
 * (needs the Android SDK).
 */

private val LegendColour = Color(0xFFD8D4C4)

/** Standby airspeed strip (IAS, "KNOTS"). Hatched when unpowered, the readout shows "060". */
@Composable
fun IasStrip(ias: Double, powered: Boolean, modifier: Modifier = Modifier.fillMaxWidth().height(40.dp)) {
    Row(
        modifier = modifier
            .background(Color(0xFF14171B), RoundedCornerShape(3.dp))
            .border(1.dp, Color(0xFF0B0D10), RoundedCornerShape(3.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Color.Black, RoundedCornerShape(2.dp))
                .border(1.dp, Color(0xFF555A60), RoundedCornerShape(2.dp)),
        ) {
            if (!powered) {
                // hatched warning flag: diagonal stripes, as the web's repeating-linear-gradient
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val step = 8f
                    var x = -size.height
                    while (x < size.width) {
                        drawLine(Color(0xFFFF7A1A), Offset(x, 0f), Offset(x + size.height, size.height), strokeWidth = 4f)
                        drawLine(Color(0xFFF4F1E4), Offset(x + 4f, 0f), Offset(x + 4f + size.height, size.height), strokeWidth = 4f)
                        x += step * 2
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .background(Color.Black, RoundedCornerShape(2.dp))
                .border(1.dp, Color(0xFF555A60), RoundedCornerShape(2.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text(
                text = InstrumentReadouts.iasStripText(ias, powered),
                color = Color(0xFFF2F2F2),
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
            )
        }
        Text(
            text = "IAS\nKNOTS",
            color = LegendColour,
            fontFamily = FontFamily.Monospace,
            fontSize = 7.sp,
            lineHeight = 8.sp,
        )
    }
}

/** Red LED fuel quantity readout (QtyLed). Blank when DC power is off; 888 when the test is held. */
@Composable
fun QtyLed(
    label: String,
    value: Double,
    dcPower: Boolean,
    testing: Boolean,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
) {
    val text = InstrumentReadouts.qtyLedText(dcPower, value, testing)
    Column(
        modifier = Modifier
            .size(width = width, height = height)
            .background(Color(0xFF2A2F37), RoundedCornerShape(14.dp))
            .border(2.dp, Color(0xFF1C1F24), RoundedCornerShape(14.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .background(Color(0xFF2A0707), RoundedCornerShape(8.dp))
                .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp),
        ) {
            Text(
                text = text.ifEmpty { "   " },
                color = Color(0xFFFF4040),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = (height.value * 0.34f).sp,
            )
        }
        Text(text = label, color = LegendColour, fontFamily = FontFamily.Monospace, fontSize = 7.sp, textAlign = TextAlign.Center)
    }
}

/** Yaw damper indicator: the needle deflects left or right while the L / R test is held (YawIndicator). */
@Composable
fun YawIndicator(test: Int, modifier: Modifier = Modifier.size(64.dp)) {
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val s = size.width / 100f
        withTransform({ scale(s, s, Offset.Zero) }) {
            drawCircle(Color(0xFF3A2A22), radius = 48f, center = Offset(50f, 50f))
            drawCircle(Color(0xFF0B0C0E), radius = 40f, center = Offset(50f, 50f))
            drawCircle(Color(0xFF2A2D31), radius = 40f, center = Offset(50f, 50f), style = Stroke(width = 1f))
            withTransform({ rotate(InstrumentReadouts.yawNeedleDeg(test).toFloat(), Offset(50f, 50f)) }) {
                drawRect(Color(0xFFF4F1E4), topLeft = Offset(46f, 18f), size = androidx.compose.ui.geometry.Size(8f, 30f))
                drawRect(Color(0xFFF4F1E4), topLeft = Offset(46f, 54f), size = androidx.compose.ui.geometry.Size(8f, 8f))
            }
        }
    }
}

/**
 * Annunciator lamp (Lamp in MainInstrumentPanel.tsx). Lit when [on]: red or amber body, otherwise a dark lens with a
 * coloured rim. Pressing calls [onClick] when given.
 */
@Composable
fun PanelLamp(
    label: String,
    on: Boolean,
    red: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier.height(34.dp),
) {
    val body = when {
        on && red -> Color(0xFFE03A3A)
        on -> Color(0xFFE9A93A)
        else -> Color(0xFF2B2F35)
    }
    val rim = if (on && red) Color(0xFFFF9A9A) else if (on) Color(0xFFFFD27A) else Color(0xFF4A4F56)
    val textColour = if (on && red) Color.White else if (on) Color(0xFF2A1B00) else Color(0xFFBDB8A6)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(body, RoundedCornerShape(3.dp))
            .border(1.dp, rim, RoundedCornerShape(3.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColour,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            textAlign = TextAlign.Center,
        )
    }
}
