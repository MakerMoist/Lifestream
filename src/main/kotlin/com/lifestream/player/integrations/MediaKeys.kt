package com.lifestream.player.integrations

import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser

/**
 * The keyboard's media keys (play/pause, next, previous, stop) work while LifeStream has music
 * queued, even when its window isn't in front. They're let go again when the queue is empty, so
 * other players get them back.
 */
object MediaKeys {
    private const val VK_MEDIA_NEXT_TRACK = 0xB0
    private const val VK_MEDIA_PREV_TRACK = 0xB1
    private const val VK_MEDIA_STOP = 0xB2
    private const val VK_MEDIA_PLAY_PAUSE = 0xB3
    private const val MOD_NOREPEAT = 0x4000
    private const val WM_HOTKEY = 0x0312
    private const val PM_REMOVE = 0x0001

    @Volatile var wanted = false
    private var started = false

    fun start(onKey: (Key) -> Unit) {
        if (started || !System.getProperty("os.name").lowercase().contains("win")) return
        started = true
        Thread({ run(onKey) }, "media-keys").apply { isDaemon = true }.start()
    }

    enum class Key { PLAY_PAUSE, NEXT, PREVIOUS, STOP }

    private val keys = listOf(
        1 to VK_MEDIA_PLAY_PAUSE,
        2 to VK_MEDIA_NEXT_TRACK,
        3 to VK_MEDIA_PREV_TRACK,
        4 to VK_MEDIA_STOP,
    )

    /** Hot keys belong to the thread that registers them, so this one thread does it all. */
    private fun run(onKey: (Key) -> Unit) {
        val user32 = runCatching { User32.INSTANCE }.getOrNull() ?: return
        var registered = false
        val msg = WinUser.MSG()
        while (true) {
            if (wanted && !registered) {
                // A key another app already holds just isn't taken.
                keys.forEach { (id, vk) -> runCatching { user32.RegisterHotKey(null, id, MOD_NOREPEAT, vk) } }
                registered = true
            } else if (!wanted && registered) {
                keys.forEach { (id, _) -> runCatching { user32.UnregisterHotKey(null, id) } }
                registered = false
            }
            if (!registered) {
                Thread.sleep(500) // nothing queued: just look now and then whether keys are wanted
                continue
            }
            while (user32.PeekMessage(msg, null, 0, 0, PM_REMOVE)) {
                if (msg.message == WM_HOTKEY) {
                    when (msg.wParam.toInt()) {
                        1 -> onKey(Key.PLAY_PAUSE)
                        2 -> onKey(Key.NEXT)
                        3 -> onKey(Key.PREVIOUS)
                        4 -> onKey(Key.STOP)
                    }
                }
            }
            // Sleeps until a key is pressed (or half a second passes, to notice the queue emptying),
            // rather than waking constantly to check.
            runCatching { user32.MsgWaitForMultipleObjects(0, null, false, 500, QS_ALLINPUT) }
                .onFailure { Thread.sleep(100) }
        }
    }

    private const val QS_ALLINPUT = 0x04FF
}
