package com.oersted.trainer.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oersted.trainer.sim.MainPanelRules as R
import com.oersted.trainer.sim.MarkerBeacons.MarkerId
import com.oersted.trainer.sim.RadioNav
import com.oersted.trainer.ui.cockpit.ApproachProgressView
import com.oersted.trainer.ui.cockpit.CockpitModel
import com.oersted.trainer.ui.instruments.AdiFace
import com.oersted.trainer.ui.instruments.DialBand
import com.oersted.trainer.ui.instruments.RoundDial
import com.oersted.trainer.ui.instruments.AltFace
import com.oersted.trainer.ui.instruments.AnnPanel
import com.oersted.trainer.ui.instruments.AsiFace
import com.oersted.trainer.ui.instruments.ComparatorPlate
import com.oersted.trainer.ui.instruments.HeightDial
import com.oersted.trainer.ui.instruments.HsiFace
import com.oersted.trainer.ui.instruments.IasStrip
import com.oersted.trainer.ui.instruments.LightTestKey
import com.oersted.trainer.ui.instruments.QtyLed
import com.oersted.trainer.ui.instruments.RmiFace
import com.oersted.trainer.ui.instruments.StandbyAttitudeFace
import com.oersted.trainer.ui.instruments.TopStrip
import com.oersted.trainer.ui.instruments.VsiFace
import com.oersted.trainer.ui.instruments.adiNavFrom
import com.oersted.trainer.ui.pedestal.AnnunTone
import com.oersted.trainer.ui.pedestal.PedestalLegend
import com.oersted.trainer.ui.pedestal.PushButton
import com.oersted.trainer.ui.pedestal.Toggle

/*
 * Port of components/panel/MainInstrumentPanel.tsx `Panel()`: the 3036 x 720 design-px instrument panel (captain, centre,
 * first officer, lighting). Every child sits at the web's coordinates inside one canvas that is scaled as a whole.
 * Ported here: the captain and first-officer zones, the standby column, the lighting overlays. The cream engine strip, the dark
 * systems panel and the gear column are placed by [MainPanelEngines]. The lower strip (radios, plates) is not ported yet.
 * Pure rules are in sim/MainPanelRules.kt. Compose only; verified on CI, not compiled locally.
 */

const val PANEL_CW = 3036
const val PANEL_CH = 720

private const val X_SB = 1100 // centre-left standby column
private const val F0 = 2064 // first-officer panel

/**
 * The instrument panel at [scale] (design px per dp). Wider than a phone at any readable scale, so it scrolls sideways:
 * the caller usually keeps [scale] around 0.4 to 0.6.
 */
@Composable
fun MainInstrumentPanel(cockpit: CockpitModel, scale: Float, modifier: Modifier = Modifier) {
    Box(modifier.horizontalScroll(rememberScrollState())) {
        Box(Modifier.width((PANEL_CW * scale).dp).height((PANEL_CH * scale).dp).clipToBounds()) {
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .size(PANEL_CW.dp, PANEL_CH.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .background(PanelBlue)
                    .border(2.dp, Color(0xFF15181C), RoundedCornerShape(10.dp)),
            ) {
                PanelBody(cockpit)
            }
        }
    }
}

