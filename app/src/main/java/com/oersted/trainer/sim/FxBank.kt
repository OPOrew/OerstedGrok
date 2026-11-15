package com.oersted.trainer.sim

import com.oersted.trainer.sim.AudioDsp.FilterType
import com.oersted.trainer.sim.AudioDsp.Wave
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Streaming renderer of the sound-effect voices of audio.ts buildFx(): landing gear, flaps, APU door, spoilers, hydraulic pumps,
 * tyre roll, skid squeal and antiskid chatter, brakes, thrust-reverser roar and the ambience (AC hum, fans, packs, fuel pumps).
 * Each voice is a filtered noise source or a lowpassed oscillator with a panner and a gain, exactly as noiseVoice / toneVoice in
 * the web; the targets come from [FxTargets] (setTargetAtTime values and time constants). The warning voices (horn, wailer, shaker,
 * clacker) are not here: the recorded loops play instead, as on the web.
 *
 * A group is audible while its bus is on and for 1.5 s after it is switched off (mkBus), so the gains can fade first. Parameters follow
 * their targets with the setTargetAtTime law, stepped every [CHUNK] samples; the low-frequency modulators (runway bumps, squeal
 * wobble, antiskid gate) are evaluated once per chunk. Call the update functions on the mixer thread (AudioMixer.post).
 */
class FxBank(private val sr: Int, seed: Long = 1L) : AudioMixer.Voice {

    /** One live parameter with the Web Audio setTargetAtTime law. */
    class Param(var value: Double) {
        private var target = value
        private var tau = 0.0
        fun set(r: FxTargets.Ramp) {
            target = r.target
            tau = r.timeConstant
        }

        /** Jumps to the target (no ramp). */
        fun snap() {
            value = target
        }

        fun step(dt: Double): Double {
            value = if (tau <= 0.0) target else value + (target - value) * (1 - exp(-dt / tau))
            return value
        }
    }

    /** mkBus: on while wanted, then 1.5 s grace after it is released. */
    class Bus {
        var active = false
            private set
        private var offAt = Double.NaN

        fun set(want: Boolean, now: Double) {
            if (want) {
                offAt = Double.NaN
                active = true
            } else if (active && offAt.isNaN()) {
                offAt = now + OFF_DELAY
            }
        }

        fun update(now: Double) {
            if (active && !offAt.isNaN() && now >= offAt) {
                active = false
                offAt = Double.NaN
            }
        }
    }

    private abstract inner class Part(pan: Double) {
        val gain = Param(0.0)
        val freq = Param(0.0)
        private val gl: Double
        private val gr: Double

        init {
            val p = AudioDsp.equalPowerPan(pan)
            gl = p.first
            gr = p.second
        }

        /** Adds [n] samples. [gainMod] / [freqMod] are this chunk's modulation (added to the parameter). */
        fun render(l: DoubleArray, r: DoubleArray, off: Int, n: Int, dt: Double, gainMod: Double = 0.0, freqMod: Double = 0.0) {
            val g = gain.step(dt) + gainMod
            val f = freq.step(dt) + freqMod
            if (Math.abs(g) < 1e-7) return
            prepare(f)
            for (i in 0 until n) {
                val v = sample(f) * g
                l[off + i] += v * gl
                r[off + i] += v * gr
            }
        }

        open fun prepare(f: Double) {}
        abstract fun sample(f: Double): Double
    }

    private inner class NoiseV(private val type: FilterType, f0: Double, private val q: Double, pan: Double, noiseSeed: Long) : Part(pan) {
        private val noise = AudioDsp.Noise(noiseSeed)
        private val filter = AudioDsp.Biquad(type, sr.toDouble(), f0, q)
        private var last = f0

        init {
            freq.value = f0
            freq.set(FxTargets.Ramp(f0, 0.0))
        }

        override fun prepare(f: Double) {
            if (Math.abs(f - last) > 0.002 * last) {
                filter.retune(type, sr.toDouble(), f.coerceIn(20.0, sr * 0.45), q)
                last = f
            }
        }

        override fun sample(f: Double): Double = filter.process(noise.next())
    }

    private inner class ToneV(wave: Wave, f0: Double, lpHz: Double, pan: Double) : Part(pan) {
        private val osc = AudioDsp.Oscillator(wave, sr.toDouble())
        private val lp = AudioDsp.Biquad(FilterType.LOWPASS, sr.toDouble(), lpHz, 1.0)

        init {
            freq.value = f0
            freq.set(FxTargets.Ramp(f0, 0.0))
        }

        override fun sample(f: Double): Double = lp.process(osc.next(f))
    }

    private var seedN = seed
    private fun noiseV(type: FilterType, f: Double, q: Double, pan: Double = 0.0) = NoiseV(type, f, q, pan, seedN++)
    private fun toneV(wave: Wave, f: Double, lp: Double, pan: Double = 0.0) = ToneV(wave, f, lp, pan)

