package com.oersted.trainer.sim

import com.oersted.trainer.sim.MipLocal.MipState
import com.oersted.trainer.sim.Pedestal.PedestalState

/**
 * Warning-panel state changes (GlareshieldPanel.tsx: ackMc, silence, the caution-ack sync effect). Pure.
 */
object WarningReducer {
    /** MASTER CAUTION press: acknowledge it and cancel the recall. */
    fun ackMasterCaution(m: MipState): MipState = m.copy(masterCautionAck = true, recall = false)

    /** Effect in the web: when the master caution clears, the acknowledgement is cleared too. */
    fun syncCautionAck(m: MipState, masterCaution: Boolean): MipState =
        if (!masterCaution && m.masterCautionAck) m.copy(masterCautionAck = false) else m

    /** FIRE WARN press: cut the bell. */
    fun silenceBell(p: PedestalState): PedestalState = p.copy(bellSilenced = true)
}
