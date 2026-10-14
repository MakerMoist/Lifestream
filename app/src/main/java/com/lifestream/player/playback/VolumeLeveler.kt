package com.lifestream.player.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.lifestream.player.data.AppSettings
import java.nio.ByteBuffer

/**
 * Evens out loudness between songs: quiet songs are raised, loud ones lowered, toward one target.
 *
 * Runs inside the player on the decoded audio. It measures loudness the way streaming services do
 * (K-weighted, ITU-R BS.1770 style, in 100 ms blocks), ignoring silence and quiet passages, and
 * settles on one gain per song. The gain moves slowly, so a song's own quiet and loud parts keep
 * their shape; a limiter keeps boosted peaks from clipping.
 */
@OptIn(UnstableApi::class)
class VolumeLeveler : BaseAudioProcessor() {

    companion object {
        /** Bumped by the service whenever a new song starts, so the measurement starts fresh. */
        @Volatile
        var songChanges = 0
    }

    private var core: LevelerCore? = null
    private var coreRate = 0
    private var frame = DoubleArray(0)
    private var seenSongChanges = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        return if (supported && inputAudioFormat.channelCount in 1..8 && inputAudioFormat.sampleRate in 8_000..192_000) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET // not active: audio passes through untouched
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val format = inputAudioFormat // the format now in effect
        var core = core
        if (core == null || coreRate != format.sampleRate || frame.size != format.channelCount) {
            // Keep the gain across a format change (e.g. the next song at another sample rate).
            core = LevelerCore(format.sampleRate, format.channelCount, core?.currentGainDb ?: 0.0)
            this.core = core
            coreRate = format.sampleRate
            frame = DoubleArray(format.channelCount)
        }
        val out = replaceOutputBuffer(remaining)
        val changes = songChanges
        if (changes != seenSongChanges) {
            seenSongChanges = changes
            core.newSong()
        }
        core.enabled = AppSettings.levelVolume.value

        val channels = frame.size
        if (format.encoding == C.ENCODING_PCM_16BIT) {
            val frames = remaining / (2 * channels)
            repeat(frames) {
                for (c in 0 until channels) frame[c] = inputBuffer.getShort() / 32768.0
                core.process(frame)
                for (c in 0 until channels) {
                    out.putShort(Math.rint(frame[c] * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
                }
            }
        } else {
            val frames = remaining / (4 * channels)
            repeat(frames) {
                for (c in 0 until channels) frame[c] = inputBuffer.getFloat().toDouble()
                core.process(frame)
                for (c in 0 until channels) out.putFloat(frame[c].coerceIn(-1.0, 1.0).toFloat())
            }
        }
        // A partial frame can't happen with PCM, but never leave input unconsumed.
        while (inputBuffer.hasRemaining()) out.put(inputBuffer.get())
        out.flip()
    }

    override fun onReset() {
        core = null
    }
}

/**
 * The measuring and gain logic, kept free of Android types. Works on one frame of samples in
 * [-1, 1] at a time, changing them in place.
 */
internal class LevelerCore(sampleRate: Int, private val channels: Int, startGainDb: Double = 0.0) {

    private companion object {
        const val TARGET_LUFS = -14.0 // what most streaming services aim for
        const val MAX_BOOST_DB = 10.0
        const val MAX_CUT_DB = -6.0 // loud songs come down, but not by too much
        const val CEILING = 0.891 // -1 dBFS
        const val SILENCE_LUFS = -60.0
        const val PEAK_ALLOWANCE_DB = 5.0
        const val QUIET_GATE_LU = 15.0 // passages this far under the song's level don't count
    }

    var enabled = true
    var currentGainDb = startGainDb
        private set

    private val blockFrames = (sampleRate / 10).coerceAtLeast(1) // 100 ms

    // K-weighting: a high shelf (+4 dB above ~1.7 kHz) then a high-pass at ~38 Hz.
    private val shelf: DoubleArray = run {
        val f0 = 1681.974450955533
        val g = 3.999843853973347
        val q = 0.7071752369554196
        val k = Math.tan(Math.PI * f0 / sampleRate)
        val vh = Math.pow(10.0, g / 20.0)
        val vb = Math.pow(vh, 0.4996667741545416)
        val a0 = 1.0 + k / q + k * k
        doubleArrayOf(
            (vh + vb * k / q + k * k) / a0,
            2.0 * (k * k - vh) / a0,
            (vh - vb * k / q + k * k) / a0,
            2.0 * (k * k - 1.0) / a0,
            (1.0 - k / q + k * k) / a0,
        )
    }
    private val highPass: DoubleArray = run {
        val f0 = 38.13547087602444
        val q = 0.5003270373238773
        val k = Math.tan(Math.PI * f0 / sampleRate)
        val a0 = 1.0 + k / q + k * k
        doubleArrayOf(
            1.0, -2.0, 1.0,
            2.0 * (k * k - 1.0) / a0,
            (1.0 - k / q + k * k) / a0,
        )
    }
    private val state = DoubleArray(channels * 4)

