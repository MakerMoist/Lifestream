package com.lifestream.player.playback

import com.lifestream.player.data.AppSettings
import com.lifestream.player.data.AppDirs
import com.lifestream.player.net.Http
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/** Thrown when YouTube's media server rejects a request (most often an expired URL → 403). */
class StreamHttpException(val code: Int) : IOException("Media server answered HTTP $code")

/**
 * A song's audio on its way to (or already in) a file on this PC. Playback reads the file while it
 * downloads, waiting at any point that hasn't arrived yet; [open] streams can be read from the
 * start, skipped forward and rewound, which is all the decoder needs to seek.
 */
class SongSource internal constructor(
    val videoId: String,
    val itag: Int,
    internal val file: File,
    /** Whole size in bytes, or -1 until known. */
    @Volatile var total: Long,
    @Volatile var written: Long,
    @Volatile var complete: Boolean,
) : Closeable {
    @Volatile internal var error: IOException? = null
    @Volatile var closed = false
        private set
    internal val lock = Object()

    /**
     * Runs once, after the source is closed, the download has stopped and every stream reading
     * the file is closed too. Windows can't move or delete a file something still has open, so
     * tidying the file (deleting a temporary copy, or filing a finished one) waits until then.
     */
    internal var onRelease: (() -> Unit)? = null
    private var openStreams = 0
    internal var downloading = false
        set(value) {
            synchronized(lock) { field = value }
            if (!value) maybeRelease()
        }
    private var released = false

    /** Waits until byte [position] has arrived (true), or the song ended before it, failed or was closed (false). */
    fun awaitByte(position: Long): Boolean {
        synchronized(lock) {
            while (true) {
                if (position < written) return true
                if (closed) return false
                error?.let { throw it }
                if (complete) return false
                lock.wait(250)
            }
        }
    }

    /** Waits until the size is known; -1 if it never will be. */
    fun awaitTotal(): Long {
        synchronized(lock) {
            while (total < 0 && !complete && !closed && error == null) lock.wait(250)
            error?.let { if (total < 0) throw it }
            return if (total >= 0) total else written
        }
    }

    internal fun progress(newWritten: Long, newTotal: Long = total) = synchronized(lock) {
        written = newWritten
        total = newTotal
        lock.notifyAll()
    }

    internal fun finish(e: IOException? = null) = synchronized(lock) {
        if (e == null) complete = true else error = e
        lock.notifyAll()
    }

    fun open(): SongInputStream {
        synchronized(lock) {
            if (closed) throw IOException("Song closed")
            openStreams++
        }
        return try {
            SongInputStream(this)
        } catch (e: IOException) {
            streamClosed()
            throw e
        }
    }

    internal fun streamClosed() {
        synchronized(lock) { openStreams-- }
        maybeRelease()
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            lock.notifyAll()
        }
        maybeRelease()
    }

    private fun maybeRelease() {
        val run = synchronized(lock) {
            if (released || !closed || openStreams > 0 || downloading) return
            released = true
            onRelease
        }
        run?.invoke()
    }
}

/** Reads a [SongSource]'s file as it downloads (supports mark/reset to the start and skipping). */
class SongInputStream internal constructor(private val source: SongSource) : java.io.InputStream() {
    private val raf = RandomAccessFile(source.file, "r")
    private var position = 0L
    private var closed = false

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (!source.awaitByte(position)) return -1
        val available = (source.written - position).coerceAtMost(len.toLong()).toInt()
        raf.seek(position)
        val n = raf.read(b, off, available)
        if (n > 0) position += n
        return n
    }

    override fun skip(n: Long): Long {
        if (n <= 0) return 0
        val total = source.awaitTotal()
        val step = minOf(n, (total - position).coerceAtLeast(0))
        position += step
        return step
    }

    override fun available(): Int = (source.written - position).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    override fun markSupported() = true
    override fun mark(readlimit: Int) = Unit
    override fun reset() {
        position = 0
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { raf.close() }
        source.streamClosed()
    }
}

/**
 * "Saved Songs": recently played songs are kept on this PC (up to the size chosen in Settings) and
 * replayed from there; the least recently played make way first, and some free space is always
 * left on the drive. Every song, saved or not, is fetched the same way: YouTube's media servers
 * throttle long open-ended downloads, so audio comes in bounded chunks using the `range` query
 * parameter, like YouTube's own clients.
 */
object SongStore {
    private const val CHUNK = 2L * 1024 * 1024
    private const val MB = 1024L * 1024
    private const val MIN_FREE_BYTES = 500L * MB
    /** No song comes near this (an hour of YouTube's best audio is under 120 MB). */
    private const val MAX_SONG_BYTES = 600L * MB
    private const val PREFERRED_ITAG = 140 // AAC in MP4: starts quickly and seeks without the whole file
    private val POST_BODY = byteArrayOf(0x78, 0)
    private val fileName = Regex("^([A-Za-z0-9_-]{11})\\.(\\d{1,4})$")

    private lateinit var dir: File
    private lateinit var tempDir: File

