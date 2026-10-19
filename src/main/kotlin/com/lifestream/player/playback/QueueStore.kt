package com.lifestream.player.playback

import com.lifestream.player.data.AppDirs
import com.lifestream.player.data.ArtistCredits
import com.lifestream.player.data.OfficialTitles
import com.lifestream.player.data.Track
import com.lifestream.player.data.writeAtomically
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * "Playlist Stays Open": the queue that was playing, kept on this PC so it can be put back (paused)
 * the next time the app opens. Saved in the background a moment after it changes.
 */
internal object QueueStore {
    private const val FILE_NAME = "last_queue.json"
    private const val MAX_TRACKS = 1000
    private const val MAX_FILE_BYTES = 4L * 1024 * 1024

    class Saved(
        val tracks: List<Track>,
        val order: List<Int>,
        val playPosition: Int,
        val positionMs: Long,
        val shuffle: Boolean,
        val playlistId: String?,
        val instant: Boolean,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null

    private val file get() = File(AppDirs.data, FILE_NAME)

    /** Saves [saved] shortly (several quick changes become one write), or straight away with [now]. */
    @Synchronized
    fun save(saved: Saved, now: Boolean = false) {
        pending?.cancel()
        if (now) {
            write(saved)
            return
        }
        pending = scope.launch {
            delay(1_000)
            write(saved)
        }
    }

    @Synchronized
    fun clear() {
        pending?.cancel()
        file.delete()
    }

    /** The saved queue, or null if there's none (or it's damaged). Call off the UI thread. */
    fun load(): Saved? = runCatching {
        val f = file
        if (!f.exists() || f.length() > MAX_FILE_BYTES) return null
        val o = JSONObject(f.readText())
        val arr = o.getJSONArray("tracks")
        val tracks = (0 until minOf(arr.length(), MAX_TRACKS)).mapNotNull { i ->
            arr.optJSONObject(i)?.let(Track::fromJson)?.let(ArtistCredits::fix)?.let(OfficialTitles::fix)
        }.filter { SafeInput.isVideoId(it.id) }
        if (tracks.isEmpty()) return null
        val savedOrder = o.optJSONArray("order")?.let { a -> (0 until a.length()).map { a.optInt(it, -1) } }
        // Only an order that lists each song exactly once is used; otherwise songs play in order.
        val order = savedOrder?.takeIf { it.size == tracks.size && it.toSet() == tracks.indices.toSet() } ?: tracks.indices.toList()
        Saved(
            tracks = tracks,
            order = order,
            playPosition = o.optInt("playPosition").coerceIn(0, tracks.size - 1),
            positionMs = o.optLong("position").coerceAtLeast(0),
            shuffle = o.optBoolean("shuffle"),
            playlistId = o.optString("playlist").ifBlank { null },
            instant = o.optBoolean("instant"),
        )
    }.getOrNull()

    private fun write(saved: Saved) {
        runCatching {
            val arr = JSONArray()
            saved.tracks.take(MAX_TRACKS).forEach { arr.put(it.toJson()) }
            val o = JSONObject()
                .put("tracks", arr)
                .put("order", JSONArray(saved.order))
                .put("playPosition", saved.playPosition)
                .put("position", saved.positionMs)
                .put("shuffle", saved.shuffle)
                .put("playlist", saved.playlistId ?: "")
                .put("instant", saved.instant)
            writeAtomically(file, o.toString())
        }
    }
}
