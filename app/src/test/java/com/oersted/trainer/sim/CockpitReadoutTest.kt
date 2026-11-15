package com.oersted.trainer.sim

import com.oersted.trainer.sim.FlightPhaseControl.StartGate
import com.oersted.trainer.sim.FlightPhases.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CockpitReadoutTest {
    @Test fun readoutsCopyTheSimValues() {
        val s = initialSimState()
        s.psFlightAltFt = 1234.0; s.psFlightCasKt = 180.0; s.psFlightVsFpm = -500.0; s.attitudeHeadingDeg = 270.0; s.throttle1 = 0.4
        val r = CockpitReadout.build(Phase.CLIMB, "Climbing", s) { StartGate(false, "x") }
        assertEquals(1234.0, r.altFt, 1e-9)
        assertEquals(180.0, r.casKt, 1e-9)
        assertEquals(-500.0, r.vsFpm, 1e-9)
        assertEquals(270.0, r.headingDeg, 1e-9)
        assertEquals(0.4, r.throttle1, 1e-9)
        assertEquals("CLIMB", r.phaseLabel)
        assertEquals("Climbing", r.hint)
    }

    @Test fun gatesCoverEveryButtonInOrder() {
        val r = CockpitReadout.build(Phase.READY, "", initialSimState()) { p -> StartGate(p == Phase.TAKEOFF, "why ${p.name}") }
        assertEquals(CockpitReadout.BUTTON_PHASES, r.gates.keys.toList())
        assertTrue(r.gates.getValue(Phase.TAKEOFF).ok)
        assertFalse(r.gates.getValue(Phase.LAND).ok)
        assertEquals("why LAND", r.gates.getValue(Phase.LAND).reason)
    }
}
