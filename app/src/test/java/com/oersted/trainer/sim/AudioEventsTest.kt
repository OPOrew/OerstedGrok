package com.oersted.trainer.sim

import com.oersted.trainer.sim.AudioEvents.Extras
import com.oersted.trainer.sim.AudioEvents.Inputs
import com.oersted.trainer.sim.AudioEvents.Kind
import com.oersted.trainer.sim.AudioEvents.RateTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEventsTest {
    private fun ex() = Extras(random = { 0.5 })

    private fun kinds(f: AudioEvents.Frame, k: Kind) = f.events.filter { it.kind == k }

    @Test fun rateHoldsThenDecaysToZero() {
        val r = RateTracker()
        assertEquals(0.0, r.rate("a", 0.0, 0.0), 1e-9)
        assertEquals(5.0, r.rate("a", 0.5, 0.1), 1e-9)   // 0.5 change over a 0.1 s gap
        assertEquals(5.0, r.rate("a", 0.5, 0.3), 1e-9)   // held: value unchanged for 0.2 s
        assertEquals(0.0, r.rate("a", 0.5, 0.6), 1e-9)   // stopped for more than 0.4 s
    }

    @Test fun rateTreatsStaleGapAsTenthOfASecond() {
        val r = RateTracker()
        r.rate("a", 0.0, 0.0)
        assertEquals(1.0, r.rate("a", 0.1, 2.0), 1e-9)   // gap > 0.5 s: dt = 0.1
    }

    @Test fun gearDownLockedFiresLockThunkAtNormalAmplitude() {
        val e = ex()
        e.step(Inputs(gear = listOf(0.97, 0.0, 0.0)), 0.0)
        val f = e.step(Inputs(gear = listOf(0.99, 0.0, 0.0)), 0.4)
        val lt = kinds(f, Kind.LOCK_THUNK)
        assertEquals(1, lt.size)
        assertEquals(0.42, lt[0].amp, 1e-9)
        assertEquals(0.0, lt[0].pan, 1e-9)
    }

    @Test fun gearLeverChangeClunksTwice() {
        val e = ex()
        e.step(Inputs(gearLever = 0.0), 0.0)
        val f = e.step(Inputs(gearLever = 1.0), 0.1)
        assertEquals(2, kinds(f, Kind.CLUNK).size)
    }

    @Test fun touchdownFiresTyreChirpsAndNoseSlap() {
        val e = ex()
        e.step(Inputs(wow = false, groundSpeedKt = 100.0), 0.0)
        val f = e.step(Inputs(wow = true, groundSpeedKt = 100.0), 0.1)
        assertEquals(2, kinds(f, Kind.TYRE_CHIRP).size)
        assertTrue(f.levels.roll > 0.0)
    }

    @Test fun noTouchdownSoundBelowFortyKnots() {
        val e = ex()
        e.step(Inputs(wow = false, groundSpeedKt = 30.0), 0.0)
        val f = e.step(Inputs(wow = true, groundSpeedKt = 30.0), 0.1)
        assertTrue(kinds(f, Kind.TYRE_CHIRP).isEmpty())
    }

    @Test fun speedBrakeMoveClunksOnce() {
        val e = ex()
        e.step(Inputs(speedBrake = 0.0), 0.0)
        val f = e.step(Inputs(speedBrake = 0.5), 0.1)
        val c = kinds(f, Kind.CLUNK)
        assertEquals(2, c.size)
        assertEquals(0.15, c[0].pan, 1e-9)
        assertEquals(0.26, c[0].amp, 1e-9)
        assertEquals(0.1, c[1].delay, 1e-9)
    }

    @Test fun bottleDischargeThumpsOnLeftBottlePan() {
        val e = ex()
        e.step(Inputs(bottles = listOf(false, false)), 0.0)
        val f = e.step(Inputs(bottles = listOf(true, false)), 0.1)
        val t = kinds(f, Kind.THUMP)
        assertEquals(1, t.size)
        assertEquals(-0.3, t[0].pan, 1e-9)
        assertEquals(0.5, t[0].amp, 1e-9)
    }

    @Test fun glideslopeLoopIsSuppressedWhileGpwsTestRuns() {
        val f = ex().step(Inputs(glideslopeWarn = true, gpwsTest = true), 0.0)
        assertTrue(f.loops.gpwsVocab)
        assertFalse(f.loops.glideslope)
        val g = ex().step(Inputs(glideslopeWarn = true), 0.0)
        assertTrue(g.loops.glideslope)
    }

    @Test fun hornFamilyCombinesGearWarningAndConfigHorn() {
        assertTrue(ex().step(Inputs(gearHorn = true), 0.0).loops.configCabinHorn)
        assertTrue(ex().step(Inputs(configOrCabinHorn = true), 0.0).loops.configCabinHorn)
        assertFalse(ex().step(Inputs(), 0.0).loops.configCabinHorn)
    }

    @Test fun ambiencePacksCountsOnlyRunningPacks() {
        val f = ex().step(Inputs(packs = listOf(true, true)), 0.0)
        assertEquals(2, f.levels.ambPacks)
        val g = ex().step(Inputs(packs = listOf(false, true)), 0.0)
        assertEquals(1, g.levels.ambPacks)
    }

    @Test fun brakeOnsetAndReleaseWhooshes() {
        val e = ex()
        e.step(Inputs(wheelBrake = listOf(0.0, 0.0, 0.0, 0.0)), 0.0)
        // brake = (4 x 3000) / 4 / 3000 = 1.0: crosses the onset threshold (0.05) -> 2500 -> 1200 Hz whoosh
        val on = kinds(e.step(Inputs(wheelBrake = listOf(3000.0, 3000.0, 3000.0, 3000.0)), 0.1), Kind.WHOOSH)
        assertEquals(1, on.size)
        assertEquals(2500.0, on[0].f0, 1e-9)
        assertEquals(1200.0, on[0].f1, 1e-9)
        // 1.0 -> 0 crosses the release threshold (0.2 / 0.05) -> 3000 -> 1500 Hz whoosh
        val off = kinds(e.step(Inputs(wheelBrake = listOf(0.0, 0.0, 0.0, 0.0)), 0.2), Kind.WHOOSH)
        assertEquals(1, off.size)
        assertEquals(3000.0, off[0].f0, 1e-9)
        assertEquals(1500.0, off[0].f1, 1e-9)
    }
}
