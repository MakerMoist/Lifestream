package com.lifestream.player.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A user playlist: any songs from any soundtrack, in the order they were added. */
data class Playlist(val id: String, val name: String, val tracks: List<Track>)

/** How a playlist's songs are listed (and played). The songs themselves stay in the order added. */
enum class PlaylistSort(val label: String) {
    ALPHABETICAL("Alphabetical Order"),
    MOST_PLAYED("Most Played"),
    RECENTLY_ADDED("Recently Added"),
    OLDEST_ADDED("Oldest Added"),
    LONGEST("Longest Duration"),
    SHORTEST("Shortest Duration"),
}

/** Playlists and listening history, stored on the device. */
object Library {
    const val MAX_PLAYLISTS = 20
    const val MAX_NAME_LENGTH = 40
    private const val MAX_TRACKS_PER_PLAYLIST = 1000
    private const val MAX_RECENTS = 50
    private const val MAX_FILE_BYTES = 32L * 1024 * 1024

    private const val KEY_PLAYLISTS = "playlists"
    private const val KEY_RECENTS = "recents"
    private const val KEY_OLD_FAVORITES = "favorites"
    private const val KEY_LAST_PLAYLIST = "last_playlist"
    private const val KEY_PLAY_COUNTS = "play_counts"
    private const val KEY_SORTS = "playlist_sorts"
    private const val MAX_PLAY_COUNTS = 3000

    /** Exported playlist files: what they're called, and the most an import will read. */
    const val EXPORT_EXTENSION = ".lsplaylist"
    const val MAX_EXPORT_BYTES = 2L * 1024 * 1024
    private const val EXPORT_FORMAT = "lifestream-playlist"
    private const val EXPORT_VERSION = 1

    private lateinit var prefs: SharedPreferences

    /**
     * Playlists live in their own file, not in SharedPreferences: they can hold thousands of songs,
     * and SharedPreferences reads everything on the main thread at start-up and rewrites all of it
     * on every change. The file is written in the background, shortly after changes (several
     * quick changes become one write), and atomically (a temporary file renamed over it).
     */
    private lateinit var playlistsFile: File
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null
    @Volatile private var saveWanted = false
    private val playlistId = Regex("^[A-Za-z0-9-]{1,64}$")
    private val videoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")
    private val whitespace = Regex("\\s+")

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    /** Ids of every song that's in at least one playlist (for the in-a-playlist mark on song rows). */
    val inPlaylists: StateFlow<Set<String>> = _playlists
        .map { list -> list.flatMapTo(HashSet()) { p -> p.tracks.map { it.id } } }
        .stateIn(CoroutineScope(SupervisorJob() + Dispatchers.Default), SharingStarted.Eagerly, emptySet())

    private val _recents = MutableStateFlow<List<Track>>(emptyList())
    val recents: StateFlow<List<Track>> = _recents.asStateFlow()

    private val _lastPlaylistId = MutableStateFlow<String?>(null)
    /** The playlist most recently played from (Home's "Last Playlist"). */
    val lastPlaylistId: StateFlow<String?> = _lastPlaylistId.asStateFlow()

    /** How many times each song has been played (for "Most Played"), kept on the phone. */
    private val playCounts = HashMap<String, Int>()

    private val _sorts = MutableStateFlow<Map<String, PlaylistSort>>(emptyMap())
    /** Each playlist's chosen order (Oldest Added when never changed). */
    val sorts: StateFlow<Map<String, PlaylistSort>> = _sorts.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
        runCatching {
            val o = JSONObject(prefs.getString(KEY_PLAY_COUNTS, "{}") ?: "{}")
            for (id in o.keys()) {
                val n = o.optInt(id)
                if (n > 0 && videoIdPattern.matches(id)) playCounts[id] = n
            }
        }
        runCatching {
            val o = JSONObject(prefs.getString(KEY_SORTS, "{}") ?: "{}")
            _sorts.value = o.keys().asSequence().mapNotNull { id ->
                val mode = PlaylistSort.entries.firstOrNull { it.name == o.optString(id) } ?: return@mapNotNull null
                if (playlistId.matches(id)) id to mode else null
            }.toMap()
        }
        playlistsFile = File(context.filesDir, "playlists.json")
        _recents.value = readTracks(prefs.getString(KEY_RECENTS, "[]"))
        _playlists.value = readPlaylists()
        moveOutOfPreferences()
        migrateFavorites()
        _lastPlaylistId.value = prefs.getString(KEY_LAST_PLAYLIST, null)?.takeIf { id -> playlist(id) != null }
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

