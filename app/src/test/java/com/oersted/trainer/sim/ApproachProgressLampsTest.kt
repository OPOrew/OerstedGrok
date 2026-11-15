package com.oersted.trainer.sim

import com.oersted.trainer.sim.AutoFlight.Light
import org.junit.Assert.assertEquals
import org.junit.Test

class ApproachProgressLampsTest {
    @Test fun darkWithoutPower() {
        assertEquals(Light.OFF, ApproachProgressLamps.state(pw = false, test = false, light = Light.GREEN))
        assertEquals(Light.OFF, ApproachProgressLamps.state(pw = false, test = true, light = Light.AMBER))
    }

    @Test fun pressToTestForcesGreen() {
        assertEquals(Light.GREEN, ApproachProgressLamps.state(pw = true, test = true, light = Light.OFF))
    }

    @Test fun showsTheAnnunciatorLightWhenPowered() {
        assertEquals(Light.AMBER, ApproachProgressLamps.state(pw = true, test = false, light = Light.AMBER))
        assertEquals(Light.GREEN, ApproachProgressLamps.state(pw = true, test = false, light = Light.GREEN))
        assertEquals(Light.OFF, ApproachProgressLamps.state(pw = true, test = false, light = Light.OFF))
    }
}
