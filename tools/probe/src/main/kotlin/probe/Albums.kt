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
            "${game.title} special edition soundtrack", "${game.title} ultimate edition soundtrack",
            "${game.title} international soundtrack", "${game.title} complete soundtrack",
            "${game.title} expanded soundtrack", "${game.title} deluxe soundtrack",
            "${game.title} remaster soundtrack", "${game.title} pixel remaster soundtrack",
            "${game.title} HD remaster soundtrack", "${game.title} original sound version",
            "${game.title} expansion soundtrack", "${game.title} remastered soundtrack",
            "${game.query} plus", "${game.title} piano collections",
            "Kingsglaive ${game.title} original soundtrack", "Brotherhood ${game.title} soundtrack",
            "${game.title} episode gladiolus soundtrack", "${game.title} episode prompto soundtrack",
            "${game.title} episode ignis soundtrack", "${game.title} episode ardyn soundtrack",
            "${game.title} comrades soundtrack", "${game.title} royal edition soundtrack",
            "Songs from ${game.title}", "${game.title} original soundtrack volume 2",
            "${game.title} windows edition soundtrack", "${game.title} pocket edition soundtrack",
        ) + game.albumQueries + game.albumQueries.map { "$it plus" }
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
