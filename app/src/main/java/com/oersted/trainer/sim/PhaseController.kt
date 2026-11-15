package com.oersted.trainer.sim

import com.oersted.trainer.sim.FlightPhaseControl.AutoContext
import com.oersted.trainer.sim.FlightPhases.Phase
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Port of the stateful flight-phase controller in flightPhases.ts: the phase store (usePhase), loadPlanIntoControls,
 * parkAtDeparture, controllerTick (ground roll, lift-off, airborne phases, auto phases), mirrorToControls, touchdown,
 * rollout, canStart, startPhase and resetFlightPhases.
 *
 * The pure rules live in FlightPhaseControl, FlightPhases and FlightPhasePresets. This class only sequences them and
 * talks to the sim, MIP and pedestal holders through [PhaseWorld]. The web code patches those stores by string key;
 * the same keys are used here (dotted keys like "att.hdg" are read with [path]).
 *
 * Timing: the web code runs [tick] every 200 ms with real elapsed time. The platform layer must call [tick] at that
 * rate while [running] is true, passing the time in seconds.
 */
class PhaseController(private val world: PhaseWorld) {

    /** The phase store (usePhase). */
    data class Checklist(val label: String, val value: String)
    data class LogEntry(val t: Long, val text: String)
    data class LatLon(val lat: Double, val lon: Double)

    var phase: Phase = Phase.NO_PLAN
        private set
    var since: Long = 0L
        private set
    var checklist: List<Checklist> = emptyList()
        private set
    var log: List<LogEntry> = emptyList()
        private set
    var hint: String = "Activate a flight plan to load it into the controls."
        private set
    var trail: List<LatLon> = emptyList()
        private set
    /** AUTO PHASES: TAKEOFF -> CLIMB -> CRUISE -> DESCENT -> APPROACH -> LAND advance by themselves. */
    var auto: Boolean = true
        private set

    /** True between ensureFlightPhaseController and stopFlightPhaseController (the platform timer should run). */
    var running: Boolean = false
        private set

    // controller state (module-level variables in the web code)
    private var rollGs = 0.0
    private var rollT = 0.0
    private var lastTick = 0.0
    private var trailT = 0.0
    private var gearDownDone = false
    private var climbThrustCalled = false
    private var vsCmd = 0.0
    private var altHoldSetByProgram = false
    private var ilsTunedFor = ""

    fun setAutoPhases(on: Boolean) {
        auto = on
    }

    fun ensureRunning() {
        if (running) return
        lastTick = world.perfSec()
        running = true
    }

    fun stopRunning() {
        running = false
    }

    private fun say(text: String) {
        log = (log + LogEntry(world.nowMs(), text)).takeLast(30)
    }

    private fun setPhase(p: Phase, text: String? = null) {
        phase = p
        since = world.nowMs()
        if (text != null) say(text)
    }

    private fun autoHint() {
        if (!auto) return
        val h = hint.replace(Regex("[Pp]ress (CLIMB|CRUISE|DESCENT|APPROACH|LAND)( when clean)?\\.?")) { m ->
            "${m.groupValues[1]} starts automatically."
        }
        if (h != hint) hint = h
    }

    // ───────── plan to controls ─────────

