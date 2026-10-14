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
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory as F
import java.io.File

/**
 * For every game in the catalog, shows what YouTube Music actually returns and what the app's
 * rules make of it: official albums picked, every album result with match flags, and the best
 * full-soundtrack playlists (the candidate "second source" for gaps).
 */
fun main(args: Array<String>) {
    NewPipe.init(OkHttpDownloader(Http.client), Localization("en", "US"), ContentCountry("US"))
    val only = args.toSet()
    val out = StringBuilder("# Soundtrack probe\n\n")
    val summary = StringBuilder("## Summary\n\n| game | official albums | official tracks | best playlist |\n|---|---|---|---|\n")

    for (game in Catalog.games.filter { only.isEmpty() || it.id in only }) {
        println("probing ${game.id}")
        out.append("## ${game.title} (`${game.id}`)\n\n")
        val albums = LinkedHashMap<String, Album>()
        for (q in listOf(game.query, "${game.title} soundtrack")) {
            try {
                search(q, F.MUSIC_ALBUMS, pages = 2).mapNotNull { (it as? PlaylistInfoItem)?.toAlbum() }
                    .forEach { albums.putIfAbsent(it.url, it) }
            } catch (e: Exception) {
                out.append("- album search failed for \"$q\": ${e.javaClass.simpleName}: ${e.message}\n")
            }
        }
        val official = albums.values
            .filter { Matching.matches(game.match, it.title) && Matching.isOfficialSoundtrack(it) }
            .sortedByDescending { Matching.score(it, game) }

        var officialTracks = 0
        out.append("**Official picked (${official.size})**\n\n")
        for (a in official) {
            val n = runCatching { PlaylistInfo.getInfo(ServiceList.YouTube, a.url).streamCount }.getOrDefault(-1L)
            officialTracks += n.coerceAtLeast(0).toInt()
            out.append("- ${a.title} — *${a.artist}* — ${a.trackCount} listed / $n loaded — score ${Matching.score(a, game)}\n")
        }
        out.append("\n**All album results**\n\n")
        for (a in albums.values) {
            val m = if (Matching.matches(game.match, a.title)) "M" else "-"
            val o = if (Matching.isOfficialSoundtrack(a)) "O" else "-"
            out.append("- `$m$o` ${a.title} — *${a.artist}* — ${a.trackCount}\n")
        }

        out.append("\n**Playlist candidates**\n\n")
        val playlists = LinkedHashMap<String, Album>()
        for (q in listOf("${game.title} OST", "${game.title} original soundtrack full")) {
            try {
                search(q, F.PLAYLISTS, pages = 1).mapNotNull { (it as? PlaylistInfoItem)?.toAlbum() }
                    .forEach { playlists.putIfAbsent(it.url, it) }
            } catch (e: Exception) {
                out.append("- playlist search failed for \"$q\": ${e.javaClass.simpleName}: ${e.message}\n")
            }
        }
        val ranked = playlists.values.sortedByDescending { if (Matching.matches(game.match, it.title)) 1 else 0 }
        for (p in ranked.take(12)) {
            val m = if (Matching.matches(game.match, p.title)) "M" else "-"
            out.append("- `$m` ${p.title} — *${p.artist}* — ${p.trackCount} — ${p.url}\n")
        }
        out.append("\n")
        val best = ranked.firstOrNull { Matching.matches(game.match, it.title) }
        summary.append("| ${game.id} | ${official.size} | $officialTracks | ${best?.let { "${it.title} (${it.trackCount})" } ?: "—"} |\n")
    }

    val target = File(System.getenv("PROBE_OUT") ?: "report.md").absoluteFile
    target.writeText(summary.toString() + "\n" + out.toString())
    println("wrote $target")
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

@Suppress("unused")
private fun Game.label() = "$title ($id)"
