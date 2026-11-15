package com.oersted.trainer.sim

import com.oersted.trainer.sim.MarkerBeacons.MarkerId
import com.oersted.trainer.sim.MarkerBeacons.MarkerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mapping of sim state to the audio inputs (cockpit.tsx useSimAudio argument), marker gating and the switch-click rule. */
class AudioFeedTest {
    private fun feed(
        s: SimState = SimState(),
        d: Derived = Derived(),
        ped: Pedestal.PedestalState = Pedestal.PedestalState(),
        o: AudioFeed.Overhead = AudioFeed.Overhead(),
        markers: MarkerState = MarkerBeacons.NO_MARKERS,
        navOn: Boolean = false,
        apWarn: Boolean = false,
        gpws: Boolean = false,
    ) = AudioFeed.build(s, d, ped, apWarn, gpws, o, markers, navOn)

    @Test
    fun countsPumpsAndFuelPumps() {
        val d = Derived().apply { hydAPump1On = true; hydBPump2On = true; stbyOn = true; fuelPump1On = true; fuelCenterPumpOn = true }
        val f = feed(d = d)
        assertEquals(3.0, f.tick.extras.hydPumps, 0.0)
        assertTrue(f.tick.extras.elecPumps)
        assertEquals(2.0, f.tick.extras.fuelPumps, 0.0)
    }

    @Test
    fun fireBellFollowsWarnsUnlessSilenced() {
        val d = Derived().apply { dcPower = true; engine1FireWarn = true; cargoAftFireWarn = true }
        val loud = feed(d = d)
        assertTrue(loud.tick.fireE1)
        assertFalse(loud.tick.fireE2)
        assertTrue(loud.tick.fireOther)
        val quiet = feed(d = d, ped = Pedestal.PedestalState(bellSilenced = true))
        assertFalse(quiet.tick.fireE1)
        assertFalse(quiet.tick.fireOther)
    }

    @Test
    fun overheatFireTestRingsEveryBellAndOverridesSilence() {
        val d = Derived().apply { dcPower = true }
        val f = feed(d = d, ped = Pedestal.PedestalState(fireTest = Pedestal.FireTest.OVHT_FIRE, bellSilenced = true))
        assertTrue(f.tick.fireE1 && f.tick.fireE2 && f.tick.fireApu)
        // no DC: no test ring
        val dead = feed(ped = Pedestal.PedestalState(fireTest = Pedestal.FireTest.OVHT_FIRE))
        assertFalse(dead.tick.fireE1)
    }

    @Test
    fun cabinAltitudeHornNeedsDcAndNoCutout() {
        val s = SimState().apply { psFlightAltFt = 30000.0; cabinDiff = 5.0 } // 30000 - 5*1600 = 22000 ft
        assertEquals(22000.0, AudioFeed.cabinAltFt(s), 1e-9)
        val d = Derived().apply { dcPower = true }
        assertTrue(feed(s = s, d = d).tick.extras.configOrCabinHorn)
        assertFalse(feed(s = s, d = d, o = AudioFeed.Overhead(hornCutout = true)).tick.extras.configOrCabinHorn)
        assertFalse(feed(s = s).tick.extras.configOrCabinHorn)
        val low = SimState().apply { psFlightAltFt = 5000.0; cabinDiff = 9.0 } // negative clamps to 0
        assertEquals(0.0, AudioFeed.cabinAltFt(low), 0.0)
    }

    @Test
    fun warningTestsNeedDc() {
        val d = Derived().apply { dcPower = true }
        val f = feed(d = d, o = AudioFeed.Overhead(stallTest = 1, machWarn = 2), gpws = true)
        assertTrue(f.tick.extras.stallWarn && f.tick.extras.machWarn && f.tick.extras.gpwsTest)
        val off = feed(o = AudioFeed.Overhead(stallTest = 1, machWarn = 2), gpws = true)
        assertFalse(off.tick.extras.stallWarn || off.tick.extras.machWarn || off.tick.extras.gpwsTest)
        val s = SimState().apply { psStallWarningTest = true; psMachWarningTest = true }
        val ps = feed(s = s, d = d)
        assertTrue(ps.tick.extras.stallWarn && ps.tick.extras.machWarn)
    }

