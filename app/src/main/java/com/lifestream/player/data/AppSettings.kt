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
    private const val KEY_SEARCH_KEYBOARD = "search_keyboard"
    private const val KEY_CACHE_LIMIT_MB = "cache_limit_mb"
    private const val KEY_AUTO_UPDATE = "auto_update"
    private const val KEY_PAUSE_AT_ZERO_VOLUME = "pause_at_zero_volume"

    /** Size choices for saved songs, in MB (0 = don't save songs). */
    val CACHE_LIMIT_CHOICES = listOf(0, 2048, 5120, 10240)
    const val DEFAULT_CACHE_LIMIT_MB = 2048

    private lateinit var prefs: SharedPreferences

    private val _continueFromSearch = MutableStateFlow(true)

    /** After playing a song from Search, keep going with the rest of its soundtrack, shuffled. */
    val continueFromSearch: StateFlow<Boolean> = _continueFromSearch.asStateFlow()

    private val _themeId = MutableStateFlow(Palettes.default.id)

    /** The id of the colour theme chosen in Settings. */
    val themeId: StateFlow<String> = _themeId.asStateFlow()

    private val _searchKeyboard = MutableStateFlow(true)

    /** Opening the Search tab puts the cursor in the search box and shows the keyboard. */
    val searchKeyboard: StateFlow<Boolean> = _searchKeyboard.asStateFlow()

    private val _cacheLimitMb = MutableStateFlow(DEFAULT_CACHE_LIMIT_MB)

    /** How much space recently played songs may use on the phone, in MB (0 = off). */
    val cacheLimitMb: StateFlow<Int> = _cacheLimitMb.asStateFlow()

    private val _autoUpdate = MutableStateFlow(true)

    /** Check GitHub for a new version when the app opens, and ask before updating. */
    val autoUpdate: StateFlow<Boolean> = _autoUpdate.asStateFlow()

    private val _pauseAtZeroVolume = MutableStateFlow(true)

    /** Pause playback when the phone's media volume is turned all the way down. */
    val pauseAtZeroVolume: StateFlow<Boolean> = _pauseAtZeroVolume.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _continueFromSearch.value = prefs.getBoolean(KEY_CONTINUE_FROM_SEARCH, true)
        _searchKeyboard.value = prefs.getBoolean(KEY_SEARCH_KEYBOARD, true)
        _autoUpdate.value = prefs.getBoolean(KEY_AUTO_UPDATE, true)
        _pauseAtZeroVolume.value = prefs.getBoolean(KEY_PAUSE_AT_ZERO_VOLUME, true)
        _cacheLimitMb.value = prefs.getInt(KEY_CACHE_LIMIT_MB, DEFAULT_CACHE_LIMIT_MB)
            .takeIf { it in CACHE_LIMIT_CHOICES } ?: DEFAULT_CACHE_LIMIT_MB
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

    fun setPauseAtZeroVolume(on: Boolean) {
        _pauseAtZeroVolume.value = on
        prefs.edit().putBoolean(KEY_PAUSE_AT_ZERO_VOLUME, on).apply()
    }

    fun setAutoUpdate(on: Boolean) {
        _autoUpdate.value = on
        prefs.edit().putBoolean(KEY_AUTO_UPDATE, on).apply()
    }

    fun setSearchKeyboard(on: Boolean) {
        _searchKeyboard.value = on
        prefs.edit().putBoolean(KEY_SEARCH_KEYBOARD, on).apply()
    }

    /** Stores the new limit; the caller applies it to the cache off the main thread. */
    fun setCacheLimitMb(mb: Int) {
        if (mb !in CACHE_LIMIT_CHOICES) return
        _cacheLimitMb.value = mb
        prefs.edit().putInt(KEY_CACHE_LIMIT_MB, mb).apply()
    }
}