    fun setLastPlaylist(id: String) {
        if (playlist(id) == null || _lastPlaylistId.value == id) return
        _lastPlaylistId.value = id
        // Written straight away (not in the background), so it survives the app being closed
        // right after; it's one tiny value and changes rarely.
        prefs.edit().putString(KEY_LAST_PLAYLIST, id).commit()
    }

    /** Playlists that contain the song (for "Remove from playlist"). */
    fun playlistsWith(trackId: String): List<Playlist> = _playlists.value.filter { p -> p.tracks.any { it.id == trackId } }

    fun playlist(id: String): Playlist? = _playlists.value.firstOrNull { it.id == id }

    /** Creates a playlist; returns null when the limit of [MAX_PLAYLISTS] is reached. */
    fun createPlaylist(name: String): Playlist? {
        if (!canCreatePlaylist()) return null
        val p = Playlist(UUID.randomUUID().toString(), cleanName(name, "Playlist ${_playlists.value.size + 1}"), emptyList())
        _playlists.value = _playlists.value + p
        savePlaylists()
        return p
    }

    /** Creates a playlist already holding [tracks] (e.g. a saved Instant Playlist); null at the limit. */
    fun createPlaylistWith(name: String, tracks: List<Track>): Playlist? {
        if (!canCreatePlaylist()) return null
        val songs = tracks.distinctBy { it.id }.take(MAX_TRACKS_PER_PLAYLIST)
        val p = Playlist(UUID.randomUUID().toString(), cleanName(name, "Instant Playlist"), songs)
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
        if (id in _sorts.value) {
            _sorts.value = _sorts.value - id
            prefs.edit().putString(KEY_SORTS, JSONObject(_sorts.value.mapValues { it.value.name } as Map<*, *>).toString()).apply()
        }
        if (_lastPlaylistId.value == id) {
            _lastPlaylistId.value = null
            prefs.edit().remove(KEY_LAST_PLAYLIST).apply()
        }
    }

    /** Adds [track] to the playlist; returns false if it's already there. */
    fun addToPlaylist(id: String, track: Track): Boolean {
        val p = playlist(id) ?: return false
        if (p.tracks.any { it.id == track.id }) return false
        if (p.tracks.size >= MAX_TRACKS_PER_PLAYLIST) return false // full: say so instead of dropping it
        update(id) { it.copy(tracks = it.tracks + track) }
        return true
    }

    fun removeFromPlaylist(id: String, trackId: String) {
        update(id) { p -> p.copy(tracks = p.tracks.filterNot { it.id == trackId }) }
    }

    fun addRecent(track: Track) {
        val updated = (listOf(track) + _recents.value.filterNot { it.id == track.id }).take(MAX_RECENTS)
        _recents.value = updated
        countPlay(track.id)
        prefs.edit()
            .putString(KEY_RECENTS, writeTracks(updated))
            .putString(KEY_PLAY_COUNTS, writePlayCounts())
            .apply()
    }

    private fun countPlay(id: String) = synchronized(playCounts) {
        playCounts[id] = (playCounts[id] ?: 0) + 1
        // Keep the list bounded: the least played go first.
        if (playCounts.size > MAX_PLAY_COUNTS) {
            playCounts.entries.sortedBy { it.value }.take(playCounts.size - MAX_PLAY_COUNTS)
                .map { it.key }.forEach(playCounts::remove)
        }
    }

    private fun writePlayCounts(): String = synchronized(playCounts) { JSONObject(playCounts as Map<*, *>).toString() }

    fun playCount(id: String): Int = synchronized(playCounts) { playCounts[id] ?: 0 }

    // ---- Sorting ----

    fun sortOf(playlistId: String): PlaylistSort = _sorts.value[playlistId] ?: PlaylistSort.OLDEST_ADDED

