package com.oersted.trainer.sim

import com.oersted.trainer.sim.AudioDsp.FilterType
import com.oersted.trainer.sim.AudioEvents.Kind

/**
 * Turns the one-shot events of [AudioEvents] (and the ticker's bell strikes and ignition clicks) into mono sample buffers, the way
 * the web helpers (thump, clunk, whoosh, noiseBurst, lockThunk, tyreChirp, wheelSpinDown, chime, bell, click, lightOff) schedule them.
 * Pure: the Android layer hands the buffer to the mixer with the pan and delay.
 */
object AudioEventRender {
    /** [delay] is seconds after the tick; [pan] -1..1. */
    class Rendered(val samples: DoubleArray, val pan: Double, val delay: Double)

    /** BiquadFilterType name to [FilterType]. */
    fun filterType(name: String): FilterType = when (name) {
        "lowpass" -> FilterType.LOWPASS
        "highpass" -> FilterType.HIGHPASS
        else -> FilterType.BANDPASS
    }

    fun event(ev: AudioEvents.Ev, sr: Double, seed: Long = 1L): Rendered {
        val samples = when (ev.kind) {
            Kind.CLUNK -> AudioSounds.clunk(sr, ev.amp, seed)
            Kind.THUMP -> AudioSounds.thump(sr, ev.amp, seed)
            Kind.WHOOSH -> AudioSounds.whoosh(sr, ev.amp, ev.dur, ev.f0, ev.f1, filterType(ev.filter), ev.q, seed)
            Kind.NOISE_BURST -> AudioSounds.noiseBurst(sr, ev.amp, ev.attack, ev.decay, filterType(ev.filter), ev.freq, ev.q, seed)
            Kind.LOCK_THUNK -> AudioSounds.lockThunk(sr, ev.amp, seed)
            Kind.WHEEL_SPIN_DOWN -> AudioSounds.wheelSpinDown(sr, seed)
            Kind.TYRE_CHIRP -> AudioSounds.tyreChirp(sr, ev.amp, seed)
            Kind.CHIME -> AudioSounds.chime(sr, ev.notes)
        }
        return Rendered(samples, if (ev.kind == Kind.CHIME) 0.0 else ev.pan, ev.delay)
    }

    /**
     * One fire-bell hammer strike (audio.ts bell): gong frequency alternates on k, detuned by +-0.2 %, level 0.85 to 1.15 so no two
     * strikes are identical. [r1] and [r2] are random numbers in 0..1. Starts at [at] seconds after the tick.
     */
    fun bellStrike(sr: Double, strike: AudioTicker.BellStrike, now: Double, r1: Double, r2: Double): Rendered {
        val base = AudioTicker.Tunables.BELL_BASE[strike.k % 2] * (1 + (r1 - 0.5) * 0.004)
        val amp = 0.85 + r2 * 0.3
        return Rendered(AudioSounds.bell(sr, base, amp), strike.pan, maxOf(0.0, strike.at - now))
    }

    fun click(sr: Double, c: AudioTicker.Click, seed: Long = 1L): Rendered = Rendered(AudioSounds.click(sr, seed), c.pan, 0.0)

    fun lightOff(sr: Double, l: AudioTicker.LightOff, seed: Long = 1L): Rendered =
        Rendered(AudioSwitchSounds.lightOff(sr, seed), l.pan, 0.0)
}