    /** Load the active flight plan into the cockpit controls and move to READY. False when there is no active plan. */
    fun loadPlanIntoControls(): Boolean {
        val plan = world.activePlan()
        if (plan == null) {
            say("No active flight plan. Activate one on the Flight Plan page first.")
            return false
        }
        world.resetNavigation(plan)
        rollGs = 0.0; rollT = 0.0; vsCmd = 0.0; gearDownDone = false
        val rwyHdg = FlightRoute.departureRunwayHeadingMag(plan)
        val mip = world.mipLive()
        val elev = fieldElev(plan.departure)
        val items = ArrayList<Checklist>()

        world.patchSim(FlightPhasePresets.groundReadyPatch(elev))
        items += Checklist("Gear · flaps · speedbrake", "DOWN · 5 · ARMED")
        items += Checklist("Anti-skid · autobrake",
            "INBD ON · OUTBD ON · RTO (arms above 90 kt: needs DC power and hydraulic A/B above 1,500 psi)")
        val perf0 = climbPerformance(world.simLive())
        items += Checklist("Climb performance (trainer)",
            "about ${fmtInt(perf0.weightLb)} lb · ISA+${fmtInt(FlightPhases.TRAINER_ISA_DEV_C)} · climb rate ×${fmtFixed(perf0.factor, 2)}")

        // PLAN NAV is ARMED on load but cannot steer or move anything until TAKEOFF.
        val mipPatch = FlightPhasePresets.planNavArmPatch(
            navResetN = num(path(mip, "navResetN")).toInt(),
            attHdgReqN = num(path(mip, "attHdgReq.n")).toInt(),
            rwyHdgMag = rwyHdg,
        ).toMutableMap()
        mipPatch["fdMode"] = 2.0
        mipPatch["xpdrMode"] = 2.0
        mipPatch["apWarn"] = false
        mipPatch["apWhy"] = ""
        if (rwyHdg != null) {
            val h = FlightPhaseControl.jsRound(rwyHdg).toInt()
            items += Checklist("RWY ${plan.departureRunway} heading / course",
                "${h.toString().padStart(3, '0')}° M (HDG SEL bug and course set)")
        } else {
            items += Checklist("Runway heading", "RWY ${plan.departureRunway} not in nav data - set heading manually")
        }
        world.patchMip(mipPatch)
        items += Checklist("PLAN NAV", "ARMED - no movement until TAKEOFF")
        items += Checklist("Flight director", "HDG")
        items += Checklist("Transponder", "ALT ON")
        items += Checklist("Cruise altitude / speed",
            "${fmtInt(plan.cruiseAltitudeFt)} ft · ${jsNum(plan.cruiseSpeedKt)} kt")
        items += Checklist("Takeoff speeds (trainer)",
            "V1 ${jsNum(FlightPhases.V1_KT)} · VR ${jsNum(FlightPhases.VR_KT)} · V2 ${jsNum(FlightPhases.V2_KT)}")
        val ils = plan.ils
        if (ils != null && plan.arrival == null) {
            items += Checklist("ILS ${ils.ident} ${ils.airport} RWY ${ils.runway}",
                "${fmtFixed(ils.frequencyMHz, 2)} MHz · course ${pad3(FlightPhaseControl.jsRound(ils.courseMag))}° - selected in the Flight Plan; the Navigation map tunes it as the last radio target")
        }
        val arrival = plan.arrival
        if (arrival != null) {
            items += Checklist("ILS ${arrival.airport} RWY ${arrival.runway}",
                "${fmtFixed(arrival.ilsFrequencyMHz, 2)} MHz · course ${pad3(FlightPhaseControl.jsRound(arrival.courseMag))}° - tuned automatically on APPROACH")
        }
        val nWp = plan.waypoints.size
        items += Checklist("Route",
            "${plan.departure} → $nWp waypoint${if (nWp == 1) "" else "s"} → ${plan.destination}")

        checklist = items
        trail = emptyList()
        hint = "Flight plan loaded. Engine/APU state unchanged; start and configure the aircraft manually. Press TAKEOFF only when ready to depart."
        val name = if (plan.callsign.isNotEmpty()) plan.callsign else "${plan.departure}-${plan.destination}"
        setPhase(Phase.READY, "Plan $name loaded into controls${if (elev != 0.0) " (field elevation ${jsNum(elev)} ft)" else ""}.")
        ensureRunning()
        return true
    }

    /** Ready for departure: READY phase and the aircraft back at the threshold, without touching the systems preset. */
    fun parkAtDeparture() {
        rollGs = 0.0; rollT = 0.0; vsCmd = 0.0; gearDownDone = false
        val plan = world.activePlan()
        val elev = if (plan != null) fieldElev(plan.departure) else 0.0
        world.patchSim(FlightPhasePresets.parkSimPatch(elev))
        if (plan == null || phase == Phase.NO_PLAN) return

        world.resetNavigation(plan)
        val mip = world.mipLive()
        val rwyHdg = FlightRoute.departureRunwayHeadingMag(plan)
        world.patchMip(FlightPhasePresets.planNavArmPatch(
            navResetN = num(path(mip, "navResetN")).toInt(),
            attHdgReqN = num(path(mip, "attHdgReq.n")).toInt(),
            rwyHdgMag = rwyHdg,
        ))
        trail = emptyList()
        hint = "Aircraft parked and lined up. PLAN NAV armed. Press TAKEOFF to start moving."
        setPhase(Phase.READY, "Ready for departure - parked, waiting for TAKEOFF.")
    }

    // ───────── controller tick ─────────

