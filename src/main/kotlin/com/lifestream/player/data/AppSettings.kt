package com.lifestream.player.data

import com.lifestream.player.ui.theme.Palettes
import com.lifestream.player.ui.theme.ThemeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User preferences from the Settings tab. */
object AppSettings {
    private const val KEY_CONTINUE_FROM_SEARCH = "continue_from_search"
    private const val KEY_THEME = "theme"
    private const val KEY_CACHE_LIMIT_MB = "cache_limit_mb"
    private const val KEY_AUTO_UPDATE = "auto_update"
    private const val KEY_RANDOM_SONG_ORDER = "random_song_order"
    private const val KEY_SHOW_QUOTES = "show_quotes"
    private const val KEY_LEVEL_VOLUME = "level_volume"
    private const val KEY_AUTO_PLAY_AT_END = "auto_play_at_end"
    private const val KEY_CAST_SUPPORT = "cast_support"
    private const val KEY_COMPACT_VIEW = "compact_view"
    private const val KEY_DISCORD = "discord_integration"
    private const val KEY_MINIMIZE_ON_CLOSE = "minimize_on_close"
    private const val KEY_VOLUME = "volume"
    private const val KEY_HIDDEN_TITLES = "hidden_titles"
    private const val KEY_CONTINUE_MODE = "continue_mode"
    private const val KEY_KEEP_QUEUE = "keep_queue"

    /** Size choices for saved songs, in MB (0 = don't save songs). */
    val CACHE_LIMIT_CHOICES = listOf(0, 2048, 4096, 6144, 8192, 10240)

    /** The largest size "Custom" allows (less if the drive hasn't the room). */
    const val MAX_CACHE_LIMIT_MB = 50 * 1024
    const val DEFAULT_CACHE_LIMIT_MB = 2048

    private lateinit var prefs: Prefs

    private val _continueFromSearch = MutableStateFlow(true)

    /** After playing a song from Search, keep going with the rest of its soundtrack, shuffled. */
    val continueFromSearch: StateFlow<Boolean> = _continueFromSearch.asStateFlow()

    private val _themeId = MutableStateFlow(Palettes.default.id)

    /** The id of the colour theme chosen in Settings. */
    val themeId: StateFlow<String> = _themeId.asStateFlow()

    private val _cacheLimitMb = MutableStateFlow(DEFAULT_CACHE_LIMIT_MB)

    /** How much space recently played songs may use on this PC, in MB (0 = off). */
    val cacheLimitMb: StateFlow<Int> = _cacheLimitMb.asStateFlow()

    private val _autoUpdate = MutableStateFlow(false)

    /** Check GitHub for a new version when the app opens, and ask before updating. Off unless turned on. */
    val autoUpdate: StateFlow<Boolean> = _autoUpdate.asStateFlow()

    private val _randomSongOrder = MutableStateFlow(false)

    /** A soundtrack's song list opens in random order (true) or in soundtrack order (false). */
    val randomSongOrder: StateFlow<Boolean> = _randomSongOrder.asStateFlow()

    private val _showQuotes = MutableStateFlow(true)

    /** Show the villain quotes at the top of Home. */
    val showQuotes: StateFlow<Boolean> = _showQuotes.asStateFlow()

    private val _castSupport = MutableStateFlow(true)

    /** Settings → System → "Cast Support": the Cast button in the player (on by default). */
    val castSupport: StateFlow<Boolean> = _castSupport.asStateFlow()

    fun setCastSupport(on: Boolean) {
        _castSupport.value = on
        prefs.edit().putBoolean(KEY_CAST_SUPPORT, on).apply()
    }

    private val _hiddenTitles = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Titles turned off in Settings → Titles: left out of Home, Search, New Game and auto-play
     * (songs already in playlists stay where they are).
     */
    val hiddenTitles: StateFlow<Set<String>> = _hiddenTitles.asStateFlow()

    fun isTitleShown(id: String?) = id == null || id !in _hiddenTitles.value

    /** The catalog's titles that are turned on, in catalog order. */
    val shownGames: List<Game> get() = Catalog.games.filter { it.id !in _hiddenTitles.value }

    fun setTitleShown(id: String, shown: Boolean) {
        if (Catalog.games.none { it.id == id }) return
        val next = if (shown) _hiddenTitles.value - id else _hiddenTitles.value + id
        _hiddenTitles.value = next
        prefs.edit().putString(KEY_HIDDEN_TITLES, next.joinToString(",")).apply()
    }

    fun showAllTitles() {
        _hiddenTitles.value = emptySet()
        prefs.edit().remove(KEY_HIDDEN_TITLES).apply()
    }

    /** What "Keep Playing After Searching" continues with. */
    enum class ContinueMode { SAME_TITLE, ALL_TITLES }

    private val _continueMode = MutableStateFlow(ContinueMode.SAME_TITLE)
    val continueMode: StateFlow<ContinueMode> = _continueMode.asStateFlow()

    fun setContinueMode(mode: ContinueMode) {
        _continueMode.value = mode
        prefs.edit().putString(KEY_CONTINUE_MODE, mode.name).apply()
    }

    private val _keepQueue = MutableStateFlow(false)

    /** "Playlist Stays Open": the last queue is back (paused) when the app opens again. Off unless turned on. */
    val keepQueue: StateFlow<Boolean> = _keepQueue.asStateFlow()

    fun setKeepQueue(on: Boolean) {
        _keepQueue.value = on
        prefs.edit().putBoolean(KEY_KEEP_QUEUE, on).apply()
    }

