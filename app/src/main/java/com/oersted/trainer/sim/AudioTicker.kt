package com.oersted.trainer.sim

import kotlin.math.max

/**
 * Port of the scheduling logic in audio.ts `tick()` and `tickEngines()` (pure, no WebAudio):
 *  - ignition clicks while the start switch is selected and the engine is spooling;
 *  - light-off thump when a lever opens while the engine is already turning;
 *  - fire-bell strike schedule (synth path) with the continuous sample-loop path;
 *  - engine voice updates on their own 90 ms timer, with the first update after switch-on adopting the real state.
 *
 * The caller runs [tick] every 100 ms and [tickEngines] every 90 ms, like the web code. `now` is the audio clock
 * in seconds. Sound is produced by the Android layer from the returned lists.
 */
class AudioTicker(private val random: () -> Double = { kotlin.random.Random.nextDouble() }) {

    object Tunables {
        /** s between fire-bell strikes (~6.7 strikes/s) */
        const val BELL_PERIOD = 0.15
        /** Hz: the two gongs the hammer alternates between */
        val BELL_BASE = doubleArrayOf(1170.0, 1235.0)
        /** A/P disconnect siren sweeps per second */
        const val WAIL_HZ = 1.5
        /** Hz: centre of the siren sweep */
        const val WAIL_CENTER = 1000.0
        /** sweep depth either side of centre (cents; 520 ~ 740 to 1350 Hz) */
        const val WAIL_CENTS = 520.0
        /** stick-shaker motor revolutions per second (captain, first officer) */
        val SHAKER_REVS = doubleArrayOf(17.0, 18.0)
        /** Mach / airspeed warning clacks per second */
        const val CLACK_HZ = 8.0
        /** ignition click minimum gap, s */
        const val CLICK_GAP = 0.11
        /** bell lookahead window, s */
        const val BELL_LOOKAHEAD = 0.22
    }

    /** One ignition tick (short hard noise click) for engine [engine] at audio time [at]. */
    data class Click(val engine: Int, val pan: Double, val at: Double)

    /** Light-off rumble when the lever opens on a turning engine. */
    data class LightOff(val engine: Int, val pan: Double, val at: Double)

    /** One fire-bell hammer strike. [k] is the strike counter; the hammer alternates gongs on odd/even k. */
    data class BellStrike(val at: Double, val k: Int, val pan: Double)

    /** Inputs for [tick]. [extras] is the subset used by the event mapper. */
    data class TickInputs(
        /** start switch not OFF, per engine */
        val startSwitchSelected: List<Boolean>,
        /** lever open, per engine */
        val lever: List<Boolean>,
        val n2: List<Double>,
        val fireE1: Boolean = false,
        val fireE2: Boolean = false,
        val fireApu: Boolean = false,
        val fireOther: Boolean = false,
        val apuRpm: Double = 0.0,
        val apuStarter: Boolean = false,
        val extras: AudioEvents.Inputs = AudioEvents.Inputs(),
    )

    /** Engine input for [tickEngines]. */
    data class EngineInputs(val n1: List<Double>, val n2: List<Double>, val startValve: List<Boolean>)

    data class EngineUpdate(val engine: Int, val n1: Double, val n2: Double, val valve: Boolean, val snap: Boolean)

    data class TickResult(
        val apuRpm: Double,
        val apuStarter: Boolean,
        val clicks: List<Click>,
        val lightOffs: List<LightOff>,
        /** null when the tick did nothing (sound off or audio context not running) */
        val frame: AudioEvents.Frame?,
        /** synth bell strikes to schedule; empty when the sample loop is used or the bell is quiet */
        val bellStrikes: List<BellStrike>,
        /** desired state of the sample fire-bell loop (the layer should act on changes only) */
        val fireBellLoopOn: Boolean,
    )

    private val extras = AudioEvents.Extras(random)
    private val lastClick = doubleArrayOf(0.0, 0.0)
    private val lastLever = booleanArrayOf(false, false)
    private var bellNext = 0.0
    private var bellK = 0
    private var engSnap = true

    /** web setEnabled(true): reset the snapshots so nothing bursts when sound comes on. */
    fun onSoundOn() {
        extras.reset()
        engSnap = true
        bellNext = 0.0
    }

    /**
     * web tick(). [ready] is `enabled && ctx && master && ctx.state === "running"`.
     * [fireBellSampleLoaded] is true when the pre-recorded fire bell sample loaded; then the synth bell is silent.
     */
    fun tick(
        inp: TickInputs,
        now: Double,
        ready: Boolean,
        fireBellSampleLoaded: Boolean,
    ): TickResult {
        if (!ready) {
            return TickResult(inp.apuRpm, inp.apuStarter, emptyList(), emptyList(), null, emptyList(), false)
        }
        val clicks = ArrayList<Click>()
        val lightOffs = ArrayList<LightOff>()
        for (i in 0 until 2) {
            val n2 = inp.n2.getOrElse(i) { 0.0 }
            val pan = if (i == 0) -0.4 else 0.4
            val lever = inp.lever.getOrElse(i) { false }
            // ignition ticks while the start switch is selected, lever open and the engine is spooling
            if (inp.startSwitchSelected.getOrElse(i) { false } && lever && n2 > 10 && n2 < 50 &&
                now - lastClick[i] > Tunables.CLICK_GAP
            ) {
                clicks += Click(i, pan, now)
                lastClick[i] = now
            }
            // light-off when the lever is opened while the engine is already turning
            if (lever && !lastLever[i] && n2 > 12 && n2 < 60) lightOffs += LightOff(i, pan, now)
            lastLever[i] = lever
        }

        val frame = extras.step(inp.extras, now)

        // Fire bell: sample loop while any fire / OVHT-FIRE test is active; otherwise synth strikes.
        val wantBell = inp.fireE1 || inp.fireE2 || inp.fireApu || inp.fireOther
        val strikes = ArrayList<BellStrike>()
        if (fireBellSampleLoaded) {
            bellNext = 0.0
        } else if (wantBell) {
            if (bellNext < now) bellNext = now + 0.02
            while (bellNext < now + Tunables.BELL_LOOKAHEAD) {
                val k = bellK
                strikes += BellStrike(bellNext, k, if (k % 2 == 1) 0.08 else -0.08)
                bellK++
                bellNext += Tunables.BELL_PERIOD * (1 + (random() - 0.5) * 0.06)
            }
        } else {
            bellNext = 0.0
        }
        return TickResult(
            apuRpm = inp.apuRpm,
            apuStarter = inp.apuStarter,
            clicks = clicks,
            lightOffs = lightOffs,
            frame = frame,
            bellStrikes = strikes,
            fireBellLoopOn = fireBellSampleLoaded && wantBell,
        )
    }

    /**
     * web tickEngines(). When not ready, the next update adopts the real engine state (no spool-up from zero).
     */
    fun tickEngines(inp: EngineInputs, ready: Boolean): List<EngineUpdate> {
        if (!ready) {
            engSnap = true
            return emptyList()
        }
        val out = (0 until 2).map { i ->
            EngineUpdate(
                engine = i,
                n1 = inp.n1.getOrElse(i) { 0.0 },
                n2 = inp.n2.getOrElse(i) { 0.0 },
                valve = inp.startValve.getOrElse(i) { false },
                snap = engSnap,
            )
        }
        engSnap = false
        return out
    }
}
