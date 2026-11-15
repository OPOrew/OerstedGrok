package com.oersted.trainer.ui.glareshield

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.AutoFlight
import com.oersted.trainer.sim.GlareshieldAnnunciators
import com.oersted.trainer.sim.GlareshieldReducer
import com.oersted.trainer.sim.Pedestal
import com.oersted.trainer.sim.WarningReducer
import com.oersted.trainer.ui.cockpit.CockpitModel

/*
 * Glareshield, port of components/panel/GlareshieldPanel.tsx.
 * The web places every element at the reference photograph's own pixel coordinates (NW x NH design canvas, top lip at
 * OY) and scales the whole canvas to the requested width. Here the same design coordinates are used in dp and the
 * whole canvas is scaled with graphicsLayer, so the lenses, knobs and legends keep their spacing.
 * Not ported: the clip-path outline of the housing (a plain rectangle with the same gradient is drawn instead).
 * Not compiled in the sandbox (needs the Android SDK).
 */

private const val NW = 2172f // photo width = design canvas width
private const val OY = 165f // photo y of the housing top lip
private const val NH = 345f // visible height of the housing (photo y 165..510)
private const val MAX_WIDTH = 1360f // the housing is never wider than this many design px
private const val FD_DX = 1092f // right FD controller = left one shifted by this much
private val INK = Color(0xFFE8E4D8)