    // ── landing gear ──
    private val gMotor = toneV(Wave.SAWTOOTH, 150.0, 700.0)
    private val gMotor2 = toneV(Wave.TRIANGLE, 300.0, 900.0)
    private val gRumble = noiseV(FilterType.BANDPASS, 120.0, 1.0)
    private val gWind = noiseV(FilterType.BANDPASS, 650.0, 0.5)
    private val gWindHi = noiseV(FilterType.HIGHPASS, 1800.0, 0.5)
    private val bGear = Bus()

    // ── flaps / slats ──
    private val fMotor = toneV(Wave.SAWTOOTH, 92.0, 600.0)
    private val fMotor2 = toneV(Wave.TRIANGLE, 185.0, 900.0)
    private val fWhir = noiseV(FilterType.BANDPASS, 760.0, 1.3)
    private val bFlap = Bus()

    // ── APU door motor ──
    private val dMotor = toneV(Wave.SAWTOOTH, 120.0, 500.0)
    private val dWhir = noiseV(FilterType.BANDPASS, 520.0, 1.2)
    private val bDoor = Bus()

    // ── spoilers ──
    private val sHiss = noiseV(FilterType.BANDPASS, 1100.0, 0.7)
    private val sRumble = noiseV(FilterType.LOWPASS, 240.0, 0.7)
    private val bSpoil = Bus()

    // ── hydraulic pumps ──
    private val hEng = toneV(Wave.SAWTOOTH, 260.0, 800.0, -0.3)
    private val hElec = toneV(Wave.SINE, 330.0, 1200.0, 0.3)
    private val hElec2 = toneV(Wave.SINE, 333.0, 1200.0, 0.3)
    private val bHyd = Bus()

    // ── tyres on the runway ──
    private val rRumble = noiseV(FilterType.LOWPASS, 160.0, 0.8)
    private val rHum = noiseV(FilterType.BANDPASS, 420.0, 1.4)
    private val rBumpRate = Param(3.2)
    private val rBumpDepth = Param(0.0)
    private var rBumpPhase = 0.0
    private val bRoll = Bus()

    // ── tyre squeal + antiskid chatter ──
    private val kSqueal = noiseV(FilterType.BANDPASS, 1300.0, 9.0)
    private val kSqDepth = Param(0.0)
    private var kSqPhase = 0.0
    private val kBuzz = toneV(Wave.SAWTOOTH, 125.0, 650.0)
    private val kGateDepth = Param(0.0)
    private var kGatePhase = 0.0
    private val bSkid = Bus()

    // ── brakes ──
    private val bHiss = noiseV(FilterType.BANDPASS, 650.0, 0.9)
    private val bGroan = toneV(Wave.SAWTOOTH, 70.0, 260.0)
    private val bBrake = Bus()

    // ── thrust reverser roar ──
    private val vRoar = noiseV(FilterType.LOWPASS, 800.0, 0.7)
    private val vLow = noiseV(FilterType.LOWPASS, 160.0, 0.8)
    private val bRev = Bus()

    // ── ambience ──
    private val aHum = toneV(Wave.SINE, 400.0, 1200.0)
    private val aHum2 = toneV(Wave.SINE, 800.0, 1500.0)
    private val aFan = noiseV(FilterType.BANDPASS, 2300.0, 0.8)
    private val aPack = noiseV(FilterType.BANDPASS, 3000.0, 0.5)
    private val aFuel = toneV(Wave.TRIANGLE, 118.0, 500.0)
    private val bAmb = Bus()

    private var now = 0.0

    // ── updates (web fx.*), each takes the targets of FxTargets ─────────────────

    fun gear(t: FxTargets.Gear, at: Double) {
        bGear.set(t.busOn, at)
        gMotor.freq.set(t.motorFreq); gMotor2.freq.set(t.motor2Freq)
        gMotor.gain.set(t.motorGain); gMotor2.gain.set(t.motor2Gain)
        gRumble.gain.set(t.rumbleGain)
        gWind.freq.set(t.windFreq); gWind.gain.set(t.windGain)
        gWindHi.gain.set(t.windHiGain)
    }

    fun flap(t: FxTargets.Flap, at: Double) {
        bFlap.set(t.busOn, at)
        fMotor.freq.set(t.motorFreq); fMotor2.freq.set(t.motor2Freq)
        fMotor.gain.set(t.motorGain); fMotor2.gain.set(t.motor2Gain)
        fWhir.gain.set(t.whirGain)
    }

    fun door(t: FxTargets.Door, at: Double) {
        bDoor.set(t.busOn, at)
        dMotor.gain.set(t.motorGain)
        dWhir.gain.set(t.whirGain)
    }

    fun spoiler(t: FxTargets.Spoiler, at: Double) {
        bSpoil.set(t.busOn, at)
        sHiss.gain.set(t.hissGain)
        sRumble.gain.set(t.rumbleGain)
    }

    fun hyd(t: FxTargets.Hyd, at: Double) {
        bHyd.set(t.busOn, at)
        hEng.freq.set(t.engFreq); hEng.gain.set(t.engGain)
        hElec.gain.set(t.elecGain); hElec2.gain.set(t.elec2Gain)
    }

