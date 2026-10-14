package probe

import com.lifestream.player.data.Catalog
import com.lifestream.player.data.Matching
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import java.io.File
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory as F

/** Every YouTube Music album that names each game, with whether the app counts it as official. */
fun runAlbums(only: Set<String>) {
    val out = StringBuilder("# Albums per game\n\n")
    for (game in Catalog.games.filter { only.isEmpty() || it.id in only }) {
        println("albums ${game.id}")
        out.append("## ${game.title} (`${game.id}`)\n\n")
        val used = runCatching { sourcesFor(game, StringBuilder()).take(game.maxAlbums).map { it.url }.toSet() }
            .getOrDefault(emptySet())
        val queries = listOf(
            game.query, "${game.title} soundtrack", "${game.title} original soundtrack plus",
            "${game.title} original soundtrack vol 2", "${game.title} original soundtrack DLC",
        ) + game.albumQueries
        val seen = LinkedHashMap<String, com.lifestream.player.data.Album>()
        for (q in queries) {
            runCatching { search(q, F.MUSIC_ALBUMS, pages = 2) }.getOrDefault(emptyList())
                .mapNotNull { (it as? PlaylistInfoItem)?.toAlbum() }
                .filter { Matching.matches(game.match, it.title) }
                .forEach { seen.putIfAbsent(it.url, it) }
        }
        for (a in seen.values) {
            val flag = when {
                a.url in used -> "USED"
                Matching.isOfficialSoundtrack(a) -> "official-not-used"
                else -> "ignored"
            }
            out.append("- [$flag] ${a.title} — ${a.artist} — ${a.trackCount}\n")
        }
        out.append("\n")
    }
    val target = File(System.getenv("PROBE_OUT") ?: "report.md").absoluteFile
    target.writeText(out.toString())
    println("wrote $target")
}
