package probe

import com.lifestream.player.data.Catalog
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory as F

private val japanese = Regex("[\\u3040-\\u30FF\\u31F0-\\u31FF\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uFF66-\\uFF9F]")

/**
 * The official English half of a bilingual YouTube Music song name, e.g.
 * "ミドガルズオルム (FF7 Rebirth OST Ver.) - Midgardsormr (FF7 Rebirth OST Ver.)" →
 * "Midgardsormr (FF7 Rebirth OST Ver.)". Null if there isn't one.
 */
internal fun englishPart(name: String): String? {
    var i = name.indexOf(" - ")
    while (i >= 0) {
        val left = name.substring(0, i)
        val right = name.substring(i + 3).trim()
        if (japanese.containsMatchIn(left) && right.isNotEmpty() && !japanese.containsMatchIn(right)) {
            // "スノウのテーマ - Complete Mix - - Snow's Theme (Complete Mix)": the English name is
            // after the doubled dash; the version label is already part of it.
            return right.substringAfter(" - - ").removePrefix("- ").trim()
        }
        i = name.indexOf(" - ", i + 1)
    }
    return null
}

/**
 * Builds official_titles.json: for every song the app plays whose album lists it only in
 * Japanese, the official English name Square Enix gives it on YouTube Music. Found by paging
 * through song search for the album, then searching each remaining song directly.
 */
fun runTitles(only: Set<String>) {
    val yt = ServiceList.YouTube
    val map = sortedMapOf<String, String>()
    val report = StringBuilder("# Official English titles\n\n| game | Japanese-only songs | named | missing |\n|---|---|---|---|\n")
    val misses = StringBuilder()
    for (game in Catalog.games.filter { only.isEmpty() || it.id in only }) {
        println("titles ${game.id}")
        val songs = runCatching { gameSongs(game, StringBuilder()) }.getOrDefault(emptyList())
        val wanted = songs.filter { japanese.containsMatchIn(it.title) }.associateBy { it.id }.toMutableMap()
        if (wanted.isEmpty()) continue
        val total = wanted.size
        fun take(items: List<StreamInfoItem>) {
            for (s in items) {
                val id = runCatching { yt.streamLHFactory.getId(s.url) }.getOrNull() ?: continue
                if (id !in wanted) continue
                englishPart(s.name ?: "")?.let { map[id] = it; wanted.remove(id) }
            }
        }
        // 1) Song search for the soundtrack's album titles, many pages.
        val albums = runCatching { sourcesFor(game, StringBuilder()).take(game.maxAlbums) }.getOrDefault(emptyList())
        for (album in albums) {
            if (wanted.isEmpty()) break
            runCatching {
                val handler = yt.searchQHFactory.fromQuery(album.title, listOf(F.MUSIC_SONGS), "")
                val first = SearchInfo.getInfo(yt, handler)
                take(first.relatedItems.filterIsInstance<StreamInfoItem>())
                var next = if (first.hasNextPage()) first.nextPage else null
                var pages = 1
                var dry = 0
                while (next != null && pages < 40 && wanted.isNotEmpty() && dry < 6) {
                    val before = wanted.size
                    val more = SearchInfo.getMoreItems(yt, handler, next)
                    take(more.items.filterIsInstance<StreamInfoItem>())
                    dry = if (wanted.size == before) dry + 1 else 0
                    next = if (more.hasNextPage()) more.nextPage else null
                    pages++
                }
            }
        }
        // 2) Whatever's left: search each song by its Japanese name and the game.
        for (song in wanted.values.toList()) {
            runCatching {
                val handler = yt.searchQHFactory.fromQuery("${song.title} ${game.title}", listOf(F.MUSIC_SONGS), "")
                take(SearchInfo.getInfo(yt, handler).relatedItems.filterIsInstance<StreamInfoItem>())
            }
            Thread.sleep(120)
        }
        report.append("| ${game.id} | $total | ${total - wanted.size} | ${wanted.size} |\n")
        wanted.values.forEach { misses.append("- ${game.id}: `${it.id}` ${it.title}\n") }
    }
    val dir = File(System.getenv("PROBE_OUT") ?: "report.md").absoluteFile.parentFile
    File(dir, "official_titles.json").writeText(JSONObject(map as Map<*, *>).toString(1))
    File(dir, "report.md").writeText("$report\n## Still Japanese\n\n$misses")
    println("named ${map.size}")
}