    fun setSort(playlistId: String, sort: PlaylistSort) {
        if (playlist(playlistId) == null) return
        _sorts.value = _sorts.value + (playlistId to sort)
        prefs.edit().putString(KEY_SORTS, JSONObject(_sorts.value.mapValues { it.value.name } as Map<*, *>).toString()).apply()
    }

    /** The playlist's songs in its chosen order. */
    fun sorted(p: Playlist, sort: PlaylistSort = sortOf(p.id)): List<Track> = when (sort) {
        PlaylistSort.OLDEST_ADDED -> p.tracks
        PlaylistSort.RECENTLY_ADDED -> p.tracks.asReversed()
        PlaylistSort.ALPHABETICAL -> p.tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        // Ties keep the order they were added in (sortedBy is stable).
        PlaylistSort.MOST_PLAYED -> p.tracks.sortedByDescending { playCount(it.id) }
        // Songs whose length isn't known go last either way.
        PlaylistSort.LONGEST -> p.tracks.sortedByDescending { it.durationSec.takeIf { d -> d > 0 } ?: -1 }
        PlaylistSort.SHORTEST -> p.tracks.sortedBy { it.durationSec.takeIf { d -> d > 0 } ?: Long.MAX_VALUE }
    }

    // ---- Export and import (a file on the phone; nothing goes online) ----

    /** A fingerprint that marks a file as LifeStream's own export (and unchanged since). */
    private fun fingerprint(name: String, data: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest("$EXPORT_FORMAT:$EXPORT_VERSION\n$name\n$data".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** The playlist as an export file's contents, or null if it no longer exists. */
    fun exportText(id: String): String? {
        val p = playlist(id) ?: return null
        val data = writeTracks(p.tracks)
        return JSONObject()
            .put("format", EXPORT_FORMAT)
            .put("version", EXPORT_VERSION)
            .put("name", p.name)
            .put("tracks", data)
            .put("check", fingerprint(p.name, data))
            .toString()
    }

    sealed class ImportResult {
        class Imported(val playlist: Playlist) : ImportResult()
        object NotAPlaylist : ImportResult()
        object TooMany : ImportResult()
    }

    /**
     * Adds a playlist from an exported file's contents. Only LifeStream's own exports are accepted:
     * the right format and version, and a fingerprint that matches (a changed or foreign file is
     * refused). Every song is checked like any saved song.
     */
    fun importText(text: String): ImportResult {
        if (text.length > MAX_EXPORT_BYTES) return ImportResult.NotAPlaylist
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return ImportResult.NotAPlaylist
        if (o.optString("format") != EXPORT_FORMAT || o.optInt("version", -1) != EXPORT_VERSION) return ImportResult.NotAPlaylist
        val name = o.optString("name")
        val data = o.optString("tracks")
        if (name.isBlank() || data.isEmpty() || o.optString("check") != fingerprint(name, data)) return ImportResult.NotAPlaylist
        val tracks = runCatching { readTracks(JSONArray(data)) }.getOrNull() ?: return ImportResult.NotAPlaylist
        if (!canCreatePlaylist()) return ImportResult.TooMany
        val p = Playlist(UUID.randomUUID().toString(), uniqueName(cleanName(name, "Imported Playlist")), tracks.take(MAX_TRACKS_PER_PLAYLIST))
        _playlists.value = _playlists.value + p
        savePlaylists()
        return ImportResult.Imported(p)
    }

    /** "Road Trip" stays as it is unless a playlist has that name already: then "Road Trip (2)"… */
    private fun uniqueName(name: String): String {
        val taken = _playlists.value.map { it.name.lowercase() }.toSet()
        if (name.lowercase() !in taken) return name
        var n = 2
        while (true) {
            val suffix = " ($n)"
            val candidate = name.take(MAX_NAME_LENGTH - suffix.length) + suffix
            if (candidate.lowercase() !in taken) return candidate
            n++
        }
    }

    /**
     * Pull-to-refresh on the Library: brings stored songs up to date with the latest official
     * names, composer credits and soundtrack links (e.g. songs saved before a title was indexed
     * get their "Open title" link). Saves only if something changed.
     */
    suspend fun refresh() = kotlinx.coroutines.withContext(Dispatchers.Default) {
        fun fresh(t: Track): Track {
            // As the song index has it now (e.g. a new cover), otherwise just tidied up.
            SoundtrackIndex.indexed(t.id)?.let { return it }
            val linked = if (t.gameId == null) SoundtrackIndex.gameOf(t)?.let { t.copy(gameId = it.id) } ?: t else t
            return OfficialTitles.fix(ArtistCredits.fix(linked))
        }
        val recents = _recents.value
        val newRecents = recents.map(::fresh)
        val lists = _playlists.value
        val newLists = lists.map { p -> p.copy(tracks = p.tracks.map(::fresh)) }
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            // Only if nothing changed meanwhile (a song played or a playlist edited).
            if (newRecents != recents && _recents.value === recents) {
                _recents.value = newRecents
                prefs.edit().putString(KEY_RECENTS, writeTracks(newRecents)).apply()
            }
            if (newLists != lists && _playlists.value === lists) {
                _playlists.value = newLists
                savePlaylists()
            }
        }
    }

    fun clearRecents() {
        _recents.value = emptyList()
        synchronized(playCounts) { playCounts.clear() }
        prefs.edit().putString(KEY_RECENTS, "[]").putString(KEY_PLAY_COUNTS, "{}").apply()
    }

    // ---- storage ----

    private fun update(id: String, change: (Playlist) -> Playlist) {
        _playlists.value = _playlists.value.map { if (it.id == id) change(it) else it }
        savePlaylists()
    }

    private fun cleanName(raw: String, fallback: String): String =
        raw.replace(whitespace, " ").trim().take(MAX_NAME_LENGTH).ifBlank { fallback }

    /** From the playlists file, or (before 1.5.1) from SharedPreferences. */
    private fun readPlaylists(): List<Playlist> = runCatching {
        val text = when {
            // A damaged or runaway file is ignored rather than read into memory.
            playlistsFile.exists() && playlistsFile.length() <= MAX_FILE_BYTES -> playlistsFile.readText()
            playlistsFile.exists() -> "[]"
            else -> prefs.getString(KEY_PLAYLISTS, "[]")
        }
        val arr = JSONArray(text)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { playlistId.matches(it) } ?: return@mapNotNull null
            Playlist(
                id = id,
                name = cleanName(o.optString("name"), "Playlist ${i + 1}"),
                tracks = readTracks(o.optJSONArray("tracks")).take(MAX_TRACKS_PER_PLAYLIST),
            )
        }.distinctBy { it.id }.take(MAX_PLAYLISTS)
    }.getOrDefault(emptyList())

