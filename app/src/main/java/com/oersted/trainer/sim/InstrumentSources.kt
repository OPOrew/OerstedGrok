package com.oersted.trainer.sim

import com.oersted.trainer.sim.Sources.SourceSelect
import com.oersted.trainer.sim.Sources.SrcFail
import com.oersted.trainer.sim.Sources.sourcesOf
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Port of instrumentSources.ts (ATA 34 / 22, AMM-aligned multi-channel instrument sources).
 * Independent vertical gyro channels (VG-1, VG-2, AUX): freeze, drift, tumble, erect (~60 s) with cutout in
 * manoeuvres, re-erect after power interrupt. Compass channels support SLAVED/DG and the SYNC flag.
 */
object InstrumentSources {
    /** Gyro erect state: 0 = caged/off, 1 = erecting, 2 = erected. */
    const val ERECT_OFF = 0
    const val ERECT_ERECTING = 1
    const val ERECT_ERECTED = 2

    private const val ERECT_SEC_NORMAL = 60.0
    private const val ERECT_SEC_FAST = 2.5
    /** Free gyro drift (deg/s) when valid but slightly imperfect */
    private const val DRIFT_PITCH = 0.008
    private const val DRIFT_ROLL = 0.012
    /** Tumble threshold (deg/s body rate) */
    private const val TUMBLE_RATE = 40.0

    data class GyroChannel(
        /** "gyro1", "gyro2" or "gyroAux" */
        val id: String,
        val powered: Boolean,
        /** 0 = caged/off, 1 = erecting, 2 = erected */
        val erect: Int,
        val failed: Boolean,
        val valid: Boolean,
        val pitch: Double,
        val roll: Double,
        val frozen: Boolean,
        val tumbled: Boolean,
    )

    data class CompassChannel(
        /** "comp1" or "comp2" */
        val id: String,
        val powered: Boolean,
        val failed: Boolean,
        val valid: Boolean,
        val hdg: Double,
        val syncFlag: Boolean,
        /** 0 or 1 */
        val mode: Int,
    )

    data class NavRadioPower(
        val nav1Vor: Boolean,
        val nav2Vor: Boolean,
        val nav1Gs: Boolean,
        val nav2Gs: Boolean,
        val nav1: Boolean,
        val nav2: Boolean,
        val dme1: Boolean,
        val dme2: Boolean,
        val adf1: Boolean,
        val adf2: Boolean,
        val comm1: Boolean,
        val comm2: Boolean,
        val hf1: Boolean,
        val hf2: Boolean,
        val lrra1: Boolean,
        val lrra2: Boolean,
    )

    /** Breaker and power controls feeding the sources. */
    data class SrcPower(
        val nav1Cb: Boolean = true,
        val nav2Cb: Boolean = true,
        val nav1GsCb: Boolean = true,
        val nav2GsCb: Boolean = true,
        val dme1Cb: Boolean = true,
        val dme2Cb: Boolean = true,
        val adf1Cb: Boolean = true,
        val adf2Cb: Boolean = true,
        val comm1Cb: Boolean = true,
        val comm2Cb: Boolean = true,
        val hf1Cb: Boolean = true,
        /** Trainer-separate HF-2 protection control; confirm exact aircraft breaker wiring by WDM/effectivity. */
        val hf2Cb: Boolean = true,
        val lrra1Cb: Boolean = true,
        val lrra2Cb: Boolean = true,
        val gyro1Cb: Boolean = true,
        val gyro2Cb: Boolean = true,
        val gyroAuxCb: Boolean = true,
        val comp1Cb: Boolean = true,
        val comp2Cb: Boolean = true,
        val gyroFastErect: Boolean = false,
    )

    val DEFAULT_SRC_POWER = SrcPower()

    data class GyroMemory(
        val pitch: Double = 0.0,
        val roll: Double = 0.0,
        /** accumulated free drift offsets */
        val driftP: Double = 0.0,
        val driftR: Double = 0.0,
        val tumbled: Boolean = false,
        /** was powered last frame: detects power interrupt */
        val wasPowered: Boolean = false,
    )

    data class ErectTimers(val g1: Double = 0.0, val g2: Double = 0.0, val gAux: Double = 0.0)

    data class GyroMemories(val g1: GyroMemory, val g2: GyroMemory, val gAux: GyroMemory)

    data class CompassSync(val c1: Boolean = false, val c2: Boolean = false)

    data class CompassMode(val c1: Int = 0, val c2: Int = 0)

    data class SideInstruments(
        val attValid: Boolean,
        val hdgValid: Boolean,
        val navPowered: Boolean,
        val navGsPowered: Boolean,
        val pitch: Double,
        val roll: Double,
        val hdg: Double,
    )

    data class Gyros(val gyro1: GyroChannel, val gyro2: GyroChannel, val gyroAux: GyroChannel)

    data class Compasses(val comp1: CompassChannel, val comp2: CompassChannel)

    data class ResolvedInstruments(
        val gyros: Gyros,
        val compass: Compasses,
        val radioPower: NavRadioPower,
        val capt: SideInstruments,
        val fo: SideInstruments,
        val erectTimers: ErectTimers,
        val gyroMem: GyroMemories,
    )

