@file:OptIn(ExperimentalLayoutApi::class)

package com.oersted.trainer.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.ui.common.Classic737Palette

/** Port of components/panel/widgets.tsx. Sizes follow the web rem values (1 rem = 16 dp). */

enum class LightTone { CAUTION, OK, WARN, ELEC }

data class PanelOption(val id: String, val label: String)

private fun LightTone.litColor(): Color = when (this) {
    LightTone.CAUTION -> Classic737Palette.Caution
    LightTone.OK -> Classic737Palette.Ok
    LightTone.WARN -> Classic737Palette.Warn
    LightTone.ELEC -> Classic737Palette.Elec
}

private val Line = Color(0xFF3A4150)
private val Metal = Color(0xFF4C535C)
private val Bezel = Color(0xFF101318)

@Composable
fun PanelCaptionLight(
    label: String,
    on: Boolean,
    tone: LightTone = LightTone.CAUTION,
    modifier: Modifier = Modifier,
) {
    val lit = tone.litColor()
    Box(
        modifier
            .size(width = 72.dp, height = 32.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (on) lit else Classic737Palette.Inset)
            .border(1.dp, if (on) lit else Line, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            textAlign = TextAlign.Center,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 10.sp,
            color = if (on) Color.Black else Classic737Palette.Subtle,
        )
    }
}

@Composable
fun PanelRocker(
    label: String,
    on: Boolean,
    onLabel: String = "ON",
    offLabel: String = "OFF",
    onChange: (Boolean) -> Unit,
    lit: Boolean? = null,
    disabled: Boolean = false,
) {
    val glow = lit ?: on
    Column(Modifier.width(74.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Classic737Palette.Muted, textAlign = TextAlign.Center)
        Box(
            Modifier
                .size(width = 44.dp, height = 56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Bezel)
                .border(1.dp, Metal, RoundedCornerShape(4.dp))
                .clickable(enabled = !disabled) { onChange(!on) },
        ) {
            Text(onLabel, fontSize = 6.sp, color = Color(0xFF7E8791), modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            Text(offLabel, fontSize = 6.sp, color = Color(0xFF59616B), modifier = Modifier.padding(start = 4.dp).offset(y = 46.dp))
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = if (on) 4.dp else 20.dp)
                    .width(7.dp)
                    .height(32.dp)
                    .rotate(if (on) 18f else -18f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFD4D7DA)),
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (glow) Classic737Palette.Ok.copy(alpha = 0.2f) else Color(0xFF15191E))
                    .border(1.dp, if (glow) Classic737Palette.Ok.copy(alpha = 0.6f) else Color(0xFF343A42), CircleShape),
            )
        }
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (glow) Classic737Palette.Ok else Line),
        )
    }
}

@Composable
fun PanelGuardedRocker(
    label: String,
    on: Boolean,
    onChange: (Boolean) -> Unit,
    lit: Boolean? = null,
    onLabel: String = "ON",
    offLabel: String = "OFF",
) {
    var open by rememberSaveable(label) { mutableStateOf(false) }
    val glow = lit ?: on
    Column(Modifier.width(83.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Classic737Palette.Muted, textAlign = TextAlign.Center)
        Box(Modifier.size(width = 51.dp, height = 70.dp)) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = if (open) (-4).dp else 0.dp)
                    .padding(horizontal = 4.dp)
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(Color(0xFF59616A))
                    .border(1.dp, Color(0xFF7C838B), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .clickable { open = !open },
                contentAlignment = Alignment.Center,
            ) {
                Text("GUARD", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color(0xFF171A1D))
            }
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 8.dp, y = 12.dp)
                    .size(width = 44.dp, height = 48.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF111418))
                    .border(1.dp, Color(0xFF343A40), RoundedCornerShape(2.dp))
                    .clickable(enabled = open) { onChange(!on) }
                    .padding(horizontal = 8.dp),
            ) {
                Box(
                    Modifier
                        .align(if (on) Alignment.TopCenter else Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(20.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (on) Color(0xFF424A52) else Color(0xFF252A30))
                        .border(1.dp, if (on) Classic737Palette.Ok.copy(alpha = 0.5f) else Classic737Palette.Border, RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (on) onLabel else offLabel,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (on) Classic737Palette.Ok else Classic737Palette.Subtle,
                    )
                }
            }
        }
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (glow) Classic737Palette.Ok else Line),
        )
    }
}

