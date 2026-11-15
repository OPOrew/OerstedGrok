package com.oersted.trainer.sim

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import java.util.Locale

/**
 * Port of pitotStatic.ts: Boeing 737-200 pitot/static and air-data model (ATA 34).
 *
 * Four combined pitot/static probes (UL CAPT, UR F/O, LR AUX 1, LL AUX 2), normal static cross-connects, two alternate
 * static ports, separate CAPT/F/O air-data processing paths (CADC), auxiliary paths, standby pneumatic indication,
 * probe heat and icing, and cover/blockage faults. Air data is pressure-derived (IAS, Mach, altitude, VSI).
 *
 * The TS version reads a loosely typed object and tries many legacy key names. Here the input is the typed SimState.
 * Legacy alias keys (air.*, flight.*, captPitotStatic.*, standby.*, bare altFt/iasKt/vsiFpm and similar) never resolve
 * against SimState, so they are dropped. Each remaining key is the SimState field of the same name.
 */
object PitotStatic {
    const val SEA_LEVEL_HPA = 1013.25
    private const val SEA_LEVEL_TEMP_K = 288.15
    private const val LAPSE_K_PER_M = 0.0065
    private const val R_AIR = 287.05287
    private const val G = 9.80665
    private const val GAMMA = 1.4
    private const val A0_KT = 661.4788
    private const val FT_PER_M = 3.280839895

    /** Mach warning trip. The TS only reads an override key that never exists, so the trainer always uses 0.84. */
    const val MACH_WARNING_THRESHOLD = 0.84

    /** 737-200 barber-pole VMO (KIAS). TRAINING VALUE: confirm against the AFM / AMM 34-12. */
    const val VMO_KT = 340.0
    const val MMO = 0.84

    data class Atmosphere(val pHpa: Double, val tempK: Double)

    data class PitotProbeState(
        val name: String,
        val pitotOk: Boolean,
        val staticOk: Boolean,
        val heaterOn: Boolean,
        val heaterFault: Boolean,
        val heaterIndicatorOn: Boolean,
        val pitotBlocked: Boolean,
        val staticBlocked: Boolean,
        val coverOn: Boolean,
        val iced: Boolean,
    )

    data class StaticSourceState(
        val leftOk: Boolean,
        val rightOk: Boolean,
        val selected: String,
        val selectedOk: Boolean,
        val pressureHpa: Double,
        val source: String,
    )

    data class CadcInputs(val psHpa: Double, val ptHpa: Double, val tiK: Double)

    data class CadcOutputs(
        val correctedAltitudeFt: Double,
        val indicatedAirspeedKt: Double,
        val mach: Double,
        val altitudeRateFpm: Double,
        val staticAirTempK: Double,
    )

    data class CadcData(
        val channel: Int,
        val powered: Boolean,
        val ac115: Boolean,
        val ac26: Boolean,
        val cbClosed: Boolean,
        /** "OFF", "FUNCTION", "SLEW" or "FAIL" */
        val testMode: String,
        val testValid: Boolean,
        val failed: Boolean,
        val inputs: CadcInputs,
        val outputs: CadcOutputs,
        val flags: List<String>,
    )

    data class AdcResult(val channel: Int, val valid: Boolean, val data: CadcData)

    data class SideAirData(
        val iasKt: Double,
        val altFt: Double,
        val vsiFpm: Double,
        val mach: Double,
        val pitotOk: Boolean,
        val staticOk: Boolean,
        val staticSource: String,
        val flags: List<String>,
        val probe: PitotProbeState,
        val staticSourceState: StaticSourceState,
        val pitotPressureHpa: Double,
        val staticPressureHpa: Double,
        val dynamicPressureHpa: Double,
        val adc: AdcResult,
    )

    data class FlightInputs(
        val altFt: Double,
        val casKt: Double,
        val vsiFpm: Double,
        val trainer: Boolean,
        /** web air.flight.staticPressureHpa: ambient static pressure, set on the computeAirData output only */
        val staticPressureHpa: Double = 0.0,
    )

    data class StandbyData(
        val iasKt: Double,
        val altFt: Double,
        val vsiFpm: Double,
        val mach: Double,
        val pitotOk: Boolean,
        val staticOk: Boolean,
        val flags: List<String>,
        val pitotPressureHpa: Double,
        val staticPressureHpa: Double,
    )

    data class AltStatic(
        val leftOk: Boolean,
        val rightOk: Boolean,
        val leftPressureHpa: Double,
        val rightPressureHpa: Double,
        val leftLeak: Boolean,
        val rightLeak: Boolean,
    )

    /** Normal static result. [pressureHpa] changes while leak and test effects are applied. */
    class NormalStatic(
        val ok: Boolean,
        var pressureHpa: Double,
        val onePortBlocked: Boolean,
        val trapped: Boolean,
        val leak: Boolean,
    )

    data class HeaterState(
        val requested: Boolean,
        val on: Boolean,
        val fault: Boolean,
        val indicatorOn: Boolean,
        /** only set for the probe-heat states that report it (TS heatState) */
        val indicationPowered: Boolean? = null,
    )

    data class HeaterStates(
        val captUpperL: HeaterState,
        val captLowerR: HeaterState,
        val foUpperR: HeaterState,
        val foLowerL: HeaterState,
        val elevatorL: HeaterState,
        val elevatorR: HeaterState,
        val temperatureL: HeaterState,
        val temperatureR: HeaterState,
    )

    data class LeakPitot(val observedKt: Double?, val lossKt: Double?, val lossRateKtPerMin: Double?, val pass: Boolean?)

