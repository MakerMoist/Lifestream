package com.lifestream.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

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

/**
 * Whether the cursor is in a text box. The window's shortcuts that are plain keys (Space for
 * play/pause) stand aside while you type, so a space in a search or a playlist name stays a space.
 */
object Typing {
    private val focused = java.util.concurrent.atomic.AtomicInteger(0)
    val active: Boolean get() = focused.get() > 0

    /** Put on a text box: it counts as typing while it has the cursor. */
    @Composable
    fun field(): Modifier {
        val has = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
        DisposableEffect(Unit) { onDispose { if (has.getAndSet(false)) focused.decrementAndGet() } }
        return Modifier.onFocusChanged { state ->
            if (state.isFocused && !has.getAndSet(true)) focused.incrementAndGet()
            if (!state.isFocused && has.getAndSet(false)) focused.decrementAndGet()
        }
    }
}
