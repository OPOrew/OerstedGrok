package com.oersted.trainer.sim

/**
 * Port of the hydraulic users list of src/lib/sim/derive.ts (ATA 27/29/32/52): for each user, which source
 * (System A, System B, standby, or electric alternate) is available, and the active source label.
 * Pressure threshold 1500 psi for A/B, 1800 psi for standby (trainer values copied from the TS).
 * Runs after FlightControlDerive (standby rudder, alternate flaps, flight-control availability).
 */
object HydraulicUsersDerive {
    private fun user(id: String, label: String, a: Boolean, b: Boolean, stby: Boolean, elec: Boolean, src: String) =
        HydraulicUserStatus().also {
            it.id = id; it.label = label; it.systemA = a; it.systemB = b
            it.standby = stby; it.electricAlternate = elec; it.activeSource = src
        }

    fun apply(s: SimState, d: Derived) {
        val hydA = d.hydA
        val hydB = d.hydB
        val fcA = d.flightControlAAvailable
        val fcB = d.flightControlBAvailable
        val standbyRudderPowered = d.standbyRudderPowered
        val alternateFlapTakeover = d.alternateFlapTakeover
        val alternateFlapDrivePowered = d.alternateFlapDrivePowered
        val teFlapHydraulicDriveAvailable = d.teFlapHydraulicDriveAvailable
        val leSystemAAvailable = hydA > 1500.0 && !alternateFlapTakeover
        val leStandbyAvailable = alternateFlapTakeover && s.altFlapsControl == "DN" && d.hydS > 1800.0
        val groundSpoilerAvailable = s.wow && s.speedBrake == "UP" && fcA && s.spoilerA
        val bothFc = fcA && fcB

        val list = d.hydraulicUsers
        list.clear()
        list.add(user("ailerons", "Ailerons", fcA, fcB, false, false,
            if (bothFc) "A + B" else if (fcA) "A" else if (fcB) "B" else "MECH REVERSION"))
        list.add(user("elevators", "Elevators", fcA, fcB, false, false,
            if (bothFc) "A + B" else if (fcA) "A" else if (fcB) "B" else "MECH REVERSION"))
        list.add(user("rudder", "Rudder", fcA, fcB, standbyRudderPowered, false,
            if (bothFc) "A + B MAIN PCU" else if (fcA) "SYSTEM A MAIN PCU" else if (fcB) "SYSTEM B MAIN PCU"
            else if (standbyRudderPowered) "STBY ACTUATOR" else "NONE"))
        list.add(user("le-devices", "LE devices", leSystemAAvailable, false, leStandbyAvailable, false,
            if (leSystemAAvailable && leStandbyAvailable) "A + STBY" else if (leSystemAAvailable) "A"
            else if (leStandbyAvailable) "STBY" else "NONE"))
        list.add(user("te-flaps", "TE flaps", teFlapHydraulicDriveAvailable, false, false, alternateFlapDrivePowered,
            if (alternateFlapDrivePowered) "FPU ELEC" else if (teFlapHydraulicDriveAvailable) "A" else "NONE"))
        list.add(user("thrust-reversers", "Thrust reversers", hydA > 1500.0, false, false, false,
            if (hydA > 1500.0) "A" else "NONE"))
        list.add(user("inboard-flight-spoilers", "Flight spoilers 3 / 6 (System A)", fcA && s.spoilerA, false, false, false,
            if (fcA && s.spoilerA) "A" else "NONE"))
        list.add(user("outboard-flight-spoilers", "Flight spoilers 2 / 7 (System B)", false, fcB && s.spoilerB, false, false,
            if (fcB && s.spoilerB) "B" else "NONE"))
        list.add(user("ground-spoilers", "Ground spoilers", groundSpoilerAvailable, false, false, false,
            if (groundSpoilerAvailable) "A" else "NONE"))
        list.add(user("landing-gear", "Landing gear", hydA > 1500.0, false, false, false, if (hydA > 1500.0) "A" else "NONE"))
        list.add(user("nose-wheel-steering", "Nose-wheel steering", hydA > 1500.0, false, false, false,
            if (hydA > 1500.0) "A" else "NONE"))
        list.add(user("inboard-brakes", "Inboard brakes", hydA > 1500.0, false, false, false, if (hydA > 1500.0) "A" else "NONE"))
        list.add(user("outboard-brakes", "Outboard brakes", false, hydB > 1500.0, false, false, if (hydB > 1500.0) "B" else "NONE"))
    }
}