    /** One controller tick (web controllerTick). [nowSec] is the platform clock in seconds. */
    fun tick(nowSec: Double) {
        if (!running) return
        val now = nowSec
        val dt = min(2.0, now - lastTick) // real elapsed time: a busy device delays the timer, that time is not dropped
        lastTick = now
        val plan = world.activePlan()
        val sim = world.simSnapshot()
        val nav = world.liveNavigation()
        if (plan == null || phase == Phase.NO_PLAN) return

        // route trail for the map
        trailT += dt
        if (nav != null && trailT >= 2 && nav.positionMode != PositionMode.NO_GEOGRAPHIC_REFERENCE &&
            (!boolOf(path(sim, "wow")) || nav.groundSpeedKt > 1)
        ) {
            trailT = 0.0
            trail = (trail.takeLast(499) + LatLon(nav.latitude, nav.longitude))
        }
        if (phase == Phase.READY || phase == Phase.COMPLETE) return

        val curPhase = phase
        val elev = fieldElev(plan.departure)
        val altFt = num(path(sim, "psFlightAltFt"))
        val wow = boolOf(path(sim, "wow"))
        val airborne = !wow
        val mip = world.mipSnapshot()
        val guide = world.liveGuidance()

        // ground roll
        if (curPhase == Phase.TAKEOFF && wow) {
            groundRoll(plan, sim, dt, elev, now)
            return
        }
        if (!airborne) {
            if (curPhase == Phase.ROLLOUT) rollout(dt)
            return
        }

        val agl = altFt - elev
        val cas = num(path(sim, "psFlightCasKt"))
        val distThr = nav?.distanceToDestinationNm ?: 99.0
        val toDestElev = fieldElev(plan.destination)
        // Automatic descent after approximately 75% of the route is covered. Not while READY, TAKEOFF or CLIMB.
        val routeNm = FlightPhaseControl.totalRouteDistanceNm(plan)
        if (curPhase == Phase.CRUISE && routeNm > 1 && nav?.distanceToDestinationNm != null && nav.distanceToDestinationNm <= routeNm * 0.25) {
            setPhase(Phase.DESCENT, "Automatic descent initiated at approximately 75% route progress.")
            hint = "Route progress 75% or greater. Beginning gradual descent to the approach altitude."
        }
        val scene = FlightRoute.arrivalScene(plan)

        if (auto && world.nowMs() - since > 4000) {
            val next = FlightPhaseControl.autoNext(curPhase, plan.cruiseAltitudeFt, AutoContext(
                agl = agl,
                altFt = altFt,
                distThrNm = distThr,
                descentTgtFt = FlightPhaseControl.descentTargetForPlan(plan),
                gearUp = path(sim, "gear") == "UP",
                locCaptured = scene != null && (boolOf(path(mip, "nav.locValid")) || distThr <= 10),
                inVectors = guide.inVectors,
                todReached = guide.distToTodNm != null && guide.distToTodNm <= 0,
            ))
            if (next != null && canStart(next).ok) {
                startPhase(next)
                say("AUTO PHASES: ${curPhase.label} complete, ${next.label} started.")
                return
            }
        }

        // speed, thrust and configuration per phase
        val flapsNow = num(path(sim, "flaps"))
        val accelHold = curPhase == Phase.CLIMB && agl < FlightPhases.ACCEL_ALT_AGL_FT + 300 && (flapsNow > 0 || cas < 190)
        val sched = FlightPhaseControl.schedule(
            phase = curPhase, altFt = altFt, agl = agl, cas = cas, flapsNow = flapsNow, distThrNm = distThr,
            cruiseSpeedKt = plan.cruiseSpeedKt, speedAdvisoryKt = guide.speedAdvisoryKt,
        )
        var targetCas = sched.targetCas
        var flaps = sched.flaps
        when (curPhase) {
            Phase.TAKEOFF -> {
                if (agl >= FlightPhases.ACCEL_ALT_AGL_FT - 200 && !climbThrustCalled) {
                    climbThrustCalled = true
                    say("Acceleration altitude - climb thrust.")
                }
                if (agl >= FlightPhases.ACCEL_ALT_AGL_FT - 200) {
                    hint = "Level acceleration at 1,500 ft, flaps retracting. Press CLIMB."
                }
            }
            Phase.CLIMB -> {
                if (accelHold) {
                    hint = "Level acceleration at ${fmtInt(elev + FlightPhases.ACCEL_ALT_AGL_FT)} ft - flaps retract on the speed schedule, then the climb resumes."
                } else if (abs(altFt - plan.cruiseAltitudeFt) < 300) {
                    hint = "At cruise altitude. Press CRUISE."
                } else {
                    hint = "Climbing to ${fmtInt(plan.cruiseAltitudeFt)} ft."
                }
            }
            Phase.CRUISE -> {
                val tod = guide.distToTodNm
                hint = when {
                    tod != null && tod <= 0 -> "Top of descent. Press DESCENT."
                    tod != null -> "Cruise. Top of descent in ${fmtFixed(max(0.0, tod), 0)} NM."
                    else -> "Cruise."
                }
            }
            Phase.DESCENT -> {
                val tgt = FlightPhaseControl.descentTargetForPlan(plan)
                hint = if (altFt - tgt < 400) "Level at the approach altitude. Press APPROACH."
                else "Descending to ${fmtInt(tgt)} ft.${if (guide.inVectors) " Vectors started - press APPROACH." else ""}"
            }
            Phase.APPROACH -> {
                // landing configuration: gear comes down once flaps 5 is selected or the runway is within 15 NM
                if (path(sim, "gear") != "DN" && (flaps >= 5 || (scene != null && distThr <= 15))) {
                    world.patchSim(GEAR_DN_PATCH)
                    say("Approach configuration - gear down.")
                }
                hint = when {
                    scene == null -> "No ILS in this plan - fly the descent with PLAN NAV; LAND needs the vectored ILS arrival."
                    boolOf(path(mip, "nav.locValid")) ->
                        if (num(path(mip, "apNav")) >= 1) "Localizer captured. Press LAND." else "Localizer alive. Press LAND."
                    guide.ilsArm -> "Intercepting - ILS tuned."
                    else -> "Vectors to the ILS."
                }
            }
            Phase.LAND -> {
                if (!gearDownDone) {
                    gearDownDone = true
                    world.patchSim(mapOf("gear" to "DN", "noseGearPos" to 1.0, "leftMainGearPos" to 1.0,
                        "rightMainGearPos" to 1.0, "speedBrake" to "ARMED"))
                    say("Gear down, speedbrake armed.")
                }
                hint = "Final approach, ${fmtFixed(distThr, 1)} NM to the threshold. " +
                    if (num(path(mip, "apNav")) >= 2) "Localizer / glide slope tracking." else "Waiting for LOC / GS capture."
                if (altFt <= toDestElev + 18 || (scene != null && distThr < 0.12 && altFt < toDestElev + 150)) {
                    touchdown(plan, cas, altFt)
                    return
                }
            }
            else -> {}
        }

        // gear and speedbrake follow the phase so the cockpit shows the real configuration
        if (curPhase == Phase.TAKEOFF || curPhase == Phase.CLIMB || curPhase == Phase.CRUISE || curPhase == Phase.DESCENT) {
            if (agl > FlightPhases.GEAR_UP_AGL_FT && vsCmd > 150 && path(sim, "gear") != "UP") {
                world.patchSim(GEAR_UP_PATCH)
                say("Positive rate - gear up.")
            }
        }
        val sbWanted = if (curPhase == Phase.LAND) "ARMED" else "DOWN"
        if (path(sim, "speedBrake") != sbWanted) world.patchSim(mapOf("speedBrake" to sbWanted))

        // Hand-flying by the phase buttons: vertical and lateral commands, unless the pilot engaged the AP axis.
        // TS applyPhaseToGuidance reads usePhase.getState().phase fresh: an auto-descent switched above is already in effect here
        val phaseGuide = FlightPhaseControl.applyPhaseToGuidance(guide, phase, altFt, plan)
        mirrorToControls(curPhase, plan, phaseGuide)
        if (!boolOf(path(mip, "apElev"))) {
            val perf = climbPerformance(sim).factor
            val vc = FlightPhaseControl.verticalCommand(
                phase = curPhase, altFt = altFt, elevFt = elev, toDestElevFt = toDestElev, distThrNm = distThr,
                gsKt = num(sim["groundSpeedKt"] ?: 140.0), guideTargetAltFt = phaseGuide.targetAltFt,
                perfFactor = perf, accelHold = accelHold, vsCmd = vsCmd, dt = dt,
            )
            vsCmd = vc.vsCmd
            patchChanged(mapOf(
                "psFlightProgressive" to true,
                "psFlightTargetAltFt" to vc.psFlightTargetAltFt,
                "psFlightVsFpm" to vc.psFlightVsFpm,
            ), 4.0)
        }
        if (!boolOf(path(mip, "apAil")) && phaseGuide.headingMagCmdDeg != null) {
            val curHdg = if (boolOf(path(mip, "attLive"))) num(path(mip, "att.hdg")) else num(sim["attitudeHeadingDeg"] ?: 0.0)
            val err = ((phaseGuide.headingMagCmdDeg - curHdg + 540) % 360) - 180
            if (boolOf(path(mip, "attLive"))) {
                patchChanged(mapOf("yokeRoll" to (err * 0.07).coerceIn(-0.8, 0.8)), 0.02) // bank up to ~24 degrees
            } else {
                val rate = (err * 0.5).coerceIn(-3.0, 3.0) // deg/s, standard rate
                world.patchSim(mapOf("attitudeHeadingDeg" to ((((curHdg + rate * dt) % 360) + 360) % 360)))
            }
        } else if (!boolOf(path(mip, "apAil")) && abs(num(sim["yokeRoll"] ?: 0.0)) > 0.02) {
            patchChanged(mapOf("yokeRoll" to 0.0), 0.02)
        }

        // trainer-set thrust, flaps and speed target; groundspeed from true airspeed so the route tracker moves at the right rate
        val tas = FlightPhases.tasFromCas(cas, altFt)
        val thrTarget = FlightPhaseControl.throttleFor(curPhase, agl)
        val changes = linkedMapOf<String, Any>(
            "psFlightTargetCasKt" to targetCas,
            "psFlightProgressive" to true,
            "groundSpeedKt" to jsRound(tas),
            "throttle1" to FlightPhaseControl.slewThrottle(num(sim["throttle1"] ?: 0.0), thrTarget, dt),
            "throttle2" to FlightPhaseControl.slewThrottle(num(sim["throttle2"] ?: 0.0), thrTarget, dt),
        )
        if (flaps != num(sim["flaps"] ?: 0.0)) changes["flaps"] = flaps
        patchChanged(changes, 0.8)
        autoHint()
    }

