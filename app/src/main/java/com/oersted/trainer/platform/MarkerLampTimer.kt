package com.oersted.trainer.platform

import com.oersted.trainer.sim.MarkerBeacons.MarkerId
import com.oersted.trainer.sim.MarkerBeacons.MarkerState
import com.oersted.trainer.sim.MarkerLamps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Android side of markerLamps.ts useMarkerKeyed: while a marker is received and powered, refresh its keying every 40 ms
 * and publish the lamp state. [monoSec] is a monotonic clock in seconds (the web uses performance.now() / 1000).
 * Not compiled in the sandbox (needs the Android SDK and kotlinx-coroutines).
 */
class MarkerLampTimer(
    private val scope: CoroutineScope,
    private val monoSec: () -> Double,
) {
    private val lamp = mapOf(MarkerId.OM to MutableStateFlow(false), MarkerId.MM to MutableStateFlow(false), MarkerId.IM to MutableStateFlow(false))
    private var job: Job? = null

    fun lamp(id: MarkerId): StateFlow<Boolean> = lamp.getValue(id).asStateFlow()

    /** Call when the received markers or power change. Starts the 40 ms refresh while anything is received and powered. */
    fun update(received: MarkerState, powered: Boolean) {
        val any = powered && (received.om || received.mm || received.im)
        if (!any) {
            job?.cancel(); job = null
            for (id in MarkerId.values()) lamp.getValue(id).value = false
            return
        }
        if (job?.isActive == true) {
            job?.cancel()
        }
        job = scope.launch {
            while (isActive) {
                val t = monoSec()
                for (id in MarkerId.values()) lamp.getValue(id).value = MarkerLamps.lampLit(received, id, powered, t)
                delay(MarkerLamps.REFRESH_MS)
            }
        }
    }

    fun stop() {
        job?.cancel(); job = null
        for (id in MarkerId.values()) lamp.getValue(id).value = false
    }
}
