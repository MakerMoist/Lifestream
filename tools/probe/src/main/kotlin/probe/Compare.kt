package probe

import com.lifestream.player.data.Catalog
import com.lifestream.player.data.Matching
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory as F

private val candidateQueries = mapOf(
    "ff11" to listOf("FINAL FANTASY XI Gifts from Vana'diel", "Gifts from Vana'diel Songs of Rebirth", "Gifts from Vana'diel Prime Memories"),
    "ff15" to listOf("FINAL FANTASY XV EPISODE ARDYN", "Final Fantasy XV Episode Ardyn original soundtrack", "FINAL FANTASY XV Comrades"),
)

private fun tracks(url: String): List<StreamInfoItem> {
    val yt = ServiceList.YouTube
    val info = PlaylistInfo.getInfo(yt, url)
    val rows = info.relatedItems.toMutableList()
    var next = if (info.hasNextPage()) info.nextPage else null
    var pages = 0
    while (next != null && pages < 20) {
        val more = PlaylistInfo.getMoreItems(yt, url, next)
        rows += more.items
        next = if (more.hasNextPage()) more.nextPage else null
        pages++
    }
    return rows
}

private fun id(item: StreamInfoItem) = runCatching { ServiceList.YouTube.streamLHFactory.getId(item.url) }.getOrDefault("?")

/** Every song of the albums a title uses, then each candidate album's songs marked NEW when missing. */
fun runCompare(only: Set<String>) {
    val out = StringBuilder("# Compare\n\n")
    for (game in Catalog.games.filter { it.id in only }) {
        println("compare ${game.id}")
        out.append("## ${game.title}\n\n")
        val used = sourcesFor(game, StringBuilder()).take(game.maxAlbums)
        val keys = HashSet<String>()
        val ids = HashSet<String>()
        for (a in used) {
            val t = runCatching { tracks(a.url) }.getOrDefault(emptyList())
            out.append("### USED ${a.title} (${t.size})\n")
            t.forEach {
                keys += Matching.songKey(it.name ?: "", game.fullSongTitles); ids += id(it)
                out.append("- `${id(it)}` ${it.name} — ${it.duration}s\n")
            }
        }
        game.extras.forEach { e -> keys += Matching.songKey(e.title); e.videoId?.let { ids += it } }
        val seen = used.map { it.url }.toMutableSet()
        for (q in candidateQueries[game.id].orEmpty()) {
            val albums = runCatching { search(q, F.MUSIC_ALBUMS, pages = 1) }.getOrDefault(emptyList())
                .filterIsInstance<PlaylistInfoItem>().filter { it.url !in seen }.take(4)
            for (a in albums) {
                seen += a.url
                val t = runCatching { tracks(a.url) }.getOrDefault(emptyList())
                out.append("### CANDIDATE ${a.name} — ${a.uploaderName} (${t.size}) ${a.url}\n")
                t.forEach {
                    val k = Matching.songKey(it.name ?: "", game.fullSongTitles)
                    val flag = if (id(it) in ids) "same-id" else if (k in keys) "same-name" else "NEW"
                    out.append("- [$flag] `${id(it)}` ${it.name} — ${it.uploaderName} — ${it.duration}s\n")
                }
            }
            // Single songs too (DLC themes released on their own).
            runCatching { search(q, F.MUSIC_SONGS, pages = 1) }.getOrDefault(emptyList())
                .filterIsInstance<StreamInfoItem>().take(8).forEach {
                    val flag = if (id(it) in ids) "same-id" else if (Matching.songKey(it.name ?: "", game.fullSongTitles) in keys) "same-name" else "NEW"
                    out.append("- song search \"$q\": [$flag] `${id(it)}` ${it.name} — ${it.uploaderName} — ${it.duration}s\n")
                }
        }
        out.append("\n")
    }
    File(System.getenv("PROBE_OUT") ?: "report.md").writeText(out.toString())
}