@Composable
fun GlareshieldPanel(cockpit: CockpitModel, width: Dp) {
    val s by cockpit.simState.collectAsState()
    val d by cockpit.derived.collectAsState()
    val m by cockpit.mipState.collectAsState()
    val ped by cockpit.pedestalState.collectAsState()
    val density = LocalDensity.current.density

    val pw = d.dcPower
    val fire = GlareshieldAnnunciators.anyFireWarning(
        d.engine1FireWarn, d.engine2FireWarn, d.apuFireWarn, d.wheelWellFireWarn, d.cargoFwdFireWarn, d.cargoAftFireWarn,
    )
    val fireTestOvht = ped.fireTest == Pedestal.FireTest.OVHT_FIRE
    val fireTestFault = ped.fireTest == Pedestal.FireTest.FAULT
    val fireOn = GlareshieldAnnunciators.fireWarnLit(pw, fire, ped.bellSilenced, fireTestOvht)
    val lightKeyTest = m.lightKey == 2
    val caution = GlareshieldAnnunciators.cautionPending(d.masterCaution, m.masterCautionAck)
    val mcOn = GlareshieldAnnunciators.masterCautionLit(pw, caution, m.recall, s.ltTest, lightKeyTest, fireTestOvht, fireTestFault)
    val wsOn = GlareshieldAnnunciators.windshearLit(pw, s.ltTest, m.raTest, lightKeyTest)
    val test = GlareshieldAnnunciators.testActive(pw, s.ltTest, m.recall, lightKeyTest, m.annTest)
    fun sys(on: Boolean): Boolean = GlareshieldAnnunciators.systemLamp(pw, on, test)

    val aFlt = sys(d.hydALow && d.hydBLow)
    val aApu = sys(d.apuLowOil || s.faults.apuDoorJam)
    val aFuel = sys(d.fuelLowPressure1 || d.fuelLowPressure2)
    val aOvht = sys(s.faults.packOverheat || s.faults.packOverheatR || fireTestFault)
    val aHyd = sys(d.hydALow || d.hydBLow)
    val aOvhd = sys(s.bleedTrip1 || s.bleedTrip2)
    val aAir = sys(s.packTripL || s.packTripR)
    val aElt = sys(d.eltTransmitting)
    val aDoors = sys(d.doorWarning)
    val aAntiIce = sys(
        GlareshieldAnnunciators.antiIceProblem(
            s.antiIce1, d.bleed13Available1, s.antiIce2, d.bleed13Available2,
            s.faults.alphaVaneHeatFailureL, s.faults.alphaVaneHeatFailureR,
        ),
    )
    val aOnlyTest = sys(false)

    // Interlocks for the AIL / ELEV paddles (AMM 22-11-0 p.9, p.32).
    val il = AutoFlight.afInterlocks(
        AutoFlight.AfPlant(pw = pw, hydB = d.hydB >= 1500.0, attOk = !m.fail.att, hdgOk = !m.fail.hdg),
    )
    val fdUp = pw && !m.fail.fd && !m.fail.att

    // The web clears the caution acknowledge once the master caution has gone out.
    LaunchedEffect(d.masterCaution, m.masterCautionAck) {
        if (!d.masterCaution && m.masterCautionAck) cockpit.updateMip { WarningReducer.syncCautionAck(it, d.masterCaution) }
    }

    val k = width.value.coerceAtMost(MAX_WIDTH) / NW
    val wDp: Dp = (NW * k).dp
    val hDp: Dp = (NH * k).dp

    Box(Modifier.width(width).height(hDp + 16.dp)) {
        // Outer shroud behind the housing.
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF2A2F36), Color(0xFF1D2127), Color(0xFF111316)))),
        )
        Box(
            Modifier
                .offset(x = (width - wDp) / 2, y = 8.dp)
                .size(wDp, hDp)
                .clipToBounds(),
        ) {
            // Design canvas: NW design px wide, scaled to the requested width, top lip moved to the top edge.
            Box(
                Modifier
                    // larger than the parent: top-left aligned and unbounded (requiredSize would centre it and shift the scaled canvas)
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .size(NW.dp, (OY + NH + 10).dp)
                    .drawBehind { drawHousing(density) }
                    .graphicsLayer {
                        scaleX = k
                        scaleY = k
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationY = -OY * k * density
                    },
            ) {
                // Left warning cluster.
                Box(
                    Modifier
                        .offset(14.dp, 248.dp)
                        .size(326.dp, 252.dp)
                        .background(Brush.verticalGradient(listOf(Color(0xFF14161A), Color(0xFF0D0F12))), RoundedCornerShape(4.dp)),
                )
                Screw(35f, 298f, 30f)
                Screw(27f, 430f, 75f)
                LensAt(60f, 255f, 84f, 80f, "MASTER\nCAUTION", LensTone.AMBER, mcOn, sub = "PUSH TO RESET", onClick = {
                    cockpit.updateMip { WarningReducer.ackMasterCaution(it) }
                })
                LensAt(156f, 255f, 90f, 80f, "FIRE\nWARN", LensTone.RED, fireOn, sub = "BELL CUTOUT", onClick = {
                    cockpit.updatePedestal { WarningReducer.silenceBell(it) }
                })
                LensAt(52f, 353f, 88f, 62f, "W/S\nWARNING", LensTone.GREY, wsOn, warn = true)
                LensAt(52f, 425f, 88f, 62f, "W/S\nCAUTION", LensTone.GREY, wsOn)
                LensAt(152f, 347f, 92f, 41f, "FLT CONT", LensTone.AMBER, aFlt)
                LensAt(254f, 347f, 84f, 41f, "ELT", LensTone.AMBER, aElt)
                LensAt(152f, 397f, 92f, 41f, "", LensTone.AMBER, aOnlyTest)
                LensAt(254f, 397f, 84f, 41f, "APU", LensTone.AMBER, aApu)
                LensAt(152f, 447f, 92f, 41f, "FUEL", LensTone.AMBER, aFuel)
                LensAt(254f, 447f, 84f, 41f, "OVHT/DET", LensTone.AMBER, aOvht)

                // Captain flight director controller.
                Box(Modifier.offset(348.dp, 212.dp)) {
                    FdController(
                        who = "Captain",
                        mode = m.fdMode,
                        altHold = m.fdAlt,
                        pitch = m.fdPitch,
                        powered = pw,
                        fdUp = fdUp,
                        onMode = { v -> cockpit.updateMip { GlareshieldReducer.setFdMode(it, false, v) } },
                        onAltHold = { v -> cockpit.updateMip { GlareshieldReducer.setFdAltHold(it, false, v, fdUp) } },
                        onPitch = { v -> cockpit.updateMip { GlareshieldReducer.setFdPitch(it, false, v) } },
                    )
                }

                // Centre autopilot controller (Sperry SP-77).
                Box(
                    Modifier
                        .offset(672.dp, 212.dp)
                        .size(736.dp, 268.dp)
                        .background(Brush.verticalGradient(listOf(Color(0xFF1A1D21), Color(0xFF0E1013))), RoundedCornerShape(4.dp)),
                )
                listOf(
                    Triple(690f, 237f, 12f), Triple(690f, 349f, 53f), Triple(690f, 454f, 65f),
                    Triple(1258f, 237f, 177f), Triple(1388f, 237f, 189f), Triple(1390f, 349f, 201f),
                    Triple(1388f, 454f, 213f), Triple(857f, 441f, 225f), Triple(975f, 455f, 237f),
                ).forEach { (x, y, rot) -> Screw(x, y, rot) }
                Box(Modifier.offset(672.dp, 212.dp)) {
                    ApCentreController(
                        powered = pw,
                        apNav = m.apNav,
                        apPitch = m.apPitch,
                        selCrs = m.selCrs,
                        selHdg = m.selHdg,
                        apHdgSw = m.apHdgSw,
                        apDiscWarn = m.apWarn,
                        apAil = m.apAil,
                        apElev = m.apElev,
                        interlocksMet = il,
                        fdFail = m.fail.fd,
                        attFail = m.fail.att,
                        onApNav = { v -> cockpit.updateMip { GlareshieldReducer.setApNav(it, v) } },
                        onApPitch = { v -> cockpit.updateMip { GlareshieldReducer.setApPitch(it, v) } },
                        onCourseStep = { v -> cockpit.updateMip { GlareshieldReducer.stepSelCrs(it, v.toDouble()) } },
                        onHdgStep = { v -> cockpit.updateMip { GlareshieldReducer.stepSelHdg(it, v.toDouble()) } },
                        onHdgSwitch = { cockpit.updateMip { GlareshieldReducer.toggleHdgSwitch(it) } },
                        onDiscReset = { cockpit.updateMip { GlareshieldReducer.resetApDisc(it) } },
                        onEngage = { aileron, engaged ->
                            cockpit.updateMip { GlareshieldReducer.setEngage(it, aileron, engaged, il) }
                        },
                    )
                }
                Box(Modifier.offset(917.dp, 218.dp).size(2.dp, 40.dp).background(INK.copy(alpha = 0.8f)))
                Legend(1115f, 237f, "ENGAGED", 18f)
                Box(Modifier.offset(1000.dp, 256.dp).size(230.dp, 2.dp).background(INK.copy(alpha = 0.8f)))
                Box(Modifier.offset(1112.dp, 256.dp).size(2.dp, 84.dp).background(INK.copy(alpha = 0.8f)))
                Legend(1030f, 455f, "AIL", 16f)
                Legend(1110f, 455f, "DISENGAGED", 15f)
                Legend(1196f, 455f, "ELEV", 16f)
                Triangle(1262f, 292f)
                Triangle(1332f, 292f)

                // First officer flight director controller.
                Box(Modifier.offset((348f + FD_DX).dp, 212.dp)) {
                    FdController(
                        who = "First officer",
                        mode = m.fdMode2,
                        altHold = m.fdAlt2,
                        pitch = m.fdPitch2,
                        powered = pw,
                        fdUp = fdUp,
                        onMode = { v -> cockpit.updateMip { GlareshieldReducer.setFdMode(it, true, v) } },
                        onAltHold = { v -> cockpit.updateMip { GlareshieldReducer.setFdAltHold(it, true, v, fdUp) } },
                        onPitch = { v -> cockpit.updateMip { GlareshieldReducer.setFdPitch(it, true, v) } },
                    )
                }

                // Right warning cluster.
                Box(
                    Modifier
                        .offset(1782.dp, 248.dp)
                        .size(376.dp, 252.dp)
                        .background(Brush.verticalGradient(listOf(Color(0xFF14161A), Color(0xFF0D0F12))), RoundedCornerShape(4.dp)),
                )
                Screw(1793f, 276f, 50f)
                Screw(2130f, 298f, 15f)
                LensAt(1855f, 255f, 90f, 80f, "MASTER\nCAUTION", LensTone.AMBER, mcOn, sub = "PUSH TO RESET", onClick = {
                    cockpit.updateMip { WarningReducer.ackMasterCaution(it) }
                })
                LensAt(1966f, 255f, 90f, 80f, "FIRE\nWARN", LensTone.RED, fireOn, sub = "BELL CUTOUT", onClick = {
                    cockpit.updatePedestal { WarningReducer.silenceBell(it) }
                })
                LensAt(1792f, 348f, 113f, 42f, "ANTI ICE", LensTone.AMBER, aAntiIce)
                LensAt(1917f, 348f, 113f, 42f, "", LensTone.AMBER, aOnlyTest)
                LensAt(1792f, 397f, 113f, 42f, "HYD", LensTone.AMBER, aHyd)
                LensAt(1917f, 397f, 113f, 42f, "OVERHEAD", LensTone.AMBER, aOvhd)
                LensAt(1792f, 447f, 113f, 42f, "DOORS", LensTone.AMBER, aDoors)
                LensAt(1917f, 447f, 113f, 42f, "AIR COND", LensTone.AMBER, aAir)
                LensAt(2050f, 353f, 92f, 62f, "W/S\nWARNING", LensTone.GREY, wsOn, warn = true)
                LensAt(2050f, 425f, 92f, 62f, "W/S\nCAUTION", LensTone.GREY, wsOn)
            }
        }
    }
}

