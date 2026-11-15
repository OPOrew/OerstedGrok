package com.oersted.trainer.sim

import kotlin.math.abs
import kotlin.math.max

/**
 * Port of the pure decision logic in audio.ts `extras()` (gear, flaps / leading edge, spoilers, APU door,
 * hydraulics, ground roll / touchdown / brakes, thrust reversers, relays, start valves, fire handles,
 * bottles, chime signs, ambience) and of `rate()` (speed of a changing value, robust to uneven sampling).
 *
 * This file makes NO sound. It turns the sim inputs into:
 *  - a list of one-shot sound events ([Ev]), in the same order the web code schedules them;
 *  - continuous levels for the looping layers (the web `fx` object);
 *  - the desired on/off state of the warning loops.
 * The Android layer (AudioTrack / SoundPool) plays the events and levels. Event parameters mirror the
 * web helper signatures: `pan` (-1..1), `amp` (linear amplitude), `delay` (s after this tick).
 *
 * Sampled loops: the web code prefers pre-recorded samples for the horn / wailer / shaker / GPWS / glideslope
 * loops and falls back to synth. Here both paths get the same `want*` flag; the layer decides which to use and
 * should only act on changes (the web code only called `set()` on a change).
 */
object AudioEvents {

    enum class Kind { CLUNK, THUMP, WHOOSH, NOISE_BURST, LOCK_THUNK, WHEEL_SPIN_DOWN, TYRE_CHIRP, CHIME }

    /**
     * One one-shot sound. Field use depends on [kind]:
     *  - WHOOSH: [dur], [f0] -> [f1] Hz sweep, [filter] (BiquadFilterType name), [q].
     *  - NOISE_BURST: [attack], [decay], [filter], [freq], [q].
     *  - CHIME: [notes] Hz, played with the web default gap.
     */
    data class Ev(
        val kind: Kind,
        val pan: Double,
        val amp: Double = 0.0,
        val delay: Double = 0.0,
        val dur: Double = 0.0,
        val f0: Double = 0.0,
        val f1: Double = 0.0,
        val filter: String = "bandpass",
        val q: Double = 0.8,
        val attack: Double = 0.0,
        val decay: Double = 0.0,
        val freq: Double = 0.0,
        val notes: List<Double> = emptyList(),
    )

    /** Continuous levels for the looping layers (web `fx.*` calls). */
    data class Levels(
        val gearHyd: Double = 0.0,
        val gearFree: Double = 0.0,
        /** gear wind noise; already scaled by 0.3 on the ground */
        val gearWind: Double = 0.0,
        val flap: Double = 0.0,
        val spoiler: Double = 0.0,
        val door: Double = 0.0,
        val hydLevel: Double = 0.0,
        val hydPumps: Double = 0.0,
        val hydElecPumps: Boolean = false,
        val roll: Double = 0.0,
        val grass: Boolean = false,
        val skidLevel: Double = 0.0,
        val skidFreq: Double = 0.0,
        /** antiskid valve chatter depth, 0 when the system is not modulating */
        val antiskidChatter: Double = 0.0,
        val brake: Double = 0.0,
        val rev: Double = 0.0,
        val ambAc: Boolean = false,
        val ambDc: Boolean = false,
        val ambPacks: Int = 0,
        val ambFuelPumps: Double = 0.0,
    )

    /** Desired on/off state of the warning loops this tick. */
    data class Loops(
        /** gear warning, take-off config or cabin altitude horn (one horn family on classic aircraft) */
        val configCabinHorn: Boolean = false,
        val apDisconnectWailer: Boolean = false,
        val stickShaker: Boolean = false,
        /** GPWS SYS TEST: full vocabulary loop while the test is held */
        val gpwsVocab: Boolean = false,
        /** mode 5 below-glideslope; suppressed while the GPWS test runs */
        val glideslope: Boolean = false,
        val machClacker: Boolean = false,
    )

    data class Frame(
        val events: List<Ev>,
        val levels: Levels,
        val loops: Loops,
    )

