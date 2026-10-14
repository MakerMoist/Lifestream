package probe

import com.lifestream.player.data.Catalog
import java.io.File

/** Lists every artist name each game's songs carry (with counts and examples), for review. */
fun runArtists(only: Set<String>) {
    val out = StringBuilder("# Artists per game\n\n")
    for (game in Catalog.games.filter { only.isEmpty() || it.id in only }) {
        println("artists ${game.id}")
        out.append("## ${game.title} (`${game.id}`) — composer: ${game.composer}\n\n")
        val songs = try {
            gameSongs(game, out)
        } catch (e: Exception) {
            out.append("- FAILED: ${e.message}\n\n")
            continue
        }
        songs.groupBy { com.lifestream.player.data.ArtistCredits.forSong(game.id, it.artist) }.entries.sortedByDescending { it.value.size }.forEach { (artist, list) ->
            val examples = list.take(4).joinToString(" | ") { it.title }
            out.append("- **${artist.ifBlank { "(blank)" }}** × ${list.size} — $examples\n")
        }
        out.append("\n")
    }
    val target = File(System.getenv("PROBE_OUT") ?: "report.md").absoluteFile
    target.writeText(out.toString())
    println("wrote $target")
}
