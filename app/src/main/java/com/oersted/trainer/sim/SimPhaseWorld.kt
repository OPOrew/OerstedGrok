package com.oersted.trainer.sim

/**
 * [PhaseWorld] over the typed holders: [SimStore] (sim), [MipLocal.Holder] (MIP), [Pedestal.Holder] (pedestal), the
 * [FlightNavigator] and the active-plan provider. The controller reads and writes the same string keys the web patches
 * use ("att.hdg", "navL.stby" are dotted); this adapter maps them to and from the typed state. Only the keys the
 * controller and its presets use are mapped. An unmapped patch key is an error, so a missing mapping fails loudly.
 */
class SimPhaseWorld(
    private val sim: SimStore,
    private val mip: MipLocal.Holder,
    private val pedestal: Pedestal.Holder,
    private val nav: FlightNavigator,
    private val clockMs: () -> Long,
    private val monoSec: () -> Double,
    private val planProvider: () -> ActiveFlightPlan?,
) : PhaseWorld {

    override fun nowMs(): Long = clockMs()
    override fun perfSec(): Double = monoSec()
    override fun activePlan(): ActiveFlightPlan? = planProvider()
    override fun resetNavigation(plan: ActiveFlightPlan) { nav.reset(plan) }
    override fun liveNavigation(): FlightNavigationState? = nav.liveState()
    override fun liveGuidance(): GuidanceResult = nav.liveGuidance()

    override fun simLive(): Map<String, Any?> = simToMap(sim.current())
    override fun simSnapshot(): Map<String, Any?> = simToMap(sim.current())
    override fun patchSim(p: Map<String, Any?>) {
        if (p.isEmpty()) return
        sim.apply { s -> applySim(s, p) }
    }

    override fun mipLive(): Map<String, Any?> = mipToMap(mip.state)
    override fun mipSnapshot(): Map<String, Any?> = mipToMap(mip.state)
    override fun patchMip(p: Map<String, Any?>) {
        if (p.isEmpty()) return
        mip.set { m -> applyMip(m, p) }
    }

    override fun pedestalLive(): Map<String, Any?> = pedToMap(pedestal.state)
    override fun patchPedestal(p: Map<String, Any?>) {
        if (p.isEmpty()) return
        pedestal.set { ps -> applyPed(ps, p) }
    }

    companion object {
        private fun dbl(v: Any?): Double = (v as Number).toDouble()
        private fun int(v: Any?): Int = (v as Number).toInt()

        /** Sim keys the phase controller reads or writes (web store names). */
        fun simToMap(s: SimState): Map<String, Any?> = mapOf(
        "actualDecel" to s.actualDecel,
        "afPanelOwned" to s.afPanelOwned,
        "airborneTest" to s.airborneTest,
        "antiskidInbd" to s.antiskidInbd,
        "antiskidOutbd" to s.antiskidOutbd,
        "attitudeHeadingDeg" to s.attitudeHeadingDeg,
        "autobrake" to s.autobrake,
        "battery" to s.battery,
        "bleed1" to s.bleed1,
        "bleed2" to s.bleed2,
        "elecHyd1" to s.elecHyd1,
        "elecHyd2" to s.elecHyd2,
        "engHyd1" to s.engHyd1,
        "engHyd2" to s.engHyd2,
        "externalPowerConnected" to s.externalPowerConnected,
        "flaps" to s.flaps,
        "fltCtrlA" to s.fltCtrlA,
        "fltCtrlB" to s.fltCtrlB,
        "fuelTank1" to s.fuelTank1,
        "fuelTank2" to s.fuelTank2,
        "fuelTankCenter" to s.fuelTankCenter,
        "gear" to s.gear,
        "gen1" to s.gen1,
        "gen2" to s.gen2,
        "groundPneumaticConnected" to s.groundPneumaticConnected,
        "groundPneumaticPsi" to s.groundPneumaticPsi,
        "groundSenseTest" to s.groundSenseTest,
        "groundSpeedKt" to s.groundSpeedKt,
        "hydAPress" to s.hydAPress,
        "hydBPress" to s.hydBPress,
        "hydGroundPowerConnected" to s.hydGroundPowerConnected,
        "hydServicingConnected" to s.hydServicingConnected,
        "leftMainGearPos" to s.leftMainGearPos,
        "lever1" to s.lever1,
        "lever2" to s.lever2,
        "n1_1" to s.n1_1,
        "n1_2" to s.n1_2,
        "n2_1" to s.n2_1,
        "n2_2" to s.n2_2,
        "noseGearPos" to s.noseGearPos,
        "parkingBrake" to s.parkingBrake,
        "psFlightAltFt" to s.psFlightAltFt,
        "psFlightCasKt" to s.psFlightCasKt,
        "psFlightProgressive" to s.psFlightProgressive,
        "psFlightTargetAltFt" to s.psFlightTargetAltFt,
        "psFlightTargetCasKt" to s.psFlightTargetCasKt,
        "psFlightVsFpm" to s.psFlightVsFpm,
        "psGroundRoll" to s.psGroundRoll,
        "rightMainGearPos" to s.rightMainGearPos,
        "rudderPedal" to s.rudderPedal,
        "rwyAltFt" to s.rwyAltFt,
        "rwyHdgDeg" to s.rwyHdgDeg,
        "rwyLatM" to s.rwyLatM,
        "rwyNote" to s.rwyNote,
        "rwyPhase" to s.rwyPhase,
        "rwyPosM" to s.rwyPosM,
        "rwyTrackDeg" to s.rwyTrackDeg,
        "rwyYawRate" to s.rwyYawRate,
        "speedBrake" to s.speedBrake,
        "spoilerA" to s.spoilerA,
        "spoilerB" to s.spoilerB,
        "throttle1" to s.throttle1,
        "throttle2" to s.throttle2,
        "tiller" to s.tiller,
        "wow" to s.wow,
        "yokePitch" to s.yokePitch,
        "yokeRoll" to s.yokeRoll
        )

        fun applySim(s: SimState, p: Map<String, Any?>): SimState {
            val n = s.copy()
            for ((k, v) in p) {
                when (k) {
        "actualDecel" -> n.actualDecel = dbl(v)
        "afPanelOwned" -> n.afPanelOwned = v as Boolean
        "airborneTest" -> n.airborneTest = v as Boolean
        "antiskidInbd" -> n.antiskidInbd = v as Boolean
        "antiskidOutbd" -> n.antiskidOutbd = v as Boolean
        "attitudeHeadingDeg" -> n.attitudeHeadingDeg = dbl(v)
        "autobrake" -> n.autobrake = dbl(v)
        "battery" -> n.battery = v as Boolean
        "bleed1" -> n.bleed1 = v as Boolean
        "bleed2" -> n.bleed2 = v as Boolean
        "elecHyd1" -> n.elecHyd1 = v as Boolean
        "elecHyd2" -> n.elecHyd2 = v as Boolean
        "engHyd1" -> n.engHyd1 = v as Boolean
        "engHyd2" -> n.engHyd2 = v as Boolean
        "externalPowerConnected" -> n.externalPowerConnected = v as Boolean
        "flaps" -> n.flaps = dbl(v)
        "fltCtrlA" -> n.fltCtrlA = v as Boolean
        "fltCtrlB" -> n.fltCtrlB = v as Boolean
        "fuelTank1" -> n.fuelTank1 = dbl(v)
        "fuelTank2" -> n.fuelTank2 = dbl(v)
        "fuelTankCenter" -> n.fuelTankCenter = dbl(v)
        "gear" -> n.gear = v as String
        "gen1" -> n.gen1 = v as Boolean
        "gen2" -> n.gen2 = v as Boolean
        "groundPneumaticConnected" -> n.groundPneumaticConnected = v as Boolean
        "groundPneumaticPsi" -> n.groundPneumaticPsi = dbl(v)
        "groundSenseTest" -> n.groundSenseTest = v as Boolean
        "groundSpeedKt" -> n.groundSpeedKt = dbl(v)
        "hydAPress" -> n.hydAPress = dbl(v)
        "hydBPress" -> n.hydBPress = dbl(v)
        "hydGroundPowerConnected" -> n.hydGroundPowerConnected = v as Boolean
        "hydServicingConnected" -> n.hydServicingConnected = v as Boolean
        "leftMainGearPos" -> n.leftMainGearPos = dbl(v)
        "lever1" -> n.lever1 = v as Boolean
        "lever2" -> n.lever2 = v as Boolean
        "n1_1" -> n.n1_1 = dbl(v)
        "n1_2" -> n.n1_2 = dbl(v)
        "n2_1" -> n.n2_1 = dbl(v)
        "n2_2" -> n.n2_2 = dbl(v)
        "noseGearPos" -> n.noseGearPos = dbl(v)
        "parkingBrake" -> n.parkingBrake = v as Boolean
        "psFlightAltFt" -> n.psFlightAltFt = dbl(v)
        "psFlightCasKt" -> n.psFlightCasKt = dbl(v)
        "psFlightProgressive" -> n.psFlightProgressive = v as Boolean
        "psFlightTargetAltFt" -> n.psFlightTargetAltFt = dbl(v)
        "psFlightTargetCasKt" -> n.psFlightTargetCasKt = dbl(v)
        "psFlightVsFpm" -> n.psFlightVsFpm = dbl(v)
        "psGroundRoll" -> n.psGroundRoll = v as Boolean
        "rightMainGearPos" -> n.rightMainGearPos = dbl(v)
        "rudderPedal" -> n.rudderPedal = dbl(v)
        "rwyAltFt" -> n.rwyAltFt = dbl(v)
        "rwyHdgDeg" -> n.rwyHdgDeg = dbl(v)
        "rwyLatM" -> n.rwyLatM = dbl(v)
        "rwyNote" -> n.rwyNote = v as String
        "rwyPhase" -> n.rwyPhase = v as String
        "rwyPosM" -> n.rwyPosM = dbl(v)
        "rwyTrackDeg" -> n.rwyTrackDeg = dbl(v)
        "rwyYawRate" -> n.rwyYawRate = dbl(v)
        "speedBrake" -> n.speedBrake = v as String
        "spoilerA" -> n.spoilerA = v as Boolean
        "spoilerB" -> n.spoilerB = v as Boolean
        "throttle1" -> n.throttle1 = dbl(v)
        "throttle2" -> n.throttle2 = dbl(v)
        "tiller" -> n.tiller = dbl(v)
        "wow" -> n.wow = v as Boolean
        "yokePitch" -> n.yokePitch = dbl(v)
        "yokeRoll" -> n.yokeRoll = dbl(v)
                    "wheelSpeeds" -> n.wheelSpeeds = wheelsSpeeds(v)
                    "wheelBrakePressures" -> n.wheelBrakePressures = wheelsBrake(v)
                    else -> throw IllegalArgumentException("SimPhaseWorld: unmapped sim key $k")
                }
            }
            return n
        }

        private fun wheelsSpeeds(v: Any?): SimStateWheelSpeeds {
            val r = v as Map<*, *>
            return SimStateWheelSpeeds().also {
                it.li = dbl(r["li"]); it.lo = dbl(r["lo"]); it.ri = dbl(r["ri"]); it.ro = dbl(r["ro"])
            }
        }

        private fun wheelsBrake(v: Any?): SimStateWheelBrakePressures {
            val r = v as Map<*, *>
            return SimStateWheelBrakePressures().also {
                it.li = dbl(r["li"]); it.lo = dbl(r["lo"]); it.ri = dbl(r["ri"]); it.ro = dbl(r["ro"])
            }
        }

        fun mipToMap(m: MipLocal.MipState): Map<String, Any?> = mapOf(
            "apAil" to m.apAil,
            "apElev" to m.apElev,
            "apHdgSw" to m.apHdgSw.toDouble(),
            "apNav" to m.apNav.toDouble(),
            "fdMode" to m.fdMode.toDouble(),
            "navResetN" to m.navResetN.toDouble(),
            "planNav" to m.planNav,
            "planNavStatus" to m.planNavStatus,
            "navFrequencyMHz" to m.navFrequencyMHz,
            "navOn" to m.navOn,
            "navTuned" to m.navTuned,
            "attLive" to m.attLive,
            "xpdrMode" to m.xpdrMode.toDouble(),
            "apWarn" to m.apWarn,
            "apWhy" to m.apWhy,
            "selHdg" to m.selHdg,
            "selCrs" to m.selCrs,
            "attHdgReq" to mapOf("n" to m.attHdgReq.n.toDouble(), "hdg" to m.attHdgReq.hdg),
            "nav" to mapOf("locValid" to m.nav.locValid, "gsValid" to m.nav.gsValid, "vorValid" to m.nav.vorValid),
            "att" to mapOf("pitch" to m.att.pitch, "roll" to m.att.roll, "hdg" to m.att.hdg),
        )

        fun applyMip(m: MipLocal.MipState, p: Map<String, Any?>): MipLocal.MipState {
            var n = m
            for ((k, v) in p) {
                n = when (k) {
                    "apAil" -> n.copy(apAil = v as Boolean)
                    "apElev" -> n.copy(apElev = v as Boolean)
                    "apHdgSw" -> n.copy(apHdgSw = int(v))
                    "apNav" -> n.copy(apNav = int(v))
                    "fdMode" -> n.copy(fdMode = int(v))
                    "navResetN" -> n.copy(navResetN = int(v))
                    "planNav" -> n.copy(planNav = v as Boolean)
                    "planNavStatus" -> n.copy(planNavStatus = v as String)
                    "navFrequencyMHz" -> n.copy(navFrequencyMHz = (v as Number).toDouble())
                    "navOn" -> n.copy(navOn = v as Boolean)
                    "navTuned" -> n.copy(navTuned = v as String)
                    "attLive" -> n.copy(attLive = v as Boolean)
                    "xpdrMode" -> n.copy(xpdrMode = int(v))
                    "apWarn" -> n.copy(apWarn = v as Boolean)
                    "apWhy" -> n.copy(apWhy = v as String)
                    "selHdg" -> n.copy(selHdg = dbl(v))
                    "selCrs" -> n.copy(selCrs = dbl(v))
                    "attHdgReq" -> {
                        val r = v as Map<*, *>
                        n.copy(attHdgReq = MipLocal.AttHdgRequest(int(r["n"]), dbl(r["hdg"])))
                    }
                    else -> throw IllegalArgumentException("SimPhaseWorld: unmapped MIP key $k")
                }
            }
            return n
        }

        fun pedToMap(ps: Pedestal.PedestalState): Map<String, Any?> = mapOf(
            "navL" to mapOf("act" to ps.navL.act.toDouble(), "stby" to ps.navL.stby.toDouble()),
        )

        fun applyPed(ps: Pedestal.PedestalState, p: Map<String, Any?>): Pedestal.PedestalState {
            var n = ps
            for ((k, v) in p) {
                n = when (k) {
                    "navL" -> {
                        val r = v as Map<*, *>
                        n.copy(navL = Pedestal.Freq(int(r["act"]), int(r["stby"])))
                    }
                    else -> throw IllegalArgumentException("SimPhaseWorld: unmapped pedestal key $k")
                }
            }
            return n
        }
    }
}