    // Measurement.
    private var blockEnergy = 0.0
    private var blockCount = 0
    private var blockPeak = 0.0
    private var meanEnergy = 0.0
    private var counted = 0 // blocks that have counted toward this song's level
    private var songPeak = 0.0

    // Gain, ramped per sample from one block's value to the next so changes are click-free.
    private var gain = Math.pow(10.0, startGainDb / 20.0)
    private var gainStep = 0.0
    private var limiter = 1.0
    private val limiterRelease = 1.0 - Math.exp(-1.0 / (0.15 * sampleRate)) // ~150 ms

    /** A new song: measure it from scratch, starting from the gain the last one ended on. */
    fun newSong() {
        meanEnergy = 0.0
        counted = 0
        songPeak = 0.0
    }

    fun process(frame: DoubleArray) {
        var energy = 0.0
        var peak = 0.0
        for (c in 0 until channels) {
            val x = frame[c]
            energy += kWeighted(c, x).let { it * it }
            val a = Math.abs(x)
            if (a > peak) peak = a
        }
        blockEnergy += energy
        if (peak > blockPeak) blockPeak = peak

        // Apply the gain, then the limiter (instant attack, smooth release). Without a boost the
        // song's own peaks can't clip, so the limiter only works on boosted audio.
        val g = gain
        gain += gainStep
        val out = peak * g
        limiter = if (g > 1.0 && out * limiter > CEILING) CEILING / out
        else limiter + (1.0 - limiter) * limiterRelease
        val total = g * limiter
        for (c in 0 until channels) frame[c] *= total

        if (++blockCount >= blockFrames) endBlock()
    }

    private fun kWeighted(c: Int, x: Double): Double {
        val i = c * 4
        // Transposed direct form II, two stages.
        val s = shelf
        val y1 = s[0] * x + state[i]
        state[i] = s[1] * x - s[3] * y1 + state[i + 1]
        state[i + 1] = s[2] * x - s[4] * y1
        val h = highPass
        val y2 = h[0] * y1 + state[i + 2]
        state[i + 2] = h[1] * y1 - h[3] * y2 + state[i + 3]
        state[i + 3] = h[2] * y1 - h[4] * y2
        return y2
    }

    private fun endBlock() {
        val meanSquare = blockEnergy / blockCount
        val loudness = lufs(meanSquare)
        val estimate = if (counted > 0) lufs(meanEnergy) else loudness
        if (loudness > SILENCE_LUFS && (counted < 10 || loudness > estimate - QUIET_GATE_LU)) {
            counted++
            // A running average that starts as a plain mean and settles to a ~15 s memory; it
            // catches up within a couple of seconds when the music gets much louder.
            var alpha = maxOf(1.0 / counted, 1.0 / 150)
            if (loudness > estimate + 6) alpha = maxOf(alpha, 1.0 / 20)
            meanEnergy += alpha * (meanSquare - meanEnergy)
            if (blockPeak > songPeak) songPeak = blockPeak
        }

        val wanted = when {
            !enabled -> 0.0
            counted < 3 -> currentGainDb // not enough heard yet: hold
            else -> {
                // Don't boost so far that the limiter has to squash this song's peaks by more
                // than a few dB.
                val headroom = if (songPeak > 0) 20 * Math.log10(CEILING / songPeak) + PEAK_ALLOWANCE_DB else MAX_BOOST_DB
                (TARGET_LUFS - lufs(meanEnergy)).coerceIn(MAX_CUT_DB, minOf(MAX_BOOST_DB, maxOf(0.0, headroom)))
            }
        }
        val early = counted < 40
        val up = if (early) 0.5 else 0.08 // dB per 100 ms block
        val down = if (early) 1.0 else 0.3
        currentGainDb = when {
            wanted > currentGainDb -> minOf(wanted, currentGainDb + up)
            else -> maxOf(wanted, currentGainDb - down)
        }
        val next = Math.pow(10.0, currentGainDb / 20.0)
        gainStep = (next - gain) / blockFrames

        blockEnergy = 0.0
        blockCount = 0
        blockPeak = 0.0
    }

    private fun lufs(meanSquare: Double) = -0.691 + 10 * Math.log10(meanSquare + 1e-12)
}
