package probe

import com.lifestream.player.data.Catalog
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory as F

/** Where can official English song names be read? Compares a few sources for one album. */
fun runTitles(only: Set<String>) {
    val out = StringBuilder("# Title sources\n\n")
    val game = Catalog.game(only.firstOrNull() ?: "ff7rb")!!
    val album = sourcesFor(game, StringBuilder()).first()
    out.append("album: ${album.title} ${album.url}\n\n")
    val yt = ServiceList.YouTube
    for ((lang, country) in listOf("en" to "US", "en" to "GB", "ja" to "JP")) {
        NewPipe.setupLocalization(Localization(lang, country), ContentCountry(country))
        val info = runCatching { PlaylistInfo.getInfo(yt, album.url) }.getOrNull()
        out.append("### playlist $lang-$country\n")
        info?.relatedItems?.take(5)?.forEach { out.append("- ${it.name}\n") }
    }
    NewPipe.setupLocalization(Localization("en", "US"), ContentCountry("US"))
    val first = PlaylistInfo.getInfo(yt, album.url).relatedItems.take(3)
    out.append("\n### stream pages\n")
    first.forEach { item ->
        val s = runCatching { StreamInfo.getInfo(yt, item.url) }.getOrNull()
        out.append("- ${item.name} → ${s?.name}\n")
    }
    out.append("\n### song search by album name (pages)\n")
    val albumIds = mutableSetOf<String>()
    var p = PlaylistInfo.getInfo(yt, album.url)
    albumIds += p.relatedItems.mapNotNull { runCatching { yt.streamLHFactory.getId(it.url) }.getOrNull() }
    var next = if (p.hasNextPage()) p.nextPage else null
    while (next != null) {
        val more = PlaylistInfo.getMoreItems(yt, album.url, next)
        albumIds += more.items.mapNotNull { runCatching { yt.streamLHFactory.getId(it.url) }.getOrNull() }
        next = if (more.hasNextPage()) more.nextPage else null
    }
    val found = LinkedHashMap<String, String>()
    val handler = yt.searchQHFactory.fromQuery(album.title, listOf(F.MUSIC_SONGS), "")
    var info = org.schabi.newpipe.extractor.search.SearchInfo.getInfo(yt, handler)
    var items = info.relatedItems
    var pg = if (info.hasNextPage()) info.nextPage else null
    var pages = 1
    while (true) {
        items.filterIsInstance<StreamInfoItem>().forEach { s ->
            val id = runCatching { yt.streamLHFactory.getId(s.url) }.getOrNull() ?: return@forEach
            found.putIfAbsent(id, s.name)
        }
        if (pg == null || pages >= 15) break
        val more = org.schabi.newpipe.extractor.search.SearchInfo.getMoreItems(yt, handler, pg)
        items = more.items
        pg = if (more.hasNextPage()) more.nextPage else null
        pages++
    }
    out.append("album ids: ${albumIds.size}; search pages: $pages; found: ${found.size}; in album: ${found.keys.count { it in albumIds }}\n")
    found.entries.take(8).forEach { out.append("- ${it.key} ${it.value}\n") }
    val target = File(System.getenv("PROBE_OUT") ?: "report.md").absoluteFile
    target.writeText(out.toString())
}
