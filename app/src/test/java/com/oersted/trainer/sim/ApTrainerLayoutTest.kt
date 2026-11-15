package com.oersted.trainer.sim

import org.junit.Assert.assertEquals
import org.junit.Test

class ApTrainerLayoutTest {
    @Test fun centresTheApAreaInTheViewport() {
        // 1530 * 0.5 - 400 / 2 = 565
        assertEquals(565.0, ApTrainerLayout.scrollLeft("ap", 0.5, 400.0), 0.0)
    }

    @Test fun neverScrollsPastTheLeftEdge() {
        assertEquals(0.0, ApTrainerLayout.scrollLeft("capt", 0.35, 2000.0), 0.0)
    }

    @Test fun unknownAreaStaysAtTheStart() {
        assertEquals(0.0, ApTrainerLayout.scrollLeft("nope", 1.0, 400.0), 0.0)
    }

    @Test fun navOpensOnTheCaptainAndAutopilotOnTheApControls() {
        assertEquals("capt", ApTrainerLayout.openingArea("nav"))
        assertEquals("ap", ApTrainerLayout.openingArea("ap"))
    }

    @Test fun offersTheFourZoomLevels() {
        assertEquals(listOf(0.35, 0.5, 0.75, 1.0), ApTrainerLayout.SCALES)
    }
}
