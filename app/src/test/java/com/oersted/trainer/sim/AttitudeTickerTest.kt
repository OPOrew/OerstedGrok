package com.oersted.trainer.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Smoke tests of the attitude tick caller (the web useAttitude interval). */
class AttitudeTickerTest {
    private class Rig {
        var plan: ActiveFlightPlan? = null
        val nav = FlightNavigator()
        val sim = SimStore(initialSimState(), nav, { plan }, { 0.0 })
        val mip = MipLocal.Holder()
        val ped = Pedestal.Holder()
        val ticker = AttitudeTicker(
            sim, mip, ped, nav, { plan },
            { _ -> nav.liveGuidance() },
            Situation.SituationHolder(),
        )
    }

    @Test fun startAndStopToggleTheLiveAttitudeFlag() {
        val r = Rig()
        r.ticker.start()
        assertTrue(r.mip.state.attLive)
        r.ticker.stop()
        assertFalse(r.mip.state.attLive)
    }

    @Test fun maybeTickSkipsUntilHundredMillisecondsHavePassed() {
        val r = Rig()
        r.ticker.start()
        r.ticker.maybeTick(10.0) // first call only arms the timer
        val before = r.mip.state
        r.ticker.maybeTick(10.05) // 50 ms: no tick
        assertTrue(before === r.mip.state)
    }

    @Test fun oneTickOnTheColdParkedAircraftKeepsHeadingAndLevelAttitude() {
        val r = Rig()
        r.ticker.start()
        val hdg0 = r.mip.state.att.hdg
        r.ticker.tick(0.1)
        assertEquals(hdg0, r.mip.state.att.hdg, 1e-6)
        assertEquals(0.0, r.mip.state.att.roll, 1e-6)
    }
}
