package com.oersted.trainer.ui.glareshield

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Lens tone, as the web's Lens: red (fire / warning), amber (caution) or grey (windshear and similar). */
enum class LensTone { AMBER, RED, GREY }

/**
 * Backlit annunciator lens (Lens in GlareshieldPanel.tsx). Lit: a glowing body in the tone colour; unlit: a dim body.
 * [warn] makes a grey lens red when lit (W/S WARNING). [onClick] is optional; the lens is a button when given.
 * The lit or unlit state comes from sim/GlareshieldAnnunciators.kt via the caller. Not compiled in the sandbox.
 */
@Composable
fun Lens(
    label: String,
    tone: LensTone,
    on: Boolean,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    sub: String? = null,
    warn: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val body = when (tone) {
        LensTone.RED -> if (on) Brush.radialGradient(listOf(Color(0xFFFF5D6A), Color(0xFFD4182B)))
            else Brush.verticalGradient(listOf(Color(0xFFA9172D), Color(0xFF86101F)))
        LensTone.GREY -> when {
            on && warn -> Brush.radialGradient(listOf(Color(0xFFFF5A4A), Color(0xFFC4180F)))
            on -> Brush.radialGradient(listOf(Color(0xFFFFB53A), Color(0xFFF08A10)))
            else -> Brush.verticalGradient(listOf(Color(0xFF6E6A60), Color(0xFF55514A)))
        }
        LensTone.AMBER -> if (on) Brush.radialGradient(listOf(Color(0xFFFFB94A), Color(0xFFF58A0C)))
            else Brush.verticalGradient(listOf(Color(0xFFD77C1A), Color(0xFFB9650F)))
    }
    val textColour = when (tone) {
        LensTone.RED -> if (on) Color.White else Color(0xFFF3D6DA)
        LensTone.GREY -> if (on && warn) Color.White else if (on) Color(0xFF2A1400) else Color(0xFFD8D4C4)
        LensTone.AMBER -> if (on) Color(0xFF2A1200) else Color(0xFF3A1C06)
    }
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .background(body, RoundedCornerShape(4.dp))
            .border(2.dp, Color(0xFF0B0C0D), RoundedCornerShape(4.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = textColour,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center,
            )
            if (sub != null) {
                Text(
                    text = sub,
                    color = if (tone == LensTone.RED) Color(0xFFF3D6DA) else Color(0xFF1C0D00),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
