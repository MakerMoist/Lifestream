package com.lifestream.player.playback

import android.net.Uri
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns a YouTube video id into a direct audio-only stream URL on YouTube's media servers
 * (googlevideo.com). URLs expire after a few hours, so they are resolved lazily right before
 * playback and cached until shortly before they expire.
 */
object StreamResolver {

    private data class Entry(val url: String, val expiresAtMs: Long)

    private val cache = ConcurrentHashMap<String, Entry>()

    @Throws(IOException::class)
    fun resolve(videoId: String): String {
        if (!SafeInput.isVideoId(videoId)) throw IOException("Invalid track id")
        val now = System.currentTimeMillis()
        cache[videoId]?.let { if (it.expiresAtMs - now > 5 * 60_000) return it.url }

        val info = try {
            StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Couldn't load stream info for $videoId: ${e.message}", e)
        }

        val best = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .sortedWith(
                compareBy<AudioStream> { trackPenalty(it) }
                    .thenByDescending { maxOf(it.averageBitrate, it.bitrate) }
            )
            .firstOrNull()
            ?: throw IOException("No playable audio stream for $videoId")

        val url = best.content
        val parsed = Uri.parse(url)
        if (!SafeInput.isHttps(parsed) || !SafeInput.isGoogleVideoHost(parsed.host)) {
            throw IOException("Unexpected stream host for $videoId")
        }
        val expiresAt = parsed.getQueryParameter("expire")?.toLongOrNull()?.times(1000)
            ?: (now + 3 * 60 * 60_000)
        cache[videoId] = Entry(url, expiresAt)
        return url
    }

    fun invalidate(videoId: String) {
        cache.remove(videoId)
    }

    /** Prefer the original-language audio track over dubbed / descriptive ones. */
    private fun trackPenalty(stream: AudioStream): Int {
        val type = stream.audioTrackType ?: return 0
        return if (type.name == "ORIGINAL") 0 else 1
    }
}
