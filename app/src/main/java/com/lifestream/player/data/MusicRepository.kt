package com.lifestream.player.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap

/** A page of results plus an opaque token for the next page (null when there is no more). */
data class Paged<T>(val items: List<T>, val next: Any?)

/** Something that can be paged through: search results, an album's track list, etc. */
interface PagedSource<T> {
    suspend fun load(page: Any?): Paged<T>
}

/**
 * Talks to YouTube's servers through NewPipeExtractor. No API key, no YouTube app,
 * no embedded web player: just metadata and raw stream URLs.
 */
object MusicRepository {

    private val yt get() = ServiceList.YouTube

    /**
     * Some soundtracks are split across several official albums (e.g. XIV's expansions,
     * or Vol. 1 / Vol. 2 releases). The Tracks tab plays through up to this many, best first.
     */
    private const val MAX_ALBUMS_PER_GAME = 8

    private val albumCache = ConcurrentHashMap<String, List<Album>>()

    // ---- Per-game sources: the game's official original soundtrack, nothing else ----

    /**
     * A game's official original soundtrack album(s): YouTube Music albums whose title matches
     * that specific game (see [Matching.matches]) and that are the real OST release
     * (see [Matching.isOfficialSoundtrack]). Ranked so the original release comes before
     * remasters. No fan playlists, arrangements or loose song search.
     */
    suspend fun gameAlbums(game: Game): List<Album> {
        albumCache[game.id]?.let { return it }
        return withContext(Dispatchers.IO) {
            val found = LinkedHashMap<String, Album>()
            var lastError: Exception? = null

            fun add(items: List<InfoItem>) {
                items.filterIsInstance<PlaylistInfoItem>()
                    .mapNotNull { it.toAlbum() }
                    .filter { Matching.matches(game.match, it.title) && Matching.isOfficialSoundtrack(it) }
                    .forEach { found.putIfAbsent(it.url, it) }
            }

            // A second wording helps older games whose OST is titled "Original Sound Version".
            val queries = listOf(game.query, "${game.title} soundtrack")
            for (query in queries) {
                if (found.isNotEmpty()) break
                try {
                    val handler = yt.searchQHFactory.fromQuery(
                        query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS), "",
                    )
                    val info = SearchInfo.getInfo(yt, handler)
                    add(info.relatedItems)
                    if (info.hasNextPage()) {
                        add(SearchInfo.getMoreItems(yt, handler, info.nextPage).items)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                }
            }

            if (found.isEmpty()) lastError?.let { throw it }
            val ranked = found.values.sortedByDescending { Matching.score(it, game) }
            if (ranked.isNotEmpty()) albumCache[game.id] = ranked
            ranked
        }
    }

    fun gameAlbumsSource(game: Game): PagedSource<Album> = object : PagedSource<Album> {
        override suspend fun load(page: Any?): Paged<Album> = Paged(gameAlbums(game), null)
    }

    /** A game's tracks, played through its official soundtrack album(s) in order. */
    fun gameTracks(game: Game): PagedSource<Track> = GameTracksSource(game)

    private object More

    private class GameTracksSource(private val game: Game) : PagedSource<Track> {
        private var albums: List<Album>? = null
        private var index = 0
        private var albumPage: Any? = null

        override suspend fun load(page: Any?): Paged<Track> {
            val list = albums ?: gameAlbums(game).also { albums = it }
            val max = minOf(list.size, MAX_ALBUMS_PER_GAME)
            val out = mutableListOf<Track>()
            while (out.isEmpty() && index < max) {
                val result = try {
                    albumTracks(list[index].url).load(albumPage)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The main album failing is a real error; a later volume is just skipped.
                    if (index == 0 && albumPage == null) throw e
                    Paged(emptyList(), null)
                }
                out += result.items
                if (result.next != null) {
                    albumPage = result.next
                } else {
                    index++
                    albumPage = null
                }
            }
            return Paged(out, if (index < max) More else null)
        }
    }

    // ---- Free-text search ----

