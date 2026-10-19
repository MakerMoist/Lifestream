package com.lifestream.player.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.io.File

/**
 * Every song from every soundtrack in the catalog, kept on the device. Search only looks in
 * here, so it can never return anything that isn't from one of the app's soundtracks. Game
 * pages and multi-select reuse it so they open instantly once it's built.
 *
 * Built in the background (a few games at a time), saved to disk, refreshed every few days.
 */
object SoundtrackIndex {

    /** Bump when the way soundtracks are sourced changes, so old indexes are rebuilt. */
    private const val VERSION = 16
    private const val FILE_NAME = "soundtrack-index.json"
    // Soundtracks rarely change; refreshing weekly keeps background network use low.
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    // XIV alone has ~800 songs across its albums; this only guards against a runaway source.
    private const val MAX_TRACKS_PER_GAME = 3000
    private const val RETRY_EMPTY_MS = 30L * 60 * 1000
    private const val MAX_FILE_BYTES = 64L * 1024 * 1024

    data class Progress(val done: Int, val total: Int, val running: Boolean)

    private lateinit var file: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val buildLock = Mutex()

    private val _tracks = MutableStateFlow<Map<String, List<Track>>>(emptyMap())
    /** game id → that game's soundtrack, one version of each song, in album order. */
    val tracks: StateFlow<Map<String, List<Track>>> = _tracks.asStateFlow()

    private val _progress = MutableStateFlow(Progress(0, Catalog.games.size, false))
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private val builtAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val emptyAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun init(context: Context) {
        file = File(context.filesDir, FILE_NAME)
        scope.launch {
            readFromDisk()
            ensureBuilt()
        }
    }

    fun tracksFor(gameId: String): List<Track>? = _tracks.value[gameId]?.takeIf { it.isNotEmpty() }

    /**
     * A soundtrack's songs with one version of each (the original release's, which comes first).
     * Worked out once per index update and remembered, since title pages, Instant Playlist and
     * Android Auto all ask for it.
     */
    fun songsFor(gameId: String): List<Track>? {
        val current = _tracks.value
        val list = current[gameId]?.takeIf { it.isNotEmpty() } ?: return null
        val memo = oneEach?.takeIf { it.first === current }?.second
            ?: java.util.concurrent.ConcurrentHashMap<String, List<Track>>().also { oneEach = current to it }
        return memo.getOrPut(gameId) { list.distinctBy { Matching.songKey(it) } }
    }

    @Volatile private var oneEach: Pair<Map<String, List<Track>>, java.util.concurrent.ConcurrentHashMap<String, List<Track>>>? = null

    /** The soundtrack a song belongs to: from the song itself, or by finding it in the index. */
    fun gameOf(track: Track): Game? {
        track.gameId?.let { id -> Catalog.game(id)?.let { return it } }
        return tracksById()[track.id]?.gameId?.let { Catalog.game(it) }
    }

    /** Drops the track → title lookup table; it's rebuilt the next time it's needed. */
    fun trimCaches() {
        byTrack = null
        oneEach = null
    }

    /**
     * Songs from one album share their artwork address, composer and title id; keeping one copy of
     * each repeated text (instead of thousands) noticeably shrinks the index in memory.
     */
    private fun shareStrings(lists: Map<String, List<Track>>): Map<String, List<Track>> {
        val pool = HashMap<String, String>()
        fun one(s: String?): String? = s?.let { pool.getOrPut(it) { it } }
        return lists.mapValues { (_, list) ->
            list.map { t ->
                t.copy(artist = one(t.artist)!!, thumbnail = one(t.thumbnail), gameId = one(t.gameId))
            }
        }
    }

    /** track id → the song as indexed, rebuilt only when the index itself changes. */
    @Volatile private var byTrack: Pair<Map<String, List<Track>>, Map<String, Track>>? = null

    private fun tracksById(): Map<String, Track> {
        val current = _tracks.value
        byTrack?.let { (source, map) -> if (source === current) return map }
        val map = HashMap<String, Track>()
        for ((gameId, list) in current) for (t in list) map.putIfAbsent(t.id, if (t.gameId == null) t.copy(gameId = gameId) else t)
        byTrack = current to map
        return map
    }

    /** The song as it's indexed now (latest name, credit and artwork), if it's in the index. */
    fun indexed(trackId: String): Track? = tracksById()[trackId]