    data class LeakStatic(val observedFt: Double?, val driftFt: Double?, val driftRateFtPerMin: Double?, val pass: Boolean?)

    data class PitotLeakage(val capt: LeakPitot, val fo: LeakPitot, val aux1: LeakPitot, val aux2: LeakPitot)

    data class StaticLeakage(
        val capt: LeakStatic,
        val fo: LeakStatic,
        val aux1: LeakStatic,
        val aux2: LeakStatic,
        val alternate: LeakStatic,
    )

    data class Architecture(
        val probeCount: Int,
        val alternateStaticPortCount: Int,
        val staticSourceSelectors: Int,
        val auxiliarySystems: Int,
        val heaterCircuits: Int,
        val drainTrapCount: Int,
        val testFittingNetwork: Boolean,
        val leakageModel: Boolean,
        val cadcCount: Int,
        val cadcIndependentPower: Boolean,
        val standbyPneumatic: Boolean,
        val heaterStates: HeaterStates,
        val normalStaticTopology: Map<String, List<String>>,
        val auxiliaryStaticTopology: Map<String, Any>,
        val alternateStaticTopology: Map<String, String>,
        val selectorValveLeakage: Map<String, Boolean>,
        val drainTestFittings: Map<String, Int>,
    )

    data class MachWarning(val capt: Boolean, val fo: Boolean, val test: Boolean, val inhibited: Boolean)

    data class Downstream(
        val machAirspeedWarning: MachWarning,
        val cabinDifferential: Map<String, Double>,
        val pressureController: Map<String, Boolean>,
        val autopilot: Map<String, Any>,
        val flightDirector: Map<String, Any>,
        val machTrim: Map<String, Any>,
        val flapLoadLimiter: Map<String, Any>,
        val flightRecorder: Map<String, Boolean>,
    )

    data class Maintenance(
        val drainTraps: Map<String, Boolean>,
        val testFittings: Map<String, Any>,
        val crossLeakTests: Map<String, Boolean>,
        val selectorLeakage: Map<String, Boolean>,
        val elapsedSec: Double,
        val leakageLimits: Map<String, Double>,
        val instrumentTolerances: Map<String, Any>,
        val leakage: Pair<PitotLeakage, StaticLeakage>,
    )

    data class AirData(
        val capt: SideAirData,
        val fo: SideAirData,
        val standby: StandbyData,
        val aux1PitotOk: Boolean,
        val aux2PitotOk: Boolean,
        val aux1PitotPressureHpa: Double,
        val aux2PitotPressureHpa: Double,
        val aux1StaticPressureHpa: Double,
        val aux2StaticPressureHpa: Double,
        val aux1StaticOk: Boolean,
        val aux2StaticOk: Boolean,
        val aux1: PitotProbeState,
        val aux2: PitotProbeState,
        val elevPitotLOk: Boolean,
        val elevPitotROk: Boolean,
        val alternateStatic: AltStatic,
        val cadc: Map<String, CadcData>,
        val flight: FlightInputs,
        val staticPressureHpa: Double,
        val disagree: Boolean,
        val coverOnInMotion: Boolean,
        val architecture: Architecture,
        val downstream: Downstream,
        val maintenance: Maintenance,
    )

    // ----- helpers -----

    /** TS n(): finite number, else the fallback. */
    private fun nf(v: Double, fallback: Double = 0.0): Double = if (v.isFinite()) v else fallback

    private fun clamp(v: Double, lo: Double, hi: Double) = max(lo, min(hi, v))

    /** JS Math.round: floor(x + 0.5). */
    private fun jsRound(x: Double): Double = floor(x + 0.5)

    fun atmosphere(altFt: Double): Atmosphere {
        val h = clamp(altFt, -5000.0, 60000.0) / FT_PER_M
        if (h <= 11000) {
            val t = SEA_LEVEL_TEMP_K - LAPSE_K_PER_M * h
            val p = SEA_LEVEL_HPA * (t / SEA_LEVEL_TEMP_K).pow(G / (R_AIR * LAPSE_K_PER_M))
            return Atmosphere(p, t)
        }
        val t11 = SEA_LEVEL_TEMP_K - LAPSE_K_PER_M * 11000
        val p11 = SEA_LEVEL_HPA * (t11 / SEA_LEVEL_TEMP_K).pow(G / (R_AIR * LAPSE_K_PER_M))
        val p = p11 * exp((-G * (h - 11000)) / (R_AIR * t11))
        return Atmosphere(p, t11)
    }

    fun altitudeFromStatic(pHpa: Double): Double {
        val p = max(1.0, pHpa)
        val exponent = R_AIR * LAPSE_K_PER_M / G
        val h = (SEA_LEVEL_TEMP_K / LAPSE_K_PER_M) * (1 - (p / SEA_LEVEL_HPA).pow(exponent))
        return h * FT_PER_M
    }

    fun leakToward(targetHpa: Double, currentHpa: Double, leak: Boolean, ratePctPerSec: Double, elapsedSec: Double): Double {
        if (!leak || ratePctPerSec <= 0 || elapsedSec <= 0) return currentHpa
        val k = min(1.0, 1 - exp(-(ratePctPerSec / 100) * elapsedSec))
        return currentHpa + (targetHpa - currentHpa) * k
    }

    /**
     * Shared icing rule. The probe heater is a 115 V AC load; the 28 V DC circuit only powers the indication path,
     * so DC power does not change the physical anti-ice result.
     */
    fun pitotIced(
        icing: Boolean,
        heatRequested: Boolean,
        dcPower: Boolean,
        acPower: Boolean = true,
        heaterCb: Boolean = true,
        groupEnabled: Boolean = false,
    ): Boolean {
        val requested = heatRequested || groupEnabled
        val heaterActuallyOn = requested && acPower && heaterCb
        return icing && !heaterActuallyOn
    }

