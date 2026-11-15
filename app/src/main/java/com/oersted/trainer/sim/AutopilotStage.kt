package com.oersted.trainer.sim

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI

/**
 * Second stage of the web useAttitude tick (MainInstrumentPanel.tsx ~895–1063): interlocks and autopilot disconnects,
 * the NAV knob and HDG switch, the lateral and glide submodes, approach lights, markers, the ALT HOLD release, the
 * FD glide capture, the autopilot references, the PLAN NAV guidance and ILS handoff, and the inputs for the attitude
 * targets and the flight director (both already ported in AttitudeTargets and FlightDirector / AttitudeHook).
 *
 * Pure. The caller applies [Output.mip] as the MIP state, keeps [Output.state] for the next tick, writes the pedestal
 * tune when [Output.nav1Tune] / [Output.navRAct] are set, publishes [Output.situation] when present, and then runs
 * AttitudeHook.tick with [Output.targets] and [Output.fdTemplate]. Not verified against the web yet.
 */
object AutopilotStage {
    /** Web useAttitude refs (the `r` ref fields this stage uses). */
    data class State(
        val gamma: Double = 0.0,
        val latSub: AutoFlight.LatSub = AutoFlight.LatSub.OFF,
        val osHdg: Double = 0.0,
        val gsPhaseAp: AutoFlight.GsPhase = AutoFlight.GsPhase.OFF,
        val gsCapT: Double = 0.0,
        val gsCapAp: Boolean = false,
        val gsCapFd: Boolean = false,
        val wasCwsR: Boolean = true,
        val wasCwsP: Boolean = true,
        val refRoll: Double = 0.0,
        val refHdg: Double = 0.0,
        val hdgHold: Boolean = false,
        val refPitch: Double = 0.0,
        val altAp: Double = 0.0,
        val altFd: Double = 0.0,
        val prevAltSel: Boolean = false,
        val prevFdAlt: Boolean = false,
        val vocLight: AutoFlight.Light = AutoFlight.Light.OFF,
        val gsLight: AutoFlight.Light = AutoFlight.Light.OFF,
    )

    data class Inputs(
        val dt: Double,
        /** web ias / vs / alt (useAttitude arguments) */
        val ias: Double,
        val vs: Double,
        val alt: Double,
        val tas: Double,
        val simAltFt: Double,
        /** web g.yokePitch / g.yokeRoll (clamped to -1..1 here) */
        val yokePitch: Double,
        val yokeRoll: Double,
        val wow: Boolean,
        val power: Boolean,
        /** hydraulic system B pressure good (web: hb, the boolean prop) */
        val hydBOk: Boolean,
        /** trainer flight state active (flightStateActive) and the progressive altitude profile */
        val trainer: Boolean,
        val psFlightProgressive: Boolean,
        val tgtAltFt: Double,
        /** current attitude (web cur.current) */
        val pitch: Double,
        val roll: Double,
        val hdg: Double,
        val m: MipLocal.MipState,
        val ped: Pedestal.PedestalState,
        val radio: RadioStage.Output,
        val state: State,
        val guide: GuidanceResult,
        val liveArr: ActiveArrival?,
        val sceneId: RadioNav.NavSceneId,
    )

    data class Output(
        val state: State,
        val mip: MipLocal.MipState,
        val targets: AttitudeTargets.Inputs,
        val fdTemplate: FlightDirector.Inputs,
        /** true when the aircraft is in the dynamic (trainer) vertical path */
        val dyn: Boolean,
        /** the situation sample to publish (RPLL06 only, as the web) */
        val situation: Situation.SituationSample?,
        /** pedestal NAV 1 / NAV 2 writes from the ILS handoff, null when none this tick */
        val nav1Tune: NavReceiver.Nav1Tune?,
        val navRAct: Int?,
    )

    fun trueAirspeed(ias: Double, altFt: Double): Double = ias * (1 + (altFt / 1000) * 0.016)

    private fun clamp(v: Double, lo: Double, hi: Double): Double = max(lo, min(hi, v))

    /** web kindOf: the NAV knob's VOR or LOC kind, VOR only on NAV 1 with a VOR tuned. */
    private fun kindOf(n: Int, captKind: String): String = if (n == 1 && captKind == "VOR") "VOR" else "LOC"