    /** One-time move of playlists from SharedPreferences into their own file. */
    private fun moveOutOfPreferences() {
        if (!prefs.contains(KEY_PLAYLISTS)) return
        if (!playlistsFile.exists()) writePlaylistsFile(_playlists.value)
        if (playlistsFile.exists()) prefs.edit().remove(KEY_PLAYLISTS).commit()
    }

    private fun savePlaylists() {
        saveWanted = true
        synchronized(saveScope) {
            if (saveJob != null) return // the running save picks up the latest playlists
            saveJob = saveScope.launch {
                while (true) {
                    delay(250)
                    saveWanted = false
                    writePlaylistsFile(_playlists.value)
                    synchronized(saveScope) {
                        if (!saveWanted) {
                            saveJob = null
                            return@launch
                        }
                    }
                }
            }
        }
    }

    private fun writePlaylistsFile(playlists: List<Playlist>) {
        runCatching {
            val arr = JSONArray()
            playlists.forEach { p ->
                val tracks = JSONArray()
                p.tracks.forEach { tracks.put(it.toJson()) }
                arr.put(JSONObject().put("id", p.id).put("name", p.name).put("tracks", tracks))
            }
            val tmp = File(playlistsFile.parentFile, "playlists.json.tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(playlistsFile)) tmp.delete()
        }
    }

    private fun readTracks(json: String?): List<Track> =
        runCatching { readTracks(JSONArray(json ?: "[]")) }.getOrDefault(emptyList())

    private fun readTracks(arr: JSONArray?): List<Track> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull {
            arr.optJSONObject(it)?.let(Track::fromJson)?.let(ArtistCredits::fix)?.let(OfficialTitles::fix)
        }.distinctBy { it.id }
    }

    private fun writeTracks(tracks: List<Track>): String {
        val arr = JSONArray()
        tracks.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }
}
