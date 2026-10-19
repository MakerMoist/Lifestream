package com.lifestream.player.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.lifestream.player.data.AppDirs
import com.lifestream.player.net.Http
import com.lifestream.player.playback.SafeInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Song and album artwork: downloaded from YouTube's image servers only (every hop checked),
 * kept on disk (up to [MAX_DISK_BYTES]) and in memory (up to [MAX_MEMORY_BYTES] of decoded
 * pictures), and decoded at the size it's shown.
 */
object ImageLoader {
    private const val MAX_DISK_BYTES = 64L * 1024 * 1024
    private const val MAX_MEMORY_BYTES = 96L * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 8L * 1024 * 1024

    private val dir: File by lazy { File(AppDirs.cache, "artwork").apply { mkdirs() } }
    private val downloads = Semaphore(6)
    @Volatile private var trimmedAt = 0L

    private val client by lazy {
        Http.client.newBuilder()
            .cache(null) // artwork has its own disk store
            .callTimeout(30, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                val url = chain.request().url
                if (!url.isHttps || !SafeInput.isImageHost(url.host)) throw IOException("Artwork from an unexpected host")
                chain.proceed(chain.request())
            }
            .build()
    }

    /** Decoded pictures, least recently used dropped first. */
    private val memory = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        var bytes = 0L
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>): Boolean {
            if (bytes <= MAX_MEMORY_BYTES) return false
            bytes -= eldest.value.width.toLong() * eldest.value.height * 4
            return true
        }
    }

    fun cached(url: String, maxPx: Int): ImageBitmap? = synchronized(memory) { memory["$maxPx|$url"] }

    /** The picture at [url] (checked again here), at most [maxPx] wide; null if it can't be had. */
    suspend fun load(url: String, maxPx: Int): ImageBitmap? {
        val safe = SafeInput.safeArtwork(url) ?: return null
        cached(safe, maxPx)?.let { return it }
        return withContext(Dispatchers.IO) {
            val bytes = readDisk(safe) ?: downloads.withPermit { download(safe) } ?: return@withContext null
            val image = decode(bytes, maxPx) ?: return@withContext null
            synchronized(memory) {
                memory["$maxPx|$safe"] = image
                memory.bytes += image.width.toLong() * image.height * 4
            }
            image
        }
    }

    private fun keyFile(url: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, hash.take(40))
    }

    private fun readDisk(url: String): ByteArray? = runCatching {
        val f = keyFile(url)
        if (!f.exists() || f.length() > MAX_IMAGE_BYTES) return null
        f.setLastModified(System.currentTimeMillis())
        f.readBytes()
    }.getOrNull()

    private fun download(url: String): ByteArray? = runCatching {
        client.newCall(Request.Builder().url(url).header("User-Agent", Http.DESKTOP_USER_AGENT).build()).execute().use { r ->
            if (!r.isSuccessful) return null
            val body = r.body ?: return null
            if (body.contentLength() > MAX_IMAGE_BYTES) return null
            val bytes = body.byteStream().use { it.readNBytes((MAX_IMAGE_BYTES + 1).toInt()) }
            if (bytes.size > MAX_IMAGE_BYTES) return null
            runCatching {
                com.lifestream.player.data.writeAtomicallyBytes(keyFile(url), bytes)
                trimDisk()
            }
            bytes
        }
    }.getOrNull()

    private fun trimDisk() {
        val now = System.currentTimeMillis()
        if (now - trimmedAt < 60_000) return
        trimmedAt = now
        val files = dir.listFiles()?.sortedBy { it.lastModified() }?.toMutableList() ?: return
        var total = files.sumOf { it.length() }
        while (total > MAX_DISK_BYTES && files.isNotEmpty()) {
            val f = files.removeAt(0)
            total -= f.length()
            f.delete()
        }
    }

    /** Decodes and, if it's bigger than needed, scales down (less memory, smoother drawing). */
    fun decode(bytes: ByteArray, maxPx: Int): ImageBitmap? = runCatching {
        val image = org.jetbrains.skia.Image.makeFromEncoded(bytes)
        if (image.width <= maxPx) return image.toComposeImageBitmap()
        val h = (image.height.toLong() * maxPx / image.width).toInt().coerceAtLeast(1)
        val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(maxPx, h)
        surface.canvas.drawImageRect(
            image,
            org.jetbrains.skia.Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
            org.jetbrains.skia.Rect.makeWH(maxPx.toFloat(), h.toFloat()),
            org.jetbrains.skia.SamplingMode.LINEAR,
            null,
            true,
        )
        surface.makeImageSnapshot().toComposeImageBitmap()
    }.getOrNull()

    /** Empties the artwork stores (Settings → Clear Cache). */
    fun clear() {
        synchronized(memory) {
            memory.clear()
            memory.bytes = 0
        }
        dir.listFiles()?.forEach { it.delete() }
    }

    /** Lets go of decoded pictures (they're decoded again from disk when needed). */
    fun trimMemory() = synchronized(memory) {
        memory.clear()
        memory.bytes = 0
    }
}

/** The picture at [url] once loaded (immediately when already in memory). */
@Composable
fun rememberRemoteImage(url: String?, maxPx: Int): ImageBitmap? {
    var image by remember(url, maxPx) { mutableStateOf(url?.let { ImageLoader.cached(it, maxPx) }) }
    LaunchedEffect(url, maxPx) {
        if (image == null && url != null) image = ImageLoader.load(url, maxPx)
    }
    return image
}
