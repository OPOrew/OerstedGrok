package com.oersted.trainer.platform

import com.oersted.trainer.sim.MipLocal.MipState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Holder for the MIP panel state the glareshield reads and writes. Changes are pure reducers (sim/GlareshieldReducer.kt)
 * applied with [update]; Compose collects [state]. Not compiled in the sandbox (needs the Android SDK and kotlinx-coroutines).
 */
class MipStore(initial: MipState = MipState()) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<MipState> = _state

    /** Apply a reducer to the current state. */
    fun update(reducer: (MipState) -> MipState) {
        _state.update(reducer)
    }
}
