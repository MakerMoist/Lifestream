package com.lifestream.player.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Bumped when a sidebar item is clicked while its page is already showing, so that page can go
 * back to its top and refresh. Home has [HomeReselect].
 */
object TabReselect {
    var settings by mutableIntStateOf(0)
    var library by mutableIntStateOf(0)
    var history by mutableIntStateOf(0)
}

/** Asks the app to open Now Playing (e.g. tapping the song that's already playing). */
object NowPlayingRequest {
    var count by mutableIntStateOf(0)
        private set

    fun open() {
        count++
    }
}