    private fun groundRoll(plan: ActiveFlightPlan, sim: Map<String, Any?>, dt: Double, elev: Double, now: Double) {
        // Thrust levers advanced over ~1 s, then the aircraft accelerates on the thrust the engines really produce.
        rollT += dt
        val lever = min(1.0, rollT / 1.0)
        val n1Avg = (num(sim["n1_1"] ?: 0.0) + num(sim["n1_2"] ?: 0.0)) / 2
        val enginesRunning = n1Avg > 25
        // integrate in <=0.1 s sub-steps so a late timer tick gives the same roll as a punctual one
        val t0 = rollT - dt
        var rem = dt
        var k = 0
        while (rem > 1e-6 && k < 40) {
            val h = min(0.1, rem)
            rem -= h
            val tt = t0 + (dt - rem)
            val leverNow = min(1.0, tt / 1.0)
            val thrustFrac = if (enginesRunning) {
                max((n1Avg - 32) / 60, leverNow * (1 - kotlin.math.exp(-tt / 0.6))).coerceIn(0.0, 1.0)
            } else {
                min(1.0, tt / 1.5) // un-started trainer: scripted spool-up
            }
            rollGs = min(FlightPhases.VR_KT + 5, rollGs + max(0.0, thrustFrac * (ROLL_ACCEL_KT_S - ROLL_DRAG_PER_KT * rollGs)) * h)
            k++
        }
        // airspeed indicator reads the roll speed too (no wind)
        patchChanged(mapOf(
            "groundSpeedKt" to jsRound(rollGs * 4) / 4,
            "psFlightCasKt" to jsRound(rollGs * 2) / 2,
            "psFlightTargetCasKt" to jsRound(rollGs * 2) / 2,
            "psGroundRoll" to true,
            "throttle1" to lever,
            "throttle2" to lever,
            "speedBrake" to "ARMED",
        ), 0.2)
        if (rollGs >= FlightPhases.VR_KT + FlightPhases.LIFTOFF_MARGIN_KT) {
            val cas = FlightPhases.VR_KT + FlightPhases.LIFTOFF_MARGIN_KT
            // The generic airborne preset forces both cores to N2 70. Keep the REAL engine state when the engines run;
            // only an un-started trainer gets the preset, at idle N2 so it spools up smoothly.
            val fm = FlightModes.flightModePatch(elev + 15).toMutableMap()
            if (enginesRunning) {
                fm.remove("n2_1"); fm.remove("n2_2"); fm.remove("lever1"); fm.remove("lever2")
            } else {
                fm["n2_1"] = 57.0
                fm["n2_2"] = 57.0
            }
            fm["autobrake"] = 0.0
            fm["gear"] = "DN"
            fm["noseGearPos"] = 1.0
            fm["leftMainGearPos"] = 1.0
            fm["rightMainGearPos"] = 1.0
            fm["flaps"] = 5.0
            fm["groundSpeedKt"] = FlightPhases.tasFromCas(cas, elev)
            fm["psFlightCasKt"] = cas
            fm["psFlightTargetCasKt"] = FlightPhases.V2_KT + 15
            fm["psGroundRoll"] = false
            fm["psFlightProgressive"] = true
            fm["psFlightAltFt"] = elev + 15
            fm["psFlightTargetAltFt"] = elev + 15 + FlightPhases.TAKEOFF_CLIMB_STEP_FT
            fm["psFlightVsFpm"] = FlightPhases.LIFTOFF_VS_FPM
            fm["throttle1"] = 1.0
            fm["throttle2"] = 1.0
            world.patchSim(fm)
            climbThrustCalled = false
            vsCmd = FlightPhases.LIFTOFF_VS_FPM
            world.patchMip(mapOf("apAil" to false, "apElev" to false, "apNav" to 0.0, "fdMode" to 2.0))
            say("Rotate at ${jsNum(FlightPhases.VR_KT)} kt - lift-off. Flown by the phase controller (autopilot not engaged).")
            hint = "Airborne. Gear up at positive rate; press CLIMB when clean."
            autoHint()
        }
    }

