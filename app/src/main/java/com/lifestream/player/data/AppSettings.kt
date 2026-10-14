package com.lifestream.player.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User preferences from the Settings tab. */
object AppSettings {
    private const val KEY_CONTINUE_FROM_SEARCH = "continue_from_search"

    private lateinit var prefs: SharedPreferences

    private val _continueFromSearch = MutableStateFlow(true)

    /** After playing a song from Search, keep going with the rest of its soundtrack, shuffled. */
    val continueFromSearch: StateFlow<Boolean> = _continueFromSearch.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _continueFromSearch.value = prefs.getBoolean(KEY_CONTINUE_FROM_SEARCH, true)
    }

    fun setContinueFromSearch(on: Boolean) {
        _continueFromSearch.value = on
        prefs.edit().putBoolean(KEY_CONTINUE_FROM_SEARCH, on).apply()
    }
}
