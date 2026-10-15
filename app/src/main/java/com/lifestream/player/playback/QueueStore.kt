package com.lifestream.player.playback

import android.content.Context
import com.lifestream.player.data.ArtistCredits
import com.lifestream.player.data.OfficialTitles
import com.lifestream.player.data.Track
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
 * "Playlist Stays Open": the queue that was playing, kept on the phone so it can be put back
 * (paused) the next time the app opens. Saved in the background a moment after it changes.
 */
internal object QueueStore {
    private const val FILE_NAME = "last_queue.json"
    private const val MAX_TRACKS = 1000
    private const val MAX_FILE_BYTES = 4L * 1024 * 1024

    class Saved(
        val tracks: List<Track>,
        val index: Int,
        val positionMs: Long,
        val shuffle: Boolean,
        val playlistId: String?,
        val instant: Boolean,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** Saves [saved] shortly (several quick changes become one write). */
    fun save(context: Context, saved: Saved, now: Boolean = false) {
        val f = file(context.applicationContext)
        pending?.cancel()
        pending = scope.launch {
            if (!now) delay(1_000)
            write(f, saved)
        }
    }

    fun clear(context: Context) {
        pending?.cancel()
        val f = file(context.applicationContext)
        scope.launch { f.delete() }
    }

    /** The saved queue, or null if there's none (or it's damaged). Call off the main thread. */
    fun load(context: Context): Saved? = runCatching {
        val f = file(context)
        if (!f.exists() || f.length() > MAX_FILE_BYTES) return null
        val o = JSONObject(f.readText())
        val arr = o.getJSONArray("tracks")
        val tracks = (0 until minOf(arr.length(), MAX_TRACKS)).mapNotNull { i ->
            arr.optJSONObject(i)?.let(Track::fromJson)?.let(ArtistCredits::fix)?.let(OfficialTitles::fix)
        }.filter { SafeInput.isVideoId(it.id) }
        if (tracks.isEmpty()) return null
        Saved(
            tracks = tracks,
            index = o.optInt("index").coerceIn(0, tracks.size - 1),
            positionMs = o.optLong("position").coerceAtLeast(0),
            shuffle = o.optBoolean("shuffle"),
            playlistId = o.optString("playlist").ifBlank { null },
            instant = o.optBoolean("instant"),
        )
    }.getOrNull()

    private fun write(f: File, saved: Saved) {
        runCatching {
            val arr = JSONArray()
            saved.tracks.take(MAX_TRACKS).forEach { arr.put(it.toJson()) }
            val o = JSONObject()
                .put("tracks", arr)
                .put("index", saved.index)
                .put("position", saved.positionMs)
                .put("shuffle", saved.shuffle)
                .put("playlist", saved.playlistId ?: "")
                .put("instant", saved.instant)
            val tmp = File(f.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(f)) tmp.delete()
        }
    }
}
