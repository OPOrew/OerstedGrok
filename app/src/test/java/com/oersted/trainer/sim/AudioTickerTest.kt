package com.oersted.trainer.sim

import com.oersted.trainer.sim.AudioTicker.EngineInputs
import com.oersted.trainer.sim.AudioTicker.TickInputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTickerTest {
    private fun t() = AudioTicker(random = { 0.5 })

    private fun inp(lever: Boolean = false, n2: Double = 0.0, sw: Boolean = false, fireE1: Boolean = false) =
        TickInputs(
            startSwitchSelected = listOf(sw, false),
            lever = listOf(lever, false),
            n2 = listOf(n2, 0.0),
            fireE1 = fireE1,
        )

    @Test fun ignitionClickRespectsElevenHundredthsGap() {
        val a = t()
        assertEquals(1, a.tick(inp(lever = true, n2 = 30.0, sw = true), 1.0, true, false).clicks.size)
        assertEquals(0, a.tick(inp(lever = true, n2 = 30.0, sw = true), 1.05, true, false).clicks.size)
        assertEquals(1, a.tick(inp(lever = true, n2 = 30.0, sw = true), 1.2, true, false).clicks.size)
    }

    @Test fun noIgnitionClickWhenStartSwitchOff() {
        assertEquals(0, t().tick(inp(lever = true, n2 = 30.0, sw = false), 1.0, true, false).clicks.size)
    }

    @Test fun lightOffWhenLeverOpensOnTurningEngine() {
        val a = t()
        a.tick(inp(lever = false, n2 = 20.0), 1.0, true, false)
        assertEquals(1, a.tick(inp(lever = true, n2 = 20.0), 2.0, true, false).lightOffs.size)
    }

    @Test fun noLightOffWhenEngineNotTurning() {
        val a = t()
        a.tick(inp(lever = false, n2 = 5.0), 1.0, true, false)
        assertEquals(0, a.tick(inp(lever = true, n2 = 5.0), 2.0, true, false).lightOffs.size)
    }

    @Test fun notReadyTickDoesNothingAndKeepsLeverHistory() {
        val a = t()
        val r = a.tick(inp(lever = true, n2 = 20.0), 1.0, false, false)
        assertEquals(null, r.frame)
        // history was not recorded while not ready, so the lever opening is still seen as new
        assertEquals(1, a.tick(inp(lever = true, n2 = 20.0), 2.0, true, false).lightOffs.size)
    }

    @Test fun fireBellStrikesAlternateGongsWithinLookahead() {
        val r = t().tick(inp(fireE1 = true), 1.0, true, false)
        assertEquals(2, r.bellStrikes.size)
        assertEquals(1.02, r.bellStrikes[0].at, 1e-9)
        assertEquals(-0.08, r.bellStrikes[0].pan, 1e-9)
        assertEquals(1.17, r.bellStrikes[1].at, 1e-9)
        assertEquals(0.08, r.bellStrikes[1].pan, 1e-9)
    }

    @Test fun fireBellSilentWhenSampleLoopIsUsed() {
        val r = t().tick(inp(fireE1 = true), 1.0, true, true)
        assertTrue(r.bellStrikes.isEmpty())
        assertTrue(r.fireBellLoopOn)
    }

    @Test fun enginesSnapOnFirstUpdateAfterReady() {
        val a = t()
        val e = EngineInputs(n1 = listOf(20.0, 20.0), n2 = listOf(15.0, 15.0), startValve = listOf(false, false))
        assertTrue(a.tickEngines(e, false).isEmpty())
        val first = a.tickEngines(e, true)
        assertTrue(first.all { it.snap })
        assertFalse(a.tickEngines(e, true).any { it.snap })
    }

    @Test fun soundOnResetsEngineSnap() {
        val a = t()
        val e = EngineInputs(n1 = listOf(20.0, 20.0), n2 = listOf(15.0, 15.0), startValve = listOf(false, false))
        a.tickEngines(e, true)
        a.onSoundOn()
        assertTrue(a.tickEngines(e, true).all { it.snap })
    }
}
