package com.lifestream.player.playback

import android.net.Uri
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** YouTube answered, but this song's audio isn't available (not a connection problem). */
class StreamUnavailableException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Turns a YouTube video id into a direct audio-only stream URL on YouTube's media servers
 * (googlevideo.com). URLs expire after a few hours, so they are resolved lazily right before
 * playback and cached until shortly before they expire.
 */
object StreamResolver {

    /** A playable audio file: its URL and YouTube format id (itag, -1 if unknown). */
    data class Resolved(val url: String, val itag: Int)

    /** All usable audio files for a video, best first. */
    private data class Entry(val streams: List<Resolved>, val expiresAtMs: Long)

    private val cache = ConcurrentHashMap<String, Entry>()

    /**
     * Resolves the best audio file, or [preferredItag] when that format is available (used to
     * finish a partly saved song from the same file).
     */
    @Throws(IOException::class)
    fun resolveStream(videoId: String, preferredItag: Int?): Resolved {
        if (!SafeInput.isVideoId(videoId)) throw IOException("Invalid track id")
        val now = System.currentTimeMillis()
        val entry = cache[videoId]?.takeIf { it.expiresAtMs - now > 5 * 60_000 } ?: load(videoId, now)
        return preferredItag?.let { tag -> entry.streams.firstOrNull { it.itag == tag } } ?: entry.streams.first()
    }

    private fun load(videoId: String, now: Long): Entry {
        val info = try {
            StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            // YouTube answered but won't give this song (unavailable, sign-in wall…): retrying
            // straight away won't help, unlike a dropped connection.
            throw StreamUnavailableException("Couldn't load stream info for $videoId: ${e.message}", e)
        }

        val streams = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .sortedWith(
                compareBy<AudioStream> { trackPenalty(it) }
                    .thenByDescending { maxOf(it.averageBitrate, it.bitrate) }
            )
            .mapNotNull { stream ->
                val parsed = Uri.parse(stream.content)
                if (SafeInput.isHttps(parsed) && SafeInput.isGoogleVideoHost(parsed.host)) {
                    Resolved(stream.content, stream.itag)
                } else {
                    null
                }
            }
        if (streams.isEmpty()) throw StreamUnavailableException("No playable audio stream for $videoId")

        val expiresAt = Uri.parse(streams.first().url).getQueryParameter("expire")?.toLongOrNull()?.times(1000)
            ?: (now + 3 * 60 * 60_000)
        prune(now)
        return Entry(streams, expiresAt).also { cache[videoId] = it }
    }

    /** Links expire within hours, so old ones are just dropped; a long session can't pile them up. */
    private fun prune(now: Long) {
        cache.entries.removeIf { it.value.expiresAtMs <= now }
        if (cache.size >= MAX_ENTRIES) {
            cache.entries.sortedBy { it.value.expiresAtMs }.take(cache.size - MAX_ENTRIES + 1)
                .forEach { cache.remove(it.key) }
        }
    }

    private const val MAX_ENTRIES = 200

    fun invalidate(videoId: String) {
        cache.remove(videoId)
    }

    /** Prefer the original-language audio track over dubbed / descriptive ones. */
    private fun trackPenalty(stream: AudioStream): Int {
        val type = stream.audioTrackType ?: return 0
        return if (type.name == "ORIGINAL") 0 else 1
    }
}
