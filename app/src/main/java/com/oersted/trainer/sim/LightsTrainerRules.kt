package com.oersted.trainer.sim

/**
 * Rules behind the Lights section of the TRAINING console (routes/panel/LightsTrainer.tsx, lib/sim/lights.ts).
 * Switch positions and the two list-valued fields (bulb failures "," and pulled breakers "|") are edited here;
 * the view only reads them. Presets and "reset switches" keep injected bulb failures and pulled breakers, as the web does.
 */
object LightsTrainerRules {
    /** Web toggleInList: adds [item] if absent, removes it if present. An empty list is no items. */
    fun toggleInList(list: String, item: String, sep: String): String {
        val parts = if (list.isEmpty()) emptyList() else list.split(sep)
        return (if (parts.contains(item)) parts.filter { it != item } else parts + item).joinToString(sep)
    }

    /** Web: the "Fail bulb" button. */
    fun toggleBulbFailure(s: SimState, lightId: String): SimState =
        s.copy().also { it.ltFail = toggleInList(s.ltFail, lightId, ",") }

    /** Web: the "Pull CB" button and the breaker IN/OUT tiles. */
    fun toggleBreaker(s: SimState, cb: String): SimState =
        s.copy().also { it.ltCb = toggleInList(s.ltCb, cb, "|") }

    /** Web: "Reset breakers + bulbs". */
    fun resetBreakersAndBulbs(s: SimState): SimState =
        s.copy().also {
            it.ltFail = ""
            it.ltCb = ""
        }

    /**
     * Web: "Reset switches (keep faults)" and the start of every preset. Copies every light switch from the initial
     * state; ltFail and ltCb stay as they are.
     */
    fun resetSwitches(s: SimState): SimState {
        val init = initialSimState()
        val c = s.copy()
        c.ltRetL = init.ltRetL
        c.ltRetR = init.ltRetR
        c.ltFixedL = init.ltFixedL
        c.ltFixedR = init.ltFixedR
        c.ltTurnoffL = init.ltTurnoffL
        c.ltTurnoffR = init.ltTurnoffR
        c.ltTaxi = init.ltTaxi
        c.ltWing = init.ltWing
        c.ltLogo = init.ltLogo
        c.ltWheelWell = init.ltWheelWell
        c.ltPosition = init.ltPosition
        c.ltAntiCol = init.ltAntiCol
        c.ltDome = init.ltDome
        c.ltPanelOvhd = init.ltPanelOvhd
        c.ltPanelCapt = init.ltPanelCapt
        c.ltPanelFo = init.ltPanelFo
        c.ltPanelPed = init.ltPanelPed
        c.ltMapCapt = init.ltMapCapt
        c.ltMapFo = init.ltMapFo
        c.ltStorm = init.ltStorm
        c.ltCompass = init.ltCompass
        c.ltObserver = init.ltObserver
        c.ltShieldEmer = init.ltShieldEmer
        c.ltMasterDim = init.ltMasterDim
        c.ltTest = init.ltTest
        c.ltCabin = init.ltCabin
        c.ltEntry = init.ltEntry
        c.ltGalley = init.ltGalley
        c.ltLav = init.ltLav
        c.ltReading = init.ltReading
        c.ltSidewall = init.ltSidewall
        c.ltWindow = init.ltWindow
        c.ltAttWork = init.ltAttWork
        c.ltCall = init.ltCall
        c.ltLavMirror = init.ltLavMirror
        c.ltThreshold = init.ltThreshold
        c.ltCloset = init.ltCloset
        c.ltCargo = init.ltCargo
        c.ltEquip = init.ltEquip
        c.ltAftAccessory = init.ltAftAccessory
        c.ltApuComp = init.ltApuComp
        c.ltTailCone = init.ltTailCone
        c.ltNoSmoking = init.ltNoSmoking
        c.ltSeatBelt = init.ltSeatBelt
        c.ltEmergency = init.ltEmergency
        return c
    }

    /** Web PRESETS, in order. "Emer lts test" sets only ltEmergency and does not reset the other switches. */
    val PRESET_NAMES: List<String> = listOf("All off", "Before start", "Taxi", "Takeoff", "Cruise", "Landing", "Emer lts test")

    fun applyPreset(s: SimState, name: String): SimState {
        val base = if (name == "Emer lts test") s.copy() else resetSwitches(s)
        val c = base.copy()
        when (name) {
            "All off" -> Unit
            "Before start" -> {
                c.ltAntiCol = true; c.ltPosition = "STEADY"; c.ltDome = "DIM"
                c.ltPanelOvhd = 0.5; c.ltPanelCapt = 0.5; c.ltPanelFo = 0.5; c.ltPanelPed = 0.5
                c.ltNoSmoking = "ON"; c.ltSeatBelt = "ON"; c.ltEmergency = "ARMED"; c.ltCabin = "BRT"; c.ltEntry = true
            }
            "Taxi" -> {
                c.ltAntiCol = true; c.ltPosition = "STEADY"; c.ltTaxi = true; c.ltTurnoffL = true; c.ltTurnoffR = true
                c.ltLogo = true; c.ltNoSmoking = "ON"; c.ltSeatBelt = "ON"; c.ltEmergency = "ARMED"; c.ltCabin = "DIM"
            }
            "Takeoff" -> {
                c.ltAntiCol = true; c.ltPosition = "STROBE_STEADY"; c.ltRetL = "ON"; c.ltRetR = "ON"
                c.ltFixedL = true; c.ltFixedR = true; c.ltTurnoffL = true; c.ltTurnoffR = true; c.ltLogo = true
                c.ltNoSmoking = "ON"; c.ltSeatBelt = "ON"; c.ltEmergency = "ARMED"; c.ltCabin = "DIM"
            }
            "Cruise" -> {
                c.ltAntiCol = true; c.ltPosition = "STROBE_STEADY"; c.ltLogo = true; c.ltNoSmoking = "AUTO"
                c.ltSeatBelt = "AUTO"; c.ltEmergency = "ARMED"; c.ltCabin = "BRT"; c.ltEntry = true; c.ltGalley = true
            }
            "Landing" -> {
                c.ltAntiCol = true; c.ltPosition = "STROBE_STEADY"; c.ltRetL = "ON"; c.ltRetR = "ON"
                c.ltFixedL = true; c.ltFixedR = true; c.ltTurnoffL = true; c.ltTurnoffR = true; c.ltLogo = true
                c.ltNoSmoking = "AUTO"; c.ltSeatBelt = "AUTO"; c.ltEmergency = "ARMED"; c.ltCabin = "DIM"
            }
            "Emer lts test" -> c.ltEmergency = "ON"
            else -> throw IllegalArgumentException("unknown lights preset: $name")
        }
        return c
    }
}
