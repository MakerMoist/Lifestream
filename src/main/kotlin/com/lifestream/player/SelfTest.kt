package com.lifestream.player

import com.lifestream.player.data.AppDirs
import com.lifestream.player.data.Catalog
import com.lifestream.player.playback.SongStore
import com.lifestream.player.playback.StreamResolver
import java.io.File

/**
 * Only runs when LIFESTREAM_SELFTEST=1 (the release build's launch check): checks that the
 * pieces that only exist on Windows (the decoder's native code, the network stack, sound
 * output) actually work there, and writes what it found to selftest.log.
 */
object SelfTest {
    private lateinit var log: File

    private fun line(s: String) {
        runCatching { log.appendText(s + "\n") }
    }

    fun start() {
        log = File(AppDirs.data, "selftest.log")
        runCatching { log.writeText("") }
        line("Started: ${Catalog.games.size} titles, data in ${AppDirs.data}")
        Thread({ run() }, "selftest").apply { isDaemon = true }.start()
    }

    private fun run() {
        step("Sound output") {
            val format = javax.sound.sampled.AudioFormat(48000f, 16, 2, true, false)
            javax.sound.sampled.AudioSystem.isLineSupported(
                javax.sound.sampled.DataLine.Info(javax.sound.sampled.SourceDataLine::class.java, format),
            ).let { if (it) "48 kHz stereo supported" else "no sound device (normal on a build server)" }
        }
        step("Decoder") {
            org.bytedeco.ffmpeg.global.avformat.avformat_version().let { "FFmpeg native code loaded ($it)" }
        }
        step("Stream") {
            val r = StreamResolver.resolveStream("Mn37d5ne2Zw", 140)
            "resolved (itag ${r.itag})"
        }
        step("Download + decode") {
            val source = SongStore.open("Mn37d5ne2Zw")
            try {
                source.open().use { input ->
                    val grabber = org.bytedeco.javacv.FFmpegFrameGrabber(input, com.lifestream.player.playback.AudioEngine.SEEKABLE)
                    grabber.sampleRate = 48000
                    grabber.audioChannels = 2
                    grabber.start()
                    var samples = 0
                    repeat(50) { grabber.grabSamples()?.let { samples++ } }
                    grabber.stop()
                    grabber.release()
                    "decoded $samples audio frames"
                }
            } finally {
                source.close()
            }
        }
        line("Done")
    }

    private fun step(name: String, block: () -> String) {
        val result = runCatching(block)
        line("$name: " + result.fold({ it }, { "FAILED ${it.javaClass.simpleName}: ${it.message}" }))
    }
}