    /** Inputs for the extras() port. Lists mirror the web arrays; defaults mirror the `?? 0` / `?? [0,0,0]` fallbacks. */
    data class Inputs(
        val gear: List<Double> = listOf(0.0, 0.0, 0.0),
        val wheelBrake: List<Double> = listOf(0.0, 0.0, 0.0, 0.0),
        val gearLever: Double? = null,
        val flapPos: Double = 0.0,
        val flapLever: Double = 0.0,
        val lePos: Double = 0.0,
        val spoilerPos: Double = 0.0,
        val speedBrake: Double? = null,
        val apuDoor: Double = 0.0,
        val hydPressure: List<Double> = listOf(0.0, 0.0, 0.0),
        val hydPumps: Double = 0.0,
        val elecPumps: Boolean = false,
        val wow: Boolean = false,
        val groundSpeedKt: Double = 0.0,
        /** web: rwyPhase === "OVERRUN" */
        val overrun: Boolean = false,
        val wheelSpeeds: List<Double> = listOf(0.0, 0.0, 0.0, 0.0),
        val antiskid: Boolean = false,
        val parkingBrake: Boolean = false,
        val n1: List<Double> = listOf(0.0, 0.0),
        val reversers: List<Boolean> = listOf(false, false),
        val relays: List<Boolean> = emptyList(),
        val startValve: List<Boolean> = listOf(false, false),
        val fireHandles: List<Boolean> = emptyList(),
        val bottles: List<Boolean> = emptyList(),
        val signs: List<String>? = null,
        val packs: List<Boolean> = listOf(false, false),
        val fuelPumps: Double = 0.0,
        val acPower: Boolean = false,
        val dcPower: Boolean = false,
        val gearHorn: Boolean = false,
        val configOrCabinHorn: Boolean = false,
        val apWarn: Boolean = false,
        val stallWarn: Boolean = false,
        val machWarn: Boolean = false,
        val gpwsTest: Boolean = false,
        val glideslopeWarn: Boolean = false,
    )

    /**
     * Speed of a changing value, robust to uneven sampling (audio.ts `rate`). The last measured speed is held
     * for 0.4 s after the value stops changing. A stale gap (> 0.5 s) is measured as 0.1 s.
     */
    class RateTracker {
        private class S(var v: Double, var t: Double, var r: Double)

        private val map = HashMap<String, S>()

        /** web `rates.clear()`, called when sound is switched on */
        fun clear() = map.clear()

        fun rate(key: String, v: Double, now: Double): Double {
            val s = map[key]
            if (s == null) {
                map[key] = S(v, now, 0.0)
                return 0.0
            }
            if (v != s.v) {
                val gap = now - s.t
                val dt = if (gap > 0.5) 0.1 else gap.coerceIn(0.03, 0.5)
                s.r = abs(v - s.v) / dt
                s.v = v
                s.t = now
            } else if (now - s.t > 0.4) {
                s.r = 0.0
            }
            return s.r
        }
    }

    /** Snapshot of the previous tick (web `Prev`). */
    private data class Prev(
        val gear: List<Double>,
        val lever: Double?,
        val flapPos: Double,
        val flapLever: Double,
        val lePos: Double,
        val spoiler: Double,
        val speedBrake: Double?,
        val apuDoor: Double,
        val wow: Boolean,
        val rev: List<Boolean>,
        val relays: List<Boolean>,
        val fire: List<Boolean>,
        val bottles: List<Boolean>,
        val signs: List<String>?,
        val parking: Boolean,
        val brake: Double,
        val valve: List<Boolean>,
    )

    /** Mutable state carried between ticks (web module-level `prev`, `flapMoving`, `lastJoint`, ...). */
    class Extras(private val random: () -> Double = { kotlin.random.Random.nextDouble() }) {
        private val rates = RateTracker()
        private var prev: Prev? = null
        private var lastJoint = 0.0
        private var flapMoving = false
        private var leMoving = false
        private var doorMoving = false

        /** web setEnabled(true) resets the previous-tick snapshot and the rate history. */
        fun reset() {
            prev = null
            rates.clear()
        }

