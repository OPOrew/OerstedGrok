package com.oersted.trainer.platform

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder

/**
 * Decodes the recorded loops of assets/sounds to mono float PCM so the software mixer can loop them gaplessly, change their
 * pitch (engine loops) and fade them (replaces the old MediaPlayer loops, which clicked at the seam).
 * Android only (MediaExtractor / MediaCodec); not compiled in the sandbox.
 */
object SampleBank {

    /** The recorded loops (audio.ts SAMPLE_PATHS). [paths] are tried in order; [gain] is the web's sample loop gain. */
    enum class Loop(val paths: List<String>, val gain: Float) {
        FIRE_BELL(listOf("sounds/fire-bell-loop.mp3"), 0.42f),
        STICK_SHAKER(listOf("sounds/stickshaker.mp3"), 0.38f),
        AP_DISCONNECT(listOf("sounds/autopilot-disconnect.mp3"), 0.4f),
        GLIDESLOPE(listOf("sounds/glideslope.mp3"), 0.4f),
        GPWS_VOCAB(listOf("sounds/gpws-vocabulary.mp3"), 0.42f),
        CONFIG_HORN(listOf("sounds/takeoff-config-cabin-altitude.mp3"), 0.4f),
        ENGINE_IDLE(listOf("sounds/engine-idle-loop.mp3"), 1f),
        ENGINE_POWER(listOf("sounds/engine-power-loop.mp3"), 1f),
    }

    class Pcm(val samples: FloatArray, val rate: Int)

    /** Decodes the first path of [loop] that opens, or null when none does (that loop stays silent, as on the web). */
    fun decode(context: Context, loop: Loop): Pcm? {
        for (p in loop.paths) {
            val pcm = decodeAsset(context, p)
            if (pcm != null) return pcm
        }
        return null
    }

    private fun decodeAsset(context: Context, path: String): Pcm? {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        return try {
            val ex = MediaExtractor()
            extractor = ex
            context.assets.openFd(path).use { fd -> ex.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    track = i
                    format = f
                    break
                }
            }
            if (track < 0 || format == null) return null
            ex.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val dec = MediaCodec.createDecoderByType(mime)
            codec = dec
            dec.configure(format, null, null, 0)
            dec.start()

            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var out = FloatArray(rate * 4)
            var n = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val i = dec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = dec.getInputBuffer(i)!!
                        val size = ex.readSampleData(buf, 0)
                        if (size < 0) {
                            dec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            dec.queueInputBuffer(i, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = dec.dequeueOutputBuffer(info, 10_000)
                if (o >= 0) {
                    val buf = dec.getOutputBuffer(o)!!
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    val sb = buf.order(ByteOrder.nativeOrder()).asShortBuffer()
                    val frames = sb.remaining() / channels
                    if (n + frames > out.size) out = out.copyOf(maxOf(out.size * 2, n + frames))
                    for (f in 0 until frames) {
                        var acc = 0f
                        for (c in 0 until channels) acc += sb.get() / 32768f
                        out[n++] = acc / channels
                    }
                    dec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                } else if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = dec.outputFormat
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
            if (n < 2) null else Pcm(out.copyOf(n), rate)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor?.release() }
        }
    }
}