    /** Impact pressure corresponding to calibrated airspeed at sea-level standard density. */
    fun impactPressureFromCas(casKt: Double): Double {
        val m = max(0.0, casKt) / A0_KT
        return SEA_LEVEL_HPA * ((1 + ((GAMMA - 1) / 2) * m * m).pow(GAMMA / (GAMMA - 1)) - 1)
    }

    fun machFromDynamicPressure(qHpa: Double, staticHpa: Double): Double {
        if (qHpa <= 0 || staticHpa <= 0) return 0.0
        val ratio = max(1.0, qHpa / staticHpa + 1)
        return sqrt((2 / (GAMMA - 1)) * (ratio.pow((GAMMA - 1) / GAMMA) - 1))
    }

    fun casFromDynamicPressure(qHpa: Double): Double {
        if (qHpa <= 0) return 0.0
        val term = (qHpa / SEA_LEVEL_HPA + 1).pow((GAMMA - 1) / GAMMA) - 1
        return A0_KT * sqrt(max(0.0, (2 / (GAMMA - 1)) * term))
    }

    /** Barber-pole IAS for the given static pressure: the lower of VMO and the IAS that corresponds to MMO at that pressure. */
    fun vmoMmoLimitKt(staticPressureHpa: Double): Double {
        if (!staticPressureHpa.isFinite() || staticPressureHpa <= 0) return VMO_KT
        val qAtMmo = staticPressureHpa * ((1 + ((GAMMA - 1) / 2) * MMO * MMO).pow(GAMMA / (GAMMA - 1)) - 1)
        return min(VMO_KT, casFromDynamicPressure(qAtMmo))
    }

    /**
     * True when the trainer flight state drives air data: the Pitot/Static trainer is open, or the airplane is
     * airborne (WOW off) with a flight airspeed set, or a takeoff/landing roll is in progress.
     */
    fun flightStateActive(s: SimState): Boolean {
        if (s.psTrainerEnabled) return true
        if (s.wow && s.psGroundRoll && nf(s.psFlightCasKt) > 0) return true
        return !s.wow && nf(s.psFlightCasKt) > 0
    }

    private fun source(s: SimState, side: String): String {
        val trainer = if (side == "capt") s.psAltStaticCapt else s.psAltStaticFo
        return if (trainer) "ALTERNATE" else "NORMAL"
    }

    private fun heatState(s: SimState, side: String, acPower: Boolean, dcPower: Boolean): HeaterState {
        val groupOn = if (side == "capt") s.psPitotStaticA else s.psPitotStaticB
        val cb = if (side == "capt") s.psCbCaptUpperL else s.psCbFoUpperR
        val on = groupOn && acPower && cb
        return HeaterState(
            requested = groupOn,
            on = on,
            fault = groupOn && (!acPower || !cb),
            indicatorOn = dcPower && !on,
            indicationPowered = dcPower,
        )
    }

    private fun componentHeatState(groupOn: Boolean, cb: Boolean, acPower: Boolean, dcPower: Boolean): HeaterState {
        val on = groupOn && acPower && cb
        return HeaterState(requested = groupOn, on = on, fault = groupOn && (!acPower || !cb), indicatorOn = dcPower && !on)
    }

    private fun probe(s: SimState, side: String, acPower: Boolean, dcPower: Boolean): PitotProbeState {
        val name = if (side == "capt") "CAPT" else "FO"
        val blocked = if (side == "capt") s.psPitotCaptBlocked else s.psPitotFoBlocked
        val cover = if (side == "capt") s.psPitotCaptCover else s.psPitotFoCover
        val heat = heatState(s, side, acPower, dcPower)
        val icing = s.icingConditions || s.psIcing
        val iced = icing && !heat.on
        val staticBlocked = cover
        val pitotBlocked = blocked || cover || iced
        return PitotProbeState(
            name = name,
            pitotOk = !pitotBlocked,
            staticOk = !staticBlocked,
            heaterOn = heat.on,
            heaterFault = heat.fault,
            heaterIndicatorOn = heat.indicatorOn,
            pitotBlocked = pitotBlocked,
            staticBlocked = staticBlocked,
            coverOn = cover,
            iced = iced,
        )
    }

    private fun auxiliaryProbe(s: SimState, which: String, acPower: Boolean, dcPower: Boolean): PitotProbeState {
        val name = if (which == "aux1") "AUX1" else "AUX2"
        val blocked = if (which == "aux1") s.psPitotAux1Blocked else s.psPitotAux2Blocked
        val cover = if (which == "aux1") s.psPitotAux1Cover else s.psPitotAux2Cover
        val icing = s.icingConditions || s.psIcing
        // The TS falls back to the probe-heat request only when the group command field is absent; it always exists here.
        val groupOn = if (which == "aux1") s.psPitotStaticB else s.psPitotStaticA
        val cb = if (which == "aux1") s.psCbFoLowerR else s.psCbCaptLowerL
        val heaterOn = groupOn && acPower && cb
        val iced = icing && !heaterOn
        return PitotProbeState(
            name = name,
            pitotOk = !(blocked || cover || iced),
            staticOk = !(blocked || cover),
            heaterOn = heaterOn,
            heaterFault = groupOn && (!acPower || !cb),
            heaterIndicatorOn = dcPower && !heaterOn,
            pitotBlocked = blocked || cover || iced,
            staticBlocked = blocked || cover,
            coverOn = cover,
            iced = iced,
        )
    }

