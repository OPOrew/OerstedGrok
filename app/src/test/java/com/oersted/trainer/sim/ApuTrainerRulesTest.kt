package com.oersted.trainer.sim

import com.oersted.trainer.sim.ApuTrainerRules.AutoAction
import com.oersted.trainer.sim.ApuTrainerRules.StepState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApuTrainerRulesTest {
    private val rules = ApuTrainerRules

    private fun dark(): Derived = Derived()

    @Test fun startWithoutPowerIsInhibited() {
        val a = rules.setMaster(initialSimState(), dark(), "START")
        assertEquals("OFF", a.state.apuMaster)
        assertEquals(rules.START_INHIBITED_LOG, a.message)
    }

    @Test fun startWithBatteryIsAcceptedWithItsLogLine() {
        val d = dark().apply { batteryPower = true }
        val a = rules.setMaster(initialSimState(), d, "START")
        assertEquals("START", a.state.apuMaster)
        assertEquals(rules.START_LOG, a.message)
        assertEquals(null, rules.setMaster(initialSimState(), d, "ON").message)
    }

    @Test fun effectivityFlipsAndResetsTheTimer() {
        val s = initialSimState()
        s.apuStartTimer = 12.0
        val a = rules.toggleStartEffectivity(s)
        assertTrue(a.state.apuStart50PercentEffectivityInstalled)
        assertEquals(0.0, a.state.apuStartTimer, 0.0)
        assertEquals("APU START EFFECTIVITY: 50% CUTOUT / 90-SECOND TIMER", a.message)
        assertEquals("APU START EFFECTIVITY: 35% CUTOUT / 60-SECOND TIMER", rules.toggleStartEffectivity(a.state).message)
    }

    @Test fun genButtonsNeedApuAvailable() {
        val s = initialSimState()
        assertTrue(rules.toggleGen(s, dark(), left = true) === s)
        val avail = dark().apply { apuAvailable = true }
        val l = rules.toggleGen(s, avail, left = true)
        assertTrue(l.apuLeftControl)
        assertTrue(l.apuGen)
        val r = rules.toggleGen(l, avail, left = false)
        assertTrue(r.apuRightControl)
        val lOff = rules.toggleGen(r, avail, left = true) // left off, right still on: generator stays on
        assertFalse(lOff.apuLeftControl)
        assertTrue(lOff.apuGen)
    }

    @Test fun shutdownOnlyWhenRunning() {
        val s = initialSimState()
        s.apuMaster = "ON"
        s.apuBleed = true
        assertTrue(rules.shutdown(s, dark()) === s)
        val running = dark().apply { apuAvailable = true }
        val off = rules.shutdown(s, running)
        assertEquals("OFF", off.apuMaster)
        assertFalse(off.apuBleed)
        assertFalse(off.apuGen)
    }

    @Test fun startStepsOrderAndActiveStep() {
        val s = initialSimState()
        val steps = rules.startSteps(s, dark())
        assertEquals(9, steps.size)
        assertEquals("Starter to ~35%: start relay drops out", steps[4].title)
        val states = rules.stepStates(steps)
        assertEquals(StepState.ACTIVE, states[0])
        assertEquals(StepState.PENDING, states[1])
    }

    @Test fun startStepsUse50PercentWhenModified() {
        val s = initialSimState()
        s.apuStart50PercentEffectivityInstalled = true
        assertTrue(rules.startSteps(s, dark())[4].title.contains("50%"))
    }

    @Test fun jammedDoorBlocksTheDoorStep() {
        val s = initialSimState()
        s.faults.apuDoorJam = true
        s.apuMaster = "ON"
        s.apuFuelValve = 1.0
        s.apuDoor = 0.5
        assertTrue(rules.doorJammed(s))
        val steps = rules.startSteps(s, dark())
        assertEquals("Inlet door jammed \u2014 start cannot complete", steps[2].blocked)
        assertEquals(StepState.BLOCKED, rules.stepStates(steps)[2])
    }

    @Test fun lampsStayDarkWithoutPowerUnlessLampTest() {
        val s = initialSimState()
        assertFalse(rules.captionLit(s, dark(), condition = true))
        s.ltTest = true
        val d = dark().apply { dcPower = true }
        assertTrue(rules.captionLit(s, d, condition = false))
    }

    @Test fun autoStepFollowsTheSequence() {
        val s = initialSimState()
        s.battery = false
        assertEquals(AutoAction.SetBattery, rules.autoStep(s, dark()))
        s.battery = true
        assertEquals(AutoAction.Stop, rules.autoStep(s, dark()))
        val powered = dark().apply { batteryPower = true }
        s.battery = true
        s.apuMaster = "OFF"
        assertEquals(AutoAction.SetMasterOn, rules.autoStep(s, powered))
        s.apuMaster = "ON"
        s.apuDoor = 1.0
        s.apuRpm = 0.0
        assertEquals(AutoAction.SetMasterStart, rules.autoStep(s, powered))
        s.apuRpm = 50.0
        assertEquals(null, rules.autoStep(s, powered))
    }

    @Test fun autoStepStopsWhenApuAvailableOrFirePulled() {
        val s = initialSimState()
        assertEquals(AutoAction.Stop, rules.autoStep(s, dark().apply { apuAvailable = true }))
        s.apuFirePulled = true
        assertEquals(AutoAction.Stop, rules.autoStep(s, dark().apply { batteryPower = true }))
    }
}
