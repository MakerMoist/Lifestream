package com.lifestream.player.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifestream.player.data.Album
import com.lifestream.player.data.Game
import com.lifestream.player.data.MusicRepository
import com.lifestream.player.data.Track
import com.lifestream.player.ui.components.PagedList
import java.util.concurrent.ConcurrentHashMap

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
