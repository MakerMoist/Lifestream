package com.lifestream.player.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaSession
import com.lifestream.player.data.AppSettings
import com.lifestream.player.data.Catalog
import com.lifestream.player.data.Game
import com.lifestream.player.data.Library
import com.lifestream.player.data.Mixes
import com.lifestream.player.data.Recommendations
import com.lifestream.player.data.SoundtrackIndex
import com.lifestream.player.data.Track

/**
 * What Android Auto shows: LifeStream's library as the car's tabs (Home, Titles, Playlists,
 * History), search, and voice requests. Android Auto draws the screens itself (lists, grids and
 * the full-screen player) from these items.
 *
 * Everything that comes back from the car (ids, search words) is checked here: ids must name a
 * real title, playlist or YouTube video, and lists are capped.
 */
@OptIn(UnstableApi::class)
object AutoLibrary {
    const val ROOT = "root"
    private const val HOME = "home"
    private const val TITLES = "titles"
    private const val PLAYLISTS = "playlists"
    private const val HISTORY = "history"
    private const val SEARCH = "search"
    private const val INSTANT = "instant"
    private const val OFF = "off"
    private const val TITLE = "title/"
    private const val PLAYLIST = "playlist/"
    private const val SONG = "song/"

    private const val MAX_ITEMS = 500
    private const val MAX_QUERY = 100

    /** Android Auto on the phone (its media browser connects under this package). */
    private val autoPackages = setOf("com.google.android.projection.gearhead")

