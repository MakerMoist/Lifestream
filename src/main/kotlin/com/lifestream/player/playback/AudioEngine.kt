package com.lifestream.player.playback

import com.lifestream.player.data.AppSettings
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import java.nio.ShortBuffer
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * Plays one song at a time out of the PC's speakers: FFmpeg decodes the audio (YouTube's AAC or
 * Opus) to 48 kHz stereo, "Volume Equalizer" evens out its loudness, the player's volume is
 * applied, and the result goes to the default sound device.
 */
class AudioEngine(private val listener: Listener) {

    interface Listener {
        /** The song is decoding; [durationMs] is its length (0 if unknown). */
        fun onReady(session: Any, durationMs: Long)
        fun onEnded(session: Any)
        fun onError(session: Any, error: Throwable)
    }

    private companion object {
        const val RATE = 48_000
        const val CHANNELS = 2
        val FORMAT = AudioFormat(RATE.toFloat(), 16, CHANNELS, true, false)

        init {
            runCatching { avutil.av_log_set_level(avutil.AV_LOG_QUIET) }
        }
    }

    /** Kept across songs, so the next one starts from the level the last one ended on. */
    private val leveler = LevelerCore(RATE, CHANNELS)
    private val frame = DoubleArray(CHANNELS)

    @Volatile private var session: Session? = null

    val isPlaying: Boolean get() = session?.let { !it.paused && !it.finished } == true

    /** Starts [source] at [startMs]; paused when [play] is false. Returns the session's handle. */
    fun start(source: SongSource, startMs: Long, play: Boolean): Any {
        stop()
        leveler.newSong()
        return Session(source, startMs, !play).also {
            session = it
            it.start()
        }
    }

    fun setPlaying(play: Boolean) {
        val s = session ?: return
        synchronized(s.lock) {
            s.paused = !play
            s.lock.notifyAll()
        }
    }

    fun seekTo(ms: Long) {
        val s = session ?: return
        synchronized(s.lock) {
            s.seekMs = ms.coerceAtLeast(0)
            s.positionMs = s.seekMs
            s.lock.notifyAll()
        }
    }

    /** Where the song is, in ms. */
    fun positionMs(): Long = session?.currentPositionMs() ?: 0L

    fun stop() {
        val s = session ?: return
        session = null
        s.halt()
    }

    private inner class Session(
        private val source: SongSource,
        startMs: Long,
        @Volatile var paused: Boolean,
    ) : Thread("audio") {
        val lock = Object()
        @Volatile var seekMs: Long = if (startMs > 0) startMs else -1
        @Volatile var positionMs: Long = startMs.coerceAtLeast(0)
        @Volatile var finished = false
        @Volatile private var halted = false
        private var line: SourceDataLine? = null
        /** Position of the last decoded audio, and how many line frames had been written then. */
        @Volatile private var decodedUs = 0L
        @Volatile private var writtenFrames = 0L

        init {
            isDaemon = true
            priority = MAX_PRIORITY
        }

        fun currentPositionMs(): Long {
            val l = line ?: return positionMs
            if (seekMs >= 0) return positionMs
            // What's been decoded, minus what's still waiting in the sound card's buffer.
            val queuedFrames = (writtenFrames - l.longFramePosition).coerceAtLeast(0)
            return (decodedUs / 1000 - queuedFrames * 1000 / RATE).coerceAtLeast(0)
        }

        fun halt() {
            synchronized(lock) {
                halted = true
                lock.notifyAll()
            }
            source.close()
        }

        override fun run() {
            var grabber: FFmpegFrameGrabber? = null
            var input: SongInputStream? = null
            try {
                input = source.open()
                grabber = FFmpegFrameGrabber(input, 0).apply {
                    sampleMode = FrameGrabber.SampleMode.SHORT
                    sampleFormat = avutil.AV_SAMPLE_FMT_S16
                    sampleRate = RATE
                    audioChannels = CHANNELS
                }
                grabber.start()
                if (halted) return
                val durationMs = (grabber.lengthInTime / 1000).coerceAtLeast(0)
                listener.onReady(this, durationMs)
                val l = AudioSystem.getSourceDataLine(FORMAT)
                line = l
                l.open(FORMAT, RATE * 4 / 4) // a quarter of a second of buffer
                var bytes = ByteArray(16 * 1024)
                while (!halted) {
                    // Paused: let the sound card drain and wait.
                    if (paused) {
                        l.stop()
                        synchronized(lock) { while (paused && !halted && seekMs < 0) lock.wait(500) }
                        if (halted) break
                        if (!paused) l.start()
                    }
                    val target = seekMs
                    if (target >= 0) {
                        grabber.setTimestamp(target * 1000)
                        l.flush()
                        writtenFrames = l.longFramePosition
                        decodedUs = target * 1000
                        seekMs = -1
                        leveler.newSong()
                        if (paused) continue
                    }
                    if (!l.isRunning && !paused) l.start()
                    val f = grabber.grabSamples() ?: break
                    val buffers = f.samples ?: continue
                    val samples = writeSamples(buffers, bytes) ?: continue
                    if (samples.first !== bytes) bytes = samples.first // grew for a bigger frame
                    l.write(samples.first, 0, samples.second)
                    writtenFrames += samples.second / 4
                    decodedUs = f.timestamp
                    positionMs = decodedUs / 1000
                }
                if (!halted) {
                    // The download stopped with an error rather than the song ending.
                    source.error?.let { throw it }
                    l.drain()
                    finished = true
                    listener.onEnded(this)
                }
            } catch (e: Throwable) {
                if (!halted) {
                    finished = true
                    listener.onError(this, source.error ?: e)
                }
            } finally {
                line?.let { runCatching { it.stop(); it.close() } }
                runCatching { grabber?.stop() }
                runCatching { grabber?.release() }
                runCatching { input?.close() }
                if (halted) source.close()
            }
        }

        /** Decoded samples (interleaved or one buffer per channel) → leveled, volume-scaled bytes. */
        private fun writeSamples(buffers: Array<java.nio.Buffer>, scratch: ByteArray): Pair<ByteArray, Int>? {
            val first = buffers.firstOrNull() as? ShortBuffer ?: return null
            val planar = buffers.size > 1
            val frames = if (planar) first.remaining() else first.remaining() / CHANNELS
            if (frames <= 0) return null
            val needed = frames * CHANNELS * 2
            val out = if (scratch.size >= needed) scratch else ByteArray(needed)
            leveler.enabled = AppSettings.levelVolume.value
            val v = AppSettings.volume.value.toDouble()
            val volume = v * v // the slider feels even to the ear
            val idle = leveler.idle
            var o = 0
            val start0 = first.position()
            for (i in 0 until frames) {
                for (c in 0 until CHANNELS) {
                    val s = if (planar) {
                        val b = (buffers.getOrNull(c) ?: first) as ShortBuffer
                        b.get(b.position() + i)
                    } else {
                        first.get(start0 + i * CHANNELS + c)
                    }
                    frame[c] = s / 32768.0
                }
                if (!idle) leveler.process(frame)
                for (c in 0 until CHANNELS) {
                    val value = Math.rint(frame[c] * volume * 32768.0).coerceIn(-32768.0, 32767.0).toInt()
                    out[o++] = (value and 0xFF).toByte()
                    out[o++] = ((value shr 8) and 0xFF).toByte()
                }
            }
            return out to needed
        }
    }
}
