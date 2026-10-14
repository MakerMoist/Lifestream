package com.lifestream.player.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A user playlist: any songs from any soundtrack, in the order they were added. */
data class Playlist(val id: String, val name: String, val tracks: List<Track>)

/** Playlists and listening history, stored on the device. */
object Library {
    const val MAX_PLAYLISTS = 5
    const val MAX_NAME_LENGTH = 40
    private const val MAX_TRACKS_PER_PLAYLIST = 1000
    private const val MAX_RECENTS = 50

    private const val KEY_PLAYLISTS = "playlists"
    private const val KEY_RECENTS = "recents"
    private const val KEY_OLD_FAVORITES = "favorites"

    private lateinit var prefs: SharedPreferences

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _recents = MutableStateFlow<List<Track>>(emptyList())
    val recents: StateFlow<List<Track>> = _recents.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
        _recents.value = readTracks(prefs.getString(KEY_RECENTS, "[]"))
        _playlists.value = readPlaylists()
        migrateFavorites()
    }

    /** Favorites from before playlists existed become a playlist called "Favorites". */
    private fun migrateFavorites() {
        if (!prefs.contains(KEY_OLD_FAVORITES)) return
        val old = readTracks(prefs.getString(KEY_OLD_FAVORITES, "[]"))
        if (old.isNotEmpty() && _playlists.value.size < MAX_PLAYLISTS) {
            _playlists.value = _playlists.value + Playlist(UUID.randomUUID().toString(), "Favorites", old)
            savePlaylists()
        }
        prefs.edit().remove(KEY_OLD_FAVORITES).apply()
    }

    fun canCreatePlaylist() = _playlists.value.size < MAX_PLAYLISTS

    fun playlist(id: String): Playlist? = _playlists.value.firstOrNull { it.id == id }

    /** Creates a playlist; returns null when the limit of [MAX_PLAYLISTS] is reached. */
    fun createPlaylist(name: String): Playlist? {
        if (!canCreatePlaylist()) return null
        val p = Playlist(UUID.randomUUID().toString(), cleanName(name, "Playlist ${_playlists.value.size + 1}"), emptyList())
        _playlists.value = _playlists.value + p
        savePlaylists()
        return p
    }

    fun renamePlaylist(id: String, name: String) {
        update(id) { it.copy(name = cleanName(name, it.name)) }
    }

    fun deletePlaylist(id: String) {
        _playlists.value = _playlists.value.filterNot { it.id == id }
        savePlaylists()
    }

    /** Adds [track] to the playlist; returns false if it's already there. */
    fun addToPlaylist(id: String, track: Track): Boolean {
        val p = playlist(id) ?: return false
        if (p.tracks.any { it.id == track.id }) return false
        update(id) { it.copy(tracks = (it.tracks + track).take(MAX_TRACKS_PER_PLAYLIST)) }
        return true
    }

    fun removeFromPlaylist(id: String, trackId: String) {
        update(id) { p -> p.copy(tracks = p.tracks.filterNot { it.id == trackId }) }
    }

    fun addRecent(track: Track) {
        val updated = (listOf(track) + _recents.value.filterNot { it.id == track.id }).take(MAX_RECENTS)
        _recents.value = updated
        prefs.edit().putString(KEY_RECENTS, writeTracks(updated)).apply()
    }

    fun clearRecents() {
        _recents.value = emptyList()
        prefs.edit().putString(KEY_RECENTS, "[]").apply()
    }

    // ---- storage ----

    private fun update(id: String, change: (Playlist) -> Playlist) {
        _playlists.value = _playlists.value.map { if (it.id == id) change(it) else it }
        savePlaylists()
    }

    private fun cleanName(raw: String, fallback: String): String =
        raw.replace(Regex("\\s+"), " ").trim().take(MAX_NAME_LENGTH).ifBlank { fallback }

    private fun readPlaylists(): List<Playlist> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_PLAYLISTS, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Playlist(
                id = o.getString("id"),
                name = cleanName(o.optString("name"), "Playlist ${i + 1}"),
                tracks = readTracks(o.optJSONArray("tracks")?.toString() ?: "[]").take(MAX_TRACKS_PER_PLAYLIST),
            )
        }.take(MAX_PLAYLISTS)
    }.getOrDefault(emptyList())

    private fun savePlaylists() {
        val arr = JSONArray()
        _playlists.value.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("tracks", JSONArray(writeTracks(p.tracks))))
        }
        prefs.edit().putString(KEY_PLAYLISTS, arr.toString()).apply()
    }

    private fun readTracks(json: String?): List<Track> = runCatching {
        val arr = JSONArray(json ?: "[]")
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(Track::fromJson)?.let(ArtistCredits::fix) }
    }.getOrDefault(emptyList())

    private fun writeTracks(tracks: List<Track>): String {
        val arr = JSONArray()
        tracks.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }
}
