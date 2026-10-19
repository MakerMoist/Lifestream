package com.lifestream.player.data

import com.lifestream.player.playback.SafeInput

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
            // Games split across many albums (e.g. XIV's expansions and patches) search for each.
            val queries = listOf(game.query, "${game.title} soundtrack") + game.albumQueries
            for ((i, query) in queries.withIndex()) {
                if (i == 1 && found.isNotEmpty()) continue
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

            // Second source: no official album on YouTube Music (e.g. X-2, Crisis Core Reunion,
            // Lost Odyssey). Use the most complete upload of the game's own soundtrack instead.
            if (found.isEmpty()) {
                val playlists = LinkedHashMap<String, Album>()
                for (query in listOf("${game.title} OST", "${game.title} original soundtrack")) {
                    try {
                        val handler = yt.searchQHFactory.fromQuery(
                            query, listOf(YoutubeSearchQueryHandlerFactory.PLAYLISTS), "",
                        )
                        SearchInfo.getInfo(yt, handler).relatedItems
                            .filterIsInstance<PlaylistInfoItem>()
                            .mapNotNull { it.toAlbum() }
                            .filter { Matching.isSoundtrackPlaylist(it, game) }
                            .forEach { playlists.putIfAbsent(it.url, it) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        lastError = e
                    }
                }
                playlists.values.maxByOrNull { Matching.playlistScore(it) }
                    ?.let { found[it.url] = it.copy(fromPlaylist = true) }
            }

            if (found.isEmpty()) lastError?.let { throw it }
            val ranked = Matching.ordered(found.values, game)
            if (ranked.isNotEmpty()) albumCache[game.id] = ranked
            ranked
        }
    }

    /** A game's tracks, played through its official soundtrack album(s) in order. */
    fun gameTracks(game: Game): PagedSource<Track> = GameTracksSource(game)

    private object More

    private class GameTracksSource(private val game: Game) : PagedSource<Track> {
        private var albums: List<Album>? = null
        private var index = 0
        private var albumPage: Any? = null
        private var extrasDone = game.extras.isEmpty()
        /** The soundtrack album's cover (from its first song), for extras shown with it. */
        private var albumCover: String? = null
        /** Songs of the original release, by name, so a DS/PSP song of the same name is marked. */
        private val originalKeys = HashSet<String>()
        private val versionKeys = HashSet<String>()
        private var versionIndex = 0
        private var versionPage: Any? = null

        override suspend fun load(page: Any?): Paged<Track> {
            val list = albums ?: gameAlbums(game).also { albums = it }
            val max = minOf(list.size, game.maxAlbums)
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
                val album = list[index]
                val firstAndOnlyPage = albumPage == null && result.next == null
                // Skip singles and previews that sit next to the real soundtrack.
                val tooSmall = firstAndOnlyPage && result.items.size < 4 && max > 1
                if (!tooSmall) {
                    if (albumCover == null) albumCover = result.items.firstNotNullOfOrNull { it.thumbnail }
                    out += if (album.fromPlaylist) {
                        result.items
                            .filterNot { it.title.equals("Private video", true) || it.title.equals("Deleted video", true) }
                            .map { it.copy(title = Matching.cleanTrackTitle(it.title, game)) }
                            .filterNot { Matching.isFillerTrack(it.title) }
                    } else {
                        result.items
                    }
                }
                if (result.next != null) {
                    albumPage = result.next
                } else {
                    index++
                    albumPage = null
                }
            }
            // After the soundtrack: any extra songs (e.g. a localized theme song).
            if (out.isEmpty() && index >= max && !extrasDone) {
                extrasDone = true
                out += findExtras(game, albumCover)
            }
            val original = out.filterNot { it.id in game.skipIds }
            original.forEach { originalKeys += Matching.songKey(it.title, game.fullSongTitles) }
            // Last, the DS / PSP release's soundtrack.
            val versions = if (original.isEmpty() && index >= max && extrasDone) loadVersions() else emptyList()
            return Paged(
                (original + versions).map { ArtistCredits.fix(it.copy(gameId = game.id)) },
                if (index < max || !extrasDone || versionIndex < game.versions.size) More else null,
            )
        }

        /**
         * The next page of the title's DS / PSP soundtrack. A song named like one of the original's
         * gets the release after its name ("Battle 1 (DS)"); a recording or name already listed
         * isn't listed again; every song shows the original soundtrack's cover.
         */
        private suspend fun loadVersions(): List<Track> {
            val out = mutableListOf<Track>()
            while (out.isEmpty() && versionIndex < game.versions.size) {
                val version = game.versions[versionIndex]
                val result = try {
                    albumTracks(version.url).load(versionPage)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Paged(emptyList(), null) // this release is skipped; the original still plays
                }
                for (t in result.items) {
                    if (t.id in version.skipIds || t.id in game.skipIds) continue
                    val name = version.originalNames[t.id] ?: t.title
                    val tagged = version.tagAll || t.id in version.originalNames || t.id in version.tagIds ||
                        Matching.songKey(name, game.fullSongTitles) in originalKeys
                    val title = if (tagged) "$name (${version.tag})" else name
                    if (!versionKeys.add(Matching.songKey(title, game.fullSongTitles))) continue
                    out += t.copy(title = title, thumbnail = albumCover ?: game.coverArt ?: t.thumbnail)
                }
                if (result.next != null) {
                    versionPage = result.next
                } else {
                    versionIndex++
                    versionPage = null
                }
            }
            return out
        }
    }

    /**
     * Finds each of [game]'s extra songs as its original recording; ones not found are skipped.
     * Extras marked [ExtraSong.albumArt] show [albumCover], like the soundtrack's other songs.
     */
    private suspend fun findExtras(game: Game, albumCover: String?): List<Track> = game.extras.mapNotNull { extra ->
        val found = extra.pinnedTrack() ?: try {
            Matching.pickExtra(extra, songSearch(extra.query).load(null).items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (found != null && extra.albumArt && albumCover != null) found.copy(thumbnail = albumCover) else found
    }

    // ---- Free-text search ----

    /**
     * Song search (used to find a title's extra songs). Tries YouTube Music's "songs" index
     * first, then regular video search.
     */
    fun songSearch(query: String): PagedSource<Track> = FallbackSearch(
        query,
        listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, YoutubeSearchQueryHandlerFactory.VIDEOS),
    ) { item -> (item as? StreamInfoItem)?.toTrack() }

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
        // Only real video ids reach the player (anything else would fail when it's queued).
        if (!SafeInput.isVideoId(id)) return null
        val raw = tidySongTitle(name ?: "Unknown track").take(Track.MAX_TEXT)
        // Square Enix's own English name when there is one, otherwise translated on the device.
        val title = (OfficialTitles.forId(id) ?: tidySongTitle(TitleTranslator.toEnglish(raw))).take(Track.MAX_TEXT)
        return Track(
            id = id,
            title = title,
            // The Japanese name stays searchable alongside the English one.
            altTitle = raw.takeIf { it != title && TitleTranslator.hasJapanese(it) }?.take(300),
            // Known names first, so e.g. Uematsu's channel name never goes through machine translation.
            artist = ArtistCredits.canonical(TitleTranslator.toEnglish(ArtistCredits.canonical(cleanArtist(uploaderName))))
                .take(Track.MAX_TEXT),
            durationSec = duration.coerceIn(-1L, 24L * 3600),
            thumbnail = bestImage(thumbnails),
        )
    }

    private fun PlaylistInfoItem.toAlbum(): Album? {
        val u = url ?: return null
        return Album(
            url = u,
            title = TitleTranslator.toEnglish(name ?: "Untitled"),
            artist = tidySongTitle(TitleTranslator.toEnglish(cleanArtist(uploaderName))),
            trackCount = streamCount,
            thumbnail = bestImage(thumbnails),
        )
    }

    private fun cleanArtist(raw: String?): String =
        tidySongTitle((raw ?: "").removeSuffix(" - Topic").trim())

    private fun bestImage(images: List<Image>?): String? =
        images?.maxByOrNull { it.height }?.url?.let { SafeInput.safeArtwork(it) }
}
