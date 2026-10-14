package probe

import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import java.io.File
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory as F

/** Official album covers that could stand in for games whose songs come from fan uploads. */
fun runArt() {
    val out = StringBuilder("# Album covers\n\n")
    val queries = listOf(
        "Crisis Core Final Fantasy VII Reunion original soundtrack",
        "Crisis Core Final Fantasy VII original soundtrack",
        "Crisis Core Reunion",
        "Final Fantasy X-2 original soundtrack",
        "Lost Odyssey original soundtrack",
    )
    for (q in queries) {
        out.append("## $q\n\n")
        runCatching { search(q, F.MUSIC_ALBUMS, pages = 1) }.getOrDefault(emptyList())
            .filterIsInstance<PlaylistInfoItem>().take(8).forEach { a ->
                val img = a.thumbnails.maxByOrNull { it.height }?.url
                out.append("- **${a.name}** — ${a.uploaderName} — ${a.streamCount} — $img\n")
            }
        out.append("\n")
    }
    File(System.getenv("PROBE_OUT") ?: "report.md").absoluteFile.writeText(out.toString())
}
