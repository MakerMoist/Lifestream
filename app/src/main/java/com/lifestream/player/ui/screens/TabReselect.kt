package com.lifestream.player.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Bumped when a bottom-bar tab is tapped while its first screen is already showing, so that
 * screen can go back to its top (Settings) or its first page (Library). Home has [HomeReselect].
 */
object TabReselect {
    var settings by mutableIntStateOf(0)
    var library by mutableIntStateOf(0)
}
