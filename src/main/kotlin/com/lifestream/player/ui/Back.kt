package com.lifestream.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState

/**
 * "Back" on Windows: the Esc key and the mouse's back button. The newest screen or dialog that
 * asked for Back gets it; with none asking, the window goes back a page.
 */
object BackDispatcher {
    private class Entry(val enabled: () -> Boolean, val onBack: () -> Unit)

    private val entries = ArrayList<Entry>()

    /** Handles Back; false if nothing on screen wanted it. */
    fun back(): Boolean {
        val entry = synchronized(entries) { entries.lastOrNull { it.enabled() } } ?: return false
        entry.onBack()
        return true
    }

    @Composable
    fun Register(enabled: Boolean, onBack: () -> Unit) {
        val currentEnabled = rememberUpdatedState(enabled)
        val currentOnBack = rememberUpdatedState(onBack)
        DisposableEffect(Unit) {
            val entry = Entry({ currentEnabled.value }, { currentOnBack.value() })
            synchronized(entries) { entries += entry }
            onDispose { synchronized(entries) { entries -= entry } }
        }
    }
}

/** Runs [onBack] when Back (Esc, or the mouse's back button) is pressed while [enabled]. */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) = BackDispatcher.Register(enabled, onBack)