    data class ErectStep(val erect: Int, val timer: Double)

    /** TS stepErect: gyro erect sequencing with cutout and forced re-erect. */
    fun stepErect(
        prev: Int,
        powered: Boolean,
        failed: Boolean,
        fast: Boolean,
        dtSec: Double,
        erectTimer: Double,
        cutout: Boolean = false,
        /** force re-erect after power interrupt */
        forceReerect: Boolean = false,
    ): ErectStep {
        if (!powered || failed) return ErectStep(ERECT_OFF, 0.0)
        if (forceReerect) return ErectStep(ERECT_ERECTING, 0.0)
        if (prev == ERECT_ERECTED) return ErectStep(ERECT_ERECTED, 0.0)
        if (cutout) return ErectStep(if (prev == ERECT_OFF) ERECT_ERECTING else prev, erectTimer)
        val need = if (fast) ERECT_SEC_FAST else ERECT_SEC_NORMAL
        val t = erectTimer + dtSec
        if (prev == ERECT_OFF) return ErectStep(ERECT_ERECTING, t)
        if (t >= need) return ErectStep(ERECT_ERECTED, 0.0)
        return ErectStep(ERECT_ERECTING, t)
    }

    private data class GyroStep(val ch: GyroChannel, val timer: Double, val mem: GyroMemory)