    /**
     * Android Auto connecting. Besides the package name, the app must be part of the phone's
     * system or installed from the Play Store (as Android Auto always is), so a sideloaded app
     * can't pass itself off as Android Auto on a phone that doesn't have it.
     */
    fun isAndroidAuto(context: Context, controller: MediaSession.ControllerInfo): Boolean {
        val pkg = controller.packageName
        if (pkg !in autoPackages) return false
        return runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            // The connection really comes from that app (not another app claiming its name).
            if (controller.uid != info.uid) return false
            val system = info.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or
                android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val installer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(pkg).installingPackageName
            } else {
                @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg)
            }
            system || installer == "com.android.vending"
        }.getOrDefault(false)
    }

    val enabled: Boolean get() = AppSettings.androidAuto.value

    // Lists whose songs can be tapped, kept so a tap plays the same list it was shown in.
    @Volatile private var lastHome: List<Track> = emptyList()
    @Volatile private var lastSearch: List<Track> = emptyList()

    // ---------------- browsing ----------------

    fun root(): MediaItem = folder(ROOT, "LifeStream", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, extras = listStyle())

    /** The children of [parentId], or null if there's no such node. */
    fun children(context: Context, parentId: String): List<MediaItem>? {
        if (!enabled) {
            // Off: one note in place of the library, so it's clear why nothing is here.
            return if (parentId == ROOT) listOf(offNotice()) else emptyList()
        }
        val items = when {
            parentId == ROOT -> listOf(
                folder(HOME, "Home", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, extras = listStyle()),
                folder(TITLES, "Titles", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS, extras = gridStyle()),
                folder(PLAYLISTS, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS, extras = listStyle()),
                folder(HISTORY, "History", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, extras = listStyle()),
            )
            parentId == HOME -> home()
            parentId == TITLES -> Catalog.games.map { titleItem(context, it) }
            parentId == PLAYLISTS -> Library.playlists.value.map { p ->
                folder(
                    PLAYLIST + p.id, p.name, MediaMetadata.MEDIA_TYPE_PLAYLIST,
                    subtitle = if (p.tracks.size == 1) "1 song" else "${p.tracks.size} songs",
                    extras = listStyle(),
                )
            }
            parentId == HISTORY -> songItems(HISTORY, Library.recents.value)
            parentId == SEARCH -> songItems(SEARCH, lastSearch)
            parentId.startsWith(TITLE) -> titleSongs(parentId.removePrefix(TITLE))?.let { songItems(parentId, it) }
            parentId.startsWith(PLAYLIST) -> Library.playlist(parentId.removePrefix(PLAYLIST))?.let { songItems(parentId, it.tracks) }
            else -> null
        }
        return items?.take(MAX_ITEMS)
    }

    /** One item by id (Android Auto asks for these when restoring its screens). */
    fun item(context: Context, mediaId: String): MediaItem? = when {
        mediaId == ROOT -> root()
        !enabled -> null
        mediaId == INSTANT -> instantItem()
        mediaId.startsWith(TITLE) -> Catalog.game(mediaId.removePrefix(TITLE))?.let { titleItem(context, it) }
        mediaId.startsWith(SONG) -> parseSong(mediaId)?.let { (parent, videoId) ->
            tracksFor(parent)?.firstOrNull { it.id == videoId }?.let { songItem(parent, it) }
        }
        else -> children(context, ROOT)?.firstOrNull { it.mediaId == mediaId }
    }

    /** Runs a search from the car's keyboard; results are read back with [searchResults]. */
    fun search(query: String): Int {
        val q = query.trim().take(MAX_QUERY)
        lastSearch = if (!enabled || q.isEmpty()) emptyList() else searchTracks(q)
        return lastSearch.size
    }

    fun searchResults(): List<MediaItem> = songItems(SEARCH, lastSearch)

    private fun home(): List<MediaItem> {
        val out = mutableListOf(instantItem())
        Library.lastPlaylistId.value?.let { Library.playlist(it) }?.let { p ->
            out += folder(PLAYLIST + p.id, "Last Playlist", MediaMetadata.MEDIA_TYPE_PLAYLIST, subtitle = p.name, extras = listStyle())
        }
        val recommended = Recommendations.recommend(
            Library.recents.value,
            SoundtrackIndex.tracks.value,
            seed = System.currentTimeMillis() / (30 * 60_000), // stays the same for half an hour
            limit = 24,
        ) { t -> SoundtrackIndex.gameOf(t)?.id }
        lastHome = recommended
        out += songItems(HOME, recommended)
        return out
    }

    // ---------------- playing ----------------

    /** What to play for a request from the car. */
    data class Request(val tracks: List<Track>, val startIndex: Int, val shuffle: Boolean)

    /**
     * Turns what the car asked for into a queue: a tapped song plays its whole list from that
     * song, Instant Playlist builds a fresh mix, and a voice request ("play Prelude") searches.
     * Null when it can't be resolved.
     */
    fun resolve(items: List<MediaItem>, startIndex: Int): Request? {
        if (!enabled) return null
        val first = items.getOrNull(startIndex.coerceAtLeast(0)) ?: items.firstOrNull() ?: return null
        val voice = first.requestMetadata.searchQuery?.trim()?.take(MAX_QUERY)
        return when {
            first.mediaId == INSTANT -> Mixes.instant().takeIf { it.isNotEmpty() }?.let { Request(it, 0, shuffle = true) }
            first.mediaId.startsWith(SONG) -> {
                val (parent, videoId) = parseSong(first.mediaId) ?: return null
                val list = tracksFor(parent)?.filter { SafeInput.isVideoId(it.id) } ?: return null
                val index = list.indexOfFirst { it.id == videoId }
                if (index < 0) return null
                // A title's own songs play in soundtrack order; everything else shuffles.
                Request(list, index, shuffle = !parent.startsWith(TITLE))
            }
            first.mediaId.isEmpty() && !voice.isNullOrEmpty() -> voiceRequest(voice)
            else -> null
        }
    }

    private fun voiceRequest(query: String): Request? {
        val songs = searchTracks(query)
        if (songs.isNotEmpty()) return Request(songs, 0, shuffle = false)
        // "Play Final Fantasy Seven": the title's songs.
        val game = SoundtrackIndex.searchGames(query).firstOrNull() ?: return null
        val list = titleSongs(game.id)?.takeIf { it.isNotEmpty() } ?: return null
        return Request(list, 0, shuffle = false)
    }

    // ---------------- helpers ----------------

    private fun searchTracks(query: String): List<Track> =
        SoundtrackIndex.searchTracks(query, limit = 50).map { it.track }.filter { SafeInput.isVideoId(it.id) }

    private fun titleSongs(gameId: String): List<Track>? {
        val game = Catalog.game(gameId) ?: return null
        return SoundtrackIndex.songsFor(game.id) ?: run {
            SoundtrackIndex.ensureBuilt() // not indexed yet: start it, show nothing for now
            emptyList()
        }
    }

    private fun tracksFor(parent: String): List<Track>? = when {
        parent == HOME -> lastHome
        parent == HISTORY -> Library.recents.value
        parent == SEARCH -> lastSearch
        parent.startsWith(TITLE) -> titleSongs(parent.removePrefix(TITLE))
        parent.startsWith(PLAYLIST) -> Library.playlist(parent.removePrefix(PLAYLIST))?.tracks
        else -> null
    }

    /** "song/<parent>/<videoId>" → parent and video id; null unless the video id is valid. */
    private fun parseSong(mediaId: String): Pair<String, String>? {
        if (mediaId.length > 200) return null
        val rest = mediaId.removePrefix(SONG)
        val cut = rest.lastIndexOf('/')
        if (cut <= 0) return null
        val parent = rest.substring(0, cut)
        val videoId = rest.substring(cut + 1)
        return if (SafeInput.isVideoId(videoId)) parent to videoId else null
    }

    private fun songItems(parent: String, tracks: List<Track>): List<MediaItem> =
        tracks.asSequence().filter { SafeInput.isVideoId(it.id) }.take(MAX_ITEMS).map { songItem(parent, it) }.toList()

    private fun songItem(parent: String, track: Track): MediaItem {
        val game = track.gameId?.let { Catalog.game(it) }
        return MediaItem.Builder()
            .setMediaId("$SONG$parent/${track.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setSubtitle(game?.title ?: track.artist)
                    .setArtworkUri(SafeInput.safeArtwork(track.thumbnail?.let { Uri.parse(it) }))
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()
    }

    private fun titleItem(context: Context, game: Game): MediaItem = folder(
        TITLE + game.id, game.title, MediaMetadata.MEDIA_TYPE_ALBUM,
        subtitle = "${game.year} · ${game.composer}",
        // The title's card art, straight from the app's own resources.
        artwork = game.art.takeIf { it != 0 }?.let { Uri.parse("android.resource://${context.packageName}/$it") },
        extras = listStyle(),
    )

    private fun instantItem() = MediaItem.Builder()
        .setMediaId(INSTANT)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle("Instant Playlist")
                .setSubtitle("A random mix from every title")
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                .build()
        )
        .build()

    private fun offNotice() = MediaItem.Builder()
        .setMediaId(OFF)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle("Android Auto is off")
                .setSubtitle("Turn it on in LifeStream's Settings, under System")
                .setIsPlayable(false)
                .setIsBrowsable(false)
                .build()
        )
        .build()

    private fun folder(
        id: String,
        title: String,
        type: Int,
        subtitle: String? = null,
        artwork: Uri? = null,
        extras: Bundle? = null,
    ) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtworkUri(artwork)
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .setMediaType(type)
                .setExtras(extras)
                .build()
        )
        .build()

    /** Children shown as a list (songs) or a grid of artwork (titles). */
    private fun listStyle() = Bundle().apply {
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
    }

    private fun gridStyle() = Bundle().apply {
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
    }
}
