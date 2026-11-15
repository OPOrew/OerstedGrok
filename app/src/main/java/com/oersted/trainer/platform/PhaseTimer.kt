package com.oersted.trainer.platform

import com.oersted.trainer.sim.PhaseController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Android side of flightPhases.ts startController / stopController: calls PhaseController.tick every 200 ms
 * (the web's window.setInterval(controllerTick, 200)). [monoSec] is a monotonic clock in seconds.
 * Not compiled in the sandbox (needs the Android SDK and kotlinx-coroutines).
 */
class PhaseTimer(
    private val scope: CoroutineScope,
    private val controller: PhaseController,
    private val monoSec: () -> Double,
) {
    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    fun start() {
        if (isRunning) return
        controller.ensureRunning()
        job = scope.launch {
            while (isActive) {
                controller.tick(monoSec())
                delay(INTERVAL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        controller.stopRunning()
    }

    companion object {
        const val INTERVAL_MS = 200L
    }
}