    private fun rollout(dt: Double) {
        rollGs = max(0.0, rollGs - 3.4 * dt)
        patchChanged(mapOf(
            "groundSpeedKt" to jsRound(rollGs * 2) / 2,
            "psFlightCasKt" to jsRound(rollGs * 2) / 2,
            "psFlightTargetCasKt" to jsRound(rollGs * 2) / 2,
        ), 0.4)
        if (rollGs <= 0.5) {
            world.patchSim(mapOf("groundSpeedKt" to 0.0, "psFlightCasKt" to 0.0, "psFlightTargetCasKt" to 0.0,
                "psGroundRoll" to false, "autobrake" to 0.0))
            setPhase(Phase.COMPLETE, "Aircraft stopped. Flight complete.")
            hint = "Flight complete. Load the plan again to fly another."
        }
    }

    private fun touchdown(plan: ActiveFlightPlan, cas: Double, altFt: Double) {
        val elev = fieldElev(plan.destination)
        vsCmd = 0.0
        world.patchSim(mapOf(
            "wow" to true, "airborneTest" to false, "groundSenseTest" to false, "gear" to "DN",
            "noseGearPos" to 1.0, "leftMainGearPos" to 1.0, "rightMainGearPos" to 1.0, "speedBrake" to "UP",
            "yokeRoll" to 0.0, "throttle1" to 0.0, "throttle2" to 0.0, "autobrake" to 2.0,
            "groundSpeedKt" to jsRound(FlightPhases.tasFromCas(cas, altFt)), "psFlightCasKt" to jsRound(cas),
            "psFlightTargetCasKt" to jsRound(cas), "psGroundRoll" to true, "psFlightVsFpm" to 0.0,
            "psFlightAltFt" to elev, "psFlightTargetAltFt" to elev,
        ))
        world.patchMip(mapOf("apAil" to false, "apElev" to false, "apNav" to 0.0, "fdMode" to 1.0))
        rollGs = jsRound(FlightPhases.tasFromCas(cas, altFt))
        setPhase(Phase.ROLLOUT, "Touchdown at ${jsNum(jsRound(rollGs))} kt - speedbrake UP, autobrake MED, autopilot off.")
        hint = "Rollout."
    }

