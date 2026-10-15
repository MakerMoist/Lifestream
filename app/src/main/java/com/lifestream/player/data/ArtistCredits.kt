package com.lifestream.player.data

/**
 * Turns whatever YouTube lists as a song's artist into the real composer (or singer, for vocal
 * songs). Official albums credit the actual composers, but some names need fixing:
 * - Nobuo Uematsu's tracks carry his channel name, "【公式】植松伸夫TV" ("[Official] Nobuo Uematsu
 *   TV"), which machine translation garbles (e.g. "Uematsu Gont TV").
 * - Some carry the record label ("SQUARE ENIX MUSIC"), a placeholder ("Release"), or, for
 *   soundtracks only available as fan uploads, the uploader's channel name.
 * Those become the soundtrack's credited composer(s).
 */
object ArtistCredits {

    /** Each soundtrack's composer credit, for songs whose own credit isn't a person. */
    private val composers: Map<String, List<String>> = mapOf(
        "ff1" to listOf("Nobuo Uematsu"),
        "ff2" to listOf("Nobuo Uematsu"),
        "ff3" to listOf("Nobuo Uematsu"),
        "ff4" to listOf("Nobuo Uematsu"),
        "ff5" to listOf("Nobuo Uematsu"),
        "ff6" to listOf("Nobuo Uematsu"),
        "ff7" to listOf("Nobuo Uematsu"),
        "ff7r" to listOf("Masashi Hamauzu", "Mitsuto Suzuki"),
        "ff7rb" to listOf("Mitsuto Suzuki", "Masashi Hamauzu"),
        "ff8" to listOf("Nobuo Uematsu"),
        "ff9" to listOf("Nobuo Uematsu"),
        "ff10" to listOf("Nobuo Uematsu", "Masashi Hamauzu", "Junya Nakano"),
        "ff10-2" to listOf("Noriko Matsueda", "Takahito Eguchi"),
        "ff11" to listOf("Naoshi Mizuta", "Nobuo Uematsu", "Kumi Tanioka"),
        "ff12" to listOf("Hitoshi Sakimoto"),
        "ff13" to listOf("Masashi Hamauzu"),
        "ff13-2" to listOf("Masashi Hamauzu", "Naoshi Mizuta", "Mitsuto Suzuki"),
        "ff14" to listOf("Masayoshi Soken"),
        "ff15" to listOf("Yoko Shimomura"),
        "ff16" to listOf("Masayoshi Soken"),
        "fft" to listOf("Hitoshi Sakimoto", "Masaharu Iwata"),
        "ccr" to listOf("Takeharu Ishimoto"),
        "lr" to listOf("Masashi Hamauzu", "Naoshi Mizuta", "Mitsuto Suzuki"),
        "coe33" to listOf("Lorien Testard"),
        "ct" to listOf("Yasunori Mitsuda"),
        "cx" to listOf("Yasunori Mitsuda"),
        "lo" to listOf("Nobuo Uematsu"),
    )

    /** Credits that aren't a person: labels, placeholders and fan channels that uploaded songs. */
    private val notPeople = setOf(
        "square enix music", "square enix", "squareenixmusic", "release", "various artists", "unknown",
        // uploaders of the fan playlists used where there's no official album
        "doomed account", "manwe sulimo", "synapsidae", "tukepuikko",
    )

    private val canonicalNames = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Fixes known spellings and channel names; anything else is returned tidied. */
    fun canonical(raw: String): String {
        canonicalNames[raw]?.let { return it }
        if (canonicalNames.size > 5_000) canonicalNames.clear()
        return computeCanonical(raw).also { canonicalNames[raw] = it }
    }

    /** Between names in a credit: "A, B & C", "A / B", "A、B". */
    private val separator = Regex("\\s*(?:,|&|/|、)\\s*")

    private fun computeCanonical(raw: String): String {
        val name = tidySongTitle(raw).trim()
        // Several people: fix each name on its own, so "Mitsuto Suzuki & Masashi Hamauzu" stays
        // both of them (it used to collapse to "Masashi Hamauzu").
        if (separator.containsMatchIn(name)) {
            val out = StringBuilder()
            var last = 0
            for (m in separator.findAll(name)) {
                out.append(one(name.substring(last, m.range.first)))
                out.append(if (m.value.contains('、')) ", " else m.value.let { d -> if (d.trim() == ",") ", " else " ${d.trim()} " })
                last = m.range.last + 1
            }
            out.append(one(name.substring(last)))
            return out.toString().trim()
        }
        return one(name)
    }

    /** One person's name (or channel), fixed where it's known. */
    private fun one(raw: String): String {
        val name = raw.trim()
        val lower = name.lowercase()
        return when {
            "植松" in name || "uematsu" in lower -> "Nobuo Uematsu"
            "崎元" in name || "sakimoto" in lower -> "Hitoshi Sakimoto"
            "下村" in name || "shimomura" in lower -> "Yoko Shimomura"
            "祖堅" in name || "soken" in lower -> "Masayoshi Soken"
            "浜渦" in name || "hamauzu" in lower -> "Masashi Hamauzu"
            "光田" in name || lower == "yasunori mitsuda" -> "Yasunori Mitsuda"
            lower == "kokia official" -> "KOKIA"
            lower.startsWith("the sxplay") || "sugawara" in lower -> "Sayuri Sugawara"
            "muir" in lower && "bill" in lower -> "Bill Muir"
            else -> name
        }
    }

    /** The soundtrack's composer credit, e.g. "Noriko Matsueda & Takahito Eguchi". */
    fun creditFor(gameId: String?): String? {
        val names = composers[gameId] ?: return null
        return when (names.size) {
            1 -> names[0]
            2 -> "${names[0]} & ${names[1]}"
            else -> names.dropLast(1).joinToString(", ") + " & " + names.last()
        }
    }

    /** The artist to show for a song from [gameId]. */
    fun forSong(gameId: String?, rawArtist: String): String {
        val name = canonical(rawArtist)
        if (name.isBlank() || name.lowercase() in notPeople) return creditFor(gameId) ?: name
        return name
    }

    /**
     * Applies [forSong] to a stored or freshly loaded track, moves it to its soundtrack's current
     * id (merged soundtracks), and gives it the soundtrack's official cover where one is set.
     */
    fun fix(track: Track): Track {
        val gameId = Catalog.currentId(track.gameId)
        val artist = forSong(gameId, track.artist)
        val cover = gameId?.let { Catalog.game(it)?.coverArt } ?: track.thumbnail
        if (artist == track.artist && gameId == track.gameId && cover == track.thumbnail) return track
        return track.copy(artist = artist, gameId = gameId, thumbnail = cover)
    }
}
