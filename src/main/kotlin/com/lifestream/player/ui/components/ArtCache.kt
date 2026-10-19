package com.lifestream.player.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Decoded card art (bundled with the app), kept for the life of the app: each picture is decoded
 * once, off the UI thread, at about the size it's shown.
 */
object ArtCache {
    private const val CARD_PX = 520
    private const val WIDE_PX = 1080
    private val bitmaps = ConcurrentHashMap<String, ImageBitmap>()

    fun cached(path: String): ImageBitmap? = bitmaps[path]

    /** Drops the decoded art (it's decoded again when it's next shown). */
    fun clear() = bitmaps.clear()

    private fun bytes(path: String): ByteArray? = runCatching {
        ArtCache::class.java.classLoader.getResourceAsStream(path)?.use { it.readBytes() }
    }.getOrNull()

    fun load(path: String): ImageBitmap? {
        if (path.isEmpty()) return null
        bitmaps[path]?.let { return it }
        val image = bytes(path)?.let { ImageLoader.decode(it, CARD_PX) } ?: return null
        bitmaps[path] = image
        return image
    }

    /** A screen-wide copy for a soundtrack page's header. Not kept: only one page shows it. */
    fun loadWide(path: String): ImageBitmap? = bytes(path)?.let { ImageLoader.decode(it, WIDE_PX) }

    /** Decodes every card ahead of time so Home draws them straight away. */
    suspend fun warm(paths: List<String>) = withContext(Dispatchers.IO) {
        paths.filter { it.isNotEmpty() }.forEach { load(it) }
    }
}

/** The card art at [path]: immediately when already decoded, otherwise once it's ready. */
@Composable
fun rememberArt(path: String): ImageBitmap? {
    val art by produceState(ArtCache.cached(path), path) {
        if (value == null) value = withContext(Dispatchers.IO) { ArtCache.load(path) }
    }
    return art
}