    private val _compactView = MutableStateFlow(false)

    /** Settings → Interface → "Compact View": a narrow icon-only sidebar and tighter lists. */
    val compactView: StateFlow<Boolean> = _compactView.asStateFlow()

    fun setCompactView(on: Boolean) {
        _compactView.value = on
        prefs.edit().putBoolean(KEY_COMPACT_VIEW, on).apply()
    }

    private val _discordIntegration = MutableStateFlow(false)

    /** Settings → System → "Discord Integration": the playing song shows in Discord. Off by default. */
    val discordIntegration: StateFlow<Boolean> = _discordIntegration.asStateFlow()

    fun setDiscordIntegration(on: Boolean) {
        _discordIntegration.value = on
        prefs.edit().putBoolean(KEY_DISCORD, on).apply()
    }

    private val _minimizeOnClose = MutableStateFlow(false)

    /**
     * Settings → System → "Minimize on Close": the close button hides the window to the icon
     * menu by the clock and the music keeps playing; Quit there closes it. Off by default.
     */
    val minimizeOnClose: StateFlow<Boolean> = _minimizeOnClose.asStateFlow()

    fun setMinimizeOnClose(on: Boolean) {
        _minimizeOnClose.value = on
        prefs.edit().putBoolean(KEY_MINIMIZE_ON_CLOSE, on).apply()
    }

    private val _volume = MutableStateFlow(1f)

    /** The player's own volume, 0..1 (the window's volume slider). */
    val volume: StateFlow<Float> = _volume.asStateFlow()

    fun setVolume(v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        _volume.value = clamped
        prefs.edit().putFloat(KEY_VOLUME, clamped).apply()
    }

    private val _autoPlayAtEnd = MutableStateFlow(true)

    /** When the queue runs out, keep going with random songs from any soundtrack. */
    val autoPlayAtEnd: StateFlow<Boolean> = _autoPlayAtEnd.asStateFlow()

    private val _levelVolume = MutableStateFlow(false)

    /** Even out loudness between songs (see VolumeLeveler). */
    val levelVolume: StateFlow<Boolean> = _levelVolume.asStateFlow()

    fun init() {
        prefs = Prefs.named("settings")
        _continueFromSearch.value = prefs.getBoolean(KEY_CONTINUE_FROM_SEARCH, true)
        _autoUpdate.value = prefs.getBoolean(KEY_AUTO_UPDATE, false)
        _randomSongOrder.value = prefs.getBoolean(KEY_RANDOM_SONG_ORDER, false)
        _showQuotes.value = prefs.getBoolean(KEY_SHOW_QUOTES, true)
        _levelVolume.value = prefs.getBoolean(KEY_LEVEL_VOLUME, false)
        _autoPlayAtEnd.value = prefs.getBoolean(KEY_AUTO_PLAY_AT_END, true)
        _castSupport.value = prefs.getBoolean(KEY_CAST_SUPPORT, true)
        _compactView.value = prefs.getBoolean(KEY_COMPACT_VIEW, false)
        _discordIntegration.value = prefs.getBoolean(KEY_DISCORD, false)
        _minimizeOnClose.value = prefs.getBoolean(KEY_MINIMIZE_ON_CLOSE, false)
        _volume.value = prefs.getFloat(KEY_VOLUME, 1f).coerceIn(0f, 1f)
        // Only ids of titles that still exist.
        _hiddenTitles.value = prefs.getString(KEY_HIDDEN_TITLES, "").orEmpty().split(',')
            .filter { id -> Catalog.games.any { it.id == id } }.toSet()
        _continueMode.value = ContinueMode.entries.firstOrNull { it.name == prefs.getString(KEY_CONTINUE_MODE, null) }
            ?: ContinueMode.SAME_TITLE
        _keepQueue.value = prefs.getBoolean(KEY_KEEP_QUEUE, false)
        // Any size from the choices or "Custom" (anything else stored is ignored).
        val stored = prefs.getInt(KEY_CACHE_LIMIT_MB, DEFAULT_CACHE_LIMIT_MB)
        _cacheLimitMb.value = if (stored in 0..MAX_CACHE_LIMIT_MB) stored else DEFAULT_CACHE_LIMIT_MB
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

    fun setRandomSongOrder(on: Boolean) {
        _randomSongOrder.value = on
        prefs.edit().putBoolean(KEY_RANDOM_SONG_ORDER, on).apply()
    }

    fun setShowQuotes(on: Boolean) {
        _showQuotes.value = on
        prefs.edit().putBoolean(KEY_SHOW_QUOTES, on).apply()
    }

    fun setAutoPlayAtEnd(on: Boolean) {
        _autoPlayAtEnd.value = on
        prefs.edit().putBoolean(KEY_AUTO_PLAY_AT_END, on).apply()
    }

    fun setLevelVolume(on: Boolean) {
        _levelVolume.value = on
        prefs.edit().putBoolean(KEY_LEVEL_VOLUME, on).apply()
    }

    fun setAutoUpdate(on: Boolean) {
        _autoUpdate.value = on
        prefs.edit().putBoolean(KEY_AUTO_UPDATE, on).apply()
    }

    /** Stores the new limit; the caller applies it to the cache off the main thread. */
    fun setCacheLimitMb(mb: Int) {
        if (mb !in 0..MAX_CACHE_LIMIT_MB) return
        _cacheLimitMb.value = mb
        prefs.edit().putInt(KEY_CACHE_LIMIT_MB, mb).apply()
    }
}
