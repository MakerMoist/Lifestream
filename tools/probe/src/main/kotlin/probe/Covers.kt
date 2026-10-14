package probe

import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory as F

/** Official album/song covers YouTube Music shows for Crisis Core (for its songs' artwork). */
fun runCovers() {
    val out = StringBuilder("# Crisis Core covers\n\n")
    val queries = listOf(
        "Crisis Core Final Fantasy VII Reunion", "CRISIS CORE FINAL FANTASY VII REUNION soundtrack",
        "Crisis Core Final Fantasy VII original soundtrack", "Takeharu Ishimoto Crisis Core",
    )
    for (q in queries) {
        out.append("## $q\n### albums\n")
        runCatching { search(q, F.MUSIC_ALBUMS, pages = 1) }.getOrDefault(emptyList())
            .filterIsInstance<PlaylistInfoItem>().take(8).forEach { a ->
                out.append("- ${a.name} — ${a.uploaderName} — ${a.streamCount} — ${a.thumbnails.maxByOrNull { it.height }?.url}\n")
            }
        out.append("### songs\n")
        runCatching { search(q, F.MUSIC_SONGS, pages = 1) }.getOrDefault(emptyList())
            .filterIsInstance<StreamInfoItem>().take(8).forEach { s ->
                out.append("- ${s.name} — ${s.uploaderName} — ${s.thumbnails.maxByOrNull { it.height }?.url}\n")
            }
    }
    File(System.getenv("PROBE_OUT") ?: "report.md").writeText(out.toString())
}