    private val _cachedIds = MutableStateFlow<Set<String>>(emptySet())
    /** Ids of songs saved in full on this PC (song rows show a mark for these). */
    val cachedIds: StateFlow<Set<String>> = _cachedIds.asStateFlow()

    /** Songs downloaded in full that are still open (filed as saved songs once they're let go). */
    private val finishedOpen = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    /** Songs being fetched right now, so a second request for one shares it. */
    private val active = HashMap<String, SongSource>()

    /**
     * Every hop of an audio request (redirects included) must stay on YouTube's media servers over
     * HTTPS. A slow first connection gets time to answer; a stalled one is retried.
     */
    private val client by lazy {
        Http.client.newBuilder()
            .cache(null)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                val url = chain.request().url
                if (!url.isHttps || !SafeInput.isGoogleVideoHost(url.host)) throw IOException("Refusing audio from an unexpected host")
                chain.proceed(chain.request())
            }
            .build()
    }

    fun init() {
        dir = File(AppDirs.cache, "songs").apply { mkdirs() }
        tempDir = File(AppDirs.cache, "streaming").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() } // left from last time
        }
        refreshIds()
    }

    val enabled: Boolean get() = AppSettings.cacheLimitMb.value > 0

    private fun completeFile(videoId: String): File? =
        dir.listFiles()?.firstOrNull { f -> fileName.matchEntire(f.name)?.groupValues?.get(1) == videoId }

    private fun partFile(videoId: String): File? =
        dir.listFiles()?.firstOrNull { f -> f.name.startsWith("$videoId.") && f.name.endsWith(".part") }

    /**
     * The song's audio: from the saved copy if there is one, otherwise fetched now (into the saved
     * songs when they're on, or a temporary file deleted when it's done with). Call off the UI thread.
     */
    @Synchronized
    fun open(videoId: String): SongSource {
        require(SafeInput.isVideoId(videoId)) { "Invalid track id" }
        active[videoId]?.takeIf { !it.closed && it.error == null }?.let { return it }
        completeFile(videoId)?.let { f ->
            val itag = fileName.matchEntire(f.name)!!.groupValues[2].toInt()
            f.setLastModified(System.currentTimeMillis()) // just played: last to be dropped
            return SongSource(videoId, itag, f, f.length(), f.length(), complete = true)
        }
        val keep = enabled
        // Finish a partly saved song from the same audio format it started with.
        val part = if (keep) partFile(videoId) else null
        val partItag = part?.name?.split('.')?.getOrNull(1)?.toIntOrNull()
        val stream = StreamResolver.resolveStream(videoId, partItag ?: PREFERRED_ITAG)
        val target = when {
            part != null && partItag == stream.itag -> part
            keep -> {
                part?.delete()
                File(dir, "$videoId.${stream.itag}.part")
            }
            else -> File(tempDir, "$videoId.${stream.itag}.${System.nanoTime()}")
        }
        val total = SafeInput.queryParameter(stream.url, "clen")?.toLongOrNull()?.takeIf { it in 1..MAX_SONG_BYTES } ?: -1L
        val source = SongSource(videoId, stream.itag, target, total, if (target.exists()) target.length() else 0L, complete = false)
        active[videoId] = source
        source.onRelease = {
            synchronized(this) { if (active[videoId] === source) active.remove(videoId) }
            if (!keep) {
                target.delete()
            } else if (source.complete && source.error == null) {
                // A finished song becomes a saved one (now that nothing has the file open).
                target.renameTo(File(dir, target.name.removeSuffix(".part")))
                finishedOpen -= videoId
                refreshIds()
            }
        }
        source.downloading = true
        Thread({ download(source, stream.url) }, "song-download").apply { isDaemon = true }.start()
        return source
    }

    private fun download(source: SongSource, firstUrl: String) {
        var url = firstUrl
        var expiredRetries = 0
        var networkRetries = 0
        try {
            RandomAccessFile(source.file, "rw").use { out ->
                var position = source.written
                out.seek(position)
                while (!source.closed) {
                    val end = source.total
                    if (end in 0..position) break
                    // A runaway (or endless) answer can't fill the drive.
                    if (position >= MAX_SONG_BYTES) throw IOException("The song is unexpectedly large")
                    val last = if (end >= 0) minOf(position + CHUNK, end) - 1 else position + CHUNK - 1
                    try {
                        val got = fetchChunk(url, position, last) { bytes, n ->
                            out.write(bytes, 0, n)
                            position += n
                            source.progress(position)
                            !source.closed
                        }
                        if (got.total in 1..MAX_SONG_BYTES && source.total < 0) source.progress(position, got.total)
                        if (got.endOfStream) {
                            if (source.total < 0) source.progress(position, position)
                            break
                        }
                        networkRetries = 0
                    } catch (e: StreamHttpException) {
                        // An expired link: get a fresh one (same format) and carry on.
                        if (e.code != 403 || expiredRetries++ >= 2) throw e
                        StreamResolver.invalidate(source.videoId)
                        url = StreamResolver.resolveStream(source.videoId, source.itag).url
                    } catch (e: IOException) {
                        // A dropped connection: wait a little (longer each time) and try again.
                        if (source.closed || networkRetries++ >= 6) throw e
                        Thread.sleep(minOf(30_000L, 1_000L shl networkRetries))
                    }
                }
            }
            if (source.closed && (source.total < 0 || source.written < source.total)) return // stopped part-way: keep the part
            source.finish()
            if (source.file.name.endsWith(".part")) {
                // Shown as saved straight away, even while it's still playing.
                finishedOpen += source.videoId
                trim(0)
                refreshIds()
            }
        } catch (e: IOException) {
            source.finish(e)
        } catch (e: InterruptedException) {
            source.finish(IOException("Stopped"))
        } finally {
            source.downloading = false
        }
    }

    private class Chunk(val total: Long, val endOfStream: Boolean)

    /** Fetches bytes [from]..[last] (inclusive), handing each piece to [sink] (false stops early). */
    private fun fetchChunk(baseUrl: String, from: Long, last: Long, sink: (ByteArray, Int) -> Boolean): Chunk {
        if (!SafeInput.isGoogleVideoUrl(baseUrl)) throw IOException("Refusing stream from an unexpected host")
        val separator = if (baseUrl.contains('?')) '&' else '?'
        val userAgent = when {
            YoutubeParsingHelper.isAndroidStreamingUrl(baseUrl) -> YoutubeParsingHelper.getAndroidUserAgent(null)
            YoutubeParsingHelper.isIosStreamingUrl(baseUrl) -> YoutubeParsingHelper.getIosUserAgent(null)
            YoutubeParsingHelper.isVisionOsStreamingUrl(baseUrl) -> YoutubeParsingHelper.getVisionOsUserAgent(null)
            else -> Http.DESKTOP_USER_AGENT
        }
        val request = Request.Builder()
            .url("$baseUrl${separator}range=$from-$last&rn=${(from / CHUNK)}")
            .post(POST_BODY.toRequestBody())
            .header("User-Agent", userAgent)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .apply {
                if (YoutubeParsingHelper.isWebStreamingUrl(baseUrl)) {
                    header("Origin", "https://www.youtube.com")
                    header("Referer", "https://www.youtube.com/")
                }
            }
            .build()
        client.newCall(request).execute().use { resp ->
            if (resp.code == 416) return Chunk(-1, endOfStream = true) // past the end of an unknown length
            if (!resp.isSuccessful) throw StreamHttpException(resp.code)
            val total = resp.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: -1L
            val body = resp.body ?: throw IOException("Empty response from media server")
            val wanted = last - from + 1
            var remaining = wanted
            val buffer = ByteArray(64 * 1024)
            body.byteStream().use { input ->
                // Defensive: a server that ignored the range and sent the whole file.
                if (resp.code == 200 && from > 0 && body.contentLength() > wanted) {
                    var toSkip = from
                    while (toSkip > 0) {
                        val skipped = input.skip(toSkip)
                        if (skipped <= 0) throw IOException("Couldn't skip to $from")
                        toSkip -= skipped
                    }
                }
                while (remaining > 0) {
                    val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (n < 0) break
                    remaining -= n
                    if (!sink(buffer, n)) break
                }
            }
            val short = remaining > 0
            return Chunk(total, endOfStream = short && total < 0)
        }
    }

    // ---- Size limit, free space and Settings ----

    private fun savedFiles(): List<File> = dir.listFiles()?.filter { it.isFile && fileName.matches(it.name) }.orEmpty()

    private fun refreshIds() {
        _cachedIds.value = savedFiles().mapNotNull { fileName.matchEntire(it.name)?.groupValues?.get(1) }.toSet() + finishedOpen
    }

    /** Space used by saved songs (and partly saved ones), in bytes. */
    fun usedBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    /**
     * Drops the least recently played songs until the saved songs fit the chosen size and the
     * drive keeps some space free (also after [extra] more bytes).
     */
    @Synchronized
    fun trim(extra: Long) {
        val limit = AppSettings.cacheLimitMb.value * MB
        val files = savedFiles().sortedBy { it.lastModified() }.toMutableList()
        var used = files.sumOf { it.length() }
        var short = (MIN_FREE_BYTES + extra - dir.usableSpace).coerceAtLeast(0)
        while (files.isNotEmpty() && (used + extra > limit || short > 0)) {
            val oldest = files.removeAt(0)
            val size = oldest.length()
            if (oldest.delete()) {
                used -= size
                short -= size
            }
        }
        // Partial songs that aren't being fetched only count until they're needed again.
        if (limit == 0L) dir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
    }

    /** Applies a new size limit (Settings). Call off the UI thread. */
    fun applyLimit() {
        trim(0)
        refreshIds()
    }

    /** Deletes every saved song (Settings → Clear Cache). */
    @Synchronized
    fun clear() {
        dir.listFiles()?.forEach { f -> if (active.values.none { it.file == f }) f.delete() }
        refreshIds()
    }
}
