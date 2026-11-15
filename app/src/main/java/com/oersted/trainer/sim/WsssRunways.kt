package com.oersted.trainer.sim

import com.oersted.trainer.sim.NavData.dms

/**
 * Port of WSSS_RUNWAYS in navaids-changi.ts: Changi runway threshold data from CAAS AIP AD 2.12 (2025/2026).
 * Coordinates are true (WGS-84). Training only: cross-check against the current AIP before any operational use.
 */
object WsssRunways {
    data class Runway(
        val trueBrg: Double,
        val thrLat: Double,
        val thrLon: Double,
        val elevFt: Double,
        val lengthM: Double,
        /** displaced threshold distance, m (only 20R) */
        val displacedM: Double? = null,
    )

    val WSSS_RUNWAYS: Map<String, Runway> = mapOf(
        "02L" to Runway(trueBrg = 23.02, thrLat = dms(1.0, 20.0, 56.27), thrLon = dms(103.0, 58.0, 38.82),
            elevFt = 22.0, lengthM = 4000.0),
        // displaced threshold
        "20R" to Runway(trueBrg = 203.02, thrLat = dms(1.0, 22.0, 34.02), thrLon = dms(103.0, 59.0, 20.09),
            elevFt = 13.0, lengthM = 4000.0, displacedM = 740.0),
        "02C" to Runway(trueBrg = 23.01, thrLat = dms(1.0, 19.0, 43.51), thrLon = dms(103.0, 59.0, 5.86),
            elevFt = 16.0, lengthM = 4000.0),
        "20C" to Runway(trueBrg = 203.01, thrLat = dms(1.0, 21.0, 43.37), thrLon = dms(103.0, 59.0, 56.46),
            elevFt = 16.0, lengthM = 4000.0),
        "02R" to Runway(trueBrg = 23.01, thrLat = dms(1.0, 19.0, 20.59), thrLon = dms(103.0, 59.0, 59.45),
            elevFt = 16.0, lengthM = 4000.0),
        "20L" to Runway(trueBrg = 203.01, thrLat = dms(1.0, 21.0, 20.45), thrLon = dms(104.0, 0.0, 50.05),
            elevFt = 16.0, lengthM = 4000.0),
    )
}
