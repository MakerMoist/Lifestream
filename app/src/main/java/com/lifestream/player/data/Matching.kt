package com.lifestream.player.data

import java.text.Normalizer
import java.util.Locale

/**
 * Rules for deciding whether an album title belongs to a specific game.
 *
 * Every match must mention Final Fantasy (or FF). On top of that:
 * - [numerals]: the numeral right after "Final Fantasy" must be one of these (VII, X2, …)
 * - [allOf]: every one of these words must appear (e.g. REBIRTH)
 * - [anyOf]: at least one must appear
 * - [exclude]: none may appear (e.g. the original VII excludes REMAKE / REBIRTH)
 * - [allowBare]: accept "Final Fantasy" with no numeral at all (only the first game)
 * - [requireFinalFantasy]: false for non-FF soundtracks such as Chrono Trigger
 */
data class MatchSpec(
    val numerals: Set<String> = emptySet(),
    val allOf: List<String> = emptyList(),
    val anyOf: List<String> = emptyList(),
    val exclude: List<String> = emptyList(),
    val allowBare: Boolean = false,
    val requireFinalFantasy: Boolean = true,
)

object Matching {

    private val roman = listOf(
        "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
        "XI", "XII", "XIII", "XIV", "XV", "XVI", "X2", "XIII2",
    )
    private val romanSet = roman.toSet()
    private val arabic: Map<String, String> = (1..16).associate { it.toString() to roman[it - 1] }

    /** Words that mark fan covers, compilations and filler rather than real soundtracks. */
    private val junk = listOf(
        "COVER", "COVERS", "TRIBUTE", "LOFI", "LO FI", "CHILL", "SLEEP", "RELAXING", "RELAX",
        "STUDY", "KARAOKE", "INSPIRED", "8 BIT", "8BIT", "16 BIT", "CHIPTUNE", "MEDIEVAL",
        "AMBIENCE", "AMBIENT", "ASMR", "REMIX", "REMIXES", "METAL", "EPIC VERSION", "BEST OF",
        "GREATEST", "TOP", "MEDLEY", "RINGTONE", "LULLABY", "LULLABIES", "MUSIC BOX",
    )

    /** Composers, arrangers and labels behind official Final Fantasy music. */
    private val officialArtists = listOf(
        "UEMATSU", "HAMAUZU", "SAKIMOTO", "SOKEN", "SHIMOMURA", "ISHIMOTO", "MITSUTO SUZUKI",
        "MATSUEDA", "EGUCHI", "MASAHARU IWATA", "MIZUTA", "TANIOKA", "JUNYA NAKANO", "SEKITO",
        "KAWAMORI", "ARNIE ROTH", "BLACK MAGES", "SQUARE ENIX", "SQUARESOFT", "SHOTARO SHIMA",
        "KAZUHIKO TOYAMA", "RYO YAMAZAKI", "DISTANT WORLDS", "HIROKAZU AKIYAMA", "MITSUDA",
        "NORIKO MATSUEDA", "TAKAHITO EGUCHI", "YOSHITAKA SUZUKI", "TSUYOSHI SEKITO",
    )

    fun normalize(raw: String): String {
        var t = Normalizer.normalize(raw, Normalizer.Form.NFKC).uppercase(Locale.ROOT)
        // "X-2" → "X2", "XIII-2" → "XIII2" so sequels don't match their parent game
        t = Regex("\\b([IVX]+)\\s*-\\s*2\\b").replace(t) { it.groupValues[1] + "2" }
        return Regex("[^A-Z0-9]+").replace(t, " ").trim()
    }

    private fun hasWord(normalized: String, word: String) = " $normalized ".contains(" $word ")

    private fun isNumeral(token: String) = token in romanSet || token in arabic

    private fun canonical(token: String) = arabic[token] ?: token

    fun mentionsFinalFantasy(normalized: String): Boolean =
        hasWord(normalized, "FINAL FANTASY") ||
            normalized.split(' ').any { it == "FF" || (it.startsWith("FF") && isNumeral(it.drop(2))) }

    /** Numerals that directly follow "Final Fantasy" / "FF" in a normalized title. */
    fun numerals(normalized: String): Set<String> {
        val tokens = normalized.split(' ')
        val found = mutableSetOf<String>()
        var i = 0
        while (i < tokens.size) {
            var j: Int? = null
            val tok = tokens[i]
            when {
                tok == "FINAL" && tokens.getOrNull(i + 1) == "FANTASY" -> j = i + 2
                tok == "FF" -> j = i + 1
                tok.startsWith("FF") && isNumeral(tok.drop(2)) -> {
                    found += canonical(tok.drop(2))
                    j = i + 1
                }
            }
            if (j == null) {
                i++
                continue
            }
            while (j < tokens.size && (isNumeral(tokens[j]) || tokens[j] == "AND")) {
                if (tokens[j] != "AND") found += canonical(tokens[j])
                j++
            }
            i = j
        }
        return found
    }

    fun isJunk(title: String): Boolean {
        val t = normalize(title)
        return junk.any { hasWord(t, it) }
    }