    private fun alternateStatic(s: SimState, flightStaticHpa: Double): AltStatic {
        val leftLeak = s.psAltStaticCaptLeak
        val rightLeak = s.psAltStaticFoLeak
        val elapsed = nf(s.psTestClockSec)
        val rate = nf(s.psStaticLeakRatePctPerSec, 0.5)
        val testP = atmosphere(22500.0).pHpa
        val testTarget = s.psTestTarget
        val leftTest = s.psTestVacuumSource && (testTarget == "ALL" || testTarget == "CAPT" || testTarget == "ALTERNATE")
        val rightTest = s.psTestVacuumSource && (testTarget == "ALL" || testTarget == "FO" || testTarget == "ALTERNATE")
        return AltStatic(
            leftOk = true,
            rightOk = true,
            leftPressureHpa = if (leftTest) (if (leftLeak) leakToward(flightStaticHpa, testP, true, rate, elapsed) else testP) else flightStaticHpa,
            rightPressureHpa = if (rightTest) (if (rightLeak) leakToward(flightStaticHpa, testP, true, rate, elapsed) else testP) else flightStaticHpa,
            leftLeak = leftLeak,
            rightLeak = rightLeak,
        )
    }

    fun flightInputs(s: SimState): FlightInputs {
        val trainer = flightStateActive(s)
        return FlightInputs(
            altFt = if (trainer) nf(s.psFlightAltFt) else 0.0,
            casKt = if (trainer) nf(s.psFlightCasKt) else 0.0,
            vsiFpm = if (trainer) nf(s.psFlightVsFpm) else 0.0,
            trainer = trainer,
        )
    }

    private fun normalStaticPressure(s: SimState, side: String, flightStaticHpa: Double): NormalStatic {
        val portL = if (side == "capt") s.psStaticCaptL || s.psPitotCaptCover else s.psStaticFoL || s.psPitotAux2Cover
        val portR = if (side == "capt") s.psStaticCaptR || s.psPitotAux1Cover else s.psStaticFoR || s.psPitotFoCover
        val frozen = if (side == "capt") nf(s.psStaticCaptFrozenAltFt, nf(s.psFlightAltFt)) else nf(s.psStaticFoFrozenAltFt, nf(s.psFlightAltFt))
        val openCount = (if (!portL) 1 else 0) + (if (!portR) 1 else 0)
        val leak = if (side == "capt") s.psStaticCaptLeak else s.psStaticFoLeak
        val elapsed = nf(s.psTestClockSec)
        val testTarget = s.psTestTarget
        val sideUpper = side.uppercase(Locale.ROOT)
        val vacuumThisSide = s.psTestVacuumSource && (testTarget == "ALL" || testTarget == sideUpper)
        val target = if (vacuumThisSide) atmosphere(22500.0).pHpa else flightStaticHpa
        val rate = nf(s.psStaticLeakRatePctPerSec, 0.5)

        if (openCount > 0) {
            val pressureHpa = if (vacuumThisSide) leakToward(flightStaticHpa, target, leak, rate, elapsed) else flightStaticHpa
            return NormalStatic(ok = true, pressureHpa = pressureHpa, onePortBlocked = openCount == 1, trapped = false, leak = leak)
        }
        val trappedPressure = atmosphere(frozen).pHpa
        val pressureHpa = if (leak) leakToward(flightStaticHpa, trappedPressure, leak, rate, elapsed) else trappedPressure
        return NormalStatic(ok = true, pressureHpa = pressureHpa, onePortBlocked = false, trapped = true, leak = leak)
    }

    /** Standard-pressure altitude adjusted by the selected barometric reference. */
    private fun indicatedAltitude(pressureAltFt: Double, qnhHpa: Double): Double {
        if (!qnhHpa.isFinite() || qnhHpa <= 0) return pressureAltFt
        return pressureAltFt + (qnhHpa - SEA_LEVEL_HPA) * 27.0
    }

    private data class CadcPower(val cbClosed: Boolean, val ac115: Boolean, val ac26: Boolean, val powered: Boolean)

    private fun cadcPower(s: SimState, channel: Int, acPower: Boolean): CadcPower {
        val cbClosed = if (channel == 1) s.psCadc1Cb else s.psCadc2Cb
        // The 115 V AC and 26 V AC inputs remain separately faultable.
        val ac115 = (if (channel == 1) s.psCadc1Ac115 else s.psCadc2Ac115) && acPower
        val ac26 = (if (channel == 1) s.psCadc1Ac26 else s.psCadc2Ac26) && acPower
        return CadcPower(cbClosed, ac115, ac26, cbClosed && ac115 && ac26)
    }

