package com.lifestream.player.playback

import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.SimpleCache
import com.lifestream.player.data.AppSettings
import java.io.File
import java.io.IOException
import java.util.TreeSet

/**
 * Keeps recently played songs on the phone so playing them again doesn't stream them again
 * (and works offline once a song has been played all the way through). Oldest-played songs are
 * dropped first when the size limit chosen in Settings is reached.
 *
 * Songs are stored under their track id. Because a partly saved song must always be finished
 * from the very same audio file, the YouTube format (itag) is remembered with it and preferred
 * when the rest is fetched; if that format is gone, the partial copy is thrown away first.
 */
@OptIn(UnstableApi::class)
object SongCache {
    private const val META_ITAG = "ls_itag"

    @Volatile private var cache: SimpleCache? = null
    private val evictor = AdjustableLruEvictor(AppSettings.DEFAULT_CACHE_LIMIT_MB * MB)

    private const val MB = 1024L * 1024L
    /** Free space always left on the phone, whatever the song cache limit. */
    private const val MIN_FREE_BYTES = 500L * MB

    fun init(context: Context) {
        if (cache != null) return
        synchronized(this) {
            if (cache != null) return
            evictor.maxBytes = AppSettings.cacheLimitMb.value * MB
            val dir = File(context.applicationContext.cacheDir, "songs")
            evictor.dir = dir
            // If the cache can't open (e.g. its database is damaged), songs simply stream.
            cache = runCatching {
                SimpleCache(dir, evictor, StandaloneDatabaseProvider(context.applicationContext))
            }.getOrNull()
            scheduleRefresh()
        }
    }

    val enabled: Boolean get() = cache != null && AppSettings.cacheLimitMb.value > 0

    private val _cachedIds = MutableStateFlow<Set<String>>(emptySet())

    /** Ids of songs saved in full on the phone (song rows show a check mark for these). */
    val cachedIds: StateFlow<Set<String>> = _cachedIds.asStateFlow()

    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var refreshJob: Job? = null
    @Volatile private var refreshWanted = false

    /**
     * Recomputes [cachedIds] shortly after the cache changes. The cache reports every stored
     * piece (thousands at start-up), so this only marks the list stale; one job does the work.
     */
    fun scheduleRefresh() {
        refreshWanted = true
        synchronized(refreshScope) {
            if (refreshJob != null) return // the running job will see the request
            refreshJob = refreshScope.launch {
                while (true) {
                    delay(1500)
                    refreshWanted = false
                    // A failure here must not stop later refreshes.
                    runCatching {
                        cache?.let { c ->
                            val ids = c.keys.toList()
                                .filter { it.startsWith("track:") }
                                .map { it.removePrefix("track:") }
                                .filter { isFullyCached(it) }
                                .toSet()
                            if (ids != _cachedIds.value) _cachedIds.value = ids
                        }
                    }
                    // Checked under the lock, so a request arriving now is never lost.
                    synchronized(refreshScope) {
                        if (!refreshWanted) {
                            refreshJob = null
                            return@launch
                        }
                    }
                }
            }
        }
    }

    fun keyFor(videoId: String) = "track:$videoId"

    /** Bytes currently stored. Call off the main thread. */
    fun usedBytes(): Long = cache?.cacheSpace ?: 0L

    /** Applies a new size limit, dropping the oldest songs if needed. Call off the main thread. */
    fun applyLimit(mb: Int) {
        val c = cache ?: return
        evictor.maxBytes = mb * MB
        synchronized(c) { if (mb <= 0) clearLocked(c) else evictor.trim(c) }
    }

    /** Removes every saved song. Call off the main thread. */
    fun clear() {
        val c = cache ?: return
        synchronized(c) { clearLocked(c) }
    }

    private fun clearLocked(c: SimpleCache) {
        c.keys.toList().forEach { key ->
            try {
                c.removeResource(key)
            } catch (_: Exception) {
            }
        }
    }

    /** True when the whole song is saved, so it can play with no network at all. */
    fun isFullyCached(videoId: String): Boolean {
        val c = cache ?: return false
        val key = keyFor(videoId)
        val length = ContentMetadata.getContentLength(c.getContentMetadata(key))
        return length > 0 && c.isCached(key, 0, length)
    }

    /** The YouTube format a partly saved copy of this song came from, if any. */
    fun rememberedItag(videoId: String): Int? {
        val c = cache ?: return null
        val itag = c.getContentMetadata(keyFor(videoId)).get(META_ITAG, -1L)
        return itag.takeIf { it >= 0 }?.toInt()
    }

    /** Called when a stream is resolved so the format is stored alongside the saved audio. */
    fun rememberItag(videoId: String, itag: Int) {
        val c = cache ?: return
        if (!enabled || itag < 0) return
        val key = keyFor(videoId)
        if (c.getContentMetadata(key).get(META_ITAG, -1L) == itag.toLong()) return
        try {
            c.applyContentMetadataMutations(key, ContentMetadataMutations().set(META_ITAG, itag.toLong()))
        } catch (_: Exception) {
        }
    }

