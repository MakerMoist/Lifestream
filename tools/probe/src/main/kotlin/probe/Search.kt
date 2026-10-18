package probe

import com.lifestream.player.data.Matching
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory as F

/**
 * Free search for finding specific recordings and albums: `--search words of the query`.
 * Lists YouTube Music albums (with full track lists for the first few), songs and plain videos,
 * each with its id, uploader and length, so a recording can be checked and pinned.
 */
fun runSearch(words: List<String>) {
    val out = StringBuilder()
    searchInto(out, words.joinToString(" ").trim())
    val target = File(System.getenv("PROBE_OUT") ?: "report.md").absoluteFile
    target.writeText(out.toString())
    println("wrote $target")
}

/** Several searches in one report: `--editions` looks for special and expanded soundtrack releases. */
fun runEditions() {
    val queries = listOf(
        "Kyosuke Himuro Calling",
        "氷室京介 CALLING",
        "Kyosuke Himuro Safe and Sound Gerard Way",
        "Final Fantasy VII Advent Children Complete mini album",
        "Final Fantasy VII Advent Children Reunion Tracks",
    )
    val out = StringBuilder()
    for (q in queries) {
        println("search $q")
        searchInto(out, q, albumTracks = 2)
    }
    val target = File(System.getenv("PROBE_OUT") ?: "report.md").absoluteFile
    target.writeText(out.toString())
    println("wrote $target")
}

private fun searchInto(out: StringBuilder, query: String, albumTracks: Int = 4) {
    out.append("# Search: $query\n\n")
    val yt = ServiceList.YouTube

    out.append("## Albums\n\n")
    val albums = runCatching { search(query, F.MUSIC_ALBUMS, pages = 1) }.getOrDefault(emptyList())
        .mapNotNull { it as? PlaylistInfoItem }
    albums.take(12).forEachIndexed { i, a ->
        out.append("- ${a.name} — ${a.uploaderName} — ${a.streamCount} tracks — ${a.url}\n")
        if (i < albumTracks) {
            runCatching { allTitlesWithIds(a.url) }.getOrNull()?.forEach { (id, title, artist, secs) ->
                out.append("    - `$id` $title — $artist — ${secs}s\n")
            }
        }
    }

    for ((label, filter) in listOf("Songs" to F.MUSIC_SONGS, "Music videos" to F.MUSIC_VIDEOS, "Videos" to F.VIDEOS)) {
        out.append("\n## $label\n\n")
        runCatching { search(query, filter, pages = 1) }.getOrDefault(emptyList())
            .mapNotNull { it as? StreamInfoItem }
            .take(15)
            .forEach { s ->
                val id = runCatching { yt.streamLHFactory.getId(s.url) }.getOrDefault("?")
                out.append("- `$id` ${s.name} — ${s.uploaderName} — ${s.duration}s — key `${Matching.songKey(s.name ?: "")}`\n")
            }
    }
    // Playlists too (soundtracks with no official album are often uploaded as one).
    out.append("\n## Playlists\n\n")
    runCatching { search(query, F.PLAYLISTS, pages = 1) }.getOrDefault(emptyList())
        .mapNotNull { it as? PlaylistInfoItem }
        .take(8)
        .forEach { p -> out.append("- ${p.name} — ${p.uploaderName} — ${p.streamCount} — ${p.url}\n") }
    out.append("\n")
}

private data class Row(val id: String, val title: String, val artist: String, val secs: Long)

private fun allTitlesWithIds(url: String): List<Row> {
    val yt = ServiceList.YouTube
    val info = PlaylistInfo.getInfo(yt, url)
    val rows = info.relatedItems.map { it.toRow() }.toMutableList()
    var next = if (info.hasNextPage()) info.nextPage else null
    var pages = 0
    while (next != null && pages < 10) {
        val more = PlaylistInfo.getMoreItems(yt, url, next)
        rows += more.items.map { it.toRow() }
        next = if (more.hasNextPage()) more.nextPage else null
        pages++
    }
    return rows
}

private fun StreamInfoItem.toRow() = Row(
    runCatching { ServiceList.YouTube.streamLHFactory.getId(url) }.getOrDefault("?"),
    name ?: "",
    (uploaderName ?: "").removeSuffix(" - Topic"),
    duration,
)
