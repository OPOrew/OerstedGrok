package com.oersted.trainer.ui.pedestal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oersted.trainer.ui.cockpit.CockpitModel

/**
 * The centre pedestal stage of components/panel/CenterPedestal.tsx: a 700 x 1642 design-px canvas (the web's STAGE_W x
 * STAGE_H) holding every unit at the photograph's coordinates, scaled to [width] as a whole, like GlareshieldPanel.
 *
 * The whole stage is ported: TOP DECK, CONTROL STAND, FIRE / OVERHEAT panel, radio stack, LOWER PLATE and AILERON TRIM drum.
 * Compose only; verified on CI.
 */
const val PEDESTAL_VISIBLE_H = PEDESTAL_STAGE_H

@Composable
fun CenterPedestal(cockpit: CockpitModel, width: Dp) {
    val k = width.value / PEDESTAL_STAGE_W
    val hDp = (PEDESTAL_VISIBLE_H * k).dp
    Box(
        Modifier
            .width(width)
            .height(hDp)
            .clipToBounds()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A1D21), Color(0xFF111316)))),
    ) {
        Box(
            Modifier
                // larger than the parent: top-left aligned and unbounded, then scaled about its own top-left corner
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(PEDESTAL_STAGE_W.dp, PEDESTAL_VISIBLE_H.dp)
                .graphicsLayer {
                    scaleX = k
                    scaleY = k
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
            PedestalTopDeck(cockpit)
            // pedestal case: lower body (web `<Box x={240} y={755} w={600} h={821}>`)
            val body = RoundedCornerShape(10.dp)
            Box(
                Modifier.stageAt(240, 755, 600, 821)
                    .shadow(6.dp, body)
                    .background(Brush.verticalGradient(listOf(Color(0xFF252A30), Color(0xFF15181C))), body)
                    .border(3.dp, Color(0xFF0B0C0E), body),
            )
            PedestalControlStand(cockpit)
            PedestalFirePanel(cockpit)
            PedestalRadios(cockpit)
            PedestalAileronDrum(cockpit)
            PedestalLowerPlate(cockpit)
        }
    }
}