    fun roll(t: FxTargets.Roll, at: Double) {
        bRoll.set(t.busOn, at)
        rRumble.freq.set(t.rumbleFreq)
        rBumpRate.set(t.bumpRate); rBumpDepth.set(t.bumpDepth)
        rRumble.gain.set(t.rumbleGain)
        rHum.freq.set(t.humFreq); rHum.gain.set(t.humGain)
    }

    fun skid(t: FxTargets.Skid, at: Double) {
        bSkid.set(t.busOn, at)
        kSqueal.freq.set(t.squealFreq)
        kSqDepth.set(t.squealLfoDepth)
        kSqueal.gain.set(t.squealGain)
        kGateDepth.set(t.chatterGain)
    }

    fun brake(t: FxTargets.Brake, at: Double) {
        bBrake.set(t.busOn, at)
        bHiss.freq.set(t.hissFreq); bHiss.gain.set(t.hissGain)
        bGroan.gain.set(t.groanGain)
    }

    fun rev(t: FxTargets.Rev, at: Double) {
        bRev.set(t.busOn, at)
        vRoar.gain.set(t.roarGain); vRoar.freq.set(t.roarFreq)
        vLow.gain.set(t.lowGain)
    }

    fun amb(t: FxTargets.Amb, at: Double) {
        bAmb.set(t.busOn, at)
        aHum.gain.set(t.humGain); aHum2.gain.set(t.hum2Gain)
        aFan.gain.set(t.fanGain); aPack.gain.set(t.packGain)
        aFuel.gain.set(t.fuelGain)
    }

    /** True while any group is audible (the caller can skip rendering otherwise). */
    fun active(): Boolean =
        bGear.active || bFlap.active || bDoor.active || bSpoil.active || bHyd.active || bRoll.active ||
            bSkid.active || bBrake.active || bRev.active || bAmb.active

    // ── render ──────────────────────────────────────────────────────────────

    private var lBuf = DoubleArray(CHUNK)
    private var rBuf = DoubleArray(CHUNK)

    override fun render(l: FloatArray, r: FloatArray, n: Int, mixer: AudioMixer): Boolean {
        now = mixer.time
        for (b in listOf(bGear, bFlap, bDoor, bSpoil, bHyd, bRoll, bSkid, bBrake, bRev, bAmb)) b.update(now)
        var off = 0
        while (off < n) {
            val m = minOf(CHUNK, n - off)
            java.util.Arrays.fill(lBuf, 0, m, 0.0)
            java.util.Arrays.fill(rBuf, 0, m, 0.0)
            chunk(m, m.toDouble() / sr)
            for (i in 0 until m) {
                l[off + i] += lBuf[i].toFloat()
                r[off + i] += rBuf[i].toFloat()
            }
            off += m
        }
        return true
    }

    private fun chunk(m: Int, dt: Double) {
        val l = lBuf
        val r = rBuf
        // every parameter keeps following its target even while its bus is off, as an AudioParam does
        val twoPi = 2 * PI
        val bump = rBumpRate.step(dt)
        val bumpD = rBumpDepth.step(dt)
        rBumpPhase = (rBumpPhase + twoPi * bump * dt) % twoPi
        val sqD = kSqDepth.step(dt)
        kSqPhase = (kSqPhase + twoPi * 7.0 * dt) % twoPi
        val gateD = kGateDepth.step(dt)
        kGatePhase = (kGatePhase + 17.0 * dt) % 1.0
        val gate = if (kGatePhase < 0.5) 1.0 else -1.0

        fun run(on: Boolean, vararg v: Part) {
            if (on) for (x in v) x.render(l, r, 0, m, dt) else for (x in v) {
                x.gain.step(dt)
                x.freq.step(dt)
            }
        }
        run(bGear.active, gMotor, gMotor2, gRumble, gWind, gWindHi)
        run(bFlap.active, fMotor, fMotor2, fWhir)
        run(bDoor.active, dMotor, dWhir)
        run(bSpoil.active, sHiss, sRumble)
        run(bHyd.active, hEng, hElec, hElec2)
        run(bBrake.active, bHiss, bGroan)
        run(bRev.active, vRoar, vLow)
        run(bAmb.active, aHum, aHum2, aFan, aPack, aFuel)
        // modulated voices
        if (bRoll.active) {
            rRumble.render(l, r, 0, m, dt, gainMod = sin(rBumpPhase) * bumpD)
            rHum.render(l, r, 0, m, dt)
        } else {
            rRumble.gain.step(dt); rRumble.freq.step(dt); rHum.gain.step(dt); rHum.freq.step(dt)
        }
        if (bSkid.active) {
            kSqueal.render(l, r, 0, m, dt, freqMod = sin(kSqPhase) * sqD)
            kBuzz.render(l, r, 0, m, dt, gainMod = gate * gateD)
        } else {
            kSqueal.gain.step(dt); kSqueal.freq.step(dt); kBuzz.gain.step(dt); kBuzz.freq.step(dt)
        }
    }

    companion object {
        /** web mkBus: a released bus stays connected this long so its gains can fade. */
        const val OFF_DELAY = 1.5
        const val CHUNK = 64
    }
}
