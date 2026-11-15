package com.oersted.trainer.sim

import org.junit.Assert.assertEquals
import org.junit.Test

class AirportCoordsTest {
    @Test fun airportCoordsLabels() {
        assertEquals("Manila / NAIA", AirportCoords.AIRPORT_COORDS.getValue("RPLL").label)
    }
}