    fun tick(p: Inputs): Output {
        val m = p.m
        val r = p.radio
        val nv = r.nv
        val nvFo = r.nvFo
        val dt = p.dt
        val s0 = p.state
        val yp = clamp(p.yokePitch, -1.0, 1.0)
        val yr = clamp(p.yokeRoll, -1.0, 1.0)
        val flying = !p.wow && p.ias > 60
        val tas = p.tas
        val aoa = clamp(3 + (250 - p.ias) / 20, 1.5, 12.0)
        val gammaVs = asin(clamp(p.vs / 60 / max(tas * 1.688, 60.0), -0.3, 0.3)) * 180 / PI
        val profile = p.trainer && p.psFlightProgressive && abs(p.tgtAltFt - p.simAltFt) > 1
        val dyn = flying && p.trainer && !profile
        // web: when not dynamic the flight-path angle follows the VSI-derived angle
        val gamma = if (!dyn) gammaVs else s0.gamma

        val captKind = r.captKind
        val attBad = r.attBad
        val hdgBad = r.hdgBad
        val plantIl = AutoFlight.afInterlocks(
            AutoFlight.AfPlant(pw = p.power, hydB = p.hydBOk, attOk = !attBad, hdgOk = !hdgBad),
        )
        val il = plantIl

        var mip = m
        var st = s0.copy(gamma = gamma)

        // Disconnects: an engaged AIL / ELEV without its interlocks releases, with the reason on the warning.
        if ((m.apAil || m.apElev) && !il) {
            mip = mip.copy(apWarn = true, apWhy = "A/P disconnect: ${AutoFlight.afDisconnectReason(AutoFlight.AfPlant(p.power, p.hydBOk, !attBad, !hdgBad))}")
        }
        if (m.apAil && !il) mip = mip.copy(apAil = false)
        if (m.apElev && !il) mip = mip.copy(apElev = false)

        val altBad = m.fail.ht
        val ga = AutoFlight.goAroundSelected(m.fdMode, flying)
        val ailOn = m.apAil && il
        val elevOn = m.apElev && il
        val turb = elevOn && m.apPitch == 0
        val cwsR = abs(yr) > 0.08
        val cwsP = abs(yp) > 0.08
        fun beamOk(n: Int): Boolean = when (n) {
            1 -> if (captKind == "VOR") nv.vorValid else nv.locValid
            2 -> nv.locValid
            3 -> nv.locValid && nv.gsValid
            else -> true
        }
        // NAV knob is spring-loaded to MAN and the solenoid is held only with a valid beam (AMM 22-11-0).
        val navHeld = m.apNav != 0 && ailOn && !ga && beamOk(m.apNav) &&
            !(turb && kindOf(m.apNav, captKind) == "LOC") && (m.apNav < 2 || (elevOn && !cwsP))
        if (m.apNav != 0 && !navHeld) mip = mip.copy(apNav = 0)
        if ((ailOn && cwsR && m.apHdgSw == 2) || (navHeld && m.apHdgSw == 2) || (!ailOn && m.apHdgSw != 1)) {
            mip = mip.copy(apHdgSw = 1) // HDG SEL / HDG OFF are solenoid held
        }
        val fdUp = p.power && !m.fail.fd && !attBad
        if (m.fdAlt && (!fdUp || m.fdMode == 1)) mip = mip.copy(fdAlt = false) // FD ALT HOLD toggle is solenoid held

        // Lateral submode (intercept, capture, on course, over station).
        val navKind = kindOf(m.apNav, captKind)
        val vorD = RadioNav.vorDevOf(nv.radial, m.selCrs)
        val crsErr = AutoFlight.angDiff(m.selCrs, p.hdg)
        val prevSub = s0.latSub
        val latSub = AutoFlight.stepLateral(
            prevSub,
            AutoFlight.LatIn(
                active = navHeld,
                kind = navKind,
                dev = if (navKind == "VOR") vorD.dev else nv.locDev,
                bank = p.roll,
                crsErr = crsErr,
                overStation = navKind == "VOR" && nv.vorValid && AutoFlight.inZoneOfConfusion(nv.vorDist, p.simAltFt),
            ),
        )
        var osHdg = s0.osHdg
        if (latSub == AutoFlight.LatSub.OVER_STATION && prevSub != AutoFlight.LatSub.OVER_STATION) osHdg = p.hdg
        val locCap = latSub == AutoFlight.LatSub.CAPTURE || latSub == AutoFlight.LatSub.ON_COURSE || latSub == AutoFlight.LatSub.OVER_STATION

        // Glide slope submode: armed, then capture after the localizer, then track.
        val gsWantAp = navHeld && elevOn && (m.apNav == 2 || m.apNav == 3) && nv.gsValid
        val gsStep = AutoFlight.stepGlide(
            s0.gsPhaseAp,
            s0.gsCapT,
            AutoFlight.GsIn(want = gsWantAp, manual = m.apNav == 3, locCaptured = locCap, gsErr = nv.gsErr),
            dt,
        )
        val gsPhaseAp = gsStep.phase
        val gsCapT = gsStep.capT
        val wasGsCap = s0.gsCapAp
        val gsCapAp = gsPhaseAp == AutoFlight.GsPhase.CAPTURE || gsPhaseAp == AutoFlight.GsPhase.TRACK
        if (gsCapAp && !wasGsCap && m.apPitch == 2) mip = mip.copy(apPitch = 1)

        // Approach progress lamps.
        val vocLight = AutoFlight.nextLight(
            s0.vocLight,
            navHeld && (m.apNav == 1 || m.apNav == 2),
            latSub != AutoFlight.LatSub.OFF && latSub != AutoFlight.LatSub.INTERCEPT,
        )
        val gsLight = AutoFlight.nextLight(s0.gsLight, navHeld && (m.apNav == 2 || m.apNav == 3), gsCapAp)
        if (m.apAnn.voc != vocLight || m.apAnn.gs != gsLight) {
            mip = mip.copy(apAnn = MipLocal.ApAnnunciators(voc = vocLight, gs = gsLight))
        }

        // ILS marker beacons: received only with the NAV receiver on, an ILS tuned and the aircraft flying.
        val markers = if (m.navOn && m.navTuned == "ILS" && flying) {
            MarkerBeacons.markerCoverage(p.sceneId, MarkerBeacons.Pos(r.pos.x, r.pos.y), p.simAltFt, m.markerHigh)
        } else MarkerBeacons.NO_MARKERS
        mip = mip.copy(markers = markers)

        val situation = if (p.sceneId == RadioNav.NavSceneId.RPLL06) {
            Situation.SituationSample(
                x = r.pos.x,
                y = r.pos.y,
                hdg = p.hdg,
                altFt = p.simAltFt,
                tasKt = tas,
                flying = flying,
                latSub = latSub,
                gsPhase = gsPhaseAp,
                gsCapT = gsCapT,
            )
        } else null

        // ALT HOLD is solenoid held: it releases on glide capture, source failure or go-around.
        if (m.apPitch == 2 && (!elevOn || gsCapAp || altBad || ga)) {
            mip = mip.copy(apPitch = 1)
            if (elevOn && altBad) mip = mip.copy(apWhy = "ALT HOLD released: altitude source failed")
            else if (elevOn && ga) mip = mip.copy(apWhy = "ALT HOLD released: go-around selected")
        }
        if (m.apNav != 0 && ga && ailOn) mip = mip.copy(apWhy = "NAV returned to MAN: go-around selected")

        // FD glide capture.
        val gsWantFd = fdUp && (m.fdMode == 4 || m.fdMode == 5) && nv.gsValid
        var gsCapFd = s0.gsCapFd
        if (!gsWantFd) {
            gsCapFd = false
        } else if (!gsCapFd && (m.fdMode == 5 || abs(nv.gsErr) < 0.25)) {
            gsCapFd = true
            if (m.fdAlt) mip = mip.copy(fdAlt = false)
        }

        // Autopilot references held while the yoke is out of detent.
        var wasCwsR = s0.wasCwsR
        var refRoll = s0.refRoll
        var refHdg = s0.refHdg
        var hdgHold = s0.hdgHold
        if (!ailOn || cwsR) {
            wasCwsR = true
            refRoll = p.roll
            refHdg = p.hdg
        } else if (wasCwsR) {
            wasCwsR = false
            hdgHold = abs(p.roll) < 6 // roll attitude hold unless the wings are about level
            refHdg = p.hdg
            refRoll = p.roll
        }
        var wasCwsP = s0.wasCwsP
        var refPitch = s0.refPitch
        if (!elevOn || cwsP) {
            wasCwsP = true
            refPitch = p.pitch
        } else {
            wasCwsP = false
        }

        // PLAN NAV guidance (trainer aid): heading from the plan while AIL is engaged and HDG SEL is not selected;
        // altitude target while ELEV is engaged with the pitch switch OFF.
        val guide = p.guide
        val planLat = m.planNav && guide.active && guide.headingMagCmdDeg != null && ailOn && !cwsR && !navHeld &&
            m.apHdgSw != 2 && flying
        val planVert = m.planNav && guide.active && guide.targetAltFt != null && elevOn && !cwsP && m.apPitch == 1 &&
            !gsCapAp && !altBad && !ga && flying
        val altSel = (elevOn && m.apPitch == 2 && !gsCapAp && !altBad && !ga) || planVert
        var altAp = s0.altAp
        if (planVert) altAp = guide.targetAltFt ?: altAp
        else if (altSel && !s0.prevAltSel) altAp = p.alt

        // ILS handoff: tune NAV 1 / NAV 2 to the arrival's ILS, set the course and select AUTO APP once the localizer is valid.
        val handoff = IlsHandoff.evaluate(
            IlsHandoff.Inputs(
                planNav = m.planNav,
                guideActive = guide.active,
                ilsArm = guide.ilsArm,
                sceneIsWsss02L = p.sceneId == RadioNav.NavSceneId.WSSS02L,
                hasLiveArrival = p.liveArr != null,
                ilsFreqMHz = p.liveArr?.ilsFrequencyMHz ?: 0.0,
                ilsCourseMag = p.liveArr?.courseMag ?: 0.0,
                navOn = m.navOn,
                captFreqMHz = r.captRx.frequencyMHz,
                selCrs = m.selCrs,
                navLStby = p.ped.navL.stby,
                navRStby = p.ped.navR.stby,
                locValid = nv.locValid,
                fdMode = m.fdMode,
                apNav = m.apNav,
                ailOn = ailOn,
                elevOn = elevOn,
                cwsR = cwsR,
                ga = ga,
            ),
        )
        if (handoff.handoff) {
            handoff.navOn?.let { mip = mip.copy(navOn = it) }
            handoff.selCrs?.let { mip = mip.copy(selCrs = it) }
            handoff.fdMode?.let { mip = mip.copy(fdMode = it) }
            handoff.apNav?.let { mip = mip.copy(apNav = it) }
            handoff.nav1Tune?.let { t ->
                mip = mip.copy(navFrequencyMHz = t.mipFrequencyMHz)
                t.mipNavTuned?.let { mip = mip.copy(navTuned = it) }
            }
        }

        val planStatus = when {
            !m.planNav -> "OFF"
            !guide.active -> "NO PLAN"
            !ailOn -> "ARMED - ENGAGE AIL"
            navHeld -> "ILS"
            m.apHdgSw == 2 -> "HDG SEL OVERRIDES"
            planLat -> "ACTIVE ${guide.note}"
            else -> "ARMED"
        }
        if (m.planNavStatus != planStatus) mip = mip.copy(planNavStatus = planStatus)

        val prevAltSel = altSel
        var altFd = s0.altFd
        val prevFdAlt = m.fdAlt
        if (m.fdAlt && !s0.prevFdAlt) altFd = p.alt

        // Lateral law output for the attitude targets (only read when the NAV switch holds).
        var lateralBank = 0.0
        var lateralLim = 0.0
        var lateralRate = 0.0
        if (navHeld) {
            val law = AutoFlight.lateralLaw(
                latSub,
                navKind,
                if (navKind == "VOR") vorD.dev else nv.locDev,
                m.selCrs,
                p.hdg,
                osHdg,
            )
            lateralBank = law.bank
            lateralLim = law.lim
            lateralRate = law.rate
        }

        val capturedGamma = when {
            gsCapAp -> AutoFlight.glideGamma(gsPhaseAp, nv.gsErr, tas, gsCapT, p.simAltFt)
            altSel -> AutoFlight.altAcquire(altAp - p.alt, tas).gamma
            else -> 0.0
        }
        val planHdgErr = guide.headingMagCmdDeg?.let { AutoFlight.angDiff(it, p.hdg) } ?: 0.0

        val targets = AttitudeTargets.Inputs(
            flying = flying,
            wow = p.wow,
            ias = p.ias,
            turb = turb,
            yokePitch = yp,
            yokeRoll = yr,
            gamma = gamma,
            aoa = aoa,
            ailOn = ailOn,
            cwsR = cwsR,
            navHeld = navHeld,
            lateralBank = lateralBank,
            lateralLim = lateralLim,
            lateralRate = lateralRate,
            planLat = planLat,
            planHdgErr = planHdgErr,
            apHdgSw = m.apHdgSw,
            selHdgErr = AutoFlight.angDiff(m.selHdg, p.hdg),
            hdgHold = hdgHold,
            refHdgErr = AutoFlight.angDiff(refHdg, p.hdg),
            refRoll = refRoll,
            dyn = dyn,
            elevOn = elevOn,
            cwsP = cwsP,
            gsCapAp = gsCapAp,
            altSel = altSel,
            refPitch = refPitch,
            capturedGamma = capturedGamma,
        )

        // Flight director template: AttitudeHook replaces roll, pitch, heading and altitude with the integrated values.
        val kindFd = if (m.fdMode == 3 && captKind == "VOR") "VOR" else "LOC"
        val fdTemplate = FlightDirector.Inputs(
            power = p.power,
            fdMode = m.fdMode,
            fdUp = fdUp,
            hdgBad = hdgBad,
            vorValid = nv.vorValid,
            locValid = nv.locValid,
            captIsVor = captKind == "VOR",
            lateralDev = if (kindFd == "VOR") vorD.dev else nv.locDev,
            selCrs = m.selCrs,
            selHdg = m.selHdg,
            hdg = p.hdg,
            roll = p.roll,
            pitch = p.pitch,
            aoa = aoa,
            flying = flying,
            altFt = p.simAltFt,
            gsCapFd = gsCapFd,
            gsErr = nv.gsErr,
            altFd = altFd,
            fdAlt = m.fdAlt,
            fdPitch = m.fdPitch,
        )

        // Radio-side MIP values from the first stage, applied the way the web merged them.
        mip = mip.copy(
            nav = nv,
            navFo = nvFo,
            dmeCh = MipLocal.DmeChannels(ch1 = r.dme1, ch2 = r.dme2),
            lrra = MipLocal.LrraStatus(
                h1 = r.lrra.ch1.heightFt,
                h2 = r.lrra.ch2.heightFt,
                dh1 = r.lrra.ch1.dhLight,
                dh2 = r.lrra.ch2.dhLight,
            ),
            hfCh = MipLocal.HfChannels(
                ch1 = MipLocal.HfTuneChannel(tuning = r.hf1.tuning, usability = r.hf1.usability),
                ch2 = MipLocal.HfTuneChannel(tuning = r.hf2.tuning, usability = r.hf2.usability),
            ),
            navScene = p.sceneId,
            attCapt = MipLocal.AttitudeSide(
                pitch = r.sideAtt.first.pitch,
                roll = r.sideAtt.first.roll,
                hdg = r.sideAtt.first.hdg,
                attValid = r.sideAtt.first.attValid,
                hdgValid = r.sideAtt.first.hdgValid,
            ),
            attFo = MipLocal.AttitudeSide(
                pitch = r.sideAtt.second.pitch,
                roll = r.sideAtt.second.roll,
                hdg = r.sideAtt.second.hdg,
                attValid = r.sideAtt.second.attValid,
                hdgValid = r.sideAtt.second.hdgValid,
            ),
            gyroMem = if (r.inst.gyroMem != m.gyroMem) r.inst.gyroMem else m.gyroMem,
        )
        if (r.synced.changed) {
            mip = mip.copy(navFrequencyMHz = r.synced.frequencyMHz)
            r.synced.kind?.let { mip = mip.copy(navTuned = it) }
        }
        if (!brgClose(m.adfBrg1, r.adf.brg1) || !brgClose(m.adfBrg2, r.adf.brg2)) {
            mip = mip.copy(adfBrg1 = r.adf.brg1, adfBrg2 = r.adf.brg2)
        }

        st = st.copy(
            gamma = gamma,
            latSub = latSub,
            osHdg = osHdg,
            gsPhaseAp = gsPhaseAp,
            gsCapT = gsCapT,
            gsCapAp = gsCapAp,
            gsCapFd = gsCapFd,
            wasCwsR = wasCwsR,
            wasCwsP = wasCwsP,
            refRoll = refRoll,
            refHdg = refHdg,
            hdgHold = hdgHold,
            refPitch = refPitch,
            altAp = altAp,
            altFd = altFd,
            prevAltSel = prevAltSel,
            prevFdAlt = prevFdAlt,
            vocLight = vocLight,
            gsLight = gsLight,
        )

        val handoffNav1 = if (handoff.handoff) handoff.nav1Tune else null
        val handoffNavR = if (handoff.handoff) handoff.navRAct else null
        return Output(
            state = st,
            mip = mip,
            targets = targets,
            fdTemplate = fdTemplate,
            dyn = dyn,
            situation = situation,
            nav1Tune = handoffNav1,
            navRAct = handoffNavR,
        )
    }

    /** web brgClose: equal, or within half a degree. */
    private fun brgClose(a: Double?, b: Double?): Boolean =
        a == b || (a != null && b != null && abs(a - b) < 0.5)
}