    /**
     * Song search. Tries YouTube Music's "songs" index first, then regular video search.
     * With [finalFantasyOnly], keeps only tracks by Final Fantasy composers / labels or with
     * Final Fantasy in the title, and drops covers, lo-fi and similar filler.
     */
    fun songSearch(query: String, finalFantasyOnly: Boolean = false): PagedSource<Track> {
        val base = FallbackSearch(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, YoutubeSearchQueryHandlerFactory.VIDEOS),
        ) { item -> (item as? StreamInfoItem)?.toTrack() }
        return if (finalFantasyOnly) Filtered(base) { Matching.looksLikeFinalFantasy(it) } else base
    }

    /** Album search: YouTube Music albums first, then regular playlists. */
    fun albumSearch(query: String, finalFantasyOnly: Boolean = false): PagedSource<Album> {
        val base = FallbackSearch(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS, YoutubeSearchQueryHandlerFactory.PLAYLISTS),
        ) { item -> (item as? PlaylistInfoItem)?.toAlbum() }
        return if (finalFantasyOnly) Filtered(base) { Matching.looksLikeFinalFantasy(it) } else base
    }

    /** The tracks inside an album / playlist. */
    fun albumTracks(url: String): PagedSource<Track> = object : PagedSource<Track> {
        override suspend fun load(page: Any?): Paged<Track> = withContext(Dispatchers.IO) {
            val p = page as? Page
            if (p == null) {
                val info = PlaylistInfo.getInfo(yt, url)
                Paged(
                    info.relatedItems.mapNotNull { it.toTrack() },
                    if (info.hasNextPage()) info.nextPage else null,
                )
            } else {
                val more = PlaylistInfo.getMoreItems(yt, url, p)
                Paged(
                    more.items.mapNotNull { it.toTrack() },
                    if (more.hasNextPage()) more.nextPage else null,
                )
            }
        }
    }

    private class Filtered<T>(
        private val inner: PagedSource<T>,
        private val keep: (T) -> Boolean,
    ) : PagedSource<T> {
        override suspend fun load(page: Any?): Paged<T> {
            val result = inner.load(page)
            return Paged(result.items.filter(keep), result.next)
        }
    }

    private class FallbackSearch<T>(
        private val query: String,
        private val filters: List<String>,
        private val map: (InfoItem) -> T?,
    ) : PagedSource<T> {
        private var chosenFilter: String? = null

        override suspend fun load(page: Any?): Paged<T> = withContext(Dispatchers.IO) {
            val fixed = chosenFilter
            val p = page as? Page
            if (p != null && fixed != null) {
                val handler = yt.searchQHFactory.fromQuery(query, listOf(fixed), "")
                val more = SearchInfo.getMoreItems(yt, handler, p)
                return@withContext Paged(
                    more.items.mapNotNull(map),
                    if (more.hasNextPage()) more.nextPage else null,
                )
            }

            var lastError: Exception? = null
            for (filter in filters) {
                try {
                    val handler = yt.searchQHFactory.fromQuery(query, listOf(filter), "")
                    val info = SearchInfo.getInfo(yt, handler)
                    val items = info.relatedItems.mapNotNull(map)
                    if (items.isNotEmpty()) {
                        chosenFilter = filter
                        return@withContext Paged(items, if (info.hasNextPage()) info.nextPage else null)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                }
            }
            lastError?.let { throw it }
            Paged(emptyList(), null)
        }
    }

    // ---- Mapping ----

    private fun StreamInfoItem.toTrack(): Track? {
        if (streamType.name.contains("LIVE")) return null
        val id = runCatching { yt.streamLHFactory.getId(url) }.getOrNull() ?: return null
        return Track(
            id = id,
            title = TitleTranslator.toEnglish(name ?: "Unknown track"),
            artist = TitleTranslator.toEnglish(cleanArtist(uploaderName)),
            durationSec = duration,
            thumbnail = bestImage(thumbnails),
        )
    }

    private fun PlaylistInfoItem.toAlbum(): Album? {
        val u = url ?: return null
        return Album(
            url = u,
            title = TitleTranslator.toEnglish(name ?: "Untitled"),
            artist = TitleTranslator.toEnglish(cleanArtist(uploaderName)),
            trackCount = streamCount,
            thumbnail = bestImage(thumbnails),
        )
    }

    private fun cleanArtist(raw: String?): String =
        (raw ?: "").removeSuffix(" - Topic").trim()

    private fun bestImage(images: List<Image>?): String? =
        images?.maxByOrNull { it.height }?.url
}
