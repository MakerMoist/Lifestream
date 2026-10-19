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
fun runVersions(withTitles: Boolean = true) {
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
    if (withTitles) runTitles(emptySet()) else File(dir, "report.md").writeText("see versions.md and albums.md\n")
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

/**
 * `--lo`: where a sharp Lost Odyssey cover could come from: album results (with their cover at
 * full size, saved next to the report), and how big each song's own video artwork really is.
 */
fun runLostOdyssey() {
    val report = File(System.getenv("PROBE_OUT") ?: "report.md").absoluteFile
    val img = File(report.parentFile, "img").apply { mkdirs() }
    val out = StringBuilder("# Lost Odyssey artwork\n\n")
    fun save(name: String, url: String): String = runCatching {
        val bytes = java.net.URI(url).toURL().openStream().use { it.readBytes() }
        File(img, name).writeBytes(bytes)
        val i = javax.imageio.ImageIO.read(bytes.inputStream())
        "${i?.width}x${i?.height}"
    }.getOrElse { "failed (${it.javaClass.simpleName})" }
    var n = 0
    for (q in listOf("Lost Odyssey original soundtrack", "Lost Odyssey", "ロストオデッセイ オリジナル・サウンドトラック", "Lost Odyssey Nobuo Uematsu")) {
        out.append("## Albums: $q\n\n")
        runCatching { search(q, F.MUSIC_ALBUMS, pages = 1) }.getOrDefault(emptyList()).filterIsInstance<PlaylistInfoItem>().take(10).forEach { a ->
            val cover = a.thumbnails.maxByOrNull { it.height }?.url
            val big = cover?.replace(Regex("=(w\\d+-h\\d+|s\\d+)([-a-z0-9]*)$"), "=w1200-h1200-l90-rj")
            val size = if (big != null && (a.name ?: "").uppercase().contains("LOST ODYSSEY")) save("album${n++}.jpg", big) else ""
            out.append("- ${a.name} — ${a.uploaderName} — ${a.streamCount} — ${a.url} — cover $cover $size\n")
        }
        out.append("\n")
    }
    // Plain videos whose full-size (1280x720) frame may show the soundtrack's cover sharply.
    out.append("## HD videos\n\n")
    val vids = LinkedHashMap<String, String>()
    for (q in listOf("Lost Odyssey original soundtrack", "Lost Odyssey OST full", "Lost Odyssey soundtrack Uematsu", "Lost Odyssey OST")) {
        runCatching { search(q, F.VIDEOS, pages = 1) }.getOrDefault(emptyList())
            .filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
            .forEach { s ->
                val id = runCatching { ServiceList.YouTube.streamLHFactory.getId(s.url) }.getOrNull() ?: return@forEach
                vids.putIfAbsent(id, "${s.name} — ${s.uploaderName} — ${s.duration}s")
            }
    }
    for ((id, label) in vids.entries.take(40)) {
        out.append("- `$id` $label — maxres ${save("v-$id.jpg", "https://i.ytimg.com/vi/$id/maxresdefault.jpg")}\n")
    }
    out.append("\n")
    val game = Catalog.game("lo")!!
    val src = sourcesFor(game, StringBuilder())
    out.append("## Source now\n\n")
    src.forEach { out.append("- ${it.title} — ${it.artist} — ${it.trackCount} — ${it.url}\n") }
    src.firstOrNull()?.let { a ->
        val ids = tracks(a.url).take(12).map { it.id }
        for (id in ids) {
            val sizes = listOf("maxresdefault", "sddefault", "hqdefault").joinToString(", ") { k ->
                "$k ${save("$id-$k.jpg", "https://i.ytimg.com/vi/$id/$k.jpg")}"
            }
            out.append("- `$id`: $sizes\n")
        }
    }
    report.writeText(out.toString())
}
