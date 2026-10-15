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
        // Clair Obscur: Expedition 33
        "LORIEN TESTARD", "ALICE DUPORT", "SANDFALL",
    )

    // Compiled once: these run for every song on every search keystroke.
    private val sequelDash = Regex("\\b([IVX]+)\\s*-\\s*2\\b")
    private val nonAlnumUpper = Regex("[^A-Z0-9]+")

    fun normalize(raw: String): String {
        var t = Normalizer.normalize(raw, Normalizer.Form.NFKC).uppercase(Locale.ROOT)
        // "X-2" → "X2", "XIII-2" → "XIII2" so sequels don't match their parent game
        t = sequelDash.replace(t) { it.groupValues[1] + "2" }
        return nonAlnumUpper.replace(t, " ").trim()
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
    /**
     * A search compares one query against every song, so the query's tokens and normalized form
     * are worked out once and reused (the last query is remembered), and song titles' normalized
     * forms are kept between keystrokes.
     */
    private class QueryParts(val query: String, val tokens: List<String>, val phrase: String) {
        val compact = tokens.joinToString("")
    }

    /** A song title split up for matching: normalized, its words (numbers as numerals), squashed. */
    private class TitleParts(val normalized: String, val words: List<String>) {
        val compact = words.joinToString("")
    }

    @Volatile private var lastQuery: QueryParts? = null
    private val titleParts = java.util.concurrent.ConcurrentHashMap<String, TitleParts>()
    private const val MAX_NORMALIZED_TITLES = 40_000

    private fun forQuery(query: String): QueryParts =
        lastQuery?.takeIf { it.query == query }
            ?: QueryParts(query, computeQueryTokens(query), normalize(query)).also { lastQuery = it }

    /** Worked out once per title and kept, so typing a search doesn't split every title again. */
    private fun partsOf(title: String): TitleParts = titleParts[title] ?: run {
        val normalized = normalize(title)
        // Numbers are compared the way the query has them: the query's "2" became "II" (so "ff 7"
        // finds VII), so a song called "Battle 2" must count as "Battle II" too.
        TitleParts(normalized, normalized.split(' ').filter { it.isNotEmpty() }.map(::canonical))
    }.also {
        if (titleParts.size >= MAX_NORMALIZED_TITLES) titleParts.clear()
        titleParts[title] = it
    }

    fun queryTokens(query: String): List<String> = forQuery(query).tokens

    /** Frees search caches (they're rebuilt as soon as a search runs again). */
    fun trimCaches() {
        titleParts.clear()
        songKeys.clear()
        lastQuery = null
    }

    private fun computeQueryTokens(query: String): List<String> {
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

    /**
     * How well [query] matches a song [title] (plus optional [extraTokens], e.g. its game's name);
     * null when it doesn't. Higher is better. Forgiving on purpose:
     * - words can be typed partially ("one wing" → One-Winged Angel)
     * - spacing doesn't matter ("Air Buster" ↔ "Airbuster", "Liberifatali")
     * - small typos are tolerated ("Sephirot", "Zanarkan")
     */
    fun searchScore(query: String, title: String, extraTokens: List<String> = emptyList()): Int? {
        val parts = forQuery(query)
        val q = parts.tokens
        if (q.isEmpty()) return null
        val phrase = parts.phrase
        val compactQuery = parts.compact
        val t = partsOf(title)
        val normTitle = t.normalized
        val words = t.words
        val compactTitle = t.compact

        var score = when {
            q.all { w -> words.any { it.startsWith(w) } } -> 6
            compactQuery.length >= 3 && compactTitle.contains(compactQuery) -> 5
            // The title's words plus [extraTokens] (e.g. its game's name), without joining lists.
            q.all { w -> words.any { it.startsWith(w) } || extraTokens.any { it.startsWith(w) } } -> 4
            q.all { w -> words.any { wordMatches(w, it) } || extraTokens.any { wordMatches(w, it) } } -> 2
            compactQuery.length >= 6 && fuzzyContains(compactTitle, compactQuery) -> 1
            else -> return null
        }
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
        if (!mayBeWithin(needle, text, allowed)) return false
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
        if (!mayBeWithin(needle, text, allowed)) return false
        for (len in (needle.length - allowed)..(needle.length + allowed)) {
            if (len <= 0 || len > text.length) continue
            for (start in 0..(text.length - len)) {
                if (editDistance(needle, text.substring(start, start + len), allowed) <= allowed) return true
            }
        }
        return false
    }

    /**
     * Quick rejection before the expensive typo search: split [needle] into [allowed] + 1 pieces;
     * [allowed] typos can spoil at most that many pieces, so a real match always contains at least
     * one piece unchanged. If none appears in [text], it can't match (most songs, for most
     * searches), and the slice-by-slice comparison is skipped.
     */
    private fun mayBeWithin(needle: String, text: String, allowed: Int): Boolean {
        val pieces = allowed + 1
        if (needle.length < pieces * 2) return true // pieces too short to say anything
        val size = needle.length / pieces
        for (k in 0 until pieces) {
            val start = k * size
            val end = if (k == pieces - 1) needle.length else start + size
            if (text.contains(needle.substring(start, end))) return true
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
    fun titleTokens(game: Game): List<String> {
        val words = normalize(game.title).split(' ').filter { it.isNotEmpty() }
        // The card label counts where it's a real numeral ("I" for Final Fantasy); labels like
        // "CC·R" would otherwise make "cc" find Crisis Core instead of Chrono Cross.
        val numerals = normalize(game.numeral).split(' ').filter { isNumeral(it) }
        return (words + numerals + game.aliases.map(::normalize)).distinct()
    }

    /** True when the whole query is one of [game]'s short names ("fft", "cc", "ct"…). */
    fun isAlias(query: String, game: Game): Boolean {
        val compact = queryTokens(query).joinToString("")
        return compact.isNotEmpty() && game.aliases.any { normalize(it).replace(" ", "") == compact }
    }

    private val bareFollowers = setOf("ORIGINAL", "OST", "SOUNDTRACK", "PIXEL", "PS", "OSV")

    /** "FINAL FANTASY Original Soundtrack" / "FINAL FANTASY (PS Version)…" — not "FINAL FANTASY Legend". */
    private fun bareTitleIsSoundtrack(normalized: String): Boolean {
        val tokens = normalized.split(' ')
        val i = tokens.windowed(2).indexOfFirst { it[0] == "FINAL" && it[1] == "FANTASY" }
        return i >= 0 && tokens.getOrNull(i + 2) in bareFollowers
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
    private val bracketed = Regex("[(\\[【〔{「].*?[)\\]】〕}」]")
    private val tildeQuoted = Regex("[~〜].*?[~〜]")
    private val segmentSplit = Regex("\\s+[-–—|/]\\s+|\\s*[:：]\\s+")
    private val leadingNumber = Regex("^(disc\\s*\\d+\\s*)?(track\\s*)?\\d{1,3}(?=[.\\s_-])[.\\s_-]+")
    private val nonLetterDigit = Regex("[^\\p{L}\\p{N}]+")

    /** "Forever Today ep ver." is another version of "Forever Today". */
    private val epVersion = Regex("\\s+ep\\.?\\s+ver(sion)?\\.?\\s*$")

    /**
     * A Japanese name followed by its English one ("ヴァナ・ディールの星唄 -Rhapsodies of Vana'diel"):
     * the English part names the song, so it matches the same song listed in English.
     */
    private val japaneseThenEnglish = Regex("^[^a-z]*[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}][^a-z]*?\\s*[-–—]\\s*([a-z].*)$")

    /** A song sung in another language is its own recording ("1000 Words (English Version)"). */
    private val languageVersion = Regex("\\b(english|japanese)\\s+(version|ver\\.?|vocal)")

    private val songKeys = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Worked out for every song row as lists scroll (their keys) and for every song when lists
     * are built, so each title's key is remembered rather than recomputed with its regexes.
     */
    fun songKey(title: String, fullTitle: Boolean = false): String {
        val cacheKey = if (fullTitle) "\u0001$title" else title
        songKeys[cacheKey]?.let { return it }
        if (songKeys.size >= MAX_NORMALIZED_TITLES) songKeys.clear()
        return computeSongKey(title, fullTitle).also { songKeys[cacheKey] = it }
    }

    private fun computeSongKey(title: String, fullTitle: Boolean): String {
        var t = Normalizer.normalize(title, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        t = epVersion.replace(t, "")
        // (Not when the English part only says which version it is: "ザナルカンドにて - Piano Ver.")
        japaneseThenEnglish.find(t)?.groupValues?.get(1)
            ?.takeIf { english -> !versionWords.containsMatchIn(english) && english.count { it.isLetter() } >= 3 }
            ?.let { t = it }
        val language = languageVersion.find(t)?.groupValues?.get(1)
        val key = if (fullTitle) fullSongKey(t, title) else baseSongKey(t, title)
        return if (language != null) "$key $language" else key
    }

    /** A song's identity, using its soundtrack's naming style (see [Game.fullSongTitles]). */
    fun songKey(track: Track): String =
        songKey(track.title, Catalog.game(track.gameId ?: "")?.fullSongTitles == true)

    /** The whole name, minus bracketed version notes: "Verso (Music Box)" is still "verso". */
    private fun fullSongKey(lowered: String, title: String): String {
        var t = bracketed.replace(lowered, " ")
        t = tildeQuoted.replace(t, " ")
        t = nonLetterDigit.replace(t, " ").trim()
        return t.ifEmpty { title.lowercase(Locale.ROOT).trim() }
    }

    private fun baseSongKey(lowered: String, title: String): String {
        var t = lowered
        t = bracketed.replace(t, " ")
        t = tildeQuoted.replace(t, " ")
        val segments = t.split(segmentSplit).map { it.trim() }.filter { it.isNotEmpty() }
        t = segments.firstOrNull { !versionWords.containsMatchIn(it) && !numberingOnly.matches(it) }
            ?: segments.firstOrNull() ?: t
        t = leadingNumber.replace(t, "")
        t = nonLetterDigit.replace(t, " ").trim()
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
        "BATTLE TRAX", "BLACK MAGES", "PRELUDE", "PREVIEW", "KINGSGLAIVE", "BROTHERHOOD",
        "ADVENT CHILDREN", "SPIRITS WITHIN",
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
        val gameWords = titleTokens(game).toSet() + normalize(game.numeral).split(' ') + setOf(
            "FINAL", "FANTASY", "FF", "OST", "SOUNDTRACK", "ORIGINAL", "MUSIC", "DISC", "CD", "TRACK", "BGM",
        )
        var t = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim()
        // `"Mission Start" CCFF7-R OST 03 …` → the quoted part is the song.
        quotedSong.find(t)?.let { return it.groupValues[1].trim() }
        t = qualityTag.replace(t, " ")
        val parts = t.split(segmentSplit).map { it.trim() }.filter { it.isNotEmpty() }
        fun isNoise(part: String): Boolean {
            val tokens = normalize(part).split(' ').filter { it.isNotEmpty() }
            return tokens.isEmpty() || tokens.all {
                it in gameWords || isNumeral(it) || numberingToken.matches(it) || it.startsWith("FF")
            }
        }
        val song = parts.firstOrNull { !isNoise(it) } ?: parts.lastOrNull() ?: raw
        return trackNumber.replace(song, "").trim().ifEmpty { raw }
    }

    private val quotedSong = Regex("^[\"“「『]([^\"”」』]{2,})[\"”」』]")
    private val qualityTag = Regex(
        "[\\[(【](?:[^\\])】]*\\b(?:hd|hq|ost|audio|official|soundtrack|1080p|720p|4k|lyrics|extended|ps\\d|ver\\.?)\\b[^\\])】]*)[\\])】]",
        RegexOption.IGNORE_CASE,
    )
    private val trackNumber = Regex("^\\s*(?:disc\\s*\\d+\\s*)?(?:track\\s*)?\\d{1,3}(?=[.\\s_)-])[.\\s_)-]+", RegexOption.IGNORE_CASE)
}
