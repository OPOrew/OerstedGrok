package com.oersted.trainer.sim

/**
 * Handler rules behind the Fuel and Engine Start panels and the ground-service buttons of the Pneumatic panel
 * (routes/cockpit.tsx FuelTrainerPanel, EngineStartTrainerPanel and PneumaticTrainerPanel). Each function returns the
 * new state. Engine START and SHUTDOWN are already in [SimActions]. Labels and enable rules are here so the views stay simple.
 */
object FuelAndGroundRules {
    /** The six boost-pump switches, in web order. */
    val BOOST_PUMPS: List<String> = listOf("fuelPump1Fwd", "fuelPump1Aft", "fuelPumpCenter1", "fuelPumpCenter2", "fuelPump2Fwd", "fuelPump2Aft")

    private inline fun edited(s: SimState, edit: SimState.() -> Unit): SimState {
        val c = s.copy()
        c.edit()
        return c
    }

    /** Web boost pump buttons: flips one pump switch. */
    fun toggleBoostPump(s: SimState, pump: String): SimState {
        val on = when (pump) {
            "fuelPump1Fwd" -> !s.fuelPump1Fwd
            "fuelPump1Aft" -> !s.fuelPump1Aft
            "fuelPumpCenter1" -> !s.fuelPumpCenter1
            "fuelPumpCenter2" -> !s.fuelPumpCenter2
            "fuelPump2Fwd" -> !s.fuelPump2Fwd
            "fuelPump2Aft" -> !s.fuelPump2Aft
            else -> throw IllegalArgumentException("not a boost pump: $pump")
        }
        return edited(s) {
            when (pump) {
                "fuelPump1Fwd" -> fuelPump1Fwd = on
                "fuelPump1Aft" -> fuelPump1Aft = on
                "fuelPumpCenter1" -> fuelPumpCenter1 = on
                "fuelPumpCenter2" -> fuelPumpCenter2 = on
                "fuelPump2Fwd" -> fuelPump2Fwd = on
                else -> fuelPump2Aft = on
            }
        }
    }

    fun toggleCrossfeed(s: SimState): SimState = edited(s) { fuelCrossfeed = !s.fuelCrossfeed }

    /** Web label: CROSSFEED VALVE OPEN / CLOSED when a shutoff valve is open, otherwise CONTROL OFF. */
    fun crossfeedLabel(crossfeed: Boolean, shutoffOpen: Boolean): String =
        if (shutoffOpen) (if (crossfeed) "OPEN" else "CLOSED") else "CONTROL OFF"

    /** Web SET LOW FUEL: all three tanks to 25 %. */
    fun setLowFuel(s: SimState): SimState = edited(s) {
        fuelTank1 = 25.0
        fuelTankCenter = 25.0
        fuelTank2 = 25.0
    }

    /** Web RESET QUANTITY: all three tanks to 100 %. */
    fun resetQuantity(s: SimState): SimState = edited(s) {
        fuelTank1 = 100.0
        fuelTankCenter = 100.0
        fuelTank2 = 100.0
    }

    /** Web engine START button: enabled unless running, auto-starting or on fire. */
    fun startEnabled(running: Boolean, auto: Boolean, fire: Boolean): Boolean = !(running || auto || fire)

    /** Web engine SHUTDOWN button: enabled while running, auto-starting or N2 is at least 1 %. */
    fun shutdownEnabled(running: Boolean, auto: Boolean, n2: Double): Boolean = running || auto || n2 >= 1.0

    /** Web shutdown button text: ABORT START while an auto start is underway, else SHUTDOWN. */
    fun shutdownLabel(running: Boolean, auto: Boolean): String = if (auto && !running) "ABORT START" else "SHUTDOWN"

    /** Web GROUND AIR CART: enabled only on the ground. Connecting it supplies 45 psi. */
    fun toggleGroundAirCart(s: SimState): SimState {
        val next = !s.groundPneumaticConnected
        return edited(s) {
            groundPneumaticConnected = next
            groundPneumaticPsi = if (next) 45.0 else 0.0
        }
    }

    /** Web GROUND POWER button: the cart connection also sets the GRD PWR switch to match. */
    fun toggleGroundPowerCart(s: SimState): SimState {
        val next = !s.externalPowerConnected
        return edited(s) {
            externalPowerConnected = next
            groundPowerOn = next
        }
    }

    /**
     * Web APU + BLEED button. Starting the APU (master OFF -> ON) also opens the APU bleed; stopping it closes the bleed.
     */
    fun toggleApuAndBleed(s: SimState): SimState {
        val wasOff = s.apuMaster == "OFF"
        return edited(s) {
            apuMaster = if (wasOff) "ON" else "OFF"
            apuBleed = wasOff
        }
    }

    /** Web label for the APU + BLEED button. */
    fun apuAndBleedLabel(masterOff: Boolean, available: Boolean): String =
        if (masterOff) "OFF" else if (available) "AVAILABLE" else "STARTING"
}