    /** Loads any game that is missing or stale. Safe to call often. */
    fun ensureBuilt() {
        scope.launch {
            buildLock.withLock {
                val now = System.currentTimeMillis()
                val todo = Catalog.games.filter { g ->
                    if (_tracks.value[g.id].isNullOrEmpty()) {
                        // Nothing found last time: don't hammer YouTube, retry after a while.
                        now - (emptyAt[g.id] ?: 0L) > RETRY_EMPTY_MS
                    } else {
                        now - (builtAt[g.id] ?: 0L) > MAX_AGE_MS
                    }
                }
                if (todo.isEmpty()) {
                    _progress.value = Progress(Catalog.games.size, Catalog.games.size, false)
                    return@withLock
                }
                var done = Catalog.games.size - todo.size
                _progress.value = Progress(done, Catalog.games.size, true)
                val permits = Semaphore(4)
                todo.map { game ->
                    async {
                        permits.withPermit {
                            val list = loadGame(game)
                            if (list.isNotEmpty()) {
                                _tracks.update { it + (game.id to list) }
                                builtAt[game.id] = System.currentTimeMillis()
                            } else {
                                emptyAt[game.id] = System.currentTimeMillis()
                            }
                            synchronized(this@SoundtrackIndex) { done++ }
                            _progress.value = Progress(done, Catalog.games.size, true)
                        }
                    }
                }.awaitAll()
                _progress.value = Progress(Catalog.games.size, Catalog.games.size, false)
                _tracks.value = shareStrings(_tracks.value)
                writeToDisk()
                TitleTranslator.release()
            }
        }
    }

    private suspend fun loadGame(game: Game): List<Track> = try {
        val source = MusicRepository.gameTracks(game)
        val out = LinkedHashMap<String, Track>()
        val ids = HashSet<String>()
        var next: Any? = null
        var pages = 0
        do {
            val page = source.load(next)
            page.items.forEach { if (it.id !in ids && out.putIfAbsent(Matching.songKey(it.copy(gameId = game.id)), it) == null) ids += it.id }
            next = page.next
            pages++
        } while (next != null && out.size < MAX_TRACKS_PER_GAME && pages < 300)
        out.values.toList()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList() // left out of the index; retried next time
    }

    // ---- Search ----

    data class Hit(val track: Track, val game: Game)

    /** Games whose name matches, e.g. "vii", "ff7", "rebirth", "chrono", "lightnig returns". */
    fun searchGames(query: String): List<Game> {
        val q = Matching.queryTokens(query)
        if (q.isEmpty()) return emptyList()
        val compactQuery = q.joinToString("")
        return AppSettings.shownGames.filter { game ->
            val tokens = Matching.titleTokens(game)
            if (Matching.isAlias(query, game)) return@filter true
            matchesAll(q, tokens, exactNumerals = true) ||
                q.all { w -> if (Matching.isNumeralToken(w)) w in tokens else tokens.any { Matching.wordMatches(w, it) } } ||
                (compactQuery.length >= 4 && tokens.joinToString("").contains(compactQuery))
        }.sortedByDescending { Matching.isAlias(query, it) }
    }

    /** Songs whose title (optionally together with their game's name) matches, best first. */
    fun searchTracks(query: String, limit: Int = 150, stillWanted: () -> Boolean = { true }): List<Hit> {
        // Japanese queries have no Latin words but still match songs' Japanese names.
        if (Matching.queryTokens(query).isEmpty() && !Matching.hasJapanese(query)) return emptyList()
        val hits = mutableListOf<Pair<Int, Hit>>()
        val byId = Catalog.games.associateBy { it.id }
        for ((gameId, list) in _tracks.value) {
            // Typing on has replaced this search: stop instead of finishing work nobody will see.
            if (!stillWanted()) return emptyList()
            if (!AppSettings.isTitleShown(gameId)) continue // turned off in Settings → Titles
            val game = byId[gameId] ?: continue
            val gameTokens = Matching.titleTokens(game)
            for (track in list) {
                val score = Matching.songScore(track, query, gameTokens) ?: continue
                hits += score to Hit(track, game)
            }
        }
        val order = Catalog.games.withIndex().associate { it.value.id to it.index }
        // When some songs match the words as typed, leave out the ones that only matched through
        // a typo or by sound; those are only for when nothing matches as typed.
        val best = hits.maxOfOrNull { it.first } ?: 0
        if (best >= 5) hits.removeAll { it.first <= 2 }
        return hits
            .sortedWith(compareByDescending<Pair<Int, Hit>> { it.first }.thenBy { order[it.second.game.id] ?: 0 })
            .take(limit)
            .map { it.second }
    }

    /**
     * Every query word must start a word in [tokens]. Numerals ("VII") must match exactly when
     * looking up games, so "V" doesn't pull in VI–VIII; in song titles a prefix is fine ("VI" → "Vivi").
     */
    private fun matchesAll(query: List<String>, tokens: List<String>, exactNumerals: Boolean): Boolean =
        query.all { q ->
            if (exactNumerals && Matching.isNumeralToken(q)) tokens.any { it == q } else tokens.any { it.startsWith(q) }
        }

    // ---- Disk ----

    /**
     * Read piece by piece (not the whole file into one big text and object tree first): the
     * index holds thousands of songs, and this keeps the memory it needs at start-up small.
     */
    private fun readFromDisk() {
        val saved = parseFile() ?: return
        builtAt.clear()
        builtAt.putAll(saved.built)
        emptyAt.putAll(saved.empty)
        _tracks.value = shareStrings(saved.tracks)
    }

    private class Saved(val tracks: Map<String, List<Track>>, val built: Map<String, Long>, val empty: Map<String, Long>)

