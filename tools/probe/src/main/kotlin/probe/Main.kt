package probe

import com.lifestream.player.data.Album
import com.lifestream.player.data.Catalog
import com.lifestream.player.data.Game
import com.lifestream.player.data.Matching
import com.lifestream.player.net.Http
import com.lifestream.player.net.OkHttpDownloader
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import java.io.File
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory as F

/**
 * Runs the same soundtrack pipeline as the app (official albums → playlist fallback, title
 * cleaning, one version per song) for every catalog game against YouTube Music, and writes
 * a report of what each game would play.
 */
fun main(args: Array<String>) {
    NewPipe.init(OkHttpDownloader(Http.client), Localization("en", "US"), ContentCountry("US"))
    val only = args.toSet()
    val detail = StringBuilder()
    val summary = StringBuilder(
        "## Summary\n\n| game | source | albums used | songs (1 version each) | first songs |\n|---|---|---|---|---|\n"
    )

    for (game in Catalog.games.filter { only.isEmpty() || it.id in only }) {
        println("probing ${game.id}")
        detail.append("## ${game.title} (`${game.id}`)\n\n")
        val sources = try {
            sourcesFor(game, detail)
        } catch (e: Exception) {
            detail.append("- FAILED: ${e.javaClass.simpleName}: ${e.message}\n\n")
            summary.append("| ${game.id} | error | | | ${e.javaClass.simpleName} |\n")
            continue
        }

        val songs = LinkedHashMap<String, String>()
        var used = 0
        for (album in sources.take(8)) {
            val titles = try {
                allTitles(album.url)
            } catch (e: Exception) {
                detail.append("- could not load ${album.title}: ${e.message}\n")
                continue
            }
            if (titles.size < 4 && sources.size > 1) {
                detail.append("- skipped (too small, ${titles.size}): ${album.title}\n")
                continue
            }
            used++
            val clean = if (album.fromPlaylist) {
                titles.filterNot { it.equals("Private video", true) || it.equals("Deleted video", true) }
                    .map { Matching.cleanTrackTitle(it, game) }
            } else titles
            clean.forEach { songs.putIfAbsent(Matching.songKey(it), it) }
            detail.append("- used: ${album.title} — *${album.artist}* — ${titles.size} tracks${if (album.fromPlaylist) " (playlist)" else ""}\n")
            if (album.fromPlaylist) {
                // Full listing for playlist sources: these are the ones that can contain stray songs.
                titles.zip(clean).forEach { (raw, c) -> detail.append("    - `$raw` → **$c**\n") }
            }
        }
        detail.append("\n")
        val source = when {
            sources.isEmpty() -> "NONE"
            sources.first().fromPlaylist -> "playlist"
            else -> "official"
        }
        val first = songs.values.take(4).joinToString("; ")
        summary.append("| ${game.id} | $source | $used | ${songs.size} | $first |\n")
    }

    val target = File(System.getenv("PROBE_OUT") ?: "report.md").absoluteFile
    target.writeText(summary.toString() + "\n# Detail\n\n" + detail.toString())
    println("wrote $target")
}

/** Mirrors MusicRepository.gameAlbums. */
private fun sourcesFor(game: Game, log: StringBuilder): List<Album> {
    val found = LinkedHashMap<String, Album>()
    for (q in listOf(game.query, "${game.title} soundtrack")) {
        if (found.isNotEmpty()) break
        search(q, F.MUSIC_ALBUMS, pages = 2).mapNotNull { (it as? PlaylistInfoItem)?.toAlbum() }
            .filter { Matching.matches(game.match, it.title) && Matching.isOfficialSoundtrack(it) }
            .forEach { found.putIfAbsent(it.url, it) }
    }
    if (found.isEmpty()) {
        val playlists = LinkedHashMap<String, Album>()
        for (q in listOf("${game.title} OST", "${game.title} original soundtrack")) {
            search(q, F.PLAYLISTS, pages = 1).mapNotNull { (it as? PlaylistInfoItem)?.toAlbum() }
                .filter { Matching.isSoundtrackPlaylist(it, game) }
                .forEach { playlists.putIfAbsent(it.url, it) }
        }
        log.append("- playlist candidates: ${playlists.values.joinToString { "${it.title} (${it.trackCount})" }}\n")
        playlists.values.maxByOrNull { Matching.playlistScore(it) }?.let { found[it.url] = it.copy(fromPlaylist = true) }
    }
    return found.values.sortedByDescending { Matching.score(it, game) }
}

private fun allTitles(url: String): List<String> {
    val yt = ServiceList.YouTube
    val info = PlaylistInfo.getInfo(yt, url)
    val titles = info.relatedItems.map { it.name ?: "" }.toMutableList()
    var next = if (info.hasNextPage()) info.nextPage else null
    var pages = 0
    while (next != null && pages < 20) {
        val more = PlaylistInfo.getMoreItems(yt, url, next)
        titles += more.items.map { it.name ?: "" }
        next = if (more.hasNextPage()) more.nextPage else null
        pages++
    }
    return titles
}

private fun search(query: String, filter: String, pages: Int): List<InfoItem> {
    val yt = ServiceList.YouTube
    val handler = yt.searchQHFactory.fromQuery(query, listOf(filter), "")
    val info = SearchInfo.getInfo(yt, handler)
    val items = info.relatedItems.toMutableList()
    var next = if (info.hasNextPage()) info.nextPage else null
    var n = 1
    while (next != null && n < pages) {
        val more = SearchInfo.getMoreItems(yt, handler, next)
        items += more.items
        next = if (more.hasNextPage()) more.nextPage else null
        n++
    }
    return items
}

private fun PlaylistInfoItem.toAlbum() = Album(
    url = url,
    title = name ?: "",
    artist = (uploaderName ?: "").removeSuffix(" - Topic").trim(),
    trackCount = streamCount,
    thumbnail = null,
)