    private fun cadcProcess(
        s: SimState,
        channel: Int,
        psHpa: Double,
        ptHpa: Double,
        tiK: Double,
        vsiFpm: Double,
        qnhHpa: Double,
        acPower: Boolean,
    ): CadcData {
        val pwr = cadcPower(s, channel, acPower)
        val mode = (if (channel == 1) s.psCadc1Test else s.psCadc2Test).uppercase(Locale.ROOT)
        val testRequested = mode != "OFF"
        val testValid = (mode == "FUNCTION" || mode == "SLEW") && pwr.powered
        val failed = mode == "FAIL" || !pwr.powered
        var testPs = psHpa
        var testPt = ptHpa
        var testVsi = vsiFpm
        if (mode == "FUNCTION") {
            testPs = atmosphere(10000.0).pHpa
            testPt = testPs + impactPressureFromCas(180.0)
        } else if (mode == "SLEW") {
            testPs = atmosphere(max(-5000.0, altitudeFromStatic(psHpa) + 600)).pHpa
            testPt = testPs + max(0.0, ptHpa - psHpa)
            testVsi = 600.0
        }
        val effectivePs = if (failed) psHpa else if (testRequested && testValid) testPs else psHpa
        val effectivePt = if (failed) ptHpa else if (testRequested && testValid) testPt else ptHpa
        val q = max(0.0, effectivePt - effectivePs)
        val ias = casFromDynamicPressure(q)
        val mach = machFromDynamicPressure(q, effectivePs)
        val pressureAlt = altitudeFromStatic(effectivePs)
        val correctedAlt = indicatedAltitude(pressureAlt, qnhHpa)
        val flags = mutableListOf<String>()
        if (!pwr.cbClosed) flags.add("CADC CB OPEN")
        if (!pwr.ac115) flags.add("115V AC FAIL")
        if (!pwr.ac26) flags.add("26V AC FAIL")
        if (mode == "FAIL") flags.add("CADC TEST FAIL")
        if (testRequested && !testValid) flags.add("TEST INVALID")
        val altitudeRate = if (failed) 0.0 else if (testRequested && testValid) testVsi else vsiFpm
        return CadcData(
            channel = channel,
            powered = pwr.powered,
            ac115 = pwr.ac115,
            ac26 = pwr.ac26,
            cbClosed = pwr.cbClosed,
            testMode = mode,
            testValid = testValid,
            failed = failed,
            inputs = CadcInputs(effectivePs, effectivePt, tiK),
            outputs = CadcOutputs(correctedAlt, ias, mach, altitudeRate, tiK),
            flags = flags,
        )
    }

    private fun sideData(s: SimState, side: String, dcPower: Boolean, acPower: Boolean, channel: Int, shared: FlightInputs): SideAirData {
        val p = probe(s, side, acPower, dcPower)
        val altFt = shared.altFt
        val env = atmosphere(altFt)
        val testTarget = s.psTestTarget
        val alt = alternateStatic(s, env.pHpa)
        val normal = normalStaticPressure(s, side, env.pHpa)
        val crossTestActive = s.psTestVacuumSource && (testTarget == "AUX1" || testTarget == "AUX2")
        val crossTestLeak = if (side == "capt") s.psAux1StaticLeak else s.psAux2StaticLeak
        val crossTestP = atmosphere(22500.0).pHpa
        if (crossTestActive && crossTestLeak) {
            normal.pressureHpa = leakToward(env.pHpa, crossTestP, true, nf(s.psStaticLeakRatePctPerSec, 0.5), nf(s.psTestClockSec))
        }
        val selected = source(s, side)
        // The AMM includes a selector-valve leakage check after the alternate-static vacuum test with the selector in NORMAL.
        val selectorLeak = if (side == "capt") s.psStaticSelectorCaptLeak else s.psStaticSelectorFoLeak
        if (selected == "NORMAL" && s.psTestVacuumSource && testTarget == "ALTERNATE" && selectorLeak) {
            val alternateTestPressure = if (side == "capt") alt.leftPressureHpa else alt.rightPressureHpa
            normal.pressureHpa = leakToward(normal.pressureHpa, alternateTestPressure, true, nf(s.psStaticLeakRatePctPerSec, 0.5), nf(s.psTestClockSec))
        }
        val normalSelectedPressure = if (selected == "NORMAL") normal.pressureHpa else if (side == "capt") alt.leftPressureHpa else alt.rightPressureHpa
        val selectedStaticOk = if (selected == "NORMAL") normal.ok else if (side == "capt") alt.leftOk else alt.rightOk
        val sideUpper = side.uppercase(Locale.ROOT)
        val normalTestActive = s.psTestVacuumSource && (testTarget == "ALL" || testTarget == sideUpper) && selected == "NORMAL"
        val staticPressureHpa = if (normalTestActive) atmosphere(22500.0).pHpa else normalSelectedPressure
        val staticOk = if (normalTestActive) true else selectedStaticOk

        val qnhHpa = if (side == "capt") s.psBaroCaptHpa else s.psBaroFoHpa
        val testPitotThisSide = testTarget == "ALL" || testTarget == sideUpper
        val baseQ = if (s.psTestPressureSource && testPitotThisSide) impactPressureFromCas(300.0) else impactPressureFromCas(shared.casKt)
        val pitotLeak = if (side == "capt") s.psPitotCaptLeak else s.psPitotFoLeak
        val leakedQ = leakToward(0.0, baseQ, pitotLeak, nf(s.psPitotLeakRatePctPerSec, 0.5), nf(s.psTestClockSec))
        val effectiveQ = if (p.pitotOk) leakedQ else 0.0
        val pitotPressureHpa = staticPressureHpa + effectiveQ
        val dynamicPressureHpa = max(0.0, pitotPressureHpa - staticPressureHpa)
        val tiK = atmosphere(altFt).tempK
        val cadcVsi = if (staticOk && !normal.trapped) shared.vsiFpm else 0.0
        val cadc = cadcProcess(s, channel, staticPressureHpa, pitotPressureHpa, tiK, cadcVsi, qnhHpa, acPower)
        val usable = p.pitotOk && staticOk && cadc.powered && !cadc.failed
        val iasKt = if (usable) cadc.outputs.indicatedAirspeedKt else 0.0
        val mach = if (usable) cadc.outputs.mach else 0.0
        val altInd = if (staticOk && cadc.powered && !cadc.failed) cadc.outputs.correctedAltitudeFt else 0.0
        val vsi = if (staticOk && cadc.powered && !cadc.failed) cadc.outputs.altitudeRateFpm else 0.0

        val flags = mutableListOf<String>()
        if (!p.pitotOk) flags.add(if (p.coverOn) "COVER" else if (p.iced) "PITOT ICE" else "PITOT BLOCKED")
        if (selected == "NORMAL" && !p.staticOk) flags.add("STATIC BLOCKED")
        if (selected == "NORMAL" && normal.onePortBlocked) flags.add("ONE PORT BLOCKED")
        if (normal.trapped && selected == "NORMAL") flags.add("STATIC TRAPPED")
        if (selected == "ALTERNATE") {
            flags.add("ALT STATIC")
            if (!(if (side == "capt") alt.leftOk else alt.rightOk)) flags.add("ALT STATIC FAULT")
        }
        if (p.heaterOn) flags.add("PITOT HEAT")
        if (p.heaterFault) flags.add("PITOT HEAT FAULT")
        if (pitotLeak) flags.add("PITOT LEAK")
        if (normal.leak || alt.leftLeak || alt.rightLeak) flags.add("STATIC LEAK")

        return SideAirData(
            iasKt = iasKt,
            altFt = altInd,
            vsiFpm = vsi,
            mach = mach,
            pitotOk = p.pitotOk,
            staticOk = staticOk,
            staticSource = selected,
            flags = flags,
            probe = p,
            staticSourceState = StaticSourceState(
                leftOk = alt.leftOk,
                rightOk = alt.rightOk,
                selected = selected,
                selectedOk = staticOk,
                pressureHpa = staticPressureHpa,
                source = if (selected == "NORMAL") "NORMAL" else if (side == "capt") "ALTERNATE_LEFT" else "ALTERNATE_RIGHT",
            ),
            pitotPressureHpa = pitotPressureHpa,
            staticPressureHpa = staticPressureHpa,
            dynamicPressureHpa = dynamicPressureHpa,
            adc = AdcResult(channel, usable, cadc),
        )
    }

