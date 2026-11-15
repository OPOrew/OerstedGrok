package com.oersted.trainer.ui.glareshield

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.oersted.trainer.sim.GlareshieldLogic

/**
 * ALT HOLD switch (AltHold in GlareshieldPanel.tsx): a silver bezel with a red dome (high = on, low = off) and a yellow lamp
 * below it, lit while on and powered. A tap calls [onToggle] only when [GlareshieldLogic.canToggleAltHold] allows it.
 * Not compiled in the sandbox (needs the Android SDK).
 */
@Composable
fun AltHoldSwitch(
    on: Boolean,
    powered: Boolean,
    fdUp: Boolean,
    fdMode: Int,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier.size(56.dp),
) {
    Box(
        modifier = modifier
            .background(
                Brush.radialGradient(listOf(Color(0xFF0B0B0C), Color(0xFF1A1B1D))),
                CircleShape,
            )
            .border(5.dp, Color(0xFFB9BCC0), CircleShape)
            .clickable(enabled = GlareshieldLogic.canToggleAltHold(on, fdUp, fdMode)) { onToggle(!on) },
    ) {
        // red dome: high when on, low when off
        Box(
            modifier = Modifier
                .offset(x = 17.dp, y = if (on) 5.dp else 20.dp)
                .size(22.dp)
                .background(
                    Brush.radialGradient(listOf(Color(0xFFFF6A55), Color(0xFFA3140D))),
                    CircleShape,
                ),
        )
        // yellow lamp below the dome
        Box(
            modifier = Modifier
                .offset(x = 20.dp, y = 32.dp)
                .size(15.dp)
                .background(
                    if (GlareshieldLogic.altHoldLampLit(on, powered)) Brush.radialGradient(listOf(Color(0xFFFFF3A0), Color(0xFFFFD21A)))
                    else Brush.radialGradient(listOf(Color(0xFF9A8420), Color(0xFF4D4210))),
                    CircleShape,
                ),
        )
    }
}

/**
 * AIL / ELEV engage paddle (EngagePaddle in GlareshieldPanel.tsx): a chrome bar that swings up into its oval housing when
 * engaged. The swing comes from [GlareshieldLogic.paddleAngleDeg]; a tap calls [onChange] with the new state.
 */
@Composable
fun EngagePaddle(
    engaged: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier.size(width = 94.dp, height = 28.dp),
) {
    Box(
        modifier = modifier
            .rotate(GlareshieldLogic.paddleAngleDeg(engaged).toFloat())
            .background(
                Brush.verticalGradient(listOf(Color(0xFFF2F3F4), Color(0xFF9A9EA3), Color(0xFFD8DADC))),
                RoundedCornerShape(14.dp),
            )
            .border(1.dp, Color(0xFF4D5156), RoundedCornerShape(14.dp))
            .clickable { onChange(GlareshieldLogic.nextPaddle(engaged)) },
    )
}