/** Design-px rectangle at (x, y) with size (w, h), positioned inside the design canvas. */
@Composable
private fun LensAt(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    label: String,
    tone: LensTone,
    on: Boolean,
    sub: String? = null,
    warn: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Lens(
        label = label,
        tone = tone,
        on = on,
        width = w.dp,
        height = h.dp,
        modifier = Modifier.offset(x.dp, y.dp),
        sub = sub,
        warn = warn,
        onClick = onClick,
    )
}

/** Printed legend centred on (x, y) in design px. */
@Composable
private fun Legend(x: Float, y: Float, text: String, size: Float) {
    val boxWidth = 160f
    Box(
        Modifier
            .offset((x - boxWidth / 2).dp, (y - size * 0.6f).dp)
            .width(boxWidth.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = INK,
            fontFamily = FontFamily.SansSerif,
            fontSize = size.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** Crinkle-finish screw head centred on (x, y) with a slot at angle [rot] degrees. */
@Composable
private fun Screw(x: Float, y: Float, rot: Float) {
    Box(
        Modifier
            .offset((x - 14f).dp, (y - 14f).dp)
            .size(28.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(listOf(Color(0xFF8D9195), Color(0xFF3D4044), Color(0xFF17191C))),
                CircleShape,
            ),
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .rotate(rot)
                .size(20.dp, 2.dp)
                .background(Color(0xFF0A0A0B)),
        )
    }
}

/** Small downward triangle (the web's CSS border triangles) at (x, y) in design px. */
@Composable
private fun Triangle(x: Float, y: Float) {
    Canvas(Modifier.offset(x.dp, y.dp).size(14.dp, 11.dp)) {
        val p = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width / 2f, size.height)
            close()
        }
        drawPath(p, INK)
    }
}

