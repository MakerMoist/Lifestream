package probe

import com.lifestream.player.data.Catalog
import com.lifestream.player.data.Game
import com.lifestream.player.data.Matching
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory as F

private data class Song(val id: String, val title: String, val artist: String, val sec: Long)

/**
 * Checks each game's soundtrack against a song list (data/tfbl.tsv: Theatrhythm Final Bar Line)
 * and, for songs that aren't in what the game plays, lists the best search candidates.
 */
fun runChecklist(only: Set<String>) {
    val file = listOf("data/tfbl.tsv", "tools/probe/data/tfbl.tsv").map(::File).first { it.exists() }
    val wanted = file.readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.split('\t', limit = 2) }
        .groupBy({ it[0] }, { it[1].trim() })

    val out = StringBuilder("# Theatrhythm checklist\n\n")
    val summary = StringBuilder("| game | songs checked | found | missing |\n|---|---|---|---|\n")
    val machine = StringBuilder() // game \t wanted \t status \t id \t title \t artist \t sec

    for ((gameId, titles) in wanted) {
        if (only.isNotEmpty() && gameId !in only) continue
        val game = Catalog.game(gameId) ?: continue
        println("checking $gameId")
        out.append("## ${game.title} (`$gameId`)\n\n")
        val songs = try {
            gameSongs(game, out)
        } catch (e: Exception) {
            out.append("- FAILED loading soundtrack: ${e.message}\n\n")
            continue
        }
        val ids = songs.map { it.id }.toSet()
        out.append("<details><summary>${songs.size} songs now</summary>\n\n")
        songs.forEachIndexed { i, s -> out.append("${i + 1}. ${s.title} (${s.sec}s)\n") }
        out.append("\n</details>\n\n| wanted | status | detail |\n|---|---|---|\n")

        var found = 0
        val missingDetail = StringBuilder()
        for (title in titles.distinct()) {
            val byTitle = songs.firstOrNull { Matching.searchScore(title, it.title) != null }
            if (byTitle != null) {
                found++
                out.append("| $title | ok (title) | ${byTitle.title} |\n")
                machine.append("$gameId\t$title\tok\t${byTitle.id}\t${byTitle.title}\t\t${byTitle.sec}\n")
                continue
            }
            val candidates = LinkedHashMap<String, Song>()
            for (q in listOf("${game.title} $title", "$title ${game.title} soundtrack")) {
                runCatching { songSearch(q) }.getOrDefault(emptyList()).forEach { candidates.putIfAbsent(it.id, it) }
                if (candidates.values.any { it.id in ids }) break
            }
            val hit = candidates.values.firstOrNull { it.id in ids }
            if (hit != null) {
                found++
                out.append("| $title | ok (id) | ${hit.title} |\n")
                machine.append("$gameId\t$title\tok\t${hit.id}\t${hit.title}\t${hit.artist}\t${hit.sec}\n")
                continue
            }
            out.append("| $title | **MISSING** | see below |\n")
            missingDetail.append("- **$title**\n")
            candidates.values.take(8).forEach { c ->
                val official = if (Matching.isOfficialArtist(c.artist)) "official" else ""
                missingDetail.append("    - `${c.id}` ${c.title} — ${c.artist} — ${c.sec}s $official\n")
                machine.append("$gameId\t$title\tcand\t${c.id}\t${c.title}\t${c.artist}\t${c.sec}\n")
            }
            if (candidates.isEmpty()) machine.append("$gameId\t$title\tnone\t\t\t\t\n")
        }
        val missing = titles.distinct().size - found
        out.append("\n$missingDetail\n")
        summary.append("| $gameId | ${titles.distinct().size} | $found | $missing |\n")
    }

    val target = File(System.getenv("PROBE_OUT") ?: "report.md").absoluteFile
    target.writeText("## Summary\n\n$summary\n$out")
    File(target.parentFile, "checklist.tsv").writeText(machine.toString())
    println("wrote $target")
}

/** What the app plays for [game]: its soundtrack album(s) (same rules as the app) plus extras. */
private fun gameSongs(game: Game, log: StringBuilder): List<Song> {
    val sources = sourcesFor(game, StringBuilder())
    val songs = LinkedHashMap<String, Song>()
    for (album in sources.take(8)) {
        val items = runCatching { allItems(album.url) }.getOrDefault(emptyList())
        if (items.size < 4 && sources.size > 1) continue
        log.append("- album: ${album.title} — ${album.artist} — ${items.size}${if (album.fromPlaylist) " (playlist)" else ""}\n")
        for (item in items) {
            val raw = item.title
            if (album.fromPlaylist && (raw.equals("Private video", true) || raw.equals("Deleted video", true))) continue
            val title = if (album.fromPlaylist) Matching.cleanTrackTitle(raw, game) else raw
            if (album.fromPlaylist && Matching.isFillerTrack(title)) continue
            songs.putIfAbsent(item.id, item.copy(title = title))
        }
    }
    log.append("\n")
    return songs.values.toList()
}

private fun allItems(url: String): List<Song> {
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
    return items.mapNotNull { it.toSong() }
}

private fun songSearch(query: String): List<Song> =
    search(query, F.MUSIC_SONGS, pages = 1).mapNotNull { (it as? StreamInfoItem)?.toSong() }.take(8)

private fun StreamInfoItem.toSong(): Song? {
    val id = runCatching { ServiceList.YouTube.streamLHFactory.getId(url) }.getOrNull() ?: return null
    return Song(id, name ?: "", (uploaderName ?: "").removeSuffix(" - Topic").trim(), duration)
}
