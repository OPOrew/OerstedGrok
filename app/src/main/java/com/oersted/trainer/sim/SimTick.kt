package com.oersted.trainer.sim

import kotlin.math.floor
import kotlin.math.min

/**
 * Port of the body of the store's tick() in src/lib/sim/store.ts: the transfer-relay and generator
 * protection timers, the one-touch engine auto start, hydraulic servicing, leaks, the ATA 34 test clock,
 * the navigation advance, and the transition log entries.
 *
 * Inputs the web read from globals are passed in: [nav] and [plan] stand in for advanceFlightNavigation
 * (which read the plan from storage), [nowMs] for Date.now(), and [liveWxIntensity] for the live weather.
 * Returns the next state and its derived values, so the store can cache them.
 */
object SimTick {
    private fun jsRound(x: Double): Double = floor(x + 0.5)

    fun tick(
        s: SimState,
        dt: Double,
        prev: Derived,
        nav: FlightNavigator?,
        plan: ActiveFlightPlan?,
        nowMs: Double,
        liveWxIntensity: Double = 0.0,
    ): Pair<SimState, Derived> {
        val scaled = dt * s.timeScale
        var next = StepSim.step(s, scaled, liveWxIntensity)

        // ATA 24 R3/R4 transfer relays have a short 0.1-0.2 s transfer delay. Demand matches derive():
        // no local generator bus, no BTB/APU/GPU tie source, opposite generator available, BUS TRANSFER on.
        val pre = SimDerive.computeAll(next)
        val side1OnTieOrGen = pre.electricalSource1 == "GEN 1" || pre.electricalSource1 == "GPU" || pre.electricalSource1 == "APU GEN"
        val side2OnTieOrGen = pre.electricalSource2 == "GEN 2" || pre.electricalSource2 == "GPU" || pre.electricalSource2 == "APU GEN"
        val relay1Demand = next.busTie && !next.faults.transferRelay1Failure && !pre.generator1BusPowered && !side1OnTieOrGen && pre.generator2BusPowered
        val relay2Demand = next.busTie && !next.faults.transferRelay2Failure && !pre.generator2BusPowered && !side2OnTieOrGen && pre.generator1BusPowered
        next.transferRelay1Timer = if (relay1Demand) min(0.15, next.transferRelay1Timer + scaled) else 0.0
        next.transferRelay2Timer = if (relay2Demand) min(0.15, next.transferRelay2Timer + scaled) else 0.0

        // ATA 24 generator protection: an abnormal condition latches after 0.40 s.
        val f = next.faults
        val p1Demand = next.gen1 && (f.generator1OverFrequency || f.generator1UnderFrequency || f.generator1OverVoltage || f.generator1UnderVoltage || f.generator1OverCurrent || f.generator1Differential)
        val p2Demand = next.gen2 && (f.generator2OverFrequency || f.generator2UnderFrequency || f.generator2OverVoltage || f.generator2UnderVoltage || f.generator2OverCurrent || f.generator2Differential)
        next.generator1ProtectionTimer = if (p1Demand) min(0.40, next.generator1ProtectionTimer + scaled) else 0.0
        next.generator2ProtectionTimer = if (p2Demand) min(0.40, next.generator2ProtectionTimer + scaled) else 0.0
        if (!next.gen1) next.generator1ProtectionLatched = false
        else if (p1Demand && next.generator1ProtectionTimer >= 0.40) next.generator1ProtectionLatched = true
        if (!next.gen2) next.generator2ProtectionLatched = false
        else if (p2Demand && next.generator2ProtectionTimer >= 0.40) next.generator2ProtectionLatched = true

        // Trainer one-touch engine start: GRD opens the starter valve; once N2 passes 20 % the start lever goes to IDLE.
        // Cleared when self-sustaining or aborted (switch OFF with the lever in CUTOFF).
        for (side in 1..2) {
            val auto = if (side == 1) next.autoStart1 else next.autoStart2
            if (!auto) continue
            val n2 = if (side == 1) next.n2_1 else next.n2_2
            val lever = if (side == 1) next.lever1 else next.lever2
            val sw = if (side == 1) next.start1 else next.start2
            val firePulled = if (side == 1) next.engine1FirePulled else next.engine2FirePulled
            if (firePulled || (sw == "OFF" && !lever)) {
                if (side == 1) next.autoStart1 = false else next.autoStart2 = false
                continue
            }
            if (!lever && n2 >= 20) {
                if (side == 1) next.lever1 = true else next.lever2 = true
                next = StepSim.pushLog(next, "Engine $side: N2 ${jsRound(n2).toLong()}% — START LEVER to IDLE, fuel and ignition on.", "info", nowMs = nowMs)
            } else if (lever && n2 >= 40) {
                if (side == 1) next.autoStart1 = false else next.autoStart2 = false
            }
        }

        // Hydraulic fluid servicing changes reservoir quantity only (trainer-scale fill rate); leaks drain it.
        if (next.hydServicingConnected && next.wow) {
            val fill = min(12 * scaled, 100.0)
            next.hydAQty = min(100.0, next.hydAQty + fill)
            next.hydBQty = min(100.0, next.hydBQty + fill)
            next.hydSQty = min(100.0, next.hydSQty + fill)
        }
        if (next.faults.hydALeak) next.hydAQty = kotlin.math.max(0.0, next.hydAQty - 0.5 * scaled)
        if (next.faults.hydBLeak) next.hydBQty = kotlin.math.max(0.0, next.hydBQty - 0.5 * scaled)

        // ATA 34 trainer test clock: advances only while the pitot/static trainer is active.
        next.psTestClockSec = if (next.psTrainerEnabled) next.psTestClockSec + kotlin.math.max(0.0, scaled) else 0.0

        // Shared geographic navigation follows the active attitude model unless the main panel owns it.
        if (!next.afPanelOwned && nav != null) {
            nav.advance(
                plan,
                NavigationTelemetry(
                    dt = scaled,
                    groundSpeedKt = next.groundSpeedKt,
                    headingDeg = next.attitudeHeadingDeg,
                    altitudeFt = next.psFlightAltFt,
                    wow = next.wow,
                ),
            )
        }

        val now = SimDerive.computeAll(next)

        // Transition log entries, written when a derived condition turns on.
        fun log(st: SimState, text: String, tone: String): SimState = StepSim.pushLog(st, text, tone, nowMs = nowMs)
        if (!prev.apuAvailable && now.apuAvailable) next = log(next, "APU at 95% — bleed valve and generator are armed.", "ok")
        if (!prev.startValveOpen1 && now.startValveOpen1) next = log(next, "Engine 1 START VALVE OPEN — pneumatic starter cutting in.", "ok")
        if (!prev.startValveOpen2 && now.startValveOpen2) next = log(next, "Engine 2 START VALVE OPEN — pneumatic starter cutting in.", "ok")
        if (!prev.eng1Running && now.eng1Running) next = log(next, "Engine 1 self-sustaining. Starter cutout ~35–40% N2.", "ok")
        if (!prev.eng2Running && now.eng2Running) next = log(next, "Engine 2 self-sustaining. Starter cutout ~35–40% N2.", "ok")
        if (!prev.dualBleed && now.dualBleed) next = log(next, "DUAL BLEED light — APU bleed is open with an engine bleed source and isolation valve open.", "caution")
        if (!prev.transferRelay1Alt && now.transferRelay1Alt) next = log(next, "TRANSFER BUS 1 — automatic transfer closed from GEN 2 (R3).", "ok")
        if (!prev.transferRelay2Alt && now.transferRelay2Alt) next = log(next, "TRANSFER BUS 2 — automatic transfer closed from GEN 1 (R4).", "ok")
        if (!prev.generator1ProtectionTripped && now.generator1ProtectionTripped) next = log(next, "GEN 1 protection trip — generator removed from bus after 0.40 s abnormal condition.", "warn")
        if (!prev.generator2ProtectionTripped && now.generator2ProtectionTripped) next = log(next, "GEN 2 protection trip — generator removed from bus after 0.40 s abnormal condition.", "warn")
        if (!prev.stbyOn && now.stbyOn) next = log(next, "Standby hydraulic pump on — covering rudder / LE devices.", "caution")
        if (s.apuDoor < 0.95 && next.apuDoor >= 0.95) next = log(next, "APU inlet door fully open. Starter lockout relay released.", "ok")

        return Pair(next, now)
    }
}
