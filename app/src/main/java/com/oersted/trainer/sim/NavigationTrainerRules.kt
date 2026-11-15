package com.oersted.trainer.sim

import com.oersted.trainer.sim.InstrumentSources.SrcPower
import com.oersted.trainer.sim.MipLocal.MipState
import com.oersted.trainer.sim.Pedestal.PedestalState
import com.oersted.trainer.sim.Sources.SrcFail

/**
 * Switch rules behind the Navigation section of the TRAINING console (routes/panel/NavigationTrainer.tsx).
 * Each function is the web's onClick: a copy with one field flipped. Labels and layout stay in the view.
 */
object NavigationTrainerRules {
    /** Source-failure keys in web order (the 4-column grid). */
    val SRC_FAIL_KEYS: List<String> = listOf("gyro1", "gyro2", "gyroAux", "comp1", "comp2", "nav1", "nav2")

    /** Circuit-breaker keys in web order (the 4-column grid). */
    val SRC_POWER_KEYS: List<String> = listOf(
        "gyro1Cb", "gyro2Cb", "gyroAuxCb", "comp1Cb", "comp2Cb",
        "nav1Cb", "nav2Cb", "nav1GsCb", "nav2GsCb",
        "dme1Cb", "dme2Cb", "adf1Cb", "adf2Cb",
        "comm1Cb", "comm2Cb", "hf1Cb", "hf2Cb",
        "lrra1Cb", "lrra2Cb",
    )

    /** Web: mip.set({ srcFail: { ...srcFail, [k]: !srcFail[k] } }). */
    fun toggleSrcFail(m: MipState, key: String): MipState {
        val f = m.srcFail
        val next: SrcFail = when (key) {
            "gyro1" -> f.copy(gyro1 = !f.gyro1)
            "gyro2" -> f.copy(gyro2 = !f.gyro2)
            "gyroAux" -> f.copy(gyroAux = !f.gyroAux)
            "comp1" -> f.copy(comp1 = !f.comp1)
            "comp2" -> f.copy(comp2 = !f.comp2)
            "nav1" -> f.copy(nav1 = !f.nav1)
            "nav2" -> f.copy(nav2 = !f.nav2)
            else -> throw IllegalArgumentException("unknown source-failure key: $key")
        }
        return m.copy(srcFail = next)
    }

    /** Web: mip.set({ srcPower: { ...srcPower, [k]: !srcPower[k] } }). Power true = breaker closed. */
    fun toggleSrcPower(m: MipState, key: String): MipState {
        val p = m.srcPower
        val next: SrcPower = when (key) {
            "gyro1Cb" -> p.copy(gyro1Cb = !p.gyro1Cb)
            "gyro2Cb" -> p.copy(gyro2Cb = !p.gyro2Cb)
            "gyroAuxCb" -> p.copy(gyroAuxCb = !p.gyroAuxCb)
            "comp1Cb" -> p.copy(comp1Cb = !p.comp1Cb)
            "comp2Cb" -> p.copy(comp2Cb = !p.comp2Cb)
            "nav1Cb" -> p.copy(nav1Cb = !p.nav1Cb)
            "nav2Cb" -> p.copy(nav2Cb = !p.nav2Cb)
            "nav1GsCb" -> p.copy(nav1GsCb = !p.nav1GsCb)
            "nav2GsCb" -> p.copy(nav2GsCb = !p.nav2GsCb)
            "dme1Cb" -> p.copy(dme1Cb = !p.dme1Cb)
            "dme2Cb" -> p.copy(dme2Cb = !p.dme2Cb)
            "adf1Cb" -> p.copy(adf1Cb = !p.adf1Cb)
            "adf2Cb" -> p.copy(adf2Cb = !p.adf2Cb)
            "comm1Cb" -> p.copy(comm1Cb = !p.comm1Cb)
            "comm2Cb" -> p.copy(comm2Cb = !p.comm2Cb)
            "hf1Cb" -> p.copy(hf1Cb = !p.hf1Cb)
            "hf2Cb" -> p.copy(hf2Cb = !p.hf2Cb)
            "lrra1Cb" -> p.copy(lrra1Cb = !p.lrra1Cb)
            "lrra2Cb" -> p.copy(lrra2Cb = !p.lrra2Cb)
            else -> throw IllegalArgumentException("unknown circuit-breaker key: $key")
        }
        return m.copy(srcPower = next)
    }

    /** Web: VG FAST ERECT button. */
    fun toggleGyroFastErect(m: MipState): MipState =
        m.copy(srcPower = m.srcPower.copy(gyroFastErect = !m.srcPower.gyroFastErect))

    /** Web: COMP1 / COMP2 HDG-flag buttons. */
    fun toggleCompSyncFlag(m: MipState, comp1: Boolean): MipState =
        if (comp1) m.copy(compSyncFlag = m.compSyncFlag.copy(c1 = !m.compSyncFlag.c1))
        else m.copy(compSyncFlag = m.compSyncFlag.copy(c2 = !m.compSyncFlag.c2))

    /** Web: COMP1 SLAVED / DG (index 0 flips 0 <-> 1). */
    fun toggleCompMode1(p: PedestalState): PedestalState =
        p.copy(compMode = Pair(if (p.compMode.first == 0) 1 else 0, p.compMode.second))

    /** Web: COMP2 SLAVED / DG. */
    fun toggleCompMode2(p: PedestalState): PedestalState =
        p.copy(compMode = Pair(p.compMode.first, if (p.compMode.second == 0) 1 else 0))

    /** Web: RMI N1 CAPT VOR / ADF. */
    fun toggleRmiNeedle1(p: PedestalState): PedestalState =
        p.copy(rmiNeedle1 = Pair(if (p.rmiNeedle1.first == 0) 1 else 0, p.rmiNeedle1.second))

    /** Web: RMI N2 CAPT VOR / ADF. */
    fun toggleRmiNeedle2(p: PedestalState): PedestalState =
        p.copy(rmiNeedle2 = Pair(if (p.rmiNeedle2.first == 0) 1 else 0, p.rmiNeedle2.second))

    /** Web: LRRA1 / LRRA2 TEST / NORM. */
    fun toggleLrraTest(p: PedestalState, first: Boolean): PedestalState =
        if (first) p.copy(lrraTest = Pair(!p.lrraTest.first, p.lrraTest.second))
        else p.copy(lrraTest = Pair(p.lrraTest.first, !p.lrraTest.second))
}
