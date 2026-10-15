package probe

import com.lifestream.player.data.Catalog
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import javax.imageio.ImageIO
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory as F

/**
 * For every title: do its songs have full-size artwork on YouTube, and what is the official album
 * cover (YouTube Music's own image, which takes its size in the URL)? Covers of titles whose songs
 * lack full-size art are saved next to the report to look at.
 */
fun runThumbs(only: Set<String>) {
    val report = File(System.getenv("PROBE_OUT") ?: "report.md")
    val dir = File(report.parentFile ?: File("."), "img").apply { mkdirs() }
    val out = StringBuilder("# Song artwork\n\n| title | songs with full-size art | album | cover |\n|---|---|---|---|\n")
    fun exists(url: String): Boolean = runCatching {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.requestMethod = "HEAD"
        c.responseCode == 200
    }.getOrDefault(false)
    fun save(name: String, url: String): String = runCatching {
        val bytes = URI(url).toURL().openStream().use { it.readBytes() }
        File(dir, name).writeBytes(bytes)
        val img = ImageIO.read(bytes.inputStream())
        "${img?.width}x${img?.height}"
    }.getOrElse { "failed" }
    for (game in Catalog.games.filter { only.isEmpty() || it.id in only }) {
        println("thumbs ${game.id}")
        val sources = runCatching { sourcesFor(game, StringBuilder()) }.getOrElse {
            out.append("| ${game.id} | error ${it.message} | | |\n"); continue
        }
        val album = sources.firstOrNull() ?: run { out.append("| ${game.id} | no source | | |\n"); continue }
        val info = runCatching { PlaylistInfo.getInfo(ServiceList.YouTube, album.url) }.getOrNull()
        val ids = info?.relatedItems?.take(4)?.map { it.url.substringAfter("v=").take(11) }.orEmpty()
        val full = ids.count { exists("https://i.ytimg.com/vi/$it/maxresdefault.jpg") }
        // The album's own cover from YouTube Music's album search.
        var cover: String? = null
        for (q in listOf(game.query, "${game.title} soundtrack") + game.albumQueries) {
            cover = runCatching { search(q, F.MUSIC_ALBUMS, pages = 1) }.getOrDefault(emptyList())
                .filterIsInstance<PlaylistInfoItem>().firstOrNull { it.url == album.url }
                ?.thumbnails?.maxByOrNull { it.height }?.url
            if (cover != null) break
        }
        var shown = ""
        if (full < ids.size && cover != null) {
            val big = cover.replace(Regex("=(w\\d+-h\\d+|s\\d+)([-a-z0-9]*)$"), "=w1200-h1200-l90-rj")
            shown = "img/${game.id}-cover.jpg ${save("${game.id}-cover.jpg", big)}"
        }
        out.append("| ${game.id} | $full of ${ids.size} | ${album.title}${if (album.fromPlaylist) " (playlist)" else ""} | ${cover ?: "none"} $shown |\n")
    }
    report.writeText(out.toString())
}
