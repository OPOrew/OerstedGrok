package com.oersted.trainer.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.oersted.trainer.sim.ApuVoice
import com.oersted.trainer.sim.AudioEventRender
import com.oersted.trainer.sim.AudioEvents
import com.oersted.trainer.sim.AudioFeed
import com.oersted.trainer.sim.AudioMixer
import com.oersted.trainer.sim.AudioSounds
import com.oersted.trainer.sim.AudioSwitchSounds
import com.oersted.trainer.sim.AudioTicker
import com.oersted.trainer.sim.EngineVoice
import com.oersted.trainer.sim.EngineSpoolAudio
import com.oersted.trainer.sim.FxBank
import com.oersted.trainer.sim.FxTargets
import com.oersted.trainer.sim.MarkerBeacons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Android side of audio.ts / MarkerAudio.tsx. A software mixer (sim/AudioMixer) feeds a streaming AudioTrack thread; this class wires
 * the sim to it:
 *  - [AudioFeed] maps the sim to the ticker inputs; [AudioTicker.tick] runs every 100 ms and [AudioTicker.tickEngines] every 90 ms
 *    (as useSimAudio does), the marker tone every 20 ms (MarkerAudio.tsx REFRESH_MS);
 *  - one-shots (clunks, thumps, chimes, ignition clicks, bell strikes ...) are synthesised on a worker thread and queued on the mixer;
 *  - recorded loops (fire bell, stick shaker, A/P disconnect, glideslope, GPWS, config horn) are decoded once and faded in and out;
 *  - each engine is an EngineVoice: JT8D synth layers plus the recorded idle and power loops, mixed and pitched from the acoustic
 *    spool (EngineSpoolAudio + EngineSampleLayer) and shaped by low / high shelves;
 *  - sound effects (gear, flaps, hydraulics, tyres, brakes, ambience ...) stream from FxBank with the levels of each tick;
 *  - the APU voice, the Mach clacker and the marker tone are streamed in the mixer.
 * On at launch and whenever the app is in front (no browser autoplay rule on Android); the SOUND button turns it off and on, and the choice is kept. Volume is the web master gain (default 0.6). Not compiled in the sandbox.
 */
