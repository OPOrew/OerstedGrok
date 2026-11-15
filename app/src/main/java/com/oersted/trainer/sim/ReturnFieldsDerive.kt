package com.oersted.trainer.sim

/**
 * Port of the derive() return-object outputs that come from the nose-wheel steering, brake source,
 * wheel/decel, engine oil/EPR, LE/speed-brake and cabin-temperature rows of src/lib/sim/derive.ts
 * (TS ~1178-1262, ~1401-1403, and the return object ~1552-1706).
 *
 * Runs last in SimDerive.computeAll: it reads gearDown, hydA, flight-control and standby-rudder
 * results that earlier sections already stored on [d].
 * Parity: ReturnFieldsParityTest compares against tools/parity/returnfields.json.
 */
object ReturnFieldsDerive {
    private fun clamp(v: Double, lo: Double, hi: Double) = maxOf(lo, minOf(hi, v))

    /** TS eprFromN1: trainer-normalized EPR, ~1.15 at idle N1 (32%), ~2.0 at 100% N1. */
    fun eprFromN1(n1: Double): Double =
        if (n1 <= 32.0) 1.0 + 0.15 * (maxOf(0.0, n1) / 32.0)
        else 1.15 + 0.85 * (minOf(100.0, n1) - 32.0) / 68.0

    fun apply(s: SimState, d: Derived) {
        // Nose-wheel steering is a ground function: nose gear down, weight on wheels, System A pressure.
        val noseWheelSteeringAvailable = d.gearDown && s.wow && d.hydA > 1500.0
        d.noseWheelSteeringAvailable = noseWheelSteeringAvailable
        d.noseWheelSteeringAngle = if (noseWheelSteeringAvailable) clamp(s.tiller * 78.0 + s.rudderPedal * 7.0, -78.0, 78.0) else 0.0

        // Brake source: System A feeds the inboard path, System B feeds the outboard path (>1500 psi).
        val brakeA = if (s.hydAPress > 1500.0) "A" else "NONE"
        val brakeB = if (s.hydBPress > 1500.0) "B" else "NONE"
        d.brakeSource = DerivedBrakeSource().also {
            it.li = brakeA
            it.lo = brakeB
            it.ri = brakeA
            it.ro = brakeB
        }

        d.wheelSpeeds = DerivedWheelSpeeds().also {
            it.li = s.wheelSpeeds.li
            it.lo = s.wheelSpeeds.lo
            it.ri = s.wheelSpeeds.ri
            it.ro = s.wheelSpeeds.ro
        }
        d.wheelBrakePressures = DerivedWheelBrakePressures().also {
            it.li = s.wheelBrakePressures.li
            it.lo = s.wheelBrakePressures.lo
            it.ri = s.wheelBrakePressures.ri
            it.ro = s.wheelBrakePressures.ro
        }
        d.targetDecel = when (s.autobrake) {
            4.0 -> 22.0
            3.0 -> 16.0
            2.0 -> 11.0
            1.0 -> 7.0
            else -> 0.0
        }
        val wbp = s.wheelBrakePressures
        d.manualBrakeOverride = maxOf(s.leftBrakePedal, s.rightBrakePedal) > 0.05 &&
            maxOf(maxOf(wbp.li, wbp.lo), maxOf(wbp.ri, wbp.ro)) > 0.0
        d.actualDecel = s.actualDecel

        // Control-surface authority: elevator and aileron are fixed at 1 in the TS; rudder needs a
        // main flight-control system or the standby rudder.
        d.controlAuthority = DerivedControlAuthority().also {
            it.elevator = 1.0
            it.aileron = 1.0
            it.rudder = if (d.flightControlAAvailable || d.flightControlBAvailable || d.standbyRudderPowered) 1.0 else 0.0
        }

        d.leExtended = s.lePos > 0.97
        d.leTransit = s.lePos > 0.03 && s.lePos < 0.97
        d.feelDiff = false
        d.speedBrakeArmed = s.speedBrake == "ARMED"

        d.epr1 = if (d.eng1Running) eprFromN1(s.n1_1) else 0.0
        d.epr2 = if (d.eng2Running) eprFromN1(s.n1_2) else 0.0
        d.oilPressure1 = s.oilPsi1
        d.oilPressure2 = s.oilPsi2
        d.oilChipDetected1 = s.faults.oilChip1
        d.oilChipDetected2 = s.faults.oilChip2

        d.controlCabinTempF = s.controlCabinTempF ?: 75.0
        d.passengerCabinTempF = s.passengerCabinTempF ?: 75.0

        d.hydAQty = s.hydAQty
        d.hydBQty = s.hydBQty
        d.hydSQty = s.hydSQty
        d.stbyOn = d.hydS > 500.0
    }
}
