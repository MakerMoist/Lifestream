package probe

import com.lifestream.player.data.Catalog
import com.lifestream.player.data.Matching
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import java.io.File
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory as F

/** Expansion, add-on and DLC album names to look for as well. */
private val expansionQueries = mapOf(
    "ff11" to listOf(
        "Final Fantasy XI Rise of the Zilart soundtrack", "Final Fantasy XI Chains of Promathia soundtrack",
        "Final Fantasy XI Treasures of Aht Urhgan soundtrack", "Final Fantasy XI Wings of the Goddess soundtrack",
        "Final Fantasy XI Seekers of Adoulin soundtrack", "Final Fantasy XI Rhapsodies of Vana'diel soundtrack",
        "Final Fantasy XI Vana'diel soundtrack", "Final Fantasy XI original soundtrack premium box",
        "Final Fantasy XI Online soundtrack", "FINAL FANTASY XI Naoshi Mizuta", "Final Fantasy XI add-on scenarios soundtrack",
    ),
    "ff14" to listOf(
        "A Realm Reborn Final Fantasy XIV soundtrack", "Before Meteor Final Fantasy XIV", "Before the Fall Final Fantasy XIV",
        "Heavensward Final Fantasy XIV", "The Far Edge of Fate Final Fantasy XIV", "Stormblood Final Fantasy XIV",
        "Shadowbringers Final Fantasy XIV", "Death Unto Dawn Final Fantasy XIV", "Endwalker Final Fantasy XIV",
        "Growing Light Final Fantasy XIV", "Dawntrail Final Fantasy XIV", "Final Fantasy XIV patch soundtrack",
        "FINAL FANTASY XIV Masayoshi Soken", "Final Fantasy XIV 7.x soundtrack", "Final Fantasy XIV arrangement album",
    ),
    "ff15" to listOf(
        "Final Fantasy XV Episode Gladiolus", "Final Fantasy XV Episode Prompto", "Final Fantasy XV Episode Ignis",
        "Final Fantasy XV Episode Ardyn", "Final Fantasy XV Comrades soundtrack", "Final Fantasy XV DLC soundtrack",
        "Final Fantasy XV Royal Edition soundtrack", "Final Fantasy XV original soundtrack volume 2",
        "Final Fantasy XV Yoko Shimomura", "Final Fantasy XV Pocket Edition soundtrack",
    ),
)

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
            "${game.title} remaster soundtrack",
        ) + game.albumQueries + (expansionQueries[game.id] ?: emptyList())
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