    /**
     * Before reading a partly saved song, make sure the missing part will come from the same audio
     * file; otherwise drop the partial copy so the song is fetched fresh. Fully saved songs and
     * unsaved songs need no network here.
     */
    fun prepareForRead(videoId: String) {
        val c = cache ?: return
        val key = keyFor(videoId)
        if (c.getCachedSpans(key).isEmpty()) return
        if (isFullyCached(videoId)) return

        val storedItag = rememberedItag(videoId)
        val resolved = try {
            StreamResolver.resolveStream(videoId, storedItag)
        } catch (_: IOException) {
            return // Offline: play what's saved; the rest fails the same way it would have anyway.
        }
        val storedLength = ContentMetadata.getContentLength(c.getContentMetadata(key))
        val newLength = Uri.parse(resolved.url).getQueryParameter("clen")?.toLongOrNull()
        val sameFile = storedItag != null && resolved.itag == storedItag &&
            (storedLength <= 0 || newLength == null || newLength == storedLength)
        if (!sameFile) {
            try {
                c.removeResource(key)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Wraps the streaming chain: lifestream:// songs go through the cache when it's turned on,
     * straight to the network when it's off.
     */
    fun wrap(upstream: DataSource.Factory): DataSource.Factory = DataSource.Factory {
        val c = cache
        if (c == null) {
            upstream.createDataSource()
        } else {
            val cached = CacheDataSource.Factory()
                .setCache(c)
                .setUpstreamDataSourceFactory(upstream)
                .setCacheWriteDataSinkFactory(CacheDataSink.Factory().setCache(c))
                .setCacheKeyFactory(keyFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .createDataSource()
            // Fully saved songs are read from storage alone: no network path is involved, so a
            // weak or switching connection can't stall them.
            val savedOnly = CacheDataSource.Factory()
                .setCache(c)
                .setCacheKeyFactory(keyFactory)
                .setUpstreamDataSourceFactory(null)
                .setCacheWriteDataSinkFactory(null)
                .createDataSource()
            CacheSwitchDataSource(cached, savedOnly, upstream.createDataSource())
        }
    }

    private val keyFactory = CacheKeyFactory { spec ->
        val uri = spec.uri
        val id = uri.lastPathSegment
        if (uri.scheme == StreamDataSources.SCHEME && SafeInput.isVideoId(id)) keyFor(id!!)
        else spec.key ?: uri.toString()
    }

    /** Picks the cached or the direct path each time a song is opened. */
    private class CacheSwitchDataSource(
        private val cached: DataSource,
        private val savedOnly: DataSource,
        private val direct: DataSource,
    ) : DataSource {
        private var active: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            cached.addTransferListener(transferListener)
            savedOnly.addTransferListener(transferListener)
            direct.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val id = dataSpec.uri.lastPathSegment
            val useCache = enabled && dataSpec.uri.scheme == StreamDataSources.SCHEME && SafeInput.isVideoId(id)
            val source = when {
                !useCache -> direct
                isFullyCached(id!!) -> savedOnly
                else -> {
                    prepareForRead(id)
                    cached
                }
            }
            active = source
            return source.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            active?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

        override fun getUri(): Uri? = active?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

        override fun close() {
            val source = active
            active = null
            source?.close()
        }
    }

    /** Least-recently-played eviction whose size limit can change while the app runs. */
    private class AdjustableLruEvictor(@Volatile var maxBytes: Long) : CacheEvictor {
        /** Where the songs are kept, to check the phone's free space. */
        @Volatile var dir: File? = null
        private var spaceCheckedAt = 0L
        private val spans = TreeSet<CacheSpan> { a, b ->
            when {
                a.lastTouchTimestamp < b.lastTouchTimestamp -> -1
                a.lastTouchTimestamp > b.lastTouchTimestamp -> 1
                else -> a.compareTo(b)
            }
        }
        private var size = 0L

        override fun requiresCacheSpanTouches() = true

        override fun onCacheInitialized() {}

        override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
            if (length != C.LENGTH_UNSET.toLong()) evict(cache, length)
        }

        override fun onSpanAdded(cache: Cache, span: CacheSpan) {
            scheduleRefresh()
            spans.add(span)
            size += span.length
            evict(cache, 0)
        }

        override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
            scheduleRefresh()
            if (spans.remove(span)) size -= span.length
        }

        override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
            onSpanRemoved(cache, oldSpan)
            onSpanAdded(cache, newSpan)
        }

        /** Must be called while holding the cache's lock. */
        fun trim(cache: Cache) = evict(cache, 0)

        private fun evict(cache: Cache, required: Long) {
            // However big a limit was chosen, saved songs never fill the phone: the oldest give way
            // so that some space always stays free for everything else.
            var short = spaceShortfall(required)
            while ((size + required > maxBytes || short > 0) && spans.isNotEmpty()) {
                val oldest = spans.first()
                try {
                    cache.removeSpan(oldest)
                } catch (_: Exception) {
                }
                // If the cache didn't report the removal, forget the span anyway so this ends.
                if (spans.remove(oldest)) size -= oldest.length
                short -= oldest.length
            }
        }

        /** Bytes to free to keep [MIN_FREE_BYTES] free after writing [required] more (0 if fine). */
        private fun spaceShortfall(required: Long): Long {
            val d = dir ?: return 0
            // Asked often while a song downloads; the phone's free space is looked up at most every
            // few seconds then (always when a new file is about to start).
            val now = android.os.SystemClock.elapsedRealtime()
            if (required == 0L && now - spaceCheckedAt < 5_000) return 0
            spaceCheckedAt = now
            val free = runCatching { d.usableSpace }.getOrDefault(Long.MAX_VALUE)
            return (MIN_FREE_BYTES + required - free).coerceAtLeast(0)
        }
    }
}