class AudioEngine(context: Context) {
    private val app = context.applicationContext
    private val sr = SAMPLE_RATE
    private val mixer = AudioMixer(sr)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "audio-render").apply { isDaemon = true } }
    private val random = Random.Default
    private val ticker = AudioTicker { random.nextDouble() }

    private val _on = MutableStateFlow(false)
    /** The SOUND ON / OFF state. */
    val on: StateFlow<Boolean> = _on.asStateFlow()
    private val _volume = MutableStateFlow(0.6f)
    /** Master volume 0..1. */
    val volume: StateFlow<Float> = _volume.asStateFlow()

    @Volatile private var track: AudioTrack? = null
    @Volatile private var mixThread: Thread? = null
    @Volatile private var running = false

    // recorded loops, created by the worker once decoded
    private val loops = java.util.concurrent.ConcurrentHashMap<SampleBank.Loop, AudioMixer.SampleLoop>()
    @Volatile private var samplesStarted = false
    @Volatile private var clickBank: List<FloatArray> = emptyList()

    // streamed voices (mixer thread)
    private val apu = ApuVoice(sr.toDouble())
    private val apuStream = mixer.Stream({ out, t -> apu.render(out, t) })
    private val tone = mixer.Tone(MarkerBeacons.markerSpec(MarkerBeacons.MarkerId.OM).hz)
    private val fx = FxBank(sr)
    private val engines = listOf(EngineVoice(sr, 0, 11L), EngineVoice(sr, 1, 23L))
    private val clacker = mixer.SampleLoop(
        AudioMixer.toFloat(AudioSounds.clackLoop(sr.toDouble(), AudioTicker.Tunables.CLACK_HZ.toInt())), sr,
    ).also { it.gainTau = 0.01 }

    // main-thread state
    private val spools = arrayOf(EngineSpoolAudio.newAcousticSpool(), EngineSpoolAudio.newAcousticSpool())
    private var lastEngineNs = 0L
    private var lastClickSec = -1.0
    @Volatile private var markerId: MarkerBeacons.MarkerId? = null
    private var job: Job? = null

    init {
        mixer.master = 0.6f
        mixer.add(apuStream)
        mixer.add(tone)
        mixer.add(clacker)
        mixer.add(fx)
        for (e in engines) mixer.add(e)
        apuStream.on = false
    }

    /** Starts the tick loops. [feed] builds the audio inputs from the live sim; it is called on the main thread. */
    fun start(feed: () -> AudioFeed.Feed) {
        if (job != null) return
        job = scope.launch {
            launch {
                while (isActive) {
                    delay(TICK_MS)
                    if (_on.value) onTick(feed())
                }
            }
            launch {
                while (isActive) {
                    delay(ENGINE_MS)
                    if (_on.value) onEngines(feed())
                }
            }
            launch {
                while (isActive) {
                    delay(MARKER_MS)
                    onMarker()
                }
            }
        }
    }

    /** What the person last chose with the SOUND button. Sound starts ON (a phone has no autoplay block, unlike a browser). */
    private var userWants = true

    /** web audio.toggle(). */
    fun toggle() {
        userWants = !_on.value
        setEnabled(userWants)
    }

    /** App left the foreground: stop the output and the ticks (the person's choice is kept). */
    fun onBackground() {
        if (_on.value) setEnabled(false)
    }

    /** App is visible again (also the first time it opens): sound comes back unless the person turned it off. */
    fun onForeground() {
        if (userWants) setEnabled(true)
    }

    /** web setEnabled. Turning on starts the output, loads the loops and resets the snapshots so nothing bursts. */
    fun setEnabled(on: Boolean) {
        if (on == _on.value) return
        if (on) {
            ticker.onSoundOn()
            spools[0] = EngineSpoolAudio.newAcousticSpool()
            spools[1] = EngineSpoolAudio.newAcousticSpool()
            lastEngineNs = 0L
            startOutput()
            startSamples()
            _on.value = true
        } else {
            _on.value = false
            silence()
            stopOutput()
        }
    }

    /** web setVolume(0..1) on the master gain. */
    fun setVolume(v: Float) {
        val c = v.coerceIn(0f, 1f)
        _volume.value = c
        mixer.master = c
    }

    /** audio.ts playSwitchClick: a short mechanical click, throttled to one per 35 ms. */
    fun switchClick() {
        if (!_on.value || !running) return
        val now = mixer.time
        if (now - lastClickSec < AudioFeed.CLICK_THROTTLE_S) return
        lastClickSec = now
        val bank = clickBank
        if (bank.isEmpty()) return
        val buf = bank[random.nextInt(bank.size)]
        mixer.post { mixer.add(mixer.OneShot(buf, 0.0, 1f)) }
    }

    fun release() {
        job?.cancel()
        job = null
        setEnabled(false)
        scope.cancel()
        worker.shutdown()
    }

    // ── output ────────────────────────────────────────────────────────────────

    private fun startOutput() {
        if (running) return
        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val bytes = maxOf(minBuf, BLOCK_FRAMES * 4 * 3)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sr)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        running = true
        t.play()
        mixThread = Thread({
            val buf = ShortArray(BLOCK_FRAMES * 2)
            while (running) {
                mixer.render(buf, BLOCK_FRAMES)
                val w = t.write(buf, 0, buf.size)
                if (w < 0) break
            }
        }, "audio-mixer").apply {
            priority = Thread.MAX_PRIORITY
            isDaemon = true
            start()
        }
    }

    private fun stopOutput() {
        running = false
        runCatching { mixThread?.join(500) }
        mixThread = null
        track?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        track = null
    }

    /** Everything quiet (sound switched off). */
    private fun silence() {
        mixer.post {
            for (l in loops.values) l.targetGain = 0f
            // engines wind down to nothing; the voices fall silent and their buses close
            for (e in engines) e.update(EngineSpoolAudio.newAcousticSpool(), false, mixer.time)
            clacker.targetGain = 0f
            tone.targetGain = 0f
            apuStream.on = false
            applyLevels(AudioEvents.Levels())
        }
        markerId = null
    }

    /** web fx.gear / flap / door / spoiler / hyd / roll / skid / brake / rev / amb with this tick's levels. Mixer thread only. */
    private fun applyLevels(lv: AudioEvents.Levels) {
        val t = mixer.time
        fx.gear(FxTargets.gear(lv.gearHyd, lv.gearFree, lv.gearWind), t)
        fx.flap(FxTargets.flap(lv.flap), t)
        fx.door(FxTargets.door(lv.door), t)
        fx.spoiler(FxTargets.spoiler(lv.spoiler), t)
        fx.hyd(FxTargets.hyd(lv.hydLevel, lv.hydPumps, lv.hydElecPumps), t)
        fx.roll(FxTargets.roll(lv.roll, lv.grass), t)
        fx.skid(FxTargets.skid(lv.skidLevel, lv.skidFreq, lv.antiskidChatter), t)
        fx.brake(FxTargets.brake(lv.brake), t)
        fx.rev(FxTargets.rev(lv.rev), t)
        fx.amb(FxTargets.amb(lv.ambAc, lv.ambDc, lv.ambPacks.toDouble(), lv.ambFuelPumps), t)
    }

    // ── samples ───────────────────────────────────────────────────────────────

    /** Decodes the recorded loops and the click bank on the worker thread, once. */
    private fun startSamples() {
        if (samplesStarted) return
        samplesStarted = true
        worker.execute {
            clickBank = (0 until CLICK_VARIANTS).map { i ->
                val pitch = AudioSwitchSounds.clickPitch(i / (CLICK_VARIANTS - 1.0))
                AudioMixer.toFloat(AudioSwitchSounds.switchClick(sr.toDouble(), pitch, 100L + i))
            }
            for (loop in SampleBank.Loop.values()) {
                if (loop == SampleBank.Loop.ENGINE_IDLE || loop == SampleBank.Loop.ENGINE_POWER) continue
                val pcm = SampleBank.decode(app, loop) ?: continue
                val v = mixer.SampleLoop(pcm.samples, pcm.rate)
                v.gainTau = 0.02
                loops[loop] = v
                mixer.post { mixer.add(v) }
            }
            val idle = SampleBank.decode(app, SampleBank.Loop.ENGINE_IDLE)
            val power = SampleBank.decode(app, SampleBank.Loop.ENGINE_POWER)
            if (idle != null && power != null) {
                // each engine gets its own pair of loops, centred (the voice pans them) and started at its own point
                val pairs = engines.map {
                    val a = mixer.SampleLoop(idle.samples, idle.rate)
                    val b = mixer.SampleLoop(power.samples, power.rate)
                    a.startAt(random.nextDouble())
                    b.startAt(random.nextDouble())
                    a to b
                }
                mixer.post { for ((i, p) in pairs.withIndex()) engines[i].attach(p.first, p.second) }
            }
        }
    }

    // ── ticks ─────────────────────────────────────────────────────────────────

    private fun onTick(feed: AudioFeed.Feed) {
        val now = mixer.time
        val ready = _on.value && running
        val r = ticker.tick(feed.tick, now, ready, loops.containsKey(SampleBank.Loop.FIRE_BELL))
        markerId = feed.marker
        val frame = r.frame ?: return

        // one-shots: synthesised off the main thread, then queued with their delay
        val events = frame.events
        val clicks = r.clicks
        val lightOffs = r.lightOffs
        val strikes = r.bellStrikes
        if (events.isNotEmpty() || clicks.isNotEmpty() || lightOffs.isNotEmpty() || strikes.isNotEmpty()) {
            val seed = random.nextLong()
            val bellRand = DoubleArray(strikes.size * 2) { random.nextDouble() }
            worker.execute {
                val out = ArrayList<AudioEventRender.Rendered>()
                for ((i, e) in events.withIndex()) out += AudioEventRender.event(e, sr.toDouble(), seed + i)
                for ((i, c) in clicks.withIndex()) out += AudioEventRender.click(sr.toDouble(), c, seed + 100 + i)
                for ((i, l) in lightOffs.withIndex()) out += AudioEventRender.lightOff(sr.toDouble(), l, seed + 200 + i)
                for ((i, s) in strikes.withIndex()) out += AudioEventRender.bellStrike(sr.toDouble(), s, now, bellRand[2 * i], bellRand[2 * i + 1])
                mixer.post { for (o in out) mixer.add(mixer.OneShot(AudioMixer.toFloat(o.samples), o.pan, 1f, o.delay)) }
            }
        }

        // loops
        val l = frame.loops
        val lv = frame.levels
        val bellOn = r.fireBellLoopOn
        mixer.post {
            setLoop(SampleBank.Loop.FIRE_BELL, bellOn)
            setLoop(SampleBank.Loop.STICK_SHAKER, l.stickShaker)
            setLoop(SampleBank.Loop.AP_DISCONNECT, l.apDisconnectWailer)
            setLoop(SampleBank.Loop.GLIDESLOPE, l.glideslope)
            setLoop(SampleBank.Loop.GPWS_VOCAB, l.gpwsVocab)
            setLoop(SampleBank.Loop.CONFIG_HORN, l.configCabinHorn)
            val (_, ramp) = FxTargets.clacker(l.machClacker)
            clacker.gainTau = ramp.timeConstant
            clacker.targetGain = ramp.target.toFloat()
            apuStream.on = apu.update(r.apuRpm, r.apuStarter, mixer.time)
            applyLevels(lv)
        }
    }

    private fun setLoop(loop: SampleBank.Loop, on: Boolean) {
        val v = loops[loop] ?: return
        v.targetGain = if (on) loop.gain else 0f
    }

    private fun onEngines(feed: AudioFeed.Feed) {
        val ready = _on.value && running
        val ups = ticker.tickEngines(feed.engines, ready)
        if (ups.isEmpty()) return
        val nowNs = System.nanoTime()
        val dt = if (lastEngineNs == 0L) 0.0 else (nowNs - lastEngineNs) / 1e9
        lastEngineNs = nowNs
        for (u in ups) {
            spools[u.engine] = EngineSpoolAudio.stepAcousticSpool(spools[u.engine], u.n1, u.n2, dt, u.snap)
            val sp = spools[u.engine]
            val e = engines[u.engine]
            mixer.post { e.update(sp, u.valve, mixer.time, u.snap) }
        }
    }

    /** MarkerAudio.tsx: the keyed beacon tone, refreshed every 20 ms; gain 0.12 while keyed. */
    private fun onMarker() {
        val id = if (_on.value && running) markerId else null
        if (id != null) tone.freq = MarkerBeacons.markerSpec(id).hz
        tone.targetGain = AudioFeed.markerGain(id, mixer.time).toFloat()
    }

    private companion object {
        const val SAMPLE_RATE = 44100
        const val BLOCK_FRAMES = 1024
        const val TICK_MS = 100L
        const val ENGINE_MS = 90L
        const val MARKER_MS = 20L
        const val CLICK_VARIANTS = 8
    }
}
