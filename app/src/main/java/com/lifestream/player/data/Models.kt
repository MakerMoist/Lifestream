package com.lifestream.player.data

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import org.json.JSONObject

private val uploadTag = Regex(
    "\\s*[\\[(（【]\\s*(official(\\s+(audio|video|music\\s+video|lyric\\s+video|visualizer|soundtrack\\s+audio))?|basiscape)\\s*[\\])）】]",
    RegexOption.IGNORE_CASE,
)

/**
 * Strips upload tags from song and artist names: "[Official]", "(Official Audio)", "(Basiscape)"
 * and the like, in any bracket style, wherever they appear.
 */
private val gameTag = Regex(
    "^\\s*FFVII REMAKE:\\s*|\\s*[(（](FF7 Rebirth OST Ver\\.?|FF7R Ver\\.?|FF7 Rebirth Ver\\.?)[)）]",
    RegexOption.IGNORE_CASE,
)

fun tidySongTitle(title: String): String {
    val cleaned = title.replace(uploadTag, "").replace(gameTag, "").replace(Regex("\\s{2,}"), " ").trim().trimEnd('-', '–', '|', ' ')
    return cleaned.ifBlank { title }
}

private val sizedImage = Regex("=(w\\d+-h\\d+|s\\d+)([-a-z0-9]*)$")
private val ytThumb = Regex("^(https://i\\d?\\.ytimg\\.com/vi(?:_webp)?/[A-Za-z0-9_-]{11}/)(?:default|mqdefault|hqdefault|sddefault|hq720)(?:_live)?\\.(?:jpg|webp)(?:\\?.*)?$")

/**
 * The full-size version of a song's artwork. YouTube hands out small thumbnails (often 60–120 px),
 * which look blurry on the big Now Playing art; album art on Google's image servers takes its size
 * in the URL, and video thumbnails have a full-resolution variant. Returns null when there's no
 * bigger version to ask for.
 */
fun hiResArtwork(url: String?, size: Int = 1200, videoThumbs: Boolean = true): String? {
    if (url == null) return null
    if (sizedImage.containsMatchIn(url)) return url.replace(sizedImage, "=w$size-h$size-l90-rj")
    if (videoThumbs) ytThumb.find(url)?.let { return it.groupValues[1] + "maxresdefault.jpg" }
    return null
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Long,
    val thumbnail: String?,
    /** Catalog id of the soundtrack this song came from (for "Open title"). */
    val gameId: String? = null,
    /** The original Japanese name when [title] is its English name, so either can be searched. */
    val altTitle: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("artist", artist)
        .put("duration", durationSec)
        .put("thumb", thumbnail ?: "")
        .put("game", gameId ?: "")
        .put("alt", altTitle ?: "")

    /** A link anyone can open, for sharing. */
    val youTubeUrl: String get() = "https://www.youtube.com/watch?v=$id"

    companion object {
        /** YouTube video ids are exactly 11 characters of [A-Za-z0-9_-]. */
        private val videoId = Regex("^[A-Za-z0-9_-]{11}$")
        private const val MAX_TEXT = 300

        /** Reads a stored song, or null if the entry isn't a valid one (e.g. a corrupted file). */
        fun fromJson(o: JSONObject): Track? {
            val id = o.optString("id")
            if (!videoId.matches(id)) return null
            return Track(
                id = id,
                title = tidySongTitle(o.optString("title")).take(MAX_TEXT).ifBlank { "Unknown track" },
                artist = tidySongTitle(o.optString("artist")).take(MAX_TEXT),
                durationSec = o.optLong("duration", -1).coerceIn(-1L, 24L * 3600),
                thumbnail = o.optString("thumb").takeIf { it.startsWith("https://") && it.length <= 2048 },
                gameId = o.optString("game").ifBlank { null }?.take(32),
                altTitle = o.optString("alt").ifBlank { null }?.take(MAX_TEXT),
            )
        }
    }
}

data class Album(
    val url: String,
    val title: String,
    val artist: String,
    val trackCount: Long,
    val thumbnail: String?,
    /** True when this is a fan-uploaded full-soundtrack playlist used because no official album exists. */
    val fromPlaylist: Boolean = false,
)

data class Game(
    val id: String,
    val numeral: String,
    val title: String,
    val year: Int,
    val composer: String,
    val query: String,
    val colorA: Color,
    val colorB: Color,
    val series: Series = Series.MAINLINE,
    /** Which album titles count as this game's music. */
    val match: MatchSpec = MatchSpec(),
    /** Original card illustration (tools/art renders these). */
    @DrawableRes val art: Int = 0,
    /** Songs from the game that aren't on its soundtrack album (e.g. a localized theme song). */
    val extras: List<ExtraSong> = emptyList(),
    /** More album searches for games whose music is split across many official albums. */
    val albumQueries: List<String> = emptyList(),
    /** How many official albums the game's list plays through. */
    val maxAlbums: Int = 8,
    /**
     * Official album cover used for every song, for games whose songs come from a fan upload
     * (whose video thumbnails aren't the album art).
     */
    val coverArt: String? = null,
)

/**
 * A specific recording to add to a game's list: either pinned to a YouTube id (checked ahead of
 * time against the official soundtrack release) or found at runtime by exact title + artist.
 */
data class ExtraSong(
    val title: String,
    val artist: String,
    val videoId: String? = null,
    val durationSec: Long = 0,
) {
    val query: String get() = "$artist $title"

    /** The song as a track when it's pinned to a known recording. */
    fun pinnedTrack(): Track? = videoId?.let {
        Track(it, title, artist, durationSec, "https://i.ytimg.com/vi/$it/hqdefault.jpg")
    }
}

enum class Series(val label: String) {
    MAINLINE("Main series"),
    SPINOFF("Spin-offs & more"),
}

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
