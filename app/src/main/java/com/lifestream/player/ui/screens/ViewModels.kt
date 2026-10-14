package com.lifestream.player.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifestream.player.data.Album
import com.lifestream.player.data.Catalog
import com.lifestream.player.data.Game
import com.lifestream.player.data.MusicRepository
import com.lifestream.player.data.Track
import com.lifestream.player.playback.PlayerConnection
import com.lifestream.player.ui.components.PagedList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

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

    /** Up to two pages (~40 tracks) per soundtrack; a failing soundtrack is just skipped. */
    private suspend fun tracksFor(game: Game): List<Track> = try {
        val source = MusicRepository.songSearch(game.query)
        val first = source.load(null)
        val second = first.next?.let { next ->
            try {
                source.load(next).items
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }.orEmpty()
        first.items + second
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }
}

class GameViewModel(game: Game) : ViewModel() {
    val tracks = PagedList(viewModelScope, MusicRepository.songSearch(game.query)) { it.id }
    val albums = PagedList(viewModelScope, MusicRepository.albumSearch(game.query)) { it.url }
    var tab by mutableStateOf(0)
}

class AlbumViewModel(url: String) : ViewModel() {
    val tracks = PagedList(viewModelScope, MusicRepository.albumTracks(url)) { it.id }
}

class SearchViewModel : ViewModel() {
    var input by mutableStateOf("")
    var ffOnly by mutableStateOf(true)
    var tab by mutableStateOf(0)
    var submitted by mutableStateOf<String?>(null)
        private set
    var songs by mutableStateOf<PagedList<Track>?>(null)
        private set
    var albums by mutableStateOf<PagedList<Album>?>(null)
        private set

    fun submit(text: String = input) {
        val q = text.trim()
        if (q.isEmpty()) return
        input = q
        val full = if (ffOnly && !q.contains("final fantasy", ignoreCase = true)) "final fantasy $q" else q
        submitted = full
        songs = PagedList(viewModelScope, MusicRepository.songSearch(full)) { it.id }
        albums = PagedList(viewModelScope, MusicRepository.albumSearch(full)) { it.url }
    }
}

/** Lets the album screen show the header info it was opened with while tracks load. */
object AlbumHolder {
    private val albums = ConcurrentHashMap<String, Album>()
    fun put(album: Album) {
        albums[album.url] = album
    }
    fun get(url: String): Album? = albums[url]
}
