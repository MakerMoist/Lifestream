package com.lifestream.player

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lifestream.player.data.AppSettings
import com.lifestream.player.playback.LevelerCore
import com.lifestream.player.playback.VolumeLeveler
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** "Even Out Volume": quiet songs come up, loud ones down, nothing clips, off means untouched. */
@RunWith(AndroidJUnit4::class)
class VolumeLevelerTest {
    private val rate = 48_000

    /** Plays [seconds] of a 1 kHz tone at [amplitude]; returns the loudest output sample. */
    private fun play(core: LevelerCore, amplitude: Double, seconds: Double, channels: Int = 2): Double {
        val frame = DoubleArray(channels)
        var peak = 0.0
        val frames = (seconds * rate).toInt()
        for (i in 0 until frames) {
            val x = amplitude * sin(2 * PI * 1000 * i / rate)
            for (c in 0 until channels) frame[c] = x
            core.process(frame)
            for (c in 0 until channels) {
                assertTrue("finite output", frame[c].isFinite())
                if (abs(frame[c]) > peak) peak = abs(frame[c])
            }
        }
        return peak
    }

    @Test
    fun quietSongIsRaised() {
        val core = LevelerCore(rate, 2)
        play(core, 0.05, 30.0)
        assertTrue("raised: ${core.currentGainDb} dB", core.currentGainDb in 8.0..10.0001)
    }

    @Test
    fun loudSongIsLowered() {
        val core = LevelerCore(rate, 2)
        play(core, 0.9, 30.0)
        assertTrue("lowered: ${core.currentGainDb} dB", core.currentGainDb in -6.0001..-3.0)
    }

    @Test
    fun boostedPeaksDontClip() {
        val core = LevelerCore(rate, 2, startGainDb = 10.0)
        // A quiet passage, then a sudden loud one while the gain is still high.
        play(core, 0.05, 5.0)
        val peak = play(core, 0.95, 2.0)
        assertTrue("peak $peak", peak <= 0.95)
    }

    @Test
    fun silenceDoesntPumpTheGain() {
        val core = LevelerCore(rate, 2, startGainDb = 3.0)
        play(core, 0.0, 20.0)
        assertEquals(3.0, core.currentGainDb, 1e-9)
    }

    @Test
    fun turningItOffReturnsToNormalVolume() {
        val core = LevelerCore(rate, 2)
        play(core, 0.05, 20.0)
        assertTrue(core.currentGainDb > 5)
        core.enabled = false
        play(core, 0.05, 30.0)
        assertEquals(0.0, core.currentGainDb, 1e-9)
        assertTrue("idle (audio untouched)", core.idle)
    }

    @Test
    fun corruptSamplesDontBreakIt() {
        val core = LevelerCore(rate, 2)
        val frame = DoubleArray(2)
        repeat(rate) {
            frame[0] = Double.NaN
            frame[1] = Double.POSITIVE_INFINITY
            core.process(frame)
        }
        assertTrue(core.currentGainDb.isFinite())
        play(core, 0.2, 5.0) // and it keeps working
        assertTrue(core.currentGainDb.isFinite())
    }

    @UnstableApi
    @Test
    fun switchedOffProcessorPassesAudioThroughExactly() {
        val was = AppSettings.levelVolume.value
        AppSettings.setLevelVolume(false)
        try {
            val processor = VolumeLeveler()
            processor.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
            processor.flush()
            val samples = ShortArray(9600) { (sin(it / 10.0) * 12000).toInt().toShort() }
            val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
            samples.forEach { input.putShort(it) }
            input.flip()
            processor.queueInput(input)
            val output = processor.output
            val back = ShortArray(output.remaining() / 2) { output.getShort() }
            assertArrayEquals(samples, back)
        } finally {
            AppSettings.setLevelVolume(was)
        }
    }

    @UnstableApi
    @Test
    fun processorKeepsEverySampleWhenOn() {
        val was = AppSettings.levelVolume.value
        AppSettings.setLevelVolume(true)
        try {
            val processor = VolumeLeveler()
            processor.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_FLOAT))
            processor.flush()
            val count = 4800 * 2
            val input = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder())
            repeat(count) { input.putFloat((sin(it / 7.0) * 0.3).toFloat()) }
            input.flip()
            processor.queueInput(input)
            assertEquals(0, input.remaining())
            assertEquals(count * 4, processor.output.remaining())
        } finally {
            AppSettings.setLevelVolume(was)
        }
        Log.i("LSTEST", "Even Out Volume: raises quiet songs, lowers loud ones, never clips")
    }
}