    /** The index file's contents, or null if there's none or it's damaged or from another version. */
    private fun parseFile(): Saved? {
        runCatching {
            if (!file.exists()) return null
            // A damaged or runaway file is ignored (and rebuilt) rather than read into memory.
            if (file.length() > MAX_FILE_BYTES) return null
            val known = Catalog.games.map { it.id }.toSet()
            val map = mutableMapOf<String, List<Track>>()
            val stamps = mutableMapOf<String, Long>()
            val empty = mutableMapOf<String, Long>()
            var version = -1
            android.util.JsonReader(file.bufferedReader()).use { r ->
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "version" -> version = r.nextInt()
                        // Written after the version; an index from another version is rebuilt.
                        "games" -> {
                            if (version != VERSION) return null
                            r.beginObject()
                            while (r.hasNext()) {
                                val id = r.nextName()
                                if (id !in known) { // only the app's current soundtracks
                                    r.skipValue()
                                    continue
                                }
                                val (built, tracks) = readGame(r, id)
                                // A soundtrack that has since been given one official cover shows it
                                // on every song, without rebuilding the index.
                                val cover = Catalog.game(id)?.coverArt
                                map[id] = if (cover == null) tracks else tracks.map { t ->
                                    if (t.thumbnail == cover) t else t.copy(thumbnail = cover)
                                }
                                stamps[id] = built
                            }
                            r.endObject()
                        }
                        // When titles last came back empty, so a restart doesn't search again at once.
                        "empty" -> {
                            r.beginObject()
                            while (r.hasNext()) {
                                val id = r.nextName()
                                val at = r.nextLong()
                                if (id in known) empty[id] = at
                            }
                            r.endObject()
                        }
                        else -> r.skipValue()
                    }
                }
            }
            if (version != VERSION) return null
            return Saved(map, stamps, empty)
        }
        return null
    }

    /**
     * For the on-device tests: saves the index, reads the file back and says whether it holds
     * exactly the songs in memory (the app itself never needs this).
     */
    internal fun savedCopyDifference(): String? {
        val snapshot = _tracks.value
        writeToDisk(snapshot)
        val saved = parseFile() ?: return "the saved file couldn't be read back"
        if (saved.tracks.keys != snapshot.keys) return "titles ${snapshot.keys} vs ${saved.tracks.keys}"
        for ((id, list) in snapshot) {
            val back = saved.tracks.getValue(id)
            if (back.size != list.size) return "$id: ${list.size} songs vs ${back.size}"
            list.zip(back).firstOrNull { (a, b) -> a != b }?.let { (a, b) -> return "$id: $a vs $b" }
        }
        return null
    }

    private fun readGame(r: android.util.JsonReader, id: String): Pair<Long, List<Track>> {
        var built = 0L
        val tracks = ArrayList<Track>()
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "built" -> built = r.nextLong()
                "tracks" -> {
                    r.beginArray()
                    while (r.hasNext()) {
                        readTrack(r)?.copy(gameId = id)?.let(ArtistCredits::fix)?.let(OfficialTitles::fix)?.let(tracks::add)
                    }
                    r.endArray()
                }
                else -> r.skipValue()
            }
        }
        r.endObject()
        return built to tracks.distinctBy { it.id }
    }

    /** One stored song; checked exactly like any other stored song (see [Track.fromJson]). */
    private fun readTrack(r: android.util.JsonReader): Track? {
        val o = JSONObject()
        r.beginObject()
        while (r.hasNext()) {
            val name = r.nextName()
            if (r.peek() == android.util.JsonToken.STRING) o.put(name, r.nextString())
            else if (name == "duration" && r.peek() == android.util.JsonToken.NUMBER) o.put(name, r.nextLong())
            else r.skipValue()
        }
        r.endObject()
        return Track.fromJson(o, tidy = false)
    }

    /** Written piece by piece, straight to the file, for the same reason. */
    private fun writeToDisk(tracks: Map<String, List<Track>> = _tracks.value) {
        runCatching {
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            android.util.JsonWriter(tmp.bufferedWriter()).use { w ->
                w.beginObject()
                w.name("version").value(VERSION.toLong())
                w.name("games").beginObject()
                for ((id, list) in tracks) {
                    w.name(id).beginObject()
                    w.name("built").value(builtAt[id] ?: 0L)
                    w.name("tracks").beginArray()
                    for (t in list) {
                        w.beginObject()
                        w.name("id").value(t.id)
                        w.name("title").value(t.title)
                        w.name("artist").value(t.artist)
                        w.name("duration").value(t.durationSec)
                        w.name("thumb").value(t.thumbnail ?: "")
                        w.name("game").value(t.gameId ?: "")
                        w.name("alt").value(t.altTitle ?: "")
                        w.endObject()
                    }
                    w.endArray()
                    w.endObject()
                }
                w.endObject()
                w.name("empty").beginObject()
                for ((id, at) in emptyAt) if (tracks[id].isNullOrEmpty()) w.name(id).value(at)
                w.endObject()
                w.endObject()
            }
            if (!tmp.renameTo(file)) tmp.delete()
        }
    }
}