@Composable
fun PanelThreePos(
    label: String,
    value: String,
    options: List<PanelOption>,
    onChange: (String) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Classic737Palette.Muted)
        Column(
            Modifier
                .clip(RoundedCornerShape(2.dp))
                .border(1.dp, Line, RoundedCornerShape(2.dp))
                .background(Classic737Palette.Inset),
        ) {
            options.forEach { opt ->
                val active = value == opt.id
                Text(
                    opt.label,
                    modifier = Modifier
                        .width(70.dp)
                        .background(if (active) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                        .clickable { onChange(opt.id) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) MaterialTheme.colorScheme.background else Classic737Palette.Muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun PanelThreeToggle(
    label: String,
    value: String,
    options: List<PanelOption>,
    onChange: (String) -> Unit,
) {
    val n = options.size
    val idx = options.indexOfFirst { it.id == value }
    val knobAngle = ((idx - 1) * 28).coerceIn(-28, 28).toFloat()
    Column(Modifier.width(86.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Classic737Palette.Muted, textAlign = TextAlign.Center)
        Box(
            Modifier
                .size(width = 44.dp, height = 64.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF15191F))
                .border(1.dp, Color(0xFF4D5560), RoundedCornerShape(6.dp)),
        ) {
            Box(Modifier.align(Alignment.TopCenter).offset(y = 4.dp).width(3.dp).height(56.dp).clip(CircleShape).background(Color(0xFF090B0E)))
            options.forEachIndexed { i, opt ->
                val y = 10.0 + i * (44.0 / maxOf(1, n - 1))
                val active = value == opt.id
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = y.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (active) Color(0xFFC8CCD0) else Color(0xFF343B44))
                        .border(1.dp, if (active) MaterialTheme.colorScheme.onSurface else Line, CircleShape)
                        .clickable { onChange(opt.id) },
                )
            }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(width = 8.dp, height = 36.dp)
                    .rotate(knobAngle)
                    .clip(CircleShape)
                    .background(Color(0xFFBFC3C7)),
            )
        }
        Text(
            options.firstOrNull { it.id == value }?.label ?: value,
            fontSize = 9.sp,
            color = Classic737Palette.Subtle,
        )
    }
}

@Composable
fun PanelRotary(
    label: String,
    value: String,
    options: List<PanelOption>,
    onChange: (String) -> Unit,
) {
    val idx = maxOf(0, options.indexOfFirst { it.id == value })
    val count = maxOf(1, options.size - 1)
    val angle = -55f + (idx.toFloat() / count) * 110f
    Column(Modifier.width(102.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Classic737Palette.Muted, textAlign = TextAlign.Center)
        BoxWithConstraints(Modifier.size(width = 102.dp, height = 93.dp)) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0B0D10))
                    .border(1.dp, Line, CircleShape),
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(55.dp)
                    .rotate(angle)
                    .clip(CircleShape)
                    .background(Color(0xFF303744))
                    .border(1.dp, Color(0xFF4A5260), CircleShape),
            ) {
                Box(Modifier.align(Alignment.TopCenter).offset(y = 5.dp).width(3.dp).height(17.dp).clip(CircleShape).background(Color(0xFFE7E2D6)))
            }
            options.forEachIndexed { i, opt ->
                val a = -55.0 + (i.toDouble() / count) * 110.0
                val rad = a * Math.PI / 180.0
                val xFrac = 0.5 + kotlin.math.sin(rad) * 0.43
                val yFrac = 0.5 - kotlin.math.cos(rad) * 0.43
                Text(
                    opt.label,
                    modifier = Modifier
                        .offset(x = maxWidth * xFrac.toFloat() - 14.dp, y = maxHeight * yFrac.toFloat() - 7.dp)
                        .clickable { onChange(opt.id) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (value == opt.id) MaterialTheme.colorScheme.onSurface else Classic737Palette.Muted,
                )
            }
        }
        Text(value, fontSize = 9.sp, color = Classic737Palette.Subtle)
    }
}

@Composable
fun PanelFireHandle(
    label: String,
    pulled: Boolean,
    onToggle: () -> Unit,
    onRotate: ((String) -> Unit)? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(
            Modifier.clickable { onToggle() },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Classic737Palette.Muted)
            Box(
                Modifier
                    .offset(y = if (pulled) 8.dp else 0.dp)
                    .size(width = 40.dp, height = 64.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (pulled) Classic737Palette.Warn else Classic737Palette.Warn.copy(alpha = 0.8f))
                    .border(1.dp, Classic737Palette.Warn.copy(alpha = if (pulled) 1f else 0.5f), RoundedCornerShape(2.dp)),
            ) {
                Text(
                    "FIRE",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Classic737Palette.Fg,
                )
            }
        }
        if (pulled && onRotate != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "L",
                    modifier = Modifier.clickable { onRotate("L") }.border(1.dp, Classic737Palette.Warn.copy(alpha = 0.5f), RoundedCornerShape(2.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "R",
                    modifier = Modifier.clickable { onRotate("R") }.border(1.dp, Classic737Palette.Warn.copy(alpha = 0.5f), RoundedCornerShape(2.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Web Module: a bordered panel with a header and the controls flowing in a centred, wrapping row. */
@Composable
fun PanelModule(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .border(1.dp, Color(0xFF555D66), RoundedCornerShape(5.dp))
            .background(Color(0xFF22262C))
            .padding(12.dp),
    ) {
        Text(
            title.uppercase(),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFC5CBD1),
        )
        Spacer(Modifier.height(1.dp).fillMaxWidth().background(Color(0xFF505760)))
        Spacer(Modifier.height(10.dp))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

/** Web ControlSlider: a labelled 0..1 slider with its value shown to two decimals. */
@Composable
fun PanelControlSlider(
    label: String,
    value: Double,
    onChange: (Double) -> Unit,
    min: Double = 0.0,
    max: Double = 1.0,
    disabled: Boolean = false,
) {
    val safe = if (value.isFinite()) value.coerceIn(min, max) else min
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, Classic737Palette.Border, RoundedCornerShape(6.dp))
            .background(Classic737Palette.Inset)
            .padding(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Classic737Palette.Muted)
            Text("%.2f".format(safe), fontSize = 9.sp, color = Classic737Palette.Subtle)
        }
        Slider(
            value = safe.toFloat(),
            onValueChange = { onChange(it.toDouble()) },
            valueRange = min.toFloat()..max.toFloat(),
            enabled = !disabled,
        )
    }
}
