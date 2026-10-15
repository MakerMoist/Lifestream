package com.lifestream.player.ui.screens

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifestream.player.data.AppSettings
import com.lifestream.player.data.Catalog
import com.lifestream.player.data.Game
import com.lifestream.player.data.Matching
import com.lifestream.player.data.SoundtrackIndex
import com.lifestream.player.data.PagedSource
import com.lifestream.player.data.Paged
import com.lifestream.player.data.MusicRepository
import com.lifestream.player.data.Track
import com.lifestream.player.playback.PlayerConnection
import com.lifestream.player.ui.components.PagedList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/** Multi-select on the Home grid: pick several soundtracks and play them as one queue. */
class HomeViewModel : ViewModel() {
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun toggle(game: Game) {
        selected = if (game.id in selected) selected - game.id else selected + game.id
        message = null
    }

    fun select(games: List<Game>) {
        selected = selected + games.map { it.id }
        message = null
    }

    fun deselect(games: List<Game>) {
        selected = selected - games.map { it.id }.toSet()
    }

    fun clear() {
        selected = emptySet()
        message = null
    }

    fun play(shuffle: Boolean) {
        if (loading || selected.isEmpty()) return
        // Keep catalog order so "Play" goes soundtrack by soundtrack.
        val games = Catalog.games.filter { it.id in selected }
        loading = true
        message = null
        viewModelScope.launch {
            try {
                val tracks = games
                    .map { game -> async { tracksFor(game) } }
                    .awaitAll()
                    .flatten()
                    .distinctBy { it.id }
                if (tracks.isEmpty()) {
                    message = "Couldn't load tracks. Check your connection."
                } else {
                    PlayerConnection.playTracks(tracks, randomStart = shuffle)
                    selected = emptySet()
                }
            } finally {
                loading = false
            }
        }
    }

    // ---- Surprise me: shuffle a random soundtrack ----

    var surprising by mutableStateOf(false)
        private set
    var surpriseNote by mutableStateOf<String?>(null)
        private set
    private var lastSurprise: String? = null

    fun surprise() {
        if (surprising) return
        surprising = true
        surpriseNote = null
        viewModelScope.launch {
            try {
                // A random mix of songs from every soundtrack, once the song index has them.
                val mix = withContext(Dispatchers.Default) { com.lifestream.player.data.Mixes.instant() }
                val titles = mix.mapNotNull { it.gameId }.distinct().size
                if (mix.size >= 20 && titles >= 3) {
                    PlayerConnection.playTracks(mix, instant = true)
                    val note = "Shuffling ${mix.size} songs from $titles soundtracks"
                    surpriseNote = note
                    clearNoteLater(note)
                    return@launch
                }
                // Index not built yet: shuffle one random soundtrack instead.
                // A few attempts in case a soundtrack can't be loaded right now.
                val candidates = AppSettings.shownGames.filter { it.id != lastSurprise }.shuffled().take(4)
                for (game in candidates) {
                    val tracks = (SoundtrackIndex.songsFor(game.id) ?: tracksFor(game)).shuffled().take(com.lifestream.player.data.Mixes.INSTANT_SIZE)
                    if (tracks.isNotEmpty()) {
                        lastSurprise = game.id
                        PlayerConnection.playTracks(tracks, randomStart = true, instant = true)
                        surpriseNote = "Shuffling ${game.title}"
                        clearNoteLater("Shuffling ${game.title}")
                        return@launch
                    }
                }
                surpriseNote = "Couldn't load a soundtrack. Check your connection."
                clearNoteLater("Couldn't load a soundtrack. Check your connection.")
            } finally {
                surprising = false
            }
        }
    }

    private fun clearNoteLater(note: String) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(6000)
            if (surpriseNote == note) surpriseNote = null
        }
    }

    /**
     * A soundtrack's songs, one version each: the whole thing once it's indexed, otherwise about
     * 40 loaded straight from its albums. A failing soundtrack is just skipped.
     */
    private suspend fun tracksFor(game: Game): List<Track> = SoundtrackIndex.songsFor(game.id) ?: try {
        val source = MusicRepository.gameTracks(game)
        val out = mutableListOf<Track>()
        var next: Any? = null
        var loads = 0
        do {
            val page = source.load(next)
            out += page.items
            next = page.next
            loads++
        } while (next != null && out.distinctBy { Matching.songKey(it) }.size < 40 && loads < 6)
        out.distinctBy { Matching.songKey(it) }.take(60)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }
}

class GameViewModel(game: Game) : ViewModel() {
    /** Settings → Interface → Song List Order, as it was when this page opened. */
    private val random = AppSettings.randomSongOrder.value

    // One version of each song: the original soundtrack loads first, so its version wins
    // (versions are picked before shuffling, so random order doesn't change which one plays).
    val tracks = PagedList(
        viewModelScope,
        SoundtrackIndex.songsFor(game.id)?.let { cached ->
            val list = if (random) cached.shuffled() else cached
            object : PagedSource<Track> {
                override suspend fun load(page: Any?) = Paged(list, null)
            }
        } ?: MusicRepository.gameTracks(game).let { source ->
            if (!random) source else object : PagedSource<Track> {
                // Not indexed yet: each page arrives shuffled.
                override suspend fun load(page: Any?) = source.load(page).let { Paged(it.items.shuffled(), it.next) }
            }
        },
        // One version of each song, and never the same recording twice.
        alsoUnique = { it.id },
    ) { Matching.songKey(it) }

    /** Text in the "search this soundtrack" box. */
    var filter by mutableStateOf("")
}

/** Search is local (see SoundtrackIndex); this just keeps the query and results across rotation. */
class SearchViewModel : ViewModel() {
    var input by mutableStateOf("")
    var games by mutableStateOf<List<Game>>(emptyList())
    var hits by mutableStateOf<List<SoundtrackIndex.Hit>>(emptyList())
}