    /** TS computeAirData. */
    fun computeAirData(s: SimState, dcPower: Boolean = true, acPower: Boolean = true): AirData {
        val dc = dcPower
        val ac = acPower
        val flight = flightInputs(s)
        val capt = sideData(s, "capt", dc, ac, 1, flight)
        val fo = sideData(s, "fo", dc, ac, 2, flight)
        val aux1 = auxiliaryProbe(s, "aux1", ac, dc)
        val aux2 = auxiliaryProbe(s, "aux2", ac, dc)
        val env = atmosphere(flight.altFt)
        val testTarget = s.psTestTarget
        val alt = alternateStatic(s, env.pHpa)
        val rate = nf(s.psStaticLeakRatePctPerSec, 0.5)
        val clock = nf(s.psTestClockSec)
        val vacuum = s.psTestVacuumSource
        val pressure = s.psTestPressureSource
        val aux1StaticPressureHpa = if (vacuum && (testTarget == "ALL" || testTarget == "AUX1"))
            leakToward(env.pHpa, atmosphere(22500.0).pHpa, s.psAux1StaticLeak, rate, clock) else env.pHpa
        val aux2StaticPressureHpa = if (vacuum && (testTarget == "ALL" || testTarget == "AUX2"))
            leakToward(env.pHpa, atmosphere(22500.0).pHpa, s.psAux2StaticLeak, rate, clock) else env.pHpa

        // Standby ALT/AS indicator: pneumatic, so loss of the main DC bus does not erase it.
        val standbyPitotBlocked = false
        val standbyStaticBlocked = false
        val standbyPitotLeak = s.psStandbyPitotLeak
        val standbyStaticLeak = s.psStandbyStaticLeak
        val standbyTest = vacuum && (testTarget == "ALL" || testTarget == "CAPT")
        val standbyTestP = atmosphere(22500.0).pHpa
        val frozenAlt = nf(s.psStandbyStaticFrozenAltFt, flight.altFt)
        val standbyStaticBase = if (standbyTest) standbyTestP
            else if (frozenAlt != flight.altFt && standbyStaticBlocked) atmosphere(frozenAlt).pHpa else env.pHpa
        val standbyStaticP = if (standbyTest) leakToward(env.pHpa, standbyTestP, standbyStaticLeak, rate, clock)
            else if (standbyStaticLeak && standbyStaticBlocked) leakToward(env.pHpa, standbyStaticBase, standbyStaticLeak, rate, clock)
            else standbyStaticBase
        val standbyQBase = impactPressureFromCas(flight.casKt)
        val standbyQ = if (pressure && (testTarget == "ALL" || testTarget == "CAPT"))
            leakToward(0.0, impactPressureFromCas(300.0), standbyPitotLeak, nf(s.psPitotLeakRatePctPerSec, 0.5), clock)
            else leakToward(0.0, standbyQBase, standbyPitotLeak, nf(s.psPitotLeakRatePctPerSec, 0.5), clock)
        val standbyPitotOk = !standbyPitotBlocked
        val standbyStaticOk = !standbyStaticBlocked
        val standbyFlags = mutableListOf<String>()
        if (!standbyPitotOk) standbyFlags.add("STANDBY PITOT FAULT")
        if (!standbyStaticOk) standbyFlags.add("STANDBY STATIC FAULT")
        val standby = StandbyData(
            iasKt = if (standbyPitotOk && standbyStaticOk) casFromDynamicPressure(standbyQ) else 0.0,
            altFt = if (standbyStaticOk) indicatedAltitude(altitudeFromStatic(standbyStaticP), nf(s.psBaroStbyHpa, SEA_LEVEL_HPA)) else 0.0,
            vsiFpm = if (standbyStaticOk) flight.vsiFpm else 0.0,
            mach = if (standbyPitotOk && standbyStaticOk) machFromDynamicPressure(standbyQ, standbyStaticP) else 0.0,
            pitotOk = standbyPitotOk,
            staticOk = standbyStaticOk,
            flags = standbyFlags,
            pitotPressureHpa = standbyStaticP + standbyQ,
            staticPressureHpa = standbyStaticP,
        )

        val aux1PitotLeakRate = nf(s.psPitotLeakRatePctPerSec, 0.5)
        val aux1PitotPressureHpa = if (pressure && (testTarget == "ALL" || testTarget == "AUX1"))
            env.pHpa + (if (aux1.pitotOk) leakToward(0.0, impactPressureFromCas(300.0), s.psAux1PitotLeak, aux1PitotLeakRate, clock) else 0.0)
            else env.pHpa + (if (aux1.pitotOk) leakToward(0.0, impactPressureFromCas(flight.casKt), s.psAux1PitotLeak, aux1PitotLeakRate, clock) else 0.0)
        val aux2PitotPressureHpa = if (pressure && (testTarget == "ALL" || testTarget == "AUX2"))
            env.pHpa + (if (aux2.pitotOk) leakToward(0.0, impactPressureFromCas(300.0), s.psAux2PitotLeak, aux1PitotLeakRate, clock) else 0.0)
            else env.pHpa + (if (aux2.pitotOk) leakToward(0.0, impactPressureFromCas(flight.casKt), s.psAux2PitotLeak, aux1PitotLeakRate, clock) else 0.0)

        val heaterStates = HeaterStates(
            captUpperL = heatState(s, "capt", ac, dc),
            captLowerR = componentlessHeater(s.psPitotStaticA, s.psCbCaptLowerL, ac, dc),
            foUpperR = heatState(s, "fo", ac, dc),
            foLowerL = componentlessHeater(s.psPitotStaticB, s.psCbFoLowerR, ac, dc),
            elevatorL = componentHeatState(s.psPitotStaticA, s.psCbElevL, ac, dc),
            elevatorR = componentHeatState(s.psPitotStaticB, s.psCbElevR, ac, dc),
            temperatureL = componentHeatState(s.psPitotStaticA, s.psCbTempL, ac, dc),
            temperatureR = componentHeatState(s.psPitotStaticB, s.psCbTempR, ac, dc),
        )

        val testPressureActive = s.psTestPressureSource
        val testVacuumActive = s.psTestVacuumSource
        fun pitotTarget(target: String) = testPressureActive && (testTarget == "ALL" || testTarget == target)
        fun staticTarget(target: String) = testVacuumActive && (testTarget == "ALL" || testTarget == target)
        val elapsedMin = max(clock / 60, 1.0 / 60)
        fun pitotLossRate(observedKt: Double) = max(0.0, 300 - observedKt) / elapsedMin
        fun staticDriftRate(observedFt: Double) = abs(observedFt - 22500) / elapsedMin
        val pitotLimitKt = 5.0
        val staticLimitFt = 400.0
        fun pitotLeak(target: String, observed: Double): LeakPitot =
            if (pitotTarget(target)) {
                val rate = pitotLossRate(observed)
                LeakPitot(observed, max(0.0, 300 - observed), rate, rate <= pitotLimitKt)
            } else LeakPitot(null, null, null, null)
        fun staticLeak(target: String, observed: Double): LeakStatic =
            if (staticTarget(target)) {
                val rate = staticDriftRate(observed)
                LeakStatic(observed, abs(observed - 22500), rate, rate <= staticLimitFt)
            } else LeakStatic(null, null, null, null)
        val aux1Cas = casFromDynamicPressure(max(0.0, aux1PitotPressureHpa - env.pHpa))
        val aux2Cas = casFromDynamicPressure(max(0.0, aux2PitotPressureHpa - env.pHpa))
        val pitotLeakage = PitotLeakage(
            capt = pitotLeak("CAPT", capt.iasKt),
            fo = pitotLeak("FO", fo.iasKt),
            aux1 = pitotLeak("AUX1", aux1Cas),
            aux2 = pitotLeak("AUX2", aux2Cas),
        )
        val staticLeakage = StaticLeakage(
            capt = staticLeak("CAPT", capt.altFt),
            fo = staticLeak("FO", fo.altFt),
            aux1 = staticLeak("AUX1", altitudeFromStatic(aux1StaticPressureHpa)),
            aux2 = staticLeak("AUX2", altitudeFromStatic(aux2StaticPressureHpa)),
            alternate = staticLeak("ALTERNATE", capt.altFt),
        )

        val machThreshold = MACH_WARNING_THRESHOLD
        val machTest = s.psMachWarningTest
        val inhibited = s.psAirspeedWarningInhibit
        val cadcCapt = capt.adc.data
        val cadcFo = fo.adc.data

        return AirData(
            capt = capt,
            fo = fo,
            standby = standby,
            aux1PitotOk = aux1.pitotOk,
            aux2PitotOk = aux2.pitotOk,
            aux1PitotPressureHpa = aux1PitotPressureHpa,
            aux2PitotPressureHpa = aux2PitotPressureHpa,
            aux1StaticPressureHpa = aux1StaticPressureHpa,
            aux2StaticPressureHpa = aux2StaticPressureHpa,
            aux1StaticOk = aux1.staticOk,
            aux2StaticOk = aux2.staticOk,
            aux1 = aux1,
            aux2 = aux2,
            elevPitotLOk = !s.psElevPitotLBlocked,
            elevPitotROk = !s.psElevPitotRBlocked,
            alternateStatic = alt,
            cadc = mapOf("capt" to cadcCapt, "fo" to cadcFo),
            flight = flight.copy(staticPressureHpa = env.pHpa),
            staticPressureHpa = env.pHpa,
            disagree = abs(capt.iasKt - fo.iasKt) > 3 || abs(capt.vsiFpm - fo.vsiFpm) > 150,
            coverOnInMotion = flight.casKt > 40 && (capt.probe.coverOn || fo.probe.coverOn || aux1.coverOn || aux2.coverOn),
            architecture = Architecture(
                probeCount = 4,
                alternateStaticPortCount = 2,
                staticSourceSelectors = 2,
                auxiliarySystems = 2,
                heaterCircuits = 8,
                drainTrapCount = 2,
                testFittingNetwork = true,
                leakageModel = true,
                cadcCount = 2,
                cadcIndependentPower = true,
                standbyPneumatic = true,
                heaterStates = heaterStates,
                normalStaticTopology = mapOf("capt" to listOf("UPPER-LEFT", "LOWER-RIGHT"), "fo" to listOf("UPPER-RIGHT", "LOWER-LEFT")),
                auxiliaryStaticTopology = mapOf("aux1" to "LOWER-RIGHT", "aux2" to "LOWER-LEFT", "crossLeakTests" to mapOf("aux1Capt" to true, "aux2Fo" to true)),
                alternateStaticTopology = mapOf("capt" to "LEFT FLUSH BODY-MOUNTED STATIC PORTS", "fo" to "RIGHT FLUSH BODY-MOUNTED STATIC PORTS"),
                selectorValveLeakage = mapOf("capt" to s.psStaticSelectorCaptLeak, "fo" to s.psStaticSelectorFoLeak),
                drainTestFittings = mapOf("alternate" to 5, "aux2" to 8, "fo" to 9, "capt" to 11, "aux1" to 12),
            ),
            downstream = Downstream(
                machAirspeedWarning = MachWarning(
                    capt = (capt.mach >= machThreshold || machTest) && !inhibited && capt.adc.valid,
                    fo = (fo.mach >= machThreshold || machTest) && !inhibited && fo.adc.valid,
                    test = machTest,
                    inhibited = inhibited,
                ),
                cabinDifferential = mapOf(
                    "staticPressureHpa" to aux1StaticPressureHpa,
                    "pressureAltitudeFt" to altitudeFromStatic(aux1StaticPressureHpa),
                ),
                pressureController = mapOf(
                    "captStaticValid" to capt.staticOk,
                    "foStaticValid" to fo.staticOk,
                    "captPitotValid" to capt.pitotOk,
                    "foPitotValid" to fo.pitotOk,
                ),
                autopilot = mapOf(
                    "cadc1Valid" to capt.adc.valid,
                    "altitudeFt" to cadcCapt.outputs.correctedAltitudeFt,
                    "airspeedKt" to cadcCapt.outputs.indicatedAirspeedKt,
                    "mach" to cadcCapt.outputs.mach,
                ),
                flightDirector = mapOf(
                    "cadc1Valid" to capt.adc.valid,
                    "cadc2Valid" to fo.adc.valid,
                    "captAltitudeFt" to cadcCapt.outputs.correctedAltitudeFt,
                    "foAltitudeFt" to cadcFo.outputs.correctedAltitudeFt,
                ),
                machTrim = mapOf("cadc1Valid" to capt.adc.valid, "mach" to cadcCapt.outputs.mach),
                flapLoadLimiter = mapOf(
                    "auxiliaryStaticValid" to (aux1.staticOk && aux2.staticOk),
                    "speedKt" to max(capt.iasKt, fo.iasKt),
                ),
                flightRecorder = mapOf(
                    "captAirDataValid" to capt.adc.valid,
                    "foAirDataValid" to fo.adc.valid,
                    "staticValid" to (capt.staticOk && fo.staticOk),
                ),
            ),
            maintenance = Maintenance(
                drainTraps = mapOf("captStatic" to true, "foStatic" to true),
                testFittings = mapOf(
                    "pressure" to pressure,
                    "vacuum" to vacuum,
                    "target" to testTarget,
                    "map" to mapOf(5 to "ALTERNATE STATIC", 8 to "AUX 2 STATIC", 9 to "F/O STATIC", 11 to "CAPT STATIC", 12 to "AUX 1 STATIC"),
                ),
                crossLeakTests = mapOf(
                    "aux1ToCapt" to (vacuum && testTarget == "AUX1"),
                    "aux2ToFo" to (vacuum && testTarget == "AUX2"),
                ),
                selectorLeakage = mapOf("capt" to s.psStaticSelectorCaptLeak, "fo" to s.psStaticSelectorFoLeak),
                elapsedSec = clock,
                leakageLimits = mapOf("pitotKtPerMin" to pitotLimitKt, "staticFtPerMin" to staticLimitFt),
                instrumentTolerances = mapOf("airspeedKt" to 3.0, "mach" to 0.012, "electricVsiFpm" to 150.0, "altimeterFt" to "SEE AMM TABLE"),
                leakage = pitotLeakage to staticLeakage,
            ),
        )
    }

    private fun componentlessHeater(groupOn: Boolean, cb: Boolean, acPower: Boolean, dcPower: Boolean): HeaterState {
        val on = groupOn && acPower && cb
        return HeaterState(requested = groupOn, on = on, fault = groupOn && (!acPower || !cb), indicatorOn = dcPower && !on)
    }
}
