package com.lifestream.player.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Favorites and listening history, stored on the device. */
object Library {
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_RECENTS = "recents"
    private const val MAX_RECENTS = 50

    private lateinit var prefs: SharedPreferences

    private val _favorites = MutableStateFlow<List<Track>>(emptyList())
    val favorites: StateFlow<List<Track>> = _favorites.asStateFlow()

    private val _recents = MutableStateFlow<List<Track>>(emptyList())
    val recents: StateFlow<List<Track>> = _recents.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
        _favorites.value = read(KEY_FAVORITES)
        _recents.value = read(KEY_RECENTS)
    }

    fun isFavorite(id: String) = _favorites.value.any { it.id == id }

    fun toggleFavorite(track: Track) {
        val current = _favorites.value
        _favorites.value = if (current.any { it.id == track.id }) {
            current.filterNot { it.id == track.id }
        } else {
            listOf(track) + current
        }
        write(KEY_FAVORITES, _favorites.value)
    }

    fun addRecent(track: Track) {
        val updated = (listOf(track) + _recents.value.filterNot { it.id == track.id }).take(MAX_RECENTS)
        _recents.value = updated
        write(KEY_RECENTS, updated)
    }

    fun clearRecents() {
        _recents.value = emptyList()
        write(KEY_RECENTS, emptyList())
    }

    private fun read(key: String): List<Track> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]"))
        (0 until arr.length()).map { Track.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun write(key: String, tracks: List<Track>) {
        val arr = JSONArray()
        tracks.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}