    // ───────── mirror to the MIP controls ─────────

    private fun mirrorToControls(p: Phase, plan: ActiveFlightPlan, guide: GuidanceResult) {
        val mip = world.mipLive()
        val patch = LinkedHashMap<String, Any>()
        if (!boolOf(path(mip, "apAil")) && guide.headingMagCmdDeg != null) {
            val h = FlightPhaseControl.jsRound(((guide.headingMagCmdDeg % 360) + 360) % 360) % 360
            if (num(path(mip, "selHdg")) != h) patch["selHdg"] = h
        }
        if (!boolOf(path(mip, "apElev"))) {
            val pitch = num(path(mip, "apPitch"))
            if (p == Phase.CRUISE && pitch == 1.0) {
                patch["apPitch"] = 2.0
                altHoldSetByProgram = true
            } else if (p != Phase.CRUISE && altHoldSetByProgram && pitch == 2.0) {
                patch["apPitch"] = 1.0
                altHoldSetByProgram = false
            }
        }
        val arrival = plan.arrival
        if ((p == Phase.APPROACH || p == Phase.LAND) && arrival != null) {
            val crs = FlightPhaseControl.jsRound(arrival.courseMag)
            if (num(path(mip, "selCrs")) != crs) patch["selCrs"] = crs
            val key = "${plan.activatedAt}:${arrival.ilsFrequencyMHz}"
            if (ilsTunedFor != key) {
                ilsTunedFor = key
                val tune = NavReceiver.tuneNav1(arrival.ilsFrequencyMHz,
                    currentNavLStby = num(path(world.pedestalLive(), "navL.stby")).toInt(), powerOn = true)
                world.patchPedestal(mapOf("navL" to mapOf("act" to tune.navLAct, "stby" to tune.navLStby)))
                val navUpd = LinkedHashMap<String, Any>()
                navUpd["navFrequencyMHz"] = tune.mipFrequencyMHz
                tune.mipNavTuned?.let { navUpd["navTuned"] = it }
                if (tune.mipNavOn) navUpd["navOn"] = true
                world.patchMip(navUpd)
                if (!boolOf(path(mip, "navOn"))) patch["navOn"] = true
            }
        }
        if (patch.isNotEmpty()) world.patchMip(patch)
    }