@Composable
private fun PanelBody(cockpit: CockpitModel) {
    val sim by cockpit.simState.collectAsState()
    val d by cockpit.derived.collectAsState()
    val m by cockpit.mipState.collectAsState()
    val air by cockpit.air.collectAsState()
    val ped by cockpit.pedestalState.collectAsState()

    val pw = d.dcPower
    val capt = air.capt
    val fo = air.fo
    val captAirPowered = capt.adc.data.powered
    val foAirPowered = fo.adc.data.powered
    val disp = R.display(sim.navXfr, sim.efiXfr, m.navSel, m.compSel, m.gyroSel)
    val src = R.sources(disp, sim.irsXfr, m.srcFail)
    val failC = R.failCapt(m.fail, src, m.srcPower, m.attCapt, disp)
    val failF = R.failFo(m.fail, src, m.srcPower, m.attFo, disp)
    val navC = if (failC.loc) RadioNav.NO_NAV else m.nav
    val navF = if (failF.loc) RadioNav.NO_NAV else m.navFo
    val tas = R.tasKt(capt.iasKt, capt.altFt)
    val gs = R.gsKt(sim.wow, sim.groundSpeedKt, tas)
    val ra = R.ra(sim.wow, m.lrra.h1)
    val test = R.test(pw, sim.ltTest, m.raTest, m.lightKey)
    val tatC = R.tatC(sim.ambientTempF)
    val hgC = sim.psBaroCaptHpa / R.HPA_PER_INHG
    val hgF = sim.psBaroFoHpa / R.HPA_PER_INHG
    val hgS = sim.psBaroStbyHpa / R.HPA_PER_INHG
    val dhOn = R.dhOn(test, pw, sim.wow, ra, m.raDh)
    val heightFt = R.heightFt(ra)
    val heightPowered = R.heightPowered(pw, m.fail.ht, m.srcPower.lrra1Cb, m.lrra.h1)
    val slip = R.clamp(sim.rudderPedal, -1.0, 1.0)
    val att = m.att
    val ilsC = m.navOn && m.navTuned == "ILS"

    fun setDh(v: Double) {
        cockpit.updateMip { it.copy(raDh = v) }
        cockpit.updatePedestal { p -> p.copy(lrraDh = Pair(Math.floor(v + 0.5).toInt(), p.lrraDh.second)) }
    }

    fun rmiBrg(sel: Int, nav: RadioNav.RadioNavData, adf: Double?) = R.rmiBearing(sel, m.navOn, nav.vorValid, nav.radial, att.hdg, adf)

    /* ═════════ CAPTAIN ═════════ */
    MpS(14, 14, 260, 46) { TopStrip(cockpit, left = true) }
    MpS(286, 14, 100, 24) { MpLens("GPWS") }
    MpS(286, 40, 100, 24) { MpLens("BELOW G/S\nP INHIBIT") }
    MpS(10, 330, 176, 150) { MpPlacardAc() }
    MpBezel(24, 500, 152, 152)
    MpS(40, 520, 121, 121) { MpClock(cockpit, 121, drivesElapsed = true) }
    MpTag(100, 500, 120, "CHR · ET")
    MpBezel(188, 77, 236, 236)
    MpC(306, 195, 104) { AsiFace(capt, captAirPowered, m.bugCapt) }
    MpBezel(230, 343, 162, 184)
    MpS(234, 347, 154, 176) {
        RmiFace(
            pw, failC, if (m.attCapt.hdgValid) m.attCapt.hdg else att.hdg,
            R.dmeDisplayNm(0, failC.dme1, m.dmeCh), R.dmeDisplayNm(1, failC.dme2, m.dmeCh),
            rmiBrg(ped.rmiNeedle1.first, navC, m.adfBrg1), rmiBrg(ped.rmiNeedle2.first, navF, m.adfBrg2),
        )
    }
    MpS(226, 550, 170, 26) { MpRegPlate() }

    MpS(428, 18, 250, 56) { Comparator(cockpit) }
    MpBezel(428, 84, 250, 262)
    MpS(432, 88, 242, 254) {
        AdiFace(
            powered = pw, test = pw && m.adiTestCapt, fail = failC,
            pitch = if (m.attCapt.attValid) m.attCapt.pitch else att.pitch,
            roll = if (m.attCapt.attValid) m.attCapt.roll else att.roll,
            slip = slip, fd = m.fdCmd,
            nav = adiNavOf(
                locErr = if (ilsC && navC.locValid) navC.locDev else null,
                gsErr = if (ilsC && navC.gsValid) navC.gsErr else null,
                flagLoc = ilsC && !navC.locValid, flagGs = ilsC && !navC.gsValid,
            ),
        )
    }
    DhLamp(644, 92, dhOn)
    MpS(432, 350, 46, 18) {
        PushButton("TEST", Modifier.fillMaxSize(), onPress = { cockpit.updateMip { it.copy(adiTestCapt = true) } }, onRelease = { cockpit.updateMip { it.copy(adiTestCapt = false) } })
    }
    MpBezel(433, 398, 240, 210)
    MpS(437, 402, 232, 202) {
        HsiFace(pw, failC, if (m.attCapt.hdgValid) m.attCapt.hdg else att.hdg, tas, gs, m.selCrs, m.selHdg, navC, m.navOn, m.navTuned)
    }

    MpS(690, 30, 76, 44) { MpLens("F/D\nWINDSHEAR") }
    MpS(690, 78, 76, 42) { ApproachProgressView(m.apAnn.voc, m.apAnn.gs, pw) }
    MpS(690, 124, 76, 44) { MpLens("ALTITUDE\nALERT") }
    MpBezel(774, 14, 300, 164)
    MpS(774, 14, 300, 164) { AnnPanel(cockpit, left = true) }
    MpBezel(695, 192, 180, 214)
    MpC(785, 282, 76) { HeightDial(heightFt, m.raDh, heightPowered) }
    MpDhKnob(696, 374, m.raDh, ::setDh) { down -> cockpit.updateMip { it.copy(raTest = down) } }
    MpBezel(896, 190, 184, 220)
    MpC(988, 288, 73) { AltFace(capt.altFt, captAirPowered, sim.psBaroCaptHpa, hgC) }
    MpBugKnob(210, 300, m.bugCapt) { n -> cockpit.updateMip { it.copy(bugCapt = n) } }
    MpBaroKnob(900, 382, hgC) { n -> cockpit.patchSim { psBaroCaptHpa = n * R.HPA_PER_INHG } }
    MpBezel(700, 430, 170, 170)
    MpC(785, 515, 74) { VsiFace(capt.vsiFpm, pw, m.fail.vsi) }
    MpBezel(886, 420, 104, 190)
    MpS(894, 430, 88, 26) { MpMarkerLamp(cockpit, MarkerId.IM, test, pw, annun = false, tone = AnnunTone.WHITE, text = "AIRWAYS") }
    MpS(894, 458, 88, 26) { MpMarkerLamp(cockpit, MarkerId.MM, test, pw, annun = false, text = "MIDDLE") }
    MpS(894, 486, 88, 26) { MpMarkerLamp(cockpit, MarkerId.OM, test, pw, annun = true, text = "OUTER") }
    MpTag(938, 518, 90, "MARKER")
    MpS(918, 532, 40, 74) {
        Toggle(m.markerHigh, { v -> cockpit.updateMip { it.copy(markerHigh = v) } }, upLabel = "HIGH", downLabel = "LOW")
    }
    MpS(996, 430, 94, 34) { MpLens("SPEED BRAKE\nARMED", pw && d.speedBrakeArmed, AnnunTone.GREEN) }
    MpS(996, 468, 94, 34) { MpLens("SPEED BRAKE\nDO NOT ARM", test) }
    MpS(1032, 520, 26, 26) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0C0D), RoundedCornerShape(3.dp)).border(1.dp, Color(0xFF555A60), RoundedCornerShape(3.dp)))
    }
    MpBezel(690, 618, 400, 98)
    MpS(694, 622, 392, 92) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            MpSel3("VHF NAV", m.navSel, { v -> cockpit.updateMip { it.copy(navSel = v) } }, listOf("BOTH\nON 1", "NORMAL", "BOTH\nON 2"))
            MpSel3("COMPASS", m.compSel, { v -> cockpit.updateMip { it.copy(compSel = v) } }, listOf("BOTH\nON 1", "NORMAL", "BOTH\nON 2"))
            MpSel3("VERTICAL GYRO", m.gyroSel, { v -> cockpit.updateMip { it.copy(gyroSel = v) } }, listOf("CAPT\nON AUX", "NORMAL", "F/O\nON AUX"))
        }
    }

    /* ═════════ CENTRE ═════════ */
    MpPlate(X_SB - 6, 14, 198, 690, CentreTone)
    MpS(X_SB + 4, 20, 100, 80) { LightTestKey(m.lightKey, { v -> cockpit.updateMip { it.copy(lightKey = v) } }, Modifier.fillMaxSize()) }
    MpBezel(X_SB + 20, 116, 150, 224)
    // ATA 34 standby airspeed / altimeter are pneumatic: their own pitot / static source, not blanked by main DC or CADC loss.
    MpC(X_SB + 95, 206, 70) { AltFace(air.standby.altFt, true, sim.psBaroStbyHpa, hgS) }
    MpBaroKnob(X_SB + 50, 340, hgS) { n -> cockpit.patchSim { psBaroStbyHpa = n * R.HPA_PER_INHG } }
    MpS(X_SB + 22, 298, 146, 38) { IasStrip(air.standby.iasKt, true, Modifier.fillMaxSize()) }
    MpBezel(X_SB + 20, 376, 150, 150)
    MpC(X_SB + 95, 451, 66) { StandbyAttitudeFace(att.roll, att.pitch, d.standbyAcSource == "NONE") }
    MpTag(X_SB + 95, 550, 120, "STBY ATT")
    MpS(X_SB + 4, 566, 36, 72) { Toggle(m.fuelSel, { v -> cockpit.updateMip { it.copy(fuelSel = v) } }, upLabel = "1", downLabel = "2") }
    MpS(X_SB + 44, 584, 40, 30) {
        PushButton("QTY\nTEST", Modifier.fillMaxSize(), onPress = { cockpit.updateMip { it.copy(qtyTest = true) } }, onRelease = { cockpit.updateMip { it.copy(qtyTest = false) } })
    }
    MpS(X_SB + 92, 560, 96, 58) { QtyLed("CTR TANK", sim.fuelTankCenter, pw, m.qtyTest, 96.dp, 58.dp) }
    MpS(X_SB + 2, 626, 92, 58) { QtyLed("TANK 1", sim.fuelTank1, pw, m.qtyTest, 92.dp, 58.dp) }
    MpS(X_SB + 98, 626, 92, 58) { QtyLed("TANK 2", sim.fuelTank2, pw, m.qtyTest, 92.dp, 58.dp) }
    MainPanelEngines(cockpit)

    /* ═════════ FIRST OFFICER ═════════ */
    MpS(F0 + 690, 14, 252, 46) { TopStrip(cockpit, left = false) }
    MpBezel(F0 + 10, 14, 292, 164)
    MpS(F0 + 10, 14, 292, 164) { AnnPanel(cockpit, left = false) }
    MpS(F0 + 308, 14, 76, 36) { MpLens("F/D\nWINDSHEAR") }
    MpS(F0 + 308, 52, 76, 36) { MpLens("") }
    MpS(F0 + 308, 92, 76, 36) { MpLens("GPWS") }
    MpS(F0 + 308, 132, 76, 36) { MpLens("BELOW G/S\nP INHIBIT") }
    MpS(F0 + 396, 18, 250, 56) { Comparator(cockpit) }
    MpBezel(F0 + 10, 190, 146, 170)
    MpS(F0 + 18, 198, 130, 120) { TatDial(pw, R.tatText(pw, tatC), m.fail.tat) }
    MpBezel(F0 + 156, 197, 236, 236)
    MpC(F0 + 274, 315, 104) { AsiFace(fo, foAirPowered, m.bugFo) }
    MpBezel(F0 + 198, 456, 162, 184)
    MpS(F0 + 202, 460, 154, 176) {
        RmiFace(
            pw, failF, if (m.attFo.hdgValid) m.attFo.hdg else att.hdg,
            R.dmeDisplayNm(0, failF.dme1, m.dmeCh), R.dmeDisplayNm(1, failF.dme2, m.dmeCh),
            // as in the web, the first-officer pointer 1 tests the captain-side VOR (navC) and pointer 2 the first-officer side (navF)
            rmiBrg(ped.rmiNeedle1.second, navC, m.adfBrg1), rmiBrg(ped.rmiNeedle2.second, navF, m.adfBrg2),
        )
    }
    MpS(F0 + 14, 440, 110, 22) { MpLens("LOW QUANTITY") }
    MpBezel(F0 + 10, 468, 146, 150)
    MpC(F0 + 83, 543, 58) { FoHydQtyDial(sim.hydAQty) }
    MpBezel(F0 + 396, 84, 250, 262)
    MpS(F0 + 400, 88, 242, 254) {
        AdiFace(
            powered = pw, test = pw && m.adiTestFo, fail = failF,
            pitch = if (m.attFo.attValid) m.attFo.pitch else att.pitch,
            roll = if (m.attFo.attValid) m.attFo.roll else att.roll,
            slip = slip, fd = m.fdCmd,
            nav = adiNavOf(
                locErr = if (m.navOn && navF.locValid) navF.locDev else null,
                gsErr = if (m.navOn && navF.gsValid) navF.gsErr else null,
                flagLoc = m.navOn && !navF.locValid && !navF.vorValid, flagGs = m.navOn && !navF.gsValid,
            ),
        )
    }
    DhLamp(F0 + 612, 92, dhOn)
    MpS(F0 + 400, 350, 46, 18) {
        PushButton("TEST", Modifier.fillMaxSize(), onPress = { cockpit.updateMip { it.copy(adiTestFo = true) } }, onRelease = { cockpit.updateMip { it.copy(adiTestFo = false) } })
    }
    MpBezel(F0 + 401, 398, 240, 210)
    MpS(F0 + 405, 402, 232, 202) {
        HsiFace(
            pw, failF, if (m.attFo.hdgValid) m.attFo.hdg else att.hdg, tas, gs, m.selCrs2, m.selHdg, navF, m.navOn,
            if (navF.locValid || !navF.vorValid) "ILS" else "VOR",
        )
    }
    MpS(F0 + 440, 640, 170, 26) { MpRegPlate() }

    MpS(F0 + 666, 64, 130, 32) { ApproachProgressView(m.apAnn.voc, m.apAnn.gs, pw) }
    MpS(F0 + 666, 100, 130, 26) { MpLens("ALTITUDE ALERT") }
    MpBezel(F0 + 650, 132, 162, 196)
    MpC(F0 + 731, 228, 76) { HeightDial(heightFt, m.raDh, heightPowered) }
    MpBezel(F0 + 816, 145, 156, 170)
    MpC(F0 + 894, 230, 74) { VsiFace(fo.vsiFpm, pw, m.fail.vsi) }
    MpBezel(F0 + 650, 339, 162, 190)
    MpC(F0 + 731, 434, 73) { AltFace(fo.altFt, foAirPowered, sim.psBaroFoHpa, hgF) }
    MpBugKnob(F0 + 180, 418, m.bugFo) { n -> cockpit.updateMip { it.copy(bugFo = n) } }
    MpBaroKnob(F0 + 656, 500, hgF) { n -> cockpit.patchSim { psBaroFoHpa = n * R.HPA_PER_INHG } }
    MpS(F0 + 842, 336, 112, 150) { MpPlacardAc(compact = true) }
    MpBezel(F0 + 816, 540, 152, 152)
    MpS(F0 + 832, 560, 121, 121) { MpClock(cockpit, 121, drivesElapsed = false) }
    MpTag(F0 + 892, 542, 110, "GMT · ET/CHR")

    /* ═════════ LIGHTING: panel dimmers darken each zone, flood knobs add a warm wash from the glareshield ═════════ */
    MpLightZone(0, 1094, PANEL_CH, R.lightOverlay(pw, sim.ltPanelCapt, m.floodCapt))
    MpLightZone(1094, 966, PANEL_CH, R.lightOverlay(pw, sim.ltPanelPed, 0.0))
    MpLightZone(2060, PANEL_CW - 2060, PANEL_CH, R.lightOverlay(pw, sim.ltPanelFo, m.floodFo))
}

