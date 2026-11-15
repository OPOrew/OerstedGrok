package com.oersted.trainer.ui.instruments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.GearLeverLogic

/**
 * Landing gear lever (GearSelectorLever in MainInstrumentPanel.tsx): a slot with UP / OFF / GEAR DN legends and a brass
 * knob at the detent. A tap moves to the next detent; UP on the ground is refused by [onRequest] (see GearLeverLogic).
 * The lock-out tag is drawn under the lever when [wow] is true. Not compiled in the sandbox (needs the Android SDK).
 */
@Composable
fun GearLever(
    gear: String,
    wow: Boolean,
    gearLeverOverride: Boolean,
    onRequest: (GearLeverLogic.Selection) -> Unit,
    modifier: Modifier = Modifier.width(136.dp).height(304.dp),
) {
    val legends = mapOf("UP" to "UP", "OFF" to "OFF", "DN" to "GEAR DN")
    Box(
        modifier = modifier
            .background(Color(0xFF6A6D71), RoundedCornerShape(2.dp))
            .border(1.dp, Color(0xFF2C2F33), RoundedCornerShape(2.dp)),
    ) {
        // the slot: a light-grey channel with a black guide inside
        Box(
            modifier = Modifier
                .padding(top = 8.dp, bottom = 12.dp, start = 45.dp)
                .size(width = 46.dp, height = 270.dp)
                .background(Color(0xFFC6C8CB), RoundedCornerShape(23.dp, 23.dp, 8.dp, 8.dp))
                .border(1.dp, Color(0xFF4B4E52), RoundedCornerShape(23.dp, 23.dp, 8.dp, 8.dp)),
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 19.dp, top = 26.dp, bottom = 30.dp)
                    .size(width = 8.dp, height = 214.dp)
                    .background(Color(0xFF0B0D0F), CircleShape),
            )
        }
        // detent legends down the left edge
        GearLeverLogic.POSITIONS.forEachIndexed { i, p ->
            val selected = gear == p
            Text(
                text = legends.getValue(p),
                color = if (selected) Color(0xFFF3EFE2) else Color(0xFFD2CDBD),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .offset(y = (GearLeverLogic.SLOT_TOP_PERCENT[i] * 2.7f - 8f).dp),
            )
        }
        // the knob: brass wheel with a short black handle, at the current detent
        Column(
            modifier = Modifier
                .padding(start = 45.dp - 2.dp)
                .offset(y = (GearLeverLogic.SLOT_TOP_PERCENT[GearLeverLogic.index(gear)] * 2.7f - 30f).dp)
                .clickable {
                    val next = GearLeverLogic.nextFrom(gear)
                    onRequest(GearLeverLogic.select(next, wow, gearLeverOverride))
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 52.dp)
                    .background(Color(0xFFD8C76A), RoundedCornerShape(48.dp))
                    .border(1.dp, Color(0xFF6B5A1B), RoundedCornerShape(48.dp)),
            )
            Box(
                modifier = Modifier
                    .size(width = 11.dp, height = 34.dp)
                    .background(Color(0xFF15171A), RoundedCornerShape(4.dp)),
            )
        }
        if (GearLeverLogic.showLockOutTag(wow)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 0.dp)
                    .offset(y = 290.dp)
                    .background(Color(0xFF1B1F24), RoundedCornerShape(2.dp))
                    .padding(horizontal = 4.dp)
                    .height(14.dp),
            ) {
                Text(
                    text = "LEVER LOCKS OUT OF UP ON GROUND",
                    color = Color(0xFFF1F1EA),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 6.sp,
                )
            }
        }
    }
}
