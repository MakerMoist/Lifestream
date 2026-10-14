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
import org.json.JSONArray
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
    private const val VERSION = 9
    private const val FILE_NAME = "soundtrack-index.json"
    private const val MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000
    private const val MAX_TRACKS_PER_GAME = 400
    private const val RETRY_EMPTY_MS = 30L * 60 * 1000

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

    /** A soundtrack's songs with one version of each (the original release's, which comes first). */
    fun songsFor(gameId: String): List<Track>? = tracksFor(gameId)?.distinctBy { Matching.songKey(it.title) }

    /** The soundtrack a song belongs to: from the song itself, or by finding it in the index. */
    fun gameOf(track: Track): Game? {
        track.gameId?.let { id -> Catalog.game(id)?.let { return it } }
        val id = _tracks.value.entries.firstOrNull { (_, list) -> list.any { it.id == track.id } }?.key
        return id?.let { Catalog.game(it) }
    }

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
                writeToDisk()
            }
        }
    }

    private suspend fun loadGame(game: Game): List<Track> = try {
        val source = MusicRepository.gameTracks(game)
        val out = LinkedHashMap<String, Track>()
        var next: Any? = null
        var pages = 0
        do {
            val page = source.load(next)
            page.items.forEach { out.putIfAbsent(Matching.songKey(it.title), it) }
            next = page.next
            pages++
        } while (next != null && out.size < MAX_TRACKS_PER_GAME && pages < 60)
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
        return Catalog.games.filter { game ->
            val tokens = Matching.titleTokens(game)
            matchesAll(q, tokens, exactNumerals = true) ||
                q.all { w -> if (Matching.isNumeralToken(w)) w in tokens else tokens.any { Matching.wordMatches(w, it) } } ||
                (compactQuery.length >= 4 && tokens.joinToString("").contains(compactQuery))
        }
    }

    /** Songs whose title (optionally together with their game's name) matches, best first. */
    fun searchTracks(query: String, limit: Int = 150): List<Hit> {
        if (Matching.queryTokens(query).isEmpty()) return emptyList()
        val hits = mutableListOf<Pair<Int, Hit>>()
        val byId = Catalog.games.associateBy { it.id }
        for ((gameId, list) in _tracks.value) {
            val game = byId[gameId] ?: continue
            val gameTokens = Matching.titleTokens(game)
            for (track in list) {
                val score = Matching.searchScore(query, track.title, gameTokens) ?: continue
                hits += score to Hit(track, game)
            }
        }
        val order = Catalog.games.withIndex().associate { it.value.id to it.index }
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

    private fun readFromDisk() {
        runCatching {
            if (!file.exists()) return
            val root = JSONObject(file.readText())
            if (root.optInt("version") != VERSION) return
            val games = root.getJSONObject("games")
            val map = mutableMapOf<String, List<Track>>()
            val stamps = mutableMapOf<String, Long>()
            for (id in games.keys()) {
                if (Catalog.game(id) == null) continue // only the app's own soundtracks
                val g = games.getJSONObject(id)
                val arr = g.getJSONArray("tracks")
                map[id] = (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let(Track::fromJson)?.copy(gameId = id)?.let(ArtistCredits::fix)
                }
                stamps[id] = g.optLong("built")
            }
            builtAt.clear()
            builtAt.putAll(stamps)
            _tracks.value = map
        }
    }

    private fun writeToDisk() {
        runCatching {
            val games = JSONObject()
            for ((id, list) in _tracks.value) {
                val arr = JSONArray()
                list.forEach { arr.put(it.toJson()) }
                games.put(id, JSONObject().put("built", builtAt[id] ?: 0L).put("tracks", arr))
            }
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(JSONObject().put("version", VERSION).put("games", games).toString())
            tmp.renameTo(file)
        }
    }
}
