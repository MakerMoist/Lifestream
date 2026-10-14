package com.lifestream.player.data

import android.content.Context
import android.content.SharedPreferences
import com.lifestream.player.ui.theme.Palettes
import com.lifestream.player.ui.theme.ThemeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User preferences from the Settings tab. */
object AppSettings {
    private const val KEY_CONTINUE_FROM_SEARCH = "continue_from_search"
    private const val KEY_THEME = "theme"

    private lateinit var prefs: SharedPreferences

    private val _continueFromSearch = MutableStateFlow(true)

    /** After playing a song from Search, keep going with the rest of its soundtrack, shuffled. */
    val continueFromSearch: StateFlow<Boolean> = _continueFromSearch.asStateFlow()

    private val _themeId = MutableStateFlow(Palettes.default.id)

    /** The id of the colour theme chosen in Settings. */
    val themeId: StateFlow<String> = _themeId.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _continueFromSearch.value = prefs.getBoolean(KEY_CONTINUE_FROM_SEARCH, true)
        // byId falls back to the default for unknown or tampered values.
        val palette = Palettes.byId(prefs.getString(KEY_THEME, null))
        _themeId.value = palette.id
        ThemeState.current = palette
    }

    fun setContinueFromSearch(on: Boolean) {
        _continueFromSearch.value = on
        prefs.edit().putBoolean(KEY_CONTINUE_FROM_SEARCH, on).apply()
    }

    fun setTheme(id: String) {
        val palette = Palettes.byId(id)
        _themeId.value = palette.id
        ThemeState.current = palette
        prefs.edit().putString(KEY_THEME, palette.id).apply()
    }
}
