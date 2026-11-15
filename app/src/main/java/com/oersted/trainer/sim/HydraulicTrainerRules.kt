package com.oersted.trainer.sim

/**
 * Handler rules behind the Hydraulics panel of the trainer (routes/cockpit.tsx HydraulicsTrainerPanel, the switch row).
 * Each function returns the new state. The view keeps the flow drawing and the status text.
 * The ground-servicing switches are enabled only on the ground (weight on wheels), as in the web.
 */
object HydraulicTrainerRules {
    /** Pump and interconnect switches that are plain flips in the web panel. */
    val FLIP_KEYS: List<String> = listOf(
        "engHyd1", "engHyd2", "elecHyd1", "elecHyd2", "groundInterconnect", "hydServicingConnected", "hydGroundPowerConnected",
    )

    /** Standby hydraulic pump modes, in web order. */
    val STBY_MODES: List<String> = listOf("OFF", "AUTO", "ON")

    private inline fun edited(s: SimState, edit: SimState.() -> Unit): SimState {
        val c = s.copy()
        c.edit()
        return c
    }

    /** Web toggle(key, value) for the switches in [FLIP_KEYS]. An unknown key is refused. */
    fun toggle(s: SimState, key: String): SimState {
        val on = when (key) {
            "engHyd1" -> !s.engHyd1
            "engHyd2" -> !s.engHyd2
            "elecHyd1" -> !s.elecHyd1
            "elecHyd2" -> !s.elecHyd2
            "groundInterconnect" -> !s.groundInterconnect
            "hydServicingConnected" -> !s.hydServicingConnected
            "hydGroundPowerConnected" -> !s.hydGroundPowerConnected
            else -> throw IllegalArgumentException("not a hydraulic switch: $key")
        }
        return edited(s) {
            when (key) {
                "engHyd1" -> engHyd1 = on
                "engHyd2" -> engHyd2 = on
                "elecHyd1" -> elecHyd1 = on
                "elecHyd2" -> elecHyd2 = on
                "groundInterconnect" -> groundInterconnect = on
                "hydServicingConnected" -> hydServicingConnected = on
                else -> hydGroundPowerConnected = on
            }
        }
    }

    /** Web STBY HYD selector. */
    fun setStbyHyd(s: SimState, mode: String): SimState = edited(s) { stbyHyd = mode }

    /** Web ELEC 1 / ELEC 2 pumps are enabled only when that AC transfer bus is powered. */
    fun elecPumpEnabled(d: Derived, engine: Int): Boolean = if (engine == 1) d.transferBus1Powered else d.transferBus2Powered

    /** Web HAND PUMP / GPMU / air cart switches are enabled only on the ground. */
    fun groundServicingEnabled(s: SimState): Boolean = s.wow

    /** Web GND AIR CART: connecting it supplies 45 psi, disconnecting drops to 0. */
    fun toggleGroundAirCart(s: SimState): SimState {
        val next = !s.groundPneumaticConnected
        return edited(s) {
            groundPneumaticConnected = next
            groundPneumaticPsi = if (next) 45.0 else 0.0
        }
    }

    /** Web GND INTERCONNECT label: OPEN when the switch is on. */
    fun interconnectState(on: Boolean): String = if (on) "OPEN" else "CLOSED"
}
