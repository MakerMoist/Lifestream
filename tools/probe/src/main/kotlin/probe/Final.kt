package probe

import com.lifestream.player.data.Catalog
import com.lifestream.player.data.Matching
import com.lifestream.player.data.tidySongTitle
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import java.io.File

/** Each title's songs as the app lists them (official English names by video id), as TSV. */
fun runFinal(only: Set<String>) {
    val text = File(System.getProperty("user.dir")).resolve("../../app/src/main/assets/official_titles.json").readText()
    val names = HashMap<String, String>()
    Regex("\"([A-Za-z0-9_-]{11})\":\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(text).forEach { m ->
        names[m.groupValues[1]] = unescape(m.groupValues[2])
    }
    val out = StringBuilder()
    for (game in Catalog.games.filter { only.isEmpty() || it.id in only }) {
        println("final ${game.id}")
        val sources = runCatching { sourcesFor(game, StringBuilder()) }.getOrDefault(emptyList())
        val keys = HashSet<String>()
        val ids = HashSet<String>()
        for (album in sources.take(game.maxAlbums)) {
            val items = runCatching { allItems(album.url) }.getOrDefault(emptyList())
            if (items.size < 4 && sources.size > 1) continue
            for ((raw, id) in items) {
                if (album.fromPlaylist && (raw.equals("Private video", true) || raw.equals("Deleted video", true))) continue
                val official = names[id]?.let(::tidySongTitle)
                val name = official ?: if (album.fromPlaylist) Matching.cleanTrackTitle(raw, game) else tidySongTitle(raw)
                if (album.fromPlaylist && Matching.isFillerTrack(name)) continue
                if (id in game.skipIds || !ids.add(id)) continue
                if (!keys.add(Matching.songKey(name, game.fullSongTitles))) continue
                out.append(game.id).append('\t').append(id).append('\t').append(name).append('\n')
            }
        }
        for (extra in game.extras) {
            val p = extra.pinnedTrack() ?: continue
            if (ids.add(p.id) && keys.add(Matching.songKey(p.title))) out.append(game.id).append('\t').append(p.id).append('\t').append(p.title).append(" [extra]\n")
        }
    }
    val target = File(System.getenv("PROBE_OUT") ?: "report.md").absoluteFile
    target.writeText(out.toString())
    println("wrote $target")
}

private fun allItems(url: String): List<Pair<String, String>> {
    val yt = ServiceList.YouTube
    fun idOf(u: String) = runCatching { yt.streamLHFactory.getId(u) }.getOrDefault("")
    val info = PlaylistInfo.getInfo(yt, url)
    val items = info.relatedItems.map { (it.name ?: "") to idOf(it.url) }.toMutableList()
    var next = if (info.hasNextPage()) info.nextPage else null
    var pages = 0
    while (next != null && pages < 30) {
        val more = PlaylistInfo.getMoreItems(yt, url, next)
        items += more.items.map { (it.name ?: "") to idOf(it.url) }
        next = if (more.hasNextPage()) more.nextPage else null
        pages++
    }
    return items
}

private fun unescape(s: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length) {
            val n = s[i + 1]
            when (n) {
                'u' -> { out.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 6; continue }
                'n' -> out.append('\n')
                't' -> out.append('\t')
                else -> out.append(n)
            }
            i += 2
        } else { out.append(c); i++ }
    }
    return out.toString()
}