    /** TS resolveInstrumentSources. Optional TS fields have Kotlin defaults that match the TS `??` fallbacks. */
    fun resolveInstrumentSources(
        truePitch: Double,
        trueRoll: Double,
        trueHdg: Double,
        acPower: Boolean,
        dcPower: Boolean,
        ac115: Boolean = acPower,
        ac26: Boolean = acPower,
        srcFail: SrcFail,
        srcPower: SrcPower,
        sel: SourceSelect,
        erectTimers: ErectTimers = ErectTimers(),
        dtSec: Double = 0.1,
        auxBiasDeg: Double = 0.4,
        compSyncFlag: CompassSync = CompassSync(),
        compMode: CompassMode = CompassMode(),
        gyroMem: GyroMemories? = null,
        pitchRate: Double = 0.0,
        rollRate: Double = 0.0,
    ): ResolvedInstruments {
        val dc = dcPower
        val dt = dtSec
        val f = srcFail
        val p = srcPower
        val bias = auxBiasDeg
        val cutout = abs(pitchRate) > 3 || abs(rollRate) > 5
        val tumbling = abs(pitchRate) > TUMBLE_RATE || abs(rollRate) > TUMBLE_RATE
        val emptyMem = GyroMemory()
        val mem = gyroMem ?: GyroMemories(emptyMem, emptyMem, emptyMem)

        fun mkGyro(
            id: String,
            cb: Boolean,
            failed: Boolean,
            prevErect: Int,
            timerValue: Double,
            pitchOff: Double,
            rollOff: Double,
            last: GyroMemory,
            driftSign: Double,
        ): GyroStep {
            val powered = ac115 && dc && cb
            val powerInterrupt = last.wasPowered && !powered
            val forceReerect = powerInterrupt
            // tumble clears erect
            var erectIn = prevErect
            var tumbled = last.tumbled
            if (tumbling && powered) {
                tumbled = true
                erectIn = ERECT_OFF
            }
            if (powered && !tumbling && tumbled && prevErect == ERECT_OFF) {
                // start re-erect after tumble stops
                tumbled = false
            }
            val stepped = stepErect(
                erectIn,
                powered,
                failed || tumbled,
                p.gyroFastErect,
                dt,
                timerValue,
                cutout,
                forceReerect,
            )
            val valid = powered && !failed && !tumbled && stepped.erect == ERECT_ERECTED

            var driftP = last.driftP
            var driftR = last.driftR
            val pitch: Double
            val roll: Double
            if (valid) {
                // slow independent drift while erected
                driftP += DRIFT_PITCH * driftSign * dt
                driftR += DRIFT_ROLL * driftSign * dt
                // clamp drift accumulation
                driftP = max(-2.0, min(2.0, driftP))
                driftR = max(-2.0, min(2.0, driftR))
                pitch = truePitch + pitchOff + driftP
                roll = trueRoll + rollOff + driftR
            } else {
                pitch = last.pitch
                roll = last.roll
            }

            return GyroStep(
                ch = GyroChannel(
                    id = id,
                    powered = powered,
                    erect = stepped.erect,
                    failed = failed,
                    valid = valid,
                    pitch = pitch,
                    roll = roll,
                    frozen = !valid && powered,
                    tumbled = tumbled,
                ),
                timer = stepped.timer,
                mem = GyroMemory(
                    pitch = pitch,
                    roll = roll,
                    driftP = if (valid) driftP else last.driftP,
                    driftR = if (valid) driftR else last.driftR,
                    tumbled = tumbled,
                    wasPowered = powered,
                ),
            )
        }

        // erect state carried from the previous timer value: negative = erected, zero = off, positive = erecting
        fun erectFromTimer(t: Double): Int = if (t < 0) ERECT_ERECTED else if (t == 0.0) ERECT_OFF else ERECT_ERECTING

        val g1 = mkGyro("gyro1", p.gyro1Cb, f.gyro1, erectFromTimer(erectTimers.g1), erectTimers.g1, 0.0, 0.0, mem.g1, 1.0)
        val g2 = mkGyro("gyro2", p.gyro2Cb, f.gyro2, erectFromTimer(erectTimers.g2), erectTimers.g2, 0.05, -0.03, mem.g2, -1.0)
        val gA = mkGyro("gyroAux", p.gyroAuxCb, f.gyroAux, erectFromTimer(erectTimers.gAux), erectTimers.gAux, bias, -bias * 0.5, mem.gAux, 0.5)

        fun mkComp(id: String, cb: Boolean, failed: Boolean, hdgOff: Double, syncFlag: Boolean, mode: Int): CompassChannel {
            val powered = ac115 && dc && cb
            val valid = powered && !failed && !syncFlag
            return CompassChannel(
                id = id,
                powered = powered,
                failed = failed,
                valid = valid,
                hdg = if (powered && !failed) ((((trueHdg + hdgOff) % 360) + 360) % 360) else 0.0,
                syncFlag = syncFlag,
                mode = mode,
            )
        }
        val c1 = mkComp("comp1", p.comp1Cb, f.comp1, 0.0, compSyncFlag.c1, compMode.c1)
        val c2 = mkComp("comp2", p.comp2Cb, f.comp2, 0.15, compSyncFlag.c2, compMode.c2)

        val radioPower = NavRadioPower(
            nav1Vor = dc && ac26 && p.nav1Cb,
            nav2Vor = dc && ac26 && p.nav2Cb,
            nav1Gs = dc && ac26 && p.nav1GsCb,
            nav2Gs = dc && ac26 && p.nav2GsCb,
            nav1 = dc && ac26 && p.nav1Cb,
            nav2 = dc && ac26 && p.nav2Cb,
            dme1 = ac115 && dc && p.dme1Cb,
            dme2 = ac115 && dc && p.dme2Cb,
            adf1 = dc && p.adf1Cb,
            adf2 = dc && p.adf2Cb,
            comm1 = dc && p.comm1Cb,
            comm2 = dc && p.comm2Cb,
            hf1 = dc && p.hf1Cb,
            hf2 = dc && p.hf2Cb,
            lrra1 = dc && p.lrra1Cb,
            lrra2 = dc && p.lrra2Cb,
        )

        fun pickGyro(side: String): GyroChannel {
            val sel = sel.gyroSel
            return if (side == "capt") { if (sel == 0) gA.ch else g1.ch } else { if (sel == 2) gA.ch else g2.ch }
        }
        fun pickComp(side: String): CompassChannel {
            val sel = sel.compSel
            return if (side == "capt") { if (sel == 2) c2 else c1 } else { if (sel == 0) c1 else c2 }
        }
        fun navVor(side: String): Boolean {
            val s = sel.navSel
            return if (side == "capt") { if (s == 2) radioPower.nav2Vor else radioPower.nav1Vor } else { if (s == 0) radioPower.nav1Vor else radioPower.nav2Vor }
        }
        fun navGs(side: String): Boolean {
            val s = sel.navSel
            return if (side == "capt") { if (s == 2) radioPower.nav2Gs else radioPower.nav1Gs } else { if (s == 0) radioPower.nav1Gs else radioPower.nav2Gs }
        }

        val cg = pickGyro("capt")
        val fg = pickGyro("fo")
        val cc = pickComp("capt")
        val fc = pickComp("fo")
        val sideMap = sourcesOf(sel, f)

        fun packSide(g: GyroChannel, c: CompassChannel, vor: Boolean, gs: Boolean, softAtt: Boolean, softHdg: Boolean): SideInstruments =
            SideInstruments(
                attValid = g.valid && !softAtt,
                hdgValid = c.valid && !softHdg,
                navPowered = vor,
                navGsPowered = gs,
                pitch = g.pitch,
                roll = g.roll,
                hdg = if (c.powered && !c.failed) c.hdg else 0.0,
            )

        return ResolvedInstruments(
            gyros = Gyros(gyro1 = g1.ch, gyro2 = g2.ch, gyroAux = gA.ch),
            compass = Compasses(comp1 = c1, comp2 = c2),
            radioPower = radioPower,
            capt = packSide(cg, cc, navVor("capt"), navGs("capt"), sideMap.capt.att, sideMap.capt.hdg),
            fo = packSide(fg, fc, navVor("fo"), navGs("fo"), sideMap.fo.att, sideMap.fo.hdg),
            erectTimers = ErectTimers(
                g1 = if (g1.ch.erect == ERECT_ERECTED) -1.0 else g1.timer,
                g2 = if (g2.ch.erect == ERECT_ERECTED) -1.0 else g2.timer,
                gAux = if (gA.ch.erect == ERECT_ERECTED) -1.0 else gA.timer,
            ),
            gyroMem = GyroMemories(g1.mem, g2.mem, gA.mem),
        )
    }
}
