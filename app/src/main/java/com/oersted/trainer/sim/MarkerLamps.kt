package com.oersted.trainer.sim

import com.oersted.trainer.sim.MarkerBeacons.MarkerId
import com.oersted.trainer.sim.MarkerBeacons.MarkerState

/**
 * Port of markerLamps.ts (pure rule): a MIP marker lamp is lit while the marker is received AND powered AND
 * its tone keying is in the ON part at time [t] (seconds). The React hook's 40 ms refresh lives in platform/MarkerLampTimer.kt.
 */
object MarkerLamps {
    fun lampLit(received: MarkerState, id: MarkerId, powered: Boolean, t: Double): Boolean {
        val got = when (id) {
            MarkerId.OM -> received.om
            MarkerId.MM -> received.mm
            MarkerId.IM -> received.im
        }
        return got && powered && MarkerBeacons.markerKeyed(id, t)
    }

    /** TS refresh period of the lamp hook (setInterval(…, 40)). */
    const val REFRESH_MS = 40L
}
