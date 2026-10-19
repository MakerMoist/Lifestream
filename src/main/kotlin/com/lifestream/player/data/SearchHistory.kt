package com.lifestream.player.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Recent searches from the Search tab, newest first, stored on the device. */
object SearchHistory {
    private const val KEY = "recent_searches"
    private const val MAX = 15
    private const val MAX_LENGTH = 100

    private lateinit var prefs: Prefs

    private val _recent = MutableStateFlow<List<String>>(emptyList())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    fun init() {
        prefs = Prefs.named("search")
        _recent.value = runCatching {
            val arr = JSONArray(prefs.getString(KEY, "[]"))
            (0 until arr.length()).map { arr.optString(it).trim().take(MAX_LENGTH) }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
                .take(MAX)
        }.getOrDefault(emptyList())
    }

    /** Remembers [query] (moving it to the top if it was already there). */
    fun add(query: String) {
        val q = query.trim().take(MAX_LENGTH)
        if (q.isEmpty()) return
        save((listOf(q) + _recent.value.filterNot { it.equals(q, ignoreCase = true) }).take(MAX))
    }

    fun remove(query: String) = save(_recent.value - query)

    fun clear() = save(emptyList())

    private fun save(list: List<String>) {
        _recent.value = list
        prefs.edit().putString(KEY, JSONArray(list).toString()).apply()
    }
}
