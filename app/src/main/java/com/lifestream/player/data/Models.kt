package com.lifestream.player.data

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import org.json.JSONObject

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Long,
    val thumbnail: String?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("artist", artist)
        .put("duration", durationSec)
        .put("thumb", thumbnail ?: "")

    companion object {
        fun fromJson(o: JSONObject) = Track(
            id = o.getString("id"),
            title = o.optString("title"),
            artist = o.optString("artist"),
            durationSec = o.optLong("duration", -1),
            thumbnail = o.optString("thumb").ifBlank { null },
        )
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
)

/** A specific recording to add to a game's list, found by exact title + artist. */
data class ExtraSong(val title: String, val artist: String) {
    val query: String get() = "$artist $title"
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
