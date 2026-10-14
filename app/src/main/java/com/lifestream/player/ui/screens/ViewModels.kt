package com.lifestream.player.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
                    PlayerConnection.playTracks(tracks, shuffle = shuffle)
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
                // A few attempts in case a soundtrack can't be loaded right now.
                val candidates = Catalog.games.filter { it.id != lastSurprise }.shuffled().take(4)
                for (game in candidates) {
                    val tracks = SoundtrackIndex.tracksFor(game.id) ?: tracksFor(game)
                    if (tracks.isNotEmpty()) {
                        lastSurprise = game.id
                        PlayerConnection.playTracks(tracks, shuffle = true)
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

    /** About 40 tracks per soundtrack from its matching albums; a failing one is just skipped. */
    private suspend fun tracksFor(game: Game): List<Track> = SoundtrackIndex.tracksFor(game.id)?.take(60) ?: try {
        val source = MusicRepository.gameTracks(game)
        val out = mutableListOf<Track>()
        var next: Any? = null
        var loads = 0
        do {
            val page = source.load(next)
            out += page.items
            next = page.next
            loads++
        } while (next != null && out.distinctBy { Matching.songKey(it.title) }.size < 40 && loads < 6)
        out.distinctBy { Matching.songKey(it.title) }.take(60)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }
}

class GameViewModel(game: Game) : ViewModel() {
    // One version of each song: the original soundtrack loads first, so its version wins.
    val tracks = PagedList(
        viewModelScope,
        SoundtrackIndex.tracksFor(game.id)?.let { cached ->
            object : PagedSource<Track> {
                override suspend fun load(page: Any?) = Paged(cached, null)
            }
        } ?: MusicRepository.gameTracks(game),
    ) { Matching.songKey(it.title) }

    /** Text in the "search this soundtrack" box. */
    var filter by mutableStateOf("")
}

/** Search is local (see SoundtrackIndex); this just keeps the query and results across rotation. */
class SearchViewModel : ViewModel() {
    var input by mutableStateOf("")
    var games by mutableStateOf<List<Game>>(emptyList())
    var hits by mutableStateOf<List<SoundtrackIndex.Hit>>(emptyList())
}