    @Test
    fun startSwitchAndLeversAndEngineSpeeds() {
        val s = SimState().apply { start1 = "GRD"; start2 = "OFF"; lever1 = true; n2_1 = 25.0; n2_2 = 0.0; n1_1 = 4.0; n1_2 = 0.0 }
        val d = Derived().apply { startValveOpen1 = true }
        val f = feed(s = s, d = d)
        assertEquals(listOf(true, false), f.tick.startSwitchSelected)
        assertEquals(listOf(true, false), f.tick.lever)
        assertEquals(listOf(25.0, 0.0), f.engines.n2)
        assertEquals(listOf(4.0, 0.0), f.engines.n1)
        assertEquals(listOf(true, false), f.engines.startValve)
    }

    @Test
    fun leverAndSpeedBrakeIdsChangeWithTheString() {
        val a = feed(s = SimState().apply { gear = "UP"; speedBrake = "DOWN" }).tick.extras
        val b = feed(s = SimState().apply { gear = "DN"; speedBrake = "DOWN" }).tick.extras
        assertTrue(a.gearLever != b.gearLever)
        assertEquals(a.speedBrake, b.speedBrake)
        assertEquals(listOf("OFF", "OFF"), feed(s = SimState().apply { ltSeatBelt = "OFF"; ltNoSmoking = "OFF" }).tick.extras.signs)
    }

    @Test
    fun overrunPhaseIsFlagged() {
        assertTrue(feed(s = SimState().apply { rwyPhase = "OVERRUN" }).tick.extras.overrun)
        assertFalse(feed(s = SimState().apply { rwyPhase = "IDLE" }).tick.extras.overrun)
    }

    @Test
    fun markerToneNeedsDcAndNavAndAReceivedMarker() {
        val d = Derived().apply { dcPower = true }
        val inner = MarkerState(om = true, mm = false, im = true)
        assertEquals(MarkerId.IM, feed(d = d, markers = inner, navOn = true).marker)
        assertEquals(null, feed(d = d, markers = inner, navOn = false).marker)
        assertEquals(null, feed(markers = inner, navOn = true).marker)
        assertEquals(null, feed(d = d, markers = MarkerBeacons.NO_MARKERS, navOn = true).marker)
        // soundEnabled gate
        val off = AudioFeed.build(SimState(), d, Pedestal.PedestalState(), false, false, AudioFeed.Overhead(), inner, true, soundEnabled = false)
        assertEquals(null, off.marker)
    }

    @Test
    fun markerGainFollowsTheKeying() {
        // OM keyed ON for 0.36 s of every 0.5 s
        assertEquals(0.12, AudioFeed.markerGain(MarkerId.OM, 0.1), 0.0)
        assertEquals(0.0, AudioFeed.markerGain(MarkerId.OM, 0.4), 0.0)
        assertEquals(0.0, AudioFeed.markerGain(null, 0.1), 0.0)
    }

    @Test
    fun switchClickRule() {
        val a = SimState()
        // boolean flip: click
        assertTrue(AudioFeed.isSwitchChange(a, a.copy().apply { battery = !battery }))
        // string change: click
        assertTrue(AudioFeed.isSwitchChange(a, a.copy().apply { start1 = "GRD" }))
        // continuous value: silent
        assertFalse(AudioFeed.isSwitchChange(a, a.copy().apply { throttle1 = 0.37 }))
        // no change: silent
        assertFalse(AudioFeed.isSwitchChange(a, a.copy()))
        // whole-number detents 0..6 on both sides: click
        val b = a.copy().apply { flaps = 1.0 }
        assertTrue(AudioFeed.isSwitchChange(b, b.copy().apply { flaps = 5.0 }))
        // a whole number outside 0..6 is not a detent
        val c = a.copy().apply { psFlightAltFt = 10.0 }
        assertFalse(AudioFeed.isSwitchChange(c, c.copy().apply { psFlightAltFt = 11.0 }))
    }
}
