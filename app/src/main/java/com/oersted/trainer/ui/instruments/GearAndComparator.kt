package com.oersted.trainer.ui.instruments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Gear lamp pairs and the instrument comparator plate from MainInstrumentPanel.tsx (GearPairMini, GearColumn, Comparator).
 * Lamp state comes from the caller; the test rules are in sim/LightTestLogic.kt. Not compiled in the sandbox (needs the
 * Android SDK).
 */

/** Two lamps stacked: red (unsafe) above green (down and locked). Each is lit by its own flag or by the lamp test. */
@Composable
fun GearPair(text: String, red: Boolean, green: Boolean, modifier: Modifier = Modifier.width(64.dp)) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        PanelLamp(label = text, on = red, red = true, modifier = Modifier.height(26.dp))
        PanelLamp(label = text, on = green, red = false, modifier = Modifier.height(26.dp))
    }
}

/**
 * Gear lamps: nose, left and right main. [test] forces every lamp on (LightTestLogic.gearTest). The lever itself is drawn
 * by the caller; this view is the lamp group.
 */
@Composable
fun GearLampGroup(
    test: Boolean,
    noseRed: Boolean, noseGreen: Boolean,
    leftRed: Boolean, leftGreen: Boolean,
    rightRed: Boolean, rightGreen: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Column(
        modifier = modifier.height(136.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GearPair("NOSE GEAR", red = test || noseRed, green = test || noseGreen)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            GearPair("LEFT GEAR", red = test || leftRed, green = test || leftGreen)
            GearPair("RIGHT GEAR", red = test || rightRed, green = test || rightGreen)
        }
    }
}

/**
 * Instrument comparator plate: seven lamps (MON PWR, HDG, PITCH, ROLL, GS, LOC, ALT) that light together, and a
 * "PUSH TO DIM" caption. [lit] comes from LightTestLogic.comparatorLit.
 */
@Composable
fun ComparatorPlate(lit: Boolean, modifier: Modifier = Modifier.fillMaxWidth().height(64.dp)) {
    val labels = listOf("MON\nPWR", "HDG", "PITCH", "ROLL", "GS", "LOC", "ALT")
    Column(
        modifier = modifier
            .background(Color(0xFFBDB9AA), RoundedCornerShape(3.dp))
            .border(1.dp, Color(0xFF1B2028), RoundedCornerShape(3.dp))
            .padding(3.dp),
    ) {
        Text(
            text = "INSTRUMENT COMPARATOR",
            color = Color(0xFF3A3A3A),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 3.dp)
                .background(Color(0xFF111111), RoundedCornerShape(2.dp))
                .padding(1.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            labels.forEach { label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(if (lit) Color(0xFFE9A93A) else Color(0xFF2B2F35), RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (lit) Color(0xFF2A1B00) else Color(0xFFBDB8A6),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 7.sp,
                        lineHeight = 8.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Text(
            text = "PUSH TO DIM",
            color = Color(0xFF3A3A3A),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 6.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 1.dp),
        )
    }
}
