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
 * - [excludeNumerals]: reject combined releases that also cover another game
 */
data class MatchSpec(
    val numerals: Set<String> = emptySet(),
    val allOf: List<String> = emptyList(),
    val anyOf: List<String> = emptyList(),
    val exclude: List<String> = emptyList(),
    val allowBare: Boolean = false,
    val requireFinalFantasy: Boolean = true,
    /** Titles that also name one of these games are rejected (e.g. "X/X-2 HD Remaster" for X-2). */
    val excludeNumerals: Set<String> = emptySet(),
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

    fun isNumeralToken(token: String) = token in romanSet

    /**
     * Search words, normalized like titles, with shorthand expanded:
     * "ff" → FINAL FANTASY, "ff7"/"ffvii" → FINAL FANTASY VII, "7" → VII.
     */
    fun queryTokens(query: String): List<String> {
        val out = mutableListOf<String>()
        for (tok in normalize(query).split(' ').filter { it.isNotEmpty() }) {
            when {
                tok == "FF" -> out += listOf("FINAL", "FANTASY")
                tok.startsWith("FF") && isNumeral(tok.drop(2)) -> out += listOf("FINAL", "FANTASY", canonical(tok.drop(2)))
                tok in arabic -> out += canonical(tok)
                else -> out += tok
            }
        }
        return out
    }

    private val notTheRecording = listOf(
        "COVER", "COVERS", "KARAOKE", "LIVE", "INSTRUMENTAL", "ACOUSTIC", "REMIX", "REMIXED", "PIANO",
        "ORCHESTRAL", "LYRICS", "LYRIC", "SPED UP", "SLOWED", "NIGHTCORE", "8D", "TRIBUTE", "VERSION",
    )

    /**
     * The original studio recording of [extra] among search results: the title must be the song
     * (allowing a trailing "(from …)" note), the artist must match, and covers, live takes,
     * karaoke, remixes and the like are rejected. Exact titles win over looser ones.
     */
    fun pickExtra(extra: ExtraSong, candidates: List<Track>): Track? {
        val wantTitle = normalize(extra.title)
        val wantArtist = normalize(extra.artist)
        val ok = candidates.filter { t ->
            val title = normalize(t.title)
            val artist = normalize(t.artist)
            (hasWord(artist, wantArtist) || hasWord(title, wantArtist)) &&
                hasWord(title, wantTitle) &&
                notTheRecording.none { hasWord(title, it) } &&
                junk.none { hasWord(title, it) }
        }
        return ok.firstOrNull { normalize(it.title) == wantTitle }
            ?: ok.minByOrNull { normalize(it.title).length }
    }

    /**
     * How well [query] matches a song by any of its names: the English title, the original
     * Japanese title (typed in Japanese), or, for English letters, how the Japanese title sounds
     * ("midgardsormr" → ミドガルズオルム). Null if none.
     */
    fun songScore(track: Track, query: String, extraTokens: List<String> = emptyList()): Int? {
        searchScore(query, track.title, extraTokens)?.let { return it }
        val alt = track.altTitle ?: track.title.takeIf { hasJapanese(it) } ?: return null
        // Typed in Japanese: match the Japanese name directly.
        if (hasJapanese(query)) return if (compactJapanese(alt).contains(compactJapanese(query))) 6 else null
        searchScore(query, alt, extraTokens)?.let { return it }
        return if (Romaji.soundsLike(query, alt)) 1 else null
    }

    private val japaneseChars = Regex("[\\u3040-\\u30FF\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uFF66-\\uFF9F]")

    fun hasJapanese(text: String) = japaneseChars.containsMatchIn(text)

    /** Japanese text with width variants unified and spacing/punctuation removed. */
    private fun compactJapanese(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    /** True if [query] matches [title] (see [searchScore]). Used by each game's search box. */

    fun titleMatches(title: String, query: String): Boolean =
        queryTokens(query).isEmpty() || searchScore(query, title) != null

    /**
     * How well [query] matches a song [title] (plus optional [extraTokens], e.g. its game's name);
     * null when it doesn't. Higher is better. Forgiving on purpose:
     * - words can be typed partially ("one wing" → One-Winged Angel)
     * - spacing doesn't matter ("Air Buster" ↔ "Airbuster", "Liberifatali")
     * - small typos are tolerated ("Sephirot", "Zanarkan")
     */
    fun searchScore(query: String, title: String, extraTokens: List<String> = emptyList()): Int? {
        val q = queryTokens(query)
        if (q.isEmpty()) return null
        val normTitle = normalize(title)
        val words = normTitle.split(' ').filter { it.isNotEmpty() }
        val all = words + extraTokens
        val compactQuery = q.joinToString("")
        val compactTitle = normTitle.replace(" ", "")

        var score = when {
            q.all { w -> words.any { it.startsWith(w) } } -> 6
            compactQuery.length >= 3 && compactTitle.contains(compactQuery) -> 5
            q.all { w -> all.any { it.startsWith(w) } } -> 4
            q.all { w -> all.any { wordMatches(w, it) } } -> 2
            compactQuery.length >= 6 && fuzzyContains(compactTitle, compactQuery) -> 1
            else -> return null
        }
        val phrase = normalize(query)
        if (phrase.isNotEmpty() && normTitle.contains(phrase)) score += 2
        if (normTitle == phrase || compactTitle == compactQuery) score += 3
        return score
    }

    /** A query word matches a title word by prefix, or with one typo (two for long words). */
    fun wordMatches(q: String, word: String): Boolean {
        if (word.startsWith(q)) return true
        if (q.length < 4 || isNumeralToken(q)) return false
        val allowed = if (q.length >= 8) 2 else 1
        return listOf(word, word.take(q.length), word.take(q.length + 1), word.take(q.length - 1))
            .any { editDistance(q, it, allowed) <= allowed }
    }

    /** True if [needle] is within [allowed] edits of [text] or of some stretch of it. */
    fun withinEdits(needle: String, text: String, allowed: Int): Boolean {
        if (editDistance(needle, text, allowed) <= allowed) return true
        for (len in (needle.length - allowed)..(needle.length + allowed)) {
            if (len <= 0 || len >= text.length) continue
            for (start in 0..(text.length - len)) {
                if (editDistance(needle, text.substring(start, start + len), allowed) <= allowed) return true
            }
        }
        return false
    }

    /** True if some slice of [text] is within a typo or two of [needle] (spacing already removed). */
    private fun fuzzyContains(text: String, needle: String): Boolean {
        val allowed = if (needle.length >= 10) 2 else 1
        for (len in (needle.length - allowed)..(needle.length + allowed)) {
            if (len <= 0 || len > text.length) continue
            for (start in 0..(text.length - len)) {
                if (editDistance(needle, text.substring(start, start + len), allowed) <= allowed) return true
            }
        }
        return false
    }

    /** Levenshtein distance, giving up early once it exceeds [max]. */
    private fun editDistance(a: String, b: String, max: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > max) return max + 1
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** A game's searchable words: its title plus its card numeral. */
    fun titleTokens(game: Game): List<String> =
        normalize("${game.title} ${game.numeral}").split(' ').filter { it.isNotEmpty() }

    private val bareFollowers = setOf("ORIGINAL", "OST", "SOUNDTRACK", "PIXEL", "PS", "OSV")

    /** "FINAL FANTASY Original Soundtrack" / "FINAL FANTASY (PS Version)…" — not "FINAL FANTASY Legend". */
    private fun bareTitleIsSoundtrack(normalized: String): Boolean {
        val tokens = normalized.split(' ')
        val i = tokens.windowed(2).indexOfFirst { it[0] == "FINAL" && it[1] == "FANTASY" }
        return i >= 0 && tokens.getOrNull(i + 2) in bareFollowers
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
        if (found.any { it in spec.excludeNumerals }) return false
        if (found.isEmpty()) return spec.allowBare && bareTitleIsSoundtrack(t)
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
            "arrange|arranged|arrangement|instrumental|extended|cover|live|acoustic|remix|edit|mix|ff[ivx0-9]*)\\b"
    )

    private val numberingOnly = Regex("^(disc|cd|track|no|#|\\s|\\d|[.\\-])+$")

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
        t = segments.firstOrNull { !versionWords.containsMatchIn(it) && !numberingOnly.matches(it) }
            ?: segments.firstOrNull() ?: t
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
        "CONCERT", "LIVE", "SELECTION", "SELECTIONS", "COLLECTION", "COLLECTIONS", "BEST", "SPECIAL",
        "DISTANT WORLDS", "TRIBUTE", "COVER", "COVERS", "REMIX", "REMIXES", "REMIXED", "MUSIC BOX", "SINGLE",
        "BATTLE TRAX", "BLACK MAGES", "PRELUDE", "PREVIEW", "KINGSGLAIVE", "BROTHERHOOD", "GIFTS FROM VANA",
        "LIGHTNING RETURNS", "ADVENT CHILDREN", "SPIRITS WITHIN",
    )

    private val remasterWords = listOf("REMASTER", "REMASTERED", "HD", "PIXEL", "REMASTERS")

    /** Later re-releases of a game's music; the original release should play first. */
    private val laterVersionWords = listOf("PS VERSION", "DS VERSION", "DS EDITION", "ZODIAC AGE", "DLC", "VOLUME 2", "VOL 2")

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
        // 0 tracks: listed on YouTube Music but empty, so it can't be played (falls back instead).
        if (album.trackCount in 0..3) return false
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
        if (laterVersionWords.any { hasWord(t, it) }) s -= 1
        if (album.trackCount >= 20) s += 1
        return s
    }

    // ---- Second source: complete soundtrack playlists, for games with no official album ----

    private val notACompleteSoundtrack = listOf(
        "GAMEPLAY", "WALKTHROUGH", "LONGPLAY", "LETS PLAY", "LET S PLAY", "UNRELEASED", "EXTENDED", "BEST",
        "MINI", "THEMES", "FAVORITE", "FAVOURITE", "FAVORITES", "MY", "HOURS", "HOUR", "LOOP", "LOOPED",
        "REACTION", "TRAILER",
    )

    /**
     * A YouTube playlist that looks like the game's complete original soundtrack (the game's own
     * audio, uploaded track by track): named for that game and as an OST, not an arrangement,
     * medley, extended loop or gameplay video list, and long enough to be the whole thing.
     */
    fun isSoundtrackPlaylist(playlist: Album, game: Game): Boolean {
        val t = normalize(playlist.title)
        if (!matches(game.match, playlist.title)) return false
        if (ostPhrases.none { hasWord(t, it) }) return false
        if (notTheOriginal.any { hasWord(t, it) }) return false
        if (notACompleteSoundtrack.any { hasWord(t, it) }) return false
        return playlist.trackCount in 20..400
    }

    /** Official channel uploads first, then the most complete playlist. */
    fun playlistScore(playlist: Album): Long {
        var s = playlist.trackCount
        if (isOfficialArtist(playlist.artist)) s += 1000
        return s
    }

    /**
     * Video titles in fan playlists carry extra noise: "FFX-2 OST - 01 - Eternity ~Memory of
     * Lightwaves~ [HD]" → "Eternity ~Memory of Lightwaves~". Picks the part that is the song's
     * name (not the game, "OST", or a track number) and drops quality tags.
     */
    private val numberingToken = Regex("^(DISC|CD|TRACK|NO|VOL)?\\d+$")

    private val filler = Regex("^\\s*(bonus\\s*track|piano\\s*mini-?game|mini-?game|unused|sound\\s*test)\\b", RegexOption.IGNORE_CASE)

    /** Extras some uploads tack on that aren't part of the game's soundtrack. */
    fun isFillerTrack(cleanTitle: String): Boolean = filler.containsMatchIn(cleanTitle)

    fun cleanTrackTitle(raw: String, game: Game): String {
        val gameWords = titleTokens(game).toSet() + setOf(
            "FINAL", "FANTASY", "FF", "OST", "SOUNDTRACK", "ORIGINAL", "MUSIC", "DISC", "CD", "TRACK", "BGM",
        )
        var t = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim()
        // `"Mission Start" CCFF7-R OST 03 …` → the quoted part is the song.
        Regex("^[\"“「『]([^\"”」』]{2,})[\"”」』]").find(t)?.let { return it.groupValues[1].trim() }
        t = Regex("[\\[(【](?:[^\\])】]*\\b(?:hd|hq|ost|audio|official|soundtrack|1080p|720p|4k|lyrics|extended|ps\\d|ver\\.?)\\b[^\\])】]*)[\\])】]",
            RegexOption.IGNORE_CASE).replace(t, " ")
        val parts = t.split(Regex("\\s+[-–—|/]\\s+|\\s*[:：]\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        fun isNoise(part: String): Boolean {
            val tokens = normalize(part).split(' ').filter { it.isNotEmpty() }
            return tokens.isEmpty() || tokens.all {
                it in gameWords || isNumeral(it) || numberingToken.matches(it) || it.startsWith("FF")
            }
        }
        val song = parts.firstOrNull { !isNoise(it) } ?: parts.lastOrNull() ?: raw
        return Regex("^\\s*(?:disc\\s*\\d+\\s*)?(?:track\\s*)?\\d{1,3}(?=[.\\s_)-])[.\\s_)-]+", RegexOption.IGNORE_CASE)
            .replace(song, "").trim().ifEmpty { raw }
    }
}
