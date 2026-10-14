package com.lifestream.player.data

import android.content.Context
import org.json.JSONObject

/**
 * Official English song names, from Square Enix's own bilingual titles on YouTube Music
 * (e.g. "ミドガルズオルム" → "Midgardsormr"). Many official albums list their tracks only in
 * Japanese; these names are collected ahead of time by the probe (tools/probe, --titles) and
 * shipped in assets/official_titles.json, so they're used instead of machine translation.
 */
object OfficialTitles {
    private const val ASSET = "official_titles.json"
    private val videoId = Regex("^[A-Za-z0-9_-]{11}$")
    private val japanese = Regex("[\\u3040-\\u30FF\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uFF66-\\uFF9F]")

    @Volatile private var context: Context? = null

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    private val names: Map<String, String> by lazy {
        val ctx = context ?: return@lazy emptyMap()
        runCatching {
            val json = JSONObject(ctx.assets.open(ASSET).bufferedReader().use { it.readText() })
            buildMap {
                for (id in json.keys()) {
                    val name = json.optString(id).trim()
                    // Only well-formed entries: a real video id and a short, English name.
                    if (videoId.matches(id) && name.isNotEmpty() && name.length <= 200 && !japanese.containsMatchIn(name)) {
                        put(id, name)
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    /** The official English name for a song, if Square Enix gives one. Call off the main thread. */
    fun forId(id: String): String? = names[id]?.let(::tidySongTitle)

    /** Uses the official English name for a stored or loaded song when there is one. */
    fun fix(track: Track): Track {
        val official = forId(track.id) ?: return track
        if (official == track.title) return track
        val alt = track.altTitle ?: track.title.takeIf { japanese.containsMatchIn(it) }
        return track.copy(title = official, altTitle = alt)
    }
}
