package com.lifestream.player.integrations

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.StdCallLibrary

/**
 * Colours the window's title bar (the strip with the minimize, maximize and close buttons) to
 * match the chosen theme. Windows 11 takes the exact colours; Windows 10 switches it to dark.
 * On anything else, nothing changes.
 */
object TitleBar {
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_BORDER_COLOR = 34
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36

    @Suppress("FunctionName")
    private interface Dwm : StdCallLibrary {
        fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: Pointer, size: Int): Int
    }

    private val dwm: Dwm? by lazy {
        if (!System.getProperty("os.name").lowercase().contains("win")) return@lazy null
        runCatching { Native.load("dwmapi", Dwm::class.java) }.getOrNull()
    }

    /** [caption] and [text] as 0xRRGGBB. */
    fun apply(window: java.awt.Window, caption: Int, text: Int) {
        val api = dwm ?: return
        if (!window.isDisplayable) return
        runCatching {
            val hwnd = WinDef.HWND(Native.getWindowPointer(window))
            fun set(attribute: Int, value: Int): Int {
                val memory = Memory(4)
                memory.setInt(0, value)
                return api.DwmSetWindowAttribute(hwnd, attribute, memory, 4)
            }
            // Dark title bar first (every theme is dark); older Windows 10 used another number.
            if (set(DWMWA_USE_IMMERSIVE_DARK_MODE, 1) != 0) set(DWMWA_USE_IMMERSIVE_DARK_MODE_OLD, 1)
            // Windows 11: the theme's own colours.
            set(DWMWA_CAPTION_COLOR, colorRef(caption))
            set(DWMWA_TEXT_COLOR, colorRef(text))
            set(DWMWA_BORDER_COLOR, colorRef(caption))
        }
    }

    /** Windows wants colours as 0x00BBGGRR. */
    private fun colorRef(rgb: Int): Int {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return (b shl 16) or (g shl 8) or r
    }
}
