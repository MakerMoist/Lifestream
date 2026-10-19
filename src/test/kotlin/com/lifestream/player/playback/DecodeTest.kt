package com.lifestream.player.playback

import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ShortBuffer
import java.nio.file.Files

/** Plays a song file the way the app does: through SongStore's stream, into the decoder, with a seek. */
class DecodeTest {
    @Test
    fun aSongDecodesAndSeeksThroughTheStream() {
        val dir = Files.createTempDirectory("ls").toFile()
        val file = java.io.File(dir, "song.m4a")
        // Three seconds of a tone, as AAC in an MP4 file (what YouTube's itag 140 is).
        FFmpegFrameRecorder(file, 2).apply {
            format = "mp4"
            audioCodec = avcodec.AV_CODEC_ID_AAC
            sampleRate = 44100
            audioBitrate = 128_000
            start()
            val n = 44100 * 3
            val pcm = ShortArray(n * 2) { i -> (Math.sin(2 * Math.PI * 440 * (i / 2) / 44100.0) * 12000).toInt().toShort() }
            var at = 0
            while (at < pcm.size) {
                val len = minOf(4096, pcm.size - at)
                recordSamples(44100, 2, ShortBuffer.wrap(pcm, at, len))
                at += len
            }
            stop()
            release()
        }
        val source = SongSource("AbCdEfGhIjK", 140, file, file.length(), file.length(), true)
        val grabber = FFmpegFrameGrabber(source.open(), 0).apply {
            sampleMode = FrameGrabber.SampleMode.SHORT
            sampleFormat = avutil.AV_SAMPLE_FMT_S16
            sampleRate = 48000
            audioChannels = 2
        }
        grabber.start()
        val lengthMs = grabber.lengthInTime / 1000
        var frames = 0
        var f: Frame? = grabber.grabSamples()
        while (f != null) {
            frames++
            f = grabber.grabSamples()
        }
        grabber.setTimestamp(1_500_000)
        val afterSeek = grabber.grabSamples()
        grabber.stop()
        grabber.release()
        source.close()
        assertTrue("length $lengthMs", lengthMs in 2800..3300)
        assertTrue("frames $frames", frames > 50)
        assertTrue("seek", afterSeek != null && afterSeek.timestamp in 1_300_000..1_700_000)
        println("LSTEST Decoder: a ${lengthMs} ms song decoded ($frames frames) and seeked to ${afterSeek!!.timestamp / 1000} ms")
    }
}
