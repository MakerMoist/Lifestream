package probe

import com.lifestream.player.data.Catalog
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File

private val japaneseText = Regex("[\\u3040-\\u30FF\\u31F0-\\u31FF\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uFF66-\\uFF9F]")

/**
 * Every song of every title, album by album in track order, with where its name comes from:
 * the album's own English name, Square Enix's official English name (official_titles.json), or
 * neither (machine translated on the phone). Written to names.tsv next to the report.
 */
fun runNames(only: Set<String>) {
    val report = File(System.getenv("PROBE_OUT") ?: "report.md").absoluteFile
    val official = listOf("../../app/src/main/assets/official_titles.json", "app/src/main/assets/official_titles.json")
        .map(::File).firstOrNull { it.exists() }
        ?.let { JSONObject(it.readText()) }
    val tsv = StringBuilder("game\talbum\tdisc_track\tid\traw\tofficial\tsource\n")
    val summary = StringBuilder("# Song names\n\nofficial_titles.json: ${official?.length() ?: "NOT FOUND"}\n\n| game | album songs | English on album | official name | machine translated |\n|---|---|---|---|---|\n")
    for (game in Catalog.games.filter { only.isEmpty() || it.id in only }) {
        println("names ${game.id}")
        val albums = runCatching { sourcesFor(game, StringBuilder()).take(game.maxAlbums) }.getOrDefault(emptyList())
        var total = 0; var english = 0; var named = 0; var mt = 0
        for ((a, album) in albums.withIndex()) {
            val items = runCatching { albumItems(album.url) }.getOrDefault(emptyList())
            for ((i, s) in items.withIndex()) {
                val id = runCatching { ServiceList.YouTube.streamLHFactory.getId(s.url) }.getOrDefault("?")
                val raw = (s.name ?: "").replace('\t', ' ')
                val off = official?.optString(id, "")?.takeIf { it.isNotBlank() }
                val source = when {
                    !japaneseText.containsMatchIn(raw) -> "ENGLISH"
                    off != null -> "OFFICIAL"
                    else -> "MT"
                }
                total++
                when (source) { "ENGLISH" -> english++; "OFFICIAL" -> named++; else -> mt++ }
                tsv.append("${game.id}\t${a + 1}:${album.title}\t${i + 1}\t$id\t$raw\t${off ?: ""}\t$source\n")
            }
        }
        summary.append("| ${game.id} | $total | $english | $named | $mt |\n")
    }
    File(report.parentFile, "names.tsv").writeText(tsv.toString())
    report.writeText(summary.toString())
}

private fun albumItems(url: String): List<StreamInfoItem> {
    val yt = ServiceList.YouTube
    val info = PlaylistInfo.getInfo(yt, url)
    val rows = info.relatedItems.toMutableList()
    var next = if (info.hasNextPage()) info.nextPage else null
    var pages = 0
    while (next != null && pages < 30) {
        val more = PlaylistInfo.getMoreItems(yt, url, next)
        rows += more.items
        next = if (more.hasNextPage()) more.nextPage else null
        pages++
    }
    return rows
}