/**
 * Housing shell, the web's two clip-path layers in design px: the body with sloped wings and raised centre, the rolled
 * black top lip, cream end trim and the bottom bar. [u] is the px per design px (the density).
 */
private fun DrawScope.drawHousing(u: Float) {
    fun poly(vararg pts: Float): Path = Path().apply {
        moveTo(pts[0] * u, pts[1] * u)
        var i = 2
        while (i < pts.size) {
            lineTo(pts[i] * u, pts[i + 1] * u)
            i += 2
        }
        close()
    }
    fun rect(x: Float, y: Float, w: Float, h: Float, brush: Brush) {
        drawRect(brush = brush, topLeft = Offset(x * u, y * u), size = Size(w * u, h * u))
    }

    drawPath(
        poly(0f, 242f, 660f, 174f, 672f, 165f, 1410f, 165f, 1422f, 174f, 2172f, 242f, 2172f, 508f, 0f, 508f),
        Brush.verticalGradient(listOf(Color(0xFF0A0B0D), Color(0xFF0E1013), Color(0xFF0C0D10)), startY = OY * u, endY = 508f * u),
    )
    drawPath(
        poly(
            0f, 242f, 660f, 174f, 672f, 165f, 1410f, 165f, 1422f, 174f, 2172f, 242f,
            2172f, 262f, 1422f, 196f, 1410f, 189f, 672f, 189f, 660f, 196f, 0f, 262f,
        ),
        Brush.verticalGradient(listOf(Color(0xFF2A2D31), Color(0xFF07080A), Color(0xFF16181B)), startY = OY * u, endY = 262f * u),
    )
    rect(0f, 244f, 14f, 264f, Brush.horizontalGradient(listOf(Color(0xFFD9CBA8), Color(0xFF8D7F61)), 0f, 14f * u))
    rect(NW - 14f, 244f, 14f, 264f, Brush.horizontalGradient(listOf(Color(0xFF8D7F61), Color(0xFFD9CBA8)), (NW - 14f) * u, NW * u))
    rect(0f, 494f, NW, 14f, Brush.verticalGradient(listOf(Color(0xFF0E1013), Color(0xFF050607)), 494f * u, 508f * u))
    rect(0f, 494f, 46f, 14f, Brush.verticalGradient(listOf(Color(0xFFCDBF9C), Color(0xFF7D7055)), 494f * u, 508f * u))
    rect(NW - 46f, 494f, 46f, 14f, Brush.verticalGradient(listOf(Color(0xFFCDBF9C), Color(0xFF7D7055)), 494f * u, 508f * u))
}