    // ───────── buttons ─────────

    fun canStart(p: Phase): FlightPhaseControl.StartGate {
        val plan = world.activePlan()
        val wow = boolOf(path(world.simLive(), "wow"))
        return FlightPhaseControl.canStart(
            p = p,
            hasPlan = plan != null,
            phase = phase,
            wow = wow,
            hasArrivalScene = plan != null && FlightRoute.arrivalScene(plan) != null,
        )
    }

    /** Press a phase button. False when the gate refuses it (the reason is logged). */
    fun startPhase(p: Phase): Boolean {
        val gate = canStart(p)
        if (!gate.ok) {
            say("${p.label} not available: ${gate.reason}.")
            return false
        }
        val plan = world.activePlan()!!
        val mip = world.mipLive()
        ensureRunning()
        if (p == Phase.TAKEOFF) {
            // TAKEOFF is the sole command that starts movement from READY.
            world.patchMip(mapOf("planNav" to true, "apAil" to false, "apElev" to false, "apNav" to 0.0, "apHdgSw" to 0.0))
            patchChanged(mapOf(
                "groundSpeedKt" to 0.0, "psFlightCasKt" to 0.0, "psFlightTargetCasKt" to 0.0, "psFlightVsFpm" to 0.0,
                "psFlightTargetAltFt" to num(world.simLive()["psFlightAltFt"] ?: 0.0), "throttle1" to 0.0,
                "throttle2" to 0.0, "psGroundRoll" to false,
            ), 0.1)
            rollGs = 0.0; rollT = 0.0; vsCmd = 0.0; gearDownDone = false
            setPhase(Phase.TAKEOFF, "TAKEOFF - thrust set, accelerating.")
            hint = "Accelerating: V1 ${jsNum(FlightPhases.V1_KT)}, rotate ${jsNum(FlightPhases.VR_KT)}."
            return true
        }
        // airborne phases: the controller flies the aircraft; the autopilot axes are not engaged for the pilot.
        val fdPatch = mutableMapOf<String, Any>("planNav" to true)
        if (num(path(mip, "fdMode")) == 1.0) fdPatch["fdMode"] = 2.0
        world.patchMip(fdPatch)
        if (p == Phase.LAND) gearDownDone = false
        val text = when (p) {
            Phase.CLIMB -> "CLIMB - to ${jsNum(plan.cruiseAltitudeFt)} ft, 250 kt below 10,000 ft."
            Phase.CRUISE -> "CRUISE - altitude hold, plan cruise speed."
            Phase.DESCENT -> "DESCENT - to ${jsNum(FlightPhaseControl.descentTargetForPlan(plan))} ft."
            Phase.APPROACH -> "APPROACH - vectors to the ILS, speed and flaps scheduled."
            Phase.LAND -> "LAND - gear down, final flaps, ILS tracking."
            else -> ""
        }
        setPhase(p, text)
        return true
    }