    fun matches(spec: MatchSpec, title: String): Boolean {
        val t = normalize(title)
        if (spec.requireFinalFantasy && !mentionsFinalFantasy(t)) return false
        if (junk.any { hasWord(t, it) }) return false
        if (spec.exclude.any { hasWord(t, it) }) return false
        if (spec.allOf.any { !hasWord(t, it) }) return false
        if (spec.anyOf.isNotEmpty() && spec.anyOf.none { hasWord(t, it) }) return false
        if (spec.numerals.isEmpty()) return true
        val found = numerals(t)
        if (found.isEmpty()) return spec.allowBare
        return found.any { it in spec.numerals }
    }

    fun isOfficialArtist(artist: String): Boolean {
        val a = normalize(artist)
        return officialArtists.any { hasWord(a, it) }
    }

    /** Track-level check used when there is no album to vouch for a song. */
    fun looksLikeFinalFantasy(track: Track): Boolean =
        !isJunk(track.title) &&
            (isOfficialArtist(track.artist) || mentionsFinalFantasy(normalize(track.title)))

    /** Album-level check for free-text search with "Final Fantasy only" on. */
    fun looksLikeFinalFantasy(album: Album): Boolean =
        !isJunk(album.title) && mentionsFinalFantasy(normalize(album.title))

    private val versionWords = Regex(
        "\\b(final fantasy|ost|soundtrack|version|ver|remaster|remastered|piano|orchestra|orchestral|" +
            "arrange|arranged|arrangement|instrumental|extended|cover|live|acoustic|remix|edit|mix)\\b"
    )

    /**
     * A key that is the same for every version of a song, so lists keep just one:
     * "One-Winged Angel (Remastered)", "01. One-Winged Angel" and
     * "FINAL FANTASY VII - One-Winged Angel [Piano Ver.]" all become "one winged angel".
     */
    fun songKey(title: String): String {
        var t = Normalizer.normalize(title, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        t = Regex("[(\\[【〔{「].*?[)\\]】〕}」]").replace(t, " ")
        t = Regex("[~〜].*?[~〜]").replace(t, " ")
        val segments = t.split(Regex("\\s+[-–—|/]\\s+|\\s*[:：]\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        t = segments.firstOrNull { !versionWords.containsMatchIn(it) } ?: segments.firstOrNull() ?: t
        t = Regex("^(disc\\s*\\d+\\s*)?(track\\s*)?\\d{1,3}(?=[.\\s_-])[.\\s_-]+").replace(t, "")
        t = Regex("[^\\p{L}\\p{N}]+").replace(t, " ").trim()
        return t.ifEmpty { title.lowercase(Locale.ROOT).trim() }
    }

    private val ostPhrases = listOf(
        "ORIGINAL SOUNDTRACK", "ORIGINAL SOUND TRACK", "ORIGINAL SOUND VERSION", "OST", "OSV", "SOUNDTRACK",
    )

    /** Words that mark an arrangement, compilation or extra rather than the game's own soundtrack. */
    private val notTheOriginal = listOf(
        "PIANO", "ORCHESTRA", "ORCHESTRAL", "ORCHESTRATIONS", "SYMPHONIC", "SYMPHONY", "ARRANGE", "ARRANGED",
        "ARRANGEMENT", "ARRANGEMENTS", "VOCAL", "VOCALS", "ACOUSTIC", "GUITAR", "JAZZ", "BRASS", "STRINGS",
        "CONCERT", "LIVE", "PLUS", "SELECTION", "SELECTIONS", "COLLECTION", "COLLECTIONS", "BEST", "SPECIAL",
        "DISTANT WORLDS", "TRIBUTE", "COVER", "COVERS", "REMIX", "REMIXES", "REMIXED", "MUSIC BOX", "SINGLE",
        "BATTLE TRAX", "BLACK MAGES", "PRELUDE",
    )

    private val remasterWords = listOf("REMASTER", "REMASTERED", "HD", "PIXEL", "REMASTERS")

    /**
     * True only for a game's official original soundtrack: titled as an OST (or the older
     * "Original Sound Version"), not an arrangement or extra, and released by the composers or
     * Square Enix (YouTube Music lists the label or composer as the album artist).
     */
    fun isOfficialSoundtrack(album: Album): Boolean {
        val t = normalize(album.title)
        if (ostPhrases.none { hasWord(t, it) }) return false
        if (notTheOriginal.any { hasWord(t, it) }) return false
        if (junk.any { hasWord(t, it) }) return false
        if (album.trackCount in 1..3) return false
        val artist = normalize(album.artist)
        return isOfficialArtist(album.artist) || hasWord(artist, "VARIOUS ARTISTS")
    }

    /** Higher is better: the original release before remasters, the composers' own credit first. */
    fun score(album: Album, game: Game): Int {
        val t = normalize(album.title)
        var s = 0
        if (hasWord(t, "ORIGINAL SOUNDTRACK") || hasWord(t, "ORIGINAL SOUND VERSION")) s += 2
        if (isOfficialArtist(album.artist)) s += 3
        if (remasterWords.any { hasWord(t, it) }) s -= 3
        if (album.trackCount >= 20) s += 1
        return s
    }
}