        fun step(inp: Inputs, now: Double): Frame {
            val events = ArrayList<Ev>()
            val g = inp.gear
            val wp = inp.wheelBrake
            val brake = (wp.getOrElse(0) { 0.0 } + wp.getOrElse(1) { 0.0 } +
                wp.getOrElse(2) { 0.0 } + wp.getOrElse(3) { 0.0 }) / 4.0 / 3000.0
            val cur = Prev(
                gear = listOf(g.getOrElse(0) { 0.0 }, g.getOrElse(1) { 0.0 }, g.getOrElse(2) { 0.0 }),
                lever = inp.gearLever,
                flapPos = inp.flapPos,
                flapLever = inp.flapLever,
                lePos = inp.lePos,
                spoiler = inp.spoilerPos,
                speedBrake = inp.speedBrake,
                apuDoor = inp.apuDoor,
                wow = inp.wow,
                rev = inp.reversers,
                relays = inp.relays,
                fire = inp.fireHandles,
                bottles = inp.bottles,
                signs = inp.signs,
                parking = inp.parkingBrake,
                brake = brake,
                valve = inp.startValve,
            )
            val p = prev
            prev = cur
            fun vel(key: String, a: Double) = rates.rate(key, a, now)
            val gs = inp.groundSpeedKt
            val pans = listOf(0.0, -0.55, 0.55)

            // ── landing gear ──
            var hyd = 0.0
            var free = 0.0
            var wind = 0.0
            for (i in 0 until 3) {
                val v = vel("gear$i", cur.gear[i])
                if (v > 0.04) {
                    if (v > 1.2) {
                        free = max(free, (v / 4.0).coerceIn(0.4, 1.0))
                        wind = max(wind, 1.0)
                    } else {
                        hyd = max(hyd, (v / 0.5).coerceIn(0.3, 1.0))
                        wind = max(wind, 0.7)
                    }
                }
                if (p == null) continue
                val a = p.gear[i]
                val b = cur.gear[i]
                val pan = pans[i]
                val manual = v > 1.2
                // uplock release + door opens
                if (a <= 0.02 && b > 0.02) {
                    events += Ev(Kind.CLUNK, pan, 0.34)
                    events += Ev(Kind.THUMP, pan, 0.2, 0.05)
                    events += Ev(Kind.WHOOSH, pan, 0.07, dur = 0.6, f0 = 250.0, f1 = 900.0, delay = 0.05)
                }
                // downlock released
                if (a > 0.98 && b <= 0.98) events += Ev(Kind.CLUNK, pan, 0.22)
                // down and locked
                if (a < 0.98 && b >= 0.98) events += Ev(Kind.LOCK_THUNK, pan, if (manual) 0.52 else 0.42, i * 0.06)
                // up and locked
                if (a > 0.02 && b <= 0.02) events += Ev(Kind.LOCK_THUNK, pan, 0.38, i * 0.05)
                // doors close
                if (a > 0.1 && b <= 0.1) {
                    events += Ev(Kind.THUMP, pan, 0.2, 0.02)
                    events += Ev(Kind.WHOOSH, pan, 0.05, dur = 0.4, f0 = 900.0, f1 = 300.0)
                }
                // wheels spin down in the well
                if (a > 0.85 && b <= 0.85) events += Ev(Kind.WHEEL_SPIN_DOWN, pan, delay = 0.1)
            }
            val gearWind = if (inp.wow) wind * 0.3 else wind
            if (p != null && cur.lever != null && p.lever != null && cur.lever != p.lever) {
                events += Ev(Kind.CLUNK, 0.0, 0.3)
                events += Ev(Kind.CLUNK, 0.0, 0.14, 0.12)
            }

            // Warning loops: the web code only acts on changes, so the layer does the same.
            val wantHorn = inp.gearHorn || inp.configOrCabinHorn
            val loops = Loops(
                configCabinHorn = wantHorn,
                apDisconnectWailer = inp.apWarn,
                stickShaker = inp.stallWarn,
                gpwsVocab = inp.gpwsTest,
                glideslope = inp.glideslopeWarn && !inp.gpwsTest,
                machClacker = inp.machWarn,
            )

            // ── flaps and leading-edge devices ──
            val vf = vel("flap", cur.flapPos)
            val vl = vel("le", cur.lePos)
            val flapNow = vf > 0.015
            val leNow = vl > 0.015
            if (p != null) {
                if (flapMoving && !flapNow) events += Ev(Kind.THUMP, -0.2, 0.14)
                if (leMoving && !leNow) events += Ev(Kind.CLUNK, 0.2, 0.22)
                if (cur.flapLever != p.flapLever) {
                    events += Ev(Kind.NOISE_BURST, 0.0, 0.2, 0.0, attack = 0.001, decay = 0.015,
                        filter = "bandpass", freq = 1900.0, q = 3.0)
                    events += Ev(Kind.NOISE_BURST, 0.0, 0.16, 0.04, attack = 0.001, decay = 0.015,
                        filter = "bandpass", freq = 2400.0, q = 3.0)
                    events += Ev(Kind.CLUNK, 0.0, 0.12, 0.02)
                }
            }
            flapMoving = flapNow
            leMoving = leNow
            val flapLevel = max(vf / 0.4, vl / 0.5 * 0.7).coerceIn(0.0, 1.0)

            // ── spoilers / speed brake ──
            val vs = vel("spoiler", cur.spoiler)
            if (p != null) {
                if (p.spoiler < 0.97 && cur.spoiler >= 0.97) events += Ev(Kind.THUMP, 0.0, 0.24)
                if (p.spoiler > 0.03 && cur.spoiler <= 0.03) events += Ev(Kind.THUMP, 0.0, 0.16)
                val sb = cur.speedBrake
                val psb = p.speedBrake
                if (sb != null && psb != null && sb != psb) {
                    events += Ev(Kind.CLUNK, 0.15, 0.26)
                    events += Ev(Kind.CLUNK, 0.15, 0.12, 0.1)
                }
            }
            val spoilerLevel = (vs / 1.0).coerceIn(0.0, 1.0)

            // ── APU door ──
            val vd = vel("door", cur.apuDoor)
            val doorNow = vd > 0.01
            if (p != null && doorMoving && !doorNow) events += Ev(Kind.THUMP, 0.5, 0.18)
            doorMoving = doorNow

            // ── hydraulics ──
            val hp = inp.hydPressure
            val hydMax = max(hp.getOrElse(0) { 0.0 }, max(hp.getOrElse(1) { 0.0 }, hp.getOrElse(2) { 0.0 }))

            // ── ground: touchdown, rollout, brakes ──
            val rolling = inp.wow && gs > 2.0
            val grass = inp.overrun
            val r = if (rolling) (gs / 150.0).coerceIn(0.0, 1.0) else 0.0
            if (p != null && !p.wow && cur.wow && gs > 40.0) {
                val k = (gs / 130.0).coerceIn(0.4, 1.0)
                events += Ev(Kind.TYRE_CHIRP, -0.4, 0.3 * k)
                events += Ev(Kind.TYRE_CHIRP, 0.4, 0.26 * k, 0.025)
                events += Ev(Kind.THUMP, -0.3, 0.55 * k)
                events += Ev(Kind.THUMP, 0.3, 0.5 * k, 0.02)
                events += Ev(Kind.WHOOSH, 0.0, 0.1 * k, dur = 1.3, f0 = 150.0, f1 = 700.0, delay = 0.05,
                    filter = "lowpass", q = 0.7)
                // nose gear slaps down
                events += Ev(Kind.THUMP, 0.0, 0.32, 1.1 + 0.5 * (1 - k))
                events += Ev(Kind.CLUNK, 0.0, 0.18, 1.12 + 0.5 * (1 - k))
            }
            if (rolling && !grass && now - lastJoint > max(0.16, 25.0 / (max(gs, 1.0) * 0.5144))) {
                lastJoint = now
                val a = 0.04 + 0.06 * r
                events += Ev(Kind.THUMP, random() * 0.5 - 0.25, a)
                events += Ev(Kind.THUMP, random() * 0.5 - 0.25, a * 0.7, 0.09)
            }

            // tyre squeal from slip, antiskid valve chatter while the system is modulating
            val ws = inp.wheelSpeeds
            var slip = 0.0
            var modulating = 0.0
            for (k in 0 until 4) {
                if (gs > 15.0 && wp.getOrElse(k) { 0.0 } > 200.0) {
                    val s = (1 - ws.getOrElse(k) { 0.0 } / gs).coerceIn(0.0, 1.0)
                    slip = max(slip, s)
                    if (s > 0.12) modulating = max(modulating, ((s - 0.1) / 0.3).coerceIn(0.0, 1.0))
                }
            }
            val skidLevel = if (rolling) ((slip - 0.25) / 0.5).coerceIn(0.0, 1.0) else 0.0
            val chatter = if (inp.antiskid && rolling) modulating else 0.0
            val brakeLevel = if (rolling) brake * (gs / 40.0).coerceIn(0.25, 1.0) else 0.0
            if (p != null) {
                if (p.brake < 0.05 && cur.brake >= 0.05) {
                    events += Ev(Kind.WHOOSH, 0.0, 0.1, dur = 0.3, f0 = 2500.0, f1 = 1200.0)
                }
                if (p.brake > 0.2 && cur.brake < 0.05) {
                    events += Ev(Kind.WHOOSH, 0.0, 0.12, dur = 0.35, f0 = 3000.0, f1 = 1500.0)
                }
                if (p.parking != cur.parking) {
                    events += Ev(Kind.CLUNK, 0.0, 0.26)
                    events += Ev(Kind.NOISE_BURST, 0.0, 0.12, 0.05, attack = 0.01, decay = 0.2,
                        filter = "highpass", freq = 2500.0, q = 0.7)
                }
            }

            // ── thrust reversers ──
            var revLevel = 0.0
            for (i in 0 until 2) {
                val deployed = cur.rev.getOrElse(i) { false }
                if (deployed) {
                    revLevel = max(revLevel, (inp.n1.getOrElse(i) { 0.0 } / 100.0).coerceIn(0.0, 1.0) * 0.9 + 0.1)
                }
                if (p == null) continue
                val pan = if (i == 0) -0.4 else 0.4
                val was = p.rev.getOrElse(i) { false }
                if (!was && deployed) {
                    events += Ev(Kind.CLUNK, pan, 0.4)
                    events += Ev(Kind.CLUNK, pan, 0.3, 0.14)
                    events += Ev(Kind.THUMP, pan, 0.3, 0.05)
                    events += Ev(Kind.WHOOSH, pan, 0.1, dur = 0.7, f0 = 300.0, f1 = 1400.0, delay = 0.1)
                }
                if (was && !deployed) {
                    events += Ev(Kind.THUMP, pan, 0.3)
                    events += Ev(Kind.CLUNK, pan, 0.3, 0.05)
                }
            }
            val revOut = if (inp.wow) revLevel else 0.0

            // ── electrical contactors, start valves, fire handles and bottles ──
            if (p != null) {
                cur.relays.forEachIndexed { i, v ->
                    val before = p.relays.getOrNull(i)
                    if (before != null && v != before) {
                        val pan = -0.3 + (i % 4) * 0.2
                        events += Ev(Kind.CLUNK, pan, 0.16, i * 0.05)
                        events += Ev(Kind.CLUNK, pan, 0.08, i * 0.05 + 0.045)
                    }
                }
                cur.valve.forEachIndexed { i, v ->
                    if (v && !p.valve.getOrElse(i) { false }) {
                        val pan = if (i == 0) -0.4 else 0.4
                        events += Ev(Kind.CLUNK, pan, 0.12)
                        events += Ev(Kind.WHOOSH, pan, 0.06, dur = 0.4, f0 = 600.0, f1 = 2200.0, delay = 0.03)
                    }
                }
                cur.fire.forEachIndexed { i, v ->
                    val before = p.fire.getOrNull(i) ?: return@forEachIndexed
                    if (v == before) return@forEachIndexed
                    if (v) {
                        events += Ev(Kind.CLUNK, 0.0, 0.42)
                        events += Ev(Kind.THUMP, 0.0, 0.28, 0.02)
                    } else {
                        events += Ev(Kind.CLUNK, 0.0, 0.26)
                    }
                }
                cur.bottles.forEachIndexed { i, v ->
                    if (!v || p.bottles.getOrElse(i) { false }) return@forEachIndexed
                    val pan = when (i) { 0 -> -0.3; 1 -> 0.3; else -> 0.0 }
                    events += Ev(Kind.THUMP, pan, 0.5)
                    events += Ev(Kind.CLUNK, pan, 0.3, 0.02)
                    events += Ev(Kind.WHOOSH, pan, 0.14, dur = 1.6, f0 = 4000.0, f1 = 1500.0, delay = 0.08,
                        filter = "highpass", q = 0.5)
                }
                val cs = cur.signs
                val ps = p.signs
                if (cs != null && ps != null) {
                    if (cs.getOrElse(0) { "OFF" } != ps.getOrElse(0) { "OFF" }) {
                        events += Ev(Kind.CHIME, 0.0, notes = listOf(1046.0, 784.0))
                    } else if (cs.getOrElse(1) { "OFF" } != ps.getOrElse(1) { "OFF" }) {
                        events += Ev(Kind.CHIME, 0.0, notes = listOf(1175.0))
                    }
                }
            }

            val levels = Levels(
                gearHyd = hyd,
                gearFree = free,
                gearWind = gearWind,
                flap = flapLevel,
                spoiler = spoilerLevel,
                door = (vd / 0.3).coerceIn(0.0, 1.0),
                hydLevel = hydMax,
                hydPumps = inp.hydPumps,
                hydElecPumps = inp.elecPumps,
                roll = r,
                grass = grass,
                skidLevel = skidLevel,
                skidFreq = 1100.0 + gs * 3.0,
                antiskidChatter = chatter,
                brake = brakeLevel,
                rev = revOut,
                ambAc = inp.acPower,
                ambDc = inp.dcPower,
                ambPacks = inp.packs.count { it },
                ambFuelPumps = inp.fuelPumps,
            )
            return Frame(events, levels, loops)
        }
    }
}
