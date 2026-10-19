package com.lifestream.player.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
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
                // Two loads of the same picture at once: keep one, and count it once.
                memory["$maxPx|$safe"]?.let { return@withContext it }
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
                com.lifestream.player.data.writeAtomicallyBytes(keyFile(url), bytes, durable = false)
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

    /** The most pixels a picture may have: real artwork is far smaller, a "decompression bomb" isn't. */
    private const val MAX_PIXELS = 25_000_000L
    private const val MAX_SIDE = 10_000

    /**
     * Decodes and, if it's bigger than needed, scales down (less memory, smoother drawing). The
     * picture's size is read from its header first, so an image that claims to be enormous is
     * refused before it's decoded.
     */
    fun decode(bytes: ByteArray, maxPx: Int): ImageBitmap? = runCatching {
        org.jetbrains.skia.Data.makeFromBytes(bytes).use { data ->
            org.jetbrains.skia.Codec.makeFromData(data).use { codec ->
                val w = codec.width
                val h = codec.height
                if (w <= 0 || h <= 0 || w > MAX_SIDE || h > MAX_SIDE || w.toLong() * h > MAX_PIXELS) return null
            }
        }
        val image = org.jetbrains.skia.Image.makeFromEncoded(bytes)
        if (image.width <= maxPx && image.height <= maxPx * 4) return image.toComposeImageBitmap()
        val scale = minOf(maxPx.toFloat() / image.width, (maxPx * 4f) / image.height, 1f)
        val outW = (image.width * scale).toInt().coerceAtLeast(1)
        val outH = (image.height * scale).toInt().coerceAtLeast(1)
        // The full-size picture and the drawing surface are native memory: let go of them now
        // rather than whenever the garbage collector gets round to it.
        image.use { full ->
            org.jetbrains.skia.Surface.makeRasterN32Premul(outW, outH).use { surface ->
                surface.canvas.drawImageRect(
                    full,
                    org.jetbrains.skia.Rect.makeWH(full.width.toFloat(), full.height.toFloat()),
                    org.jetbrains.skia.Rect.makeWH(outW.toFloat(), outH.toFloat()),
                    org.jetbrains.skia.SamplingMode.LINEAR,
                    null,
                    true,
                )
                surface.makeImageSnapshot().toComposeImageBitmap()
            }
        }
    }.getOrNull()

    /** A softly blurred copy of a (small) picture, for backgrounds. Call off the UI thread. */
    fun blurred(source: ImageBitmap, sigma: Float): ImageBitmap? = runCatching {
        val bitmap = source.asSkiaBitmap()
        org.jetbrains.skia.Image.makeFromBitmap(bitmap).use { image ->
            org.jetbrains.skia.Surface.makeRasterN32Premul(image.width, image.height).use { surface ->
                org.jetbrains.skia.Paint().use { paint ->
                    paint.imageFilter = org.jetbrains.skia.ImageFilter.makeBlur(sigma, sigma, org.jetbrains.skia.FilterTileMode.CLAMP)
                    surface.canvas.drawImage(image, 0f, 0f, paint)
                }
                surface.makeImageSnapshot().toComposeImageBitmap()
            }
        }
    }.getOrNull()

    /** Lets go of decoded pictures (they're decoded again from disk when next shown). */
    fun clearMemory() = synchronized(memory) {
        memory.clear()
        memory.bytes = 0
    }

    /** Empties the artwork stores (Settings → Clear Cache). */
    fun clear() {
        synchronized(memory) {
            memory.clear()
            memory.bytes = 0
        }
        dir.listFiles()?.forEach { it.delete() }
    }
}