    /** Back to a clean state (used when the plan is deactivated). */
    fun resetFlightPhases() {
        stopRunning()
        phase = Phase.NO_PLAN
        checklist = emptyList()
        trail = emptyList()
        hint = "Activate a flight plan to load it into the controls."
    }

    // ───────── helpers ─────────

    /** web patchChanged: write only the keys whose value moved by more than [tol] (strict inequality otherwise). */
    private fun patchChanged(p: Map<String, Any?>, tol: Double = 0.25) {
        val cur = world.simLive()
        val diff = LinkedHashMap<String, Any?>()
        for ((k, v) in p) {
            val c = cur[k]
            val changed = if (v is Number && c is Number) abs(v.toDouble() - c.toDouble()) > tol else v != c
            if (changed) diff[k] = v
        }
        if (diff.isNotEmpty()) world.patchSim(diff)
    }

    private fun fieldElev(icao: String): Double = FlightPhases.fieldElev(icao)

    private fun climbPerformance(sim: Map<String, Any?>): FlightPhases.ClimbPerformance {
        fun pct(k: String): Double {
            val v = sim[k]
            val n = if (v == null) 100.0 else num(v)
            return n.coerceIn(0.0, 100.0)
        }
        return FlightPhases.climbPerformance(pct("fuelTank1"), pct("fuelTankCenter"), pct("fuelTank2"))
    }

    companion object {
        private const val ROLL_ACCEL_KT_S = 7.2
        private const val ROLL_DRAG_PER_KT = 0.0165

        val GEAR_UP_PATCH: Map<String, Any?> = mapOf("gear" to "UP", "noseGearPos" to 0.0, "leftMainGearPos" to 0.0, "rightMainGearPos" to 0.0)
        val GEAR_DN_PATCH: Map<String, Any?> = mapOf("gear" to "DN", "noseGearPos" to 1.0, "leftMainGearPos" to 1.0, "rightMainGearPos" to 1.0)

        /** Dotted-path read used for keys like "nav.locValid" and "att.hdg". */
        fun path(map: Map<String, Any?>, key: String): Any? {
            var cur: Any? = map
            for (part in key.split('.')) {
                cur = (cur as? Map<*, *>)?.get(part) ?: return null
            }
            return cur
        }

        fun num(v: Any?): Double = when (v) {
            is Number -> v.toDouble()
            is Boolean -> if (v) 1.0 else 0.0
            is String -> v.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }

        fun boolOf(v: Any?): Boolean = when (v) {
            is Boolean -> v
            is Number -> v.toDouble() != 0.0
            else -> false
        }

        private fun jsRound(x: Double): Double = FlightPhaseControl.jsRound(x)

        /** JS Number toString for the whole and simple decimal values used in notes. */
        fun jsNum(v: Double): String =
            if (v == kotlin.math.floor(v) && abs(v) < 1e15) v.toLong().toString() else v.toString()

        /** en-US toLocaleString for whole numbers (thousands separators). */
        fun fmtInt(v: Double): String = String.format(Locale.US, "%,d", kotlin.math.round(v).toLong())

        /** JS toFixed for the decimals used in hints. */
        fun fmtFixed(v: Double, digits: Int): String = String.format(Locale.ROOT, "%.${digits}f", v)

        private fun pad3(v: Double): String = v.toLong().toString().padStart(3, '0')
    }
}

/**
 * Everything the controller reads and writes outside itself. Live reads return the current holder values; patches
 * merge top-level keys. The stateful layer (SimStore, MipLocal holder, pedestal holder) implements this.
 */
interface PhaseWorld {
    fun nowMs(): Long
    /** platform monotonic clock in seconds */
    fun perfSec(): Double
    fun activePlan(): ActiveFlightPlan?
    fun resetNavigation(plan: ActiveFlightPlan)
    fun liveNavigation(): FlightNavigationState?
    fun liveGuidance(): GuidanceResult

    fun simLive(): Map<String, Any?>
    /** A copy taken at the start of a tick (web `const sim = useSim.getState()`). */
    fun simSnapshot(): Map<String, Any?>
    fun patchSim(p: Map<String, Any?>)

    fun mipLive(): Map<String, Any?>
    fun mipSnapshot(): Map<String, Any?>
    fun patchMip(p: Map<String, Any?>)

    fun pedestalLive(): Map<String, Any?>
    fun patchPedestal(p: Map<String, Any?>)
}