/** web AdiReal adapter: this panel's receiver outputs (null = no signal) onto the face's ILS data. */
private fun adiNavOf(locErr: Double?, gsErr: Double?, flagLoc: Boolean, flagGs: Boolean) =
    adiNavFrom(locErr != null || gsErr != null || flagLoc || flagGs, locErr, gsErr)

/** web dhLamp: the amber decision-height lamp. */
@Composable
private fun DhLamp(x: Int, y: Int, on: Boolean) {
    MpS(x, y, 20, 20) {
        Box(
            Modifier.fillMaxSize()
                .background(if (on) Color(0xFFFFB21A) else Color(0xFFB0701C), CircleShape)
                .border(2.dp, Color(0xFF3A2A12), CircleShape),
        )
    }
}

/** web Comparator: INSTRUMENT COMPARATOR lamps, lit together by the lamp test. */
@Composable
private fun Comparator(cockpit: CockpitModel) {
    val sim by cockpit.simState.collectAsState()
    val d by cockpit.derived.collectAsState()
    val m by cockpit.mipState.collectAsState()
    ComparatorPlate(com.oersted.trainer.sim.LightTestLogic.comparatorLit(d.dcPower, sim.ltTest, m.compTest, m.lightKey), Modifier.fillMaxSize())
}

/** web F/O TAT display: a TAT window in a round dial, OFF flag when unpowered or failed. */
@Composable
private fun TatDial(pw: Boolean, text: String, failTat: Boolean) {
    Box(
        Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF2A2E33)).border(6.dp, Color(0xFF6A6E72), CircleShape),
    ) {
        // window: inset-x 10%, top 30%, height 38% of the 118 x 108 content box
        Box(
            Modifier.offset(12.dp, 32.dp).size(94.dp, 41.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF3B9FD0)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TAT", color = Color(0xFF06293A), fontSize = 7.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text(text, color = Color(0xFF06293A), fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            if (!pw || failTat) {
                Box(Modifier.fillMaxSize().background(Color(0xFFE8C13A)), contentAlignment = Alignment.Center) {
                    Text("OFF", color = Color(0xFF4A3300), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** web F/O HYD QTY dial: 0-100, red band below 20. */
@Composable
private fun FoHydQtyDial(value: Double) {
    RoundDial(
        "HYD QTY", value, 0.0, 100.0, 25.0, minor = 5, size = 92.dp, caption = "",
        bands = listOf(DialBand(0.0, 20.0, PanelRed)),
    )
}
