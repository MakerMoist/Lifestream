package probe

import com.lifestream.player.data.Catalog
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory as F

/**
 * `--versions`: one run that (1) looks for the DS and PSP releases' soundtracks, with full track
 * lists, (2) lists the albums the affected titles use now, and (3) gathers official English names
 * for every Japanese-only song (all of them listed, not just the first 40).
 */
fun runVersions() {
    val dir = File(System.getenv("PROBE_OUT") ?: "report.md").absoluteFile.parentFile
    val queries = listOf(
        "Final Fantasy III DS original soundtrack",
        "Final Fantasy III original soundtrack",
        "ファイナルファンタジーIII オリジナル・サウンドトラック",
        "Final Fantasy IV DS original soundtrack",
        "ファイナルファンタジーIV DS",
        "Final Fantasy IV Complete Collection soundtrack",
        "Final Fantasy IV The After Years original soundtrack",
        "Final Fantasy IV Interlude",
        "Final Fantasy I II Anniversary PSP soundtrack",
        "Final Fantasy Anniversary Edition original soundtrack",
        "Final Fantasy I II original soundtrack",
        "Final Fantasy Tactics War of the Lions soundtrack",
        "Final Fantasy Tactics The Lion War",
        "Crisis Core Final Fantasy VII original soundtrack",
        "Chrono Trigger DS soundtrack",
        "Final Fantasy Dimensions original soundtrack",
    )
    val out = StringBuilder("# DS / PSP soundtracks\n\n")
    val seen = HashSet<String>()
    for (q in queries) {
        println("search $q")
        out.append("## Search: $q\n\n")
        val albums = runCatching { search(q, F.MUSIC_ALBUMS, pages = 2) }.getOrDefault(emptyList())
            .mapNotNull { it as? PlaylistInfoItem }
        for (a in albums.take(14)) {
            out.append("- ${a.name} — ${a.uploaderName} — ${a.streamCount} tracks — ${a.url}\n")
            val name = (a.name ?: "").uppercase()
            val relevant = listOf("FANTASY", "ファイナル", "CRISIS", "CHRONO", "クロノ").any { it in name }
            if (relevant && seen.add(a.url ?: "")) {
                runCatching { tracks(a.url) }.getOrNull()?.forEach { (id, title, artist, secs) ->
                    out.append("    - `$id` $title — $artist — ${secs}s\n")
                }
            }
        }
        out.append("\n### Playlists\n\n")
        runCatching { search(q, F.PLAYLISTS, pages = 1) }.getOrDefault(emptyList())
            .mapNotNull { it as? PlaylistInfoItem }.take(6)
            .forEach { p -> out.append("- ${p.name} — ${p.uploaderName} — ${p.streamCount} — ${p.url}\n") }
        out.append("\n")
    }
    File(dir, "versions.md").writeText(out.toString())

    // What the affected titles play now.
    val albums = StringBuilder("# Albums used now\n\n")
    for (id in listOf("ff1", "ff2", "ff3", "ff4", "fft", "ccr", "ct", "ff7")) {
        val game = Catalog.game(id) ?: continue
        albums.append("## ${game.title} (`$id`)\n\n")
        runCatching { sourcesFor(game, StringBuilder()).take(game.maxAlbums) }.getOrDefault(emptyList()).forEach { a ->
            albums.append("- ${a.title} — ${a.artist} — ${a.trackCount} — ${a.url}${if (a.fromPlaylist) " (playlist)" else ""}\n")
            runCatching { tracks(a.url) }.getOrNull()?.forEach { (tid, title, artist, secs) ->
                albums.append("    - `$tid` $title — $artist — ${secs}s\n")
            }
        }
        albums.append("\n")
    }
    File(dir, "albums.md").writeText(albums.toString())

    // Official English names (writes official_titles.json and report.md).
    runTitles(emptySet())
}

private data class Item(val id: String, val title: String, val artist: String, val secs: Long)

private fun tracks(url: String): List<Item> {
    val yt = ServiceList.YouTube
    val info = PlaylistInfo.getInfo(yt, url)
    val items = info.relatedItems.toMutableList()
    var next = if (info.hasNextPage()) info.nextPage else null
    var pages = 0
    while (next != null && pages < 20) {
        val more = PlaylistInfo.getMoreItems(yt, url, next)
        items += more.items
        next = if (more.hasNextPage()) more.nextPage else null
        pages++
    }
    return items.map { s: StreamInfoItem ->
        Item(
            runCatching { yt.streamLHFactory.getId(s.url) }.getOrDefault("?"),
            s.name ?: "",
            (s.uploaderName ?: "").removeSuffix(" - Topic"),
            s.duration,
        )
    }
}
