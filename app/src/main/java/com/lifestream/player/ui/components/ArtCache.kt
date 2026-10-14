package com.lifestream.player.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Decoded card art, kept for the life of the app. Decoding the card illustrations every time
 * Home is shown made returning to it stutter; now each is decoded once, off the main thread.
 */
object ArtCache {
    private val bitmaps = ConcurrentHashMap<Int, ImageBitmap>()

    fun cached(res: Int): ImageBitmap? = bitmaps[res]

    fun load(context: Context, res: Int): ImageBitmap? = bitmaps[res] ?: runCatching {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        BitmapFactory.decodeResource(context.resources, res, options)?.asImageBitmap()
    }.getOrNull()?.also { bitmaps[res] = it }

    /** Decodes every card ahead of time so Home draws them straight away. */
    suspend fun warm(context: Context, ids: List<Int>) = withContext(Dispatchers.IO) {
        ids.filter { it != 0 }.forEach { load(context, it) }
    }
}

/** The card art for [res]: immediately when already decoded, otherwise once it's ready. */
@Composable
fun rememberArt(res: Int): ImageBitmap? {
    val context = LocalContext.current
    val art by produceState(ArtCache.cached(res), res) {
        if (value == null) value = withContext(Dispatchers.IO) { ArtCache.load(context, res) }
    }
    return art
}
