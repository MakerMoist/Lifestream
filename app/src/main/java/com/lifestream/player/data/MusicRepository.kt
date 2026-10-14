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

/** A page of results plus the token for the next page (null when there is no more). */
data class Paged<T>(val items: List<T>, val next: Page?)

/** Something that can be paged through: search results, an album's track list, etc. */
interface PagedSource<T> {
    suspend fun load(page: Page?): Paged<T>
}

/**
 * Talks to YouTube's servers through NewPipeExtractor. No API key, no YouTube app,
 * no embedded web player: just metadata and raw stream URLs.
 */
object MusicRepository {

    private val yt get() = ServiceList.YouTube

    /**
     * Song search. Tries YouTube Music's "songs" index first (clean official OST tracks),
     * and falls back to regular video search if that comes back empty or fails.
     */
    fun songSearch(query: String): PagedSource<Track> = FallbackSearch(
        query,
        listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, YoutubeSearchQueryHandlerFactory.VIDEOS),
    ) { item -> (item as? StreamInfoItem)?.toTrack() }

    /** Album search: YouTube Music albums first, then regular playlists. */
    fun albumSearch(query: String): PagedSource<Album> = FallbackSearch(
        query,
        listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS, YoutubeSearchQueryHandlerFactory.PLAYLISTS),
    ) { item -> (item as? PlaylistInfoItem)?.toAlbum() }

    /** The tracks inside an album / playlist. */
    fun albumTracks(url: String): PagedSource<Track> = object : PagedSource<Track> {
        override suspend fun load(page: Page?): Paged<Track> = withContext(Dispatchers.IO) {
            if (page == null) {
                val info = PlaylistInfo.getInfo(yt, url)
                Paged(
                    info.relatedItems.mapNotNull { it.toTrack() },
                    if (info.hasNextPage()) info.nextPage else null,
                )
            } else {
                val more = PlaylistInfo.getMoreItems(yt, url, page)
                Paged(
                    more.items.mapNotNull { it.toTrack() },
                    if (more.hasNextPage()) more.nextPage else null,
                )
            }
        }
    }

    private class FallbackSearch<T>(
        private val query: String,
        private val filters: List<String>,
        private val map: (InfoItem) -> T?,
    ) : PagedSource<T> {
        private var chosenFilter: String? = null

        override suspend fun load(page: Page?): Paged<T> = withContext(Dispatchers.IO) {
            val fixed = chosenFilter
            if (page != null && fixed != null) {
                val handler = yt.searchQHFactory.fromQuery(query, listOf(fixed), "")
                val more = SearchInfo.getMoreItems(yt, handler, page)
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

    private fun StreamInfoItem.toTrack(): Track? {
        if (streamType.name.contains("LIVE")) return null
        val id = runCatching { yt.streamLHFactory.getId(url) }.getOrNull() ?: return null
        return Track(
            id = id,
            title = name ?: "Unknown track",
            artist = cleanArtist(uploaderName),
            durationSec = duration,
            thumbnail = bestImage(thumbnails),
        )
    }

    private fun PlaylistInfoItem.toAlbum(): Album? {
        val u = url ?: return null
        return Album(
            url = u,
            title = name ?: "Untitled",
            artist = cleanArtist(uploaderName),
            trackCount = streamCount,
            thumbnail = bestImage(thumbnails),
        )
    }

    private fun cleanArtist(raw: String?): String =
        (raw ?: "").removeSuffix(" - Topic").trim()

    private fun bestImage(images: List<Image>?): String? =
        images?.maxByOrNull { it.height }?.url
}
