package com.oersted.trainer.ui.glareshield

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.GlareshieldLogic
import com.oersted.trainer.sim.KnobPick

/**
 * Centre autopilot controller (Sperry SP-77 part of GlareshieldPanel.tsx): AP NAV and AP PITCH knobs, the course knob,
 * the HDG SEL button, the A/P DISC lamp, two INOP boxes and the AIL / ELEV engage paddles. Positions are the web's,
 * relative to the controller plate (origin 672, 212; 736 x 268). The caller owns the state and passes the rules' inputs.
 * Not compiled in the sandbox (needs the Android SDK).
 */
@Composable
fun ApCentreController(
    powered: Boolean,
    apNav: Int,
    apPitch: Int,
    selCrs: Double,
    selHdg: Double,
    apHdgSw: Int,
    apDiscWarn: Boolean,
    apAil: Boolean,
    apElev: Boolean,
    interlocksMet: Boolean,
    fdFail: Boolean,
    attFail: Boolean,
    onApNav: (Int) -> Unit,
    onApPitch: (Int) -> Unit,
    onCourseStep: (Int) -> Unit,
    onHdgStep: (Int) -> Unit,
    onHdgSwitch: (Int) -> Unit,
    onDiscReset: () -> Unit,
    onEngage: (aileron: Boolean, engaged: Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 736.dp, height = 268.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF1A1D21), Color(0xFF0E1013))), RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF2B2E33), RoundedCornerShape(4.dp)),
    ) {
        // A/P DISC warning lamp (reset on press)
        Box(
            modifier = Modifier
                .offset(x = 41.dp, y = 10.dp)
                .size(width = 46.dp, height = 24.dp)
                .background(
                    if (powered && apDiscWarn) Color(0xFFFF3B30) else Color(0xFF5A1612),
                    RoundedCornerShape(3.dp),
                )
                .border(1.dp, Color(0xFF3A0D0A), RoundedCornerShape(3.dp))
                .clickable { onDiscReset() },
        ) {
            Text(
                text = "A/P DISC",
                color = Color.White,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
            )
        }
        // AP NAV knob: centre (108, 138), size 116
        DetentKnob(
            value = apNav,
            markAngles = GlareshieldLogic.AP_NAV_ANGLES,
            labels = listOf("MAN", "VOR\nLOC", "AUTO\nAPP", "MAN\nG/S"),
            onChange = onApNav,
            modifier = Modifier.offset(x = 50.dp, y = 80.dp).size(116.dp),
        )
        // INOP boxes
        InopBox(lit = GlareshieldLogic.inopLeftLit(powered, fdFail, attFail), modifier = Modifier.offset(x = 173.dp, y = 13.dp))
        InopBox(lit = GlareshieldLogic.inopRightLit(powered, interlocksMet), modifier = Modifier.offset(x = 255.dp, y = 13.dp))
        // course knob: centre (253, 96), size 80
        CourseKnob(
            value = selCrs,
            powered = powered,
            onStep = onCourseStep,
            modifier = Modifier.offset(x = 213.dp, y = 56.dp).size(80.dp),
        )
        Text(
            text = "COURSE",
            color = Color(0xFFE8E4D8),
            fontFamily = FontFamily.SansSerif,
            fontSize = 11.sp,
            modifier = Modifier.offset(x = 230.dp, y = 150.dp),
        )
        // HDG SEL button: centre (253, 202), size 66
        HdgSelButton(
            hdg = selHdg,
            on = apHdgSw == 2,
            powered = powered,
            onToggle = { onHdgSwitch(GlareshieldLogic.nextHdgSwitch(apHdgSw)) },
            onStep = onHdgStep,
            modifier = Modifier.offset(x = 220.dp, y = 169.dp).size(66.dp),
        )
        Text(
            text = "HDG SEL",
            color = Color(0xFFE8E4D8),
            fontFamily = FontFamily.SansSerif,
            fontSize = 11.sp,
            modifier = Modifier.offset(x = 222.dp, y = 240.dp),
        )
        // AP PITCH knob: centre (631, 138), size 116
        DetentKnob(
            value = apPitch,
            markAngles = GlareshieldLogic.AP_PITCH_ANGLES,
            labels = listOf("TURB", "OFF", "ALT\nHOLD"),
            onChange = onApPitch,
            modifier = Modifier.offset(x = 573.dp, y = 80.dp).size(116.dp),
        )
        // AIL / ELEV engage paddles (approximate placement inside the plate)
        EngagePaddle(
            engaged = apAil,
            onChange = { want -> if (GlareshieldLogic.canSetEngaged(want, interlocksMet)) onEngage(true, want) },
            modifier = Modifier.offset(x = 300.dp, y = 190.dp).size(width = 94.dp, height = 28.dp),
        )
        EngagePaddle(
            engaged = apElev,
            onChange = { want -> if (GlareshieldLogic.canSetEngaged(want, interlocksMet)) onEngage(false, want) },
            modifier = Modifier.offset(x = 408.dp, y = 190.dp).size(width = 94.dp, height = 28.dp),
        )
    }
}

@Composable
private fun InopBox(lit: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 64.dp, height = 32.dp)
            .border(2.dp, if (lit) Color(0xFFFFB21A) else Color(0xFFE8E4D8), RoundedCornerShape(2.dp)),
    ) {
        Text(
            text = "INOP",
            color = if (lit) Color(0xFFFFB21A) else Color(0xFFE8E4D8),
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.offset(x = 12.dp, y = 6.dp),
        )
    }
}
