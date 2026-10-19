package com.lifestream.player.playback

import com.lifestream.player.data.AppSettings
import com.lifestream.player.data.Library
import com.lifestream.player.data.Matching
import com.lifestream.player.data.Playlist
import com.lifestream.player.data.SoundtrackIndex
import com.lifestream.player.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Repeat settings (the same numbers the Android app's player uses). */
object RepeatMode {
    const val OFF = 0
    const val ONE = 1
    const val ALL = 2
}

data class PlayerUiState(
    val current: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = RepeatMode.OFF,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    /** Queue indexes in the order they'll play (the shuffled order when shuffle is on). */
    val playOrder: List<Int> = emptyList(),
    /** Where the current song sits in [playOrder]. */
    val playPosition: Int = -1,
    /** Whether skipping forward/back lands on another song (follows shuffle order and repeat). */
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val error: String? = null,
)

/**
 * The player: the queue (in play order, shuffled or not), repeat, auto-play and History, playing
 * out of the PC through [AudioEngine] or on a Cast device through [CastOutput]. All of its state
 * changes happen on the UI thread; downloads and decoding run in the background.
 */
object PlayerConnection {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(PlayerUiState())
    /** Everything about playback except the position; changes only when something happens. */
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _position = MutableStateFlow(0L)
    /** Playback position in ms (ticks while playing; only progress bars watch it). */
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _currentId = MutableStateFlow<String?>(null)
    /** The playing song's id; song rows watch this to highlight themselves. */
    val currentId: StateFlow<String?> = _currentId.asStateFlow()

    private val _instantQueue = MutableStateFlow(false)
    /** True while the queue is a New Game mix, which can be saved as a playlist of your own. */
    val instantQueue: StateFlow<Boolean> = _instantQueue.asStateFlow()

    /** The playlist the current queue was started from, if any (see [onAddedToPlaylist]). */
    var playingPlaylistId: String? = null
        private set

    private var queue: MutableList<Track> = mutableListOf()
    private var order: MutableList<Int> = mutableListOf()
    private var pos = -1
    private var shuffle = false
    private var repeat = RepeatMode.OFF
    /** Play (true) or pause (false), whatever the output is doing right now. */
    private var playWhenReady = false
    private var ended = false
    private var durationMs = 0L
    private var error: String? = null
    /** The song loaded in the output (so a second load of the same one is skipped). */
    private var loadedIndex = -1
    private var loadJob: Job? = null
    private var session: Any? = null
    private var ticker: Job? = null

    /** Where audio goes: this PC, or a Cast device while casting. */
    private var output: Output = LocalOutput

    interface Output {
        /** Loads [track] at [startMs], playing if [play]; reports back through [Events]. */
        suspend fun load(track: Track, startMs: Long, play: Boolean): Any
        fun setPlaying(play: Boolean)
        fun seekTo(ms: Long)
        fun positionMs(): Long
        fun stop()
    }

    /** Called (from any thread) by an output about the session it returned from [Output.load]. */
    object Events {
        fun ready(session: Any, durationMs: Long) = scope.launch { onReady(session, durationMs) }
        fun ended(session: Any) = scope.launch { onEnded(session) }
        fun failed(session: Any, error: Throwable) = scope.launch { onError(session, error) }
    }

    // ---- Start-up and shut-down ----

    /** Puts the last queue back (paused) when "Playlist Stays Open" is on. */
    fun connect() {
        startTicker()
        if (!AppSettings.keepQueue.value) return
        scope.launch {
            val saved = withContext(Dispatchers.IO) { QueueStore.load() } ?: return@launch
            if (queue.isNotEmpty()) return@launch
            queue = saved.tracks.toMutableList()
            order = saved.order.toMutableList()
            pos = saved.playPosition
            shuffle = saved.shuffle
            playingPlaylistId = saved.playlistId?.takeIf { Library.playlist(it) != null }
            _instantQueue.value = saved.instant
            playWhenReady = false
            pendingStartMs = saved.positionMs
            loadedIndex = -1
            publish()
            _position.value = saved.positionMs
        }
    }

    /** Where to start the restored song when play is first pressed. */
    private var pendingStartMs = 0L

    /** Saves the queue and stops sound (the app is closing). */
    fun release() {
        saveQueue(now = true)
        output.stop()
    }

    // ---- Commands (UI thread) ----

    /**
     * Plays [tracks] from [startIndex]. Shuffled play always continues from the starting song;
     * [randomStart] starts on a random song instead (the Shuffle buttons).
     */
    fun playTracks(
        tracks: List<Track>,
        startIndex: Int = 0,
        shuffle: Boolean = true,
        randomStart: Boolean = false,
        fromPlaylist: String? = null,
        /** Set by New Game, so the queue can be saved as a playlist. */
        instant: Boolean = false,
    ) {
        val chosen = tracks.getOrNull(startIndex)
        val valid = tracks.filter { SafeInput.isVideoId(it.id) }.distinctBy { it.id }
        if (valid.isEmpty()) return
        fromPlaylist?.let { Library.setLastPlaylist(it) }
        playingPlaylistId = fromPlaylist
        _instantQueue.value = instant
        val start = if (randomStart) valid.indices.random() else valid.indexOf(chosen).coerceAtLeast(0)
        queue = valid.toMutableList()
        this.shuffle = shuffle
        order = buildOrder(start)
        pos = order.indexOf(start)
        playWhenReady = true
        pendingStartMs = 0
        loadCurrent(force = true)
    }

    private fun buildOrder(first: Int): MutableList<Int> =
        if (shuffle) (listOf(first) + queue.indices.filter { it != first }.shuffled()).toMutableList()
        else queue.indices.toMutableList()

    /** "Add to queue": the song plays next, right after the one that's playing. */
    fun addToQueue(track: Track) {
        if (!SafeInput.isVideoId(track.id)) return
        if (queue.isEmpty()) {
            playTracks(listOf(track))
            return
        }
        val existing = queue.indexOfFirst { it.id == track.id }
        if (existing >= 0) {
            // Already queued: move it to play next.
            if (existing == order[pos]) return
            order.remove(existing)
            pos = order.indexOf(currentIndex())
            order.add(pos + 1, existing)
        } else {
            val at = currentIndex() + 1
            insertAt(at, track)
            order.add(pos + 1, at)
        }
        ended = false
        publish()
    }

    /**
     * A song was just added to [playlistId]. If that playlist is what's playing, the song joins the
     * queue too, at a random spot after the current song.
     */
    fun onAddedToPlaylist(playlistId: String, track: Track) {
        if (playlistId != playingPlaylistId || !SafeInput.isVideoId(track.id) || queue.isEmpty()) return
        if (queue.any { it.id == track.id }) return
        val at = queue.size
        insertAt(at, track)
        order.add(((pos + 1)..order.size).random(), at)
        publish()
    }

    /** Puts [track] at queue index [at], keeping the play order pointing at the same songs. */
    private fun insertAt(at: Int, track: Track) {
        queue.add(at, track)
        for (i in order.indices) if (order[i] >= at) order[i] = order[i] + 1
        if (loadedIndex >= at) loadedIndex++
    }

    fun togglePlay() {
        if (queue.isEmpty()) return
        when {
            ended -> {
                // From the top of the play order.
                ended = false
                pos = 0
                playWhenReady = true
                loadCurrent(force = true)
            }
            loadedIndex != currentIndex() -> {
                playWhenReady = true
                loadCurrent(force = true)
            }
            else -> {
                playWhenReady = !playWhenReady
                output.setPlaying(playWhenReady)
                if (playWhenReady) recordPlayed()
                publish()
                saveQueue()
            }
        }
    }

    /** Next song (wrapping round with Repeat All). */
    fun next() {
        if (queue.isEmpty()) return
        val target = when {
            pos + 1 < order.size -> pos + 1
            repeat == RepeatMode.ALL -> 0
            else -> return
        }
        pos = target
        ended = false
        playWhenReady = true
        loadCurrent(force = true)
    }

    /** Back to the start of the song, or (near its start already) the song before. */
    fun previous() {
        if (queue.isEmpty()) return
        if (output.positionMs() > 3_000 || !hasPrevious()) {
            seekTo(0)
            return
        }
        previousTrack()
    }

    fun nextTrack() = next()

    fun previousTrack() {
        if (queue.isEmpty()) return
        pos = when {
            pos > 0 -> pos - 1
            repeat == RepeatMode.ALL -> order.size - 1
            else -> {
                seekTo(0)
                return
            }
        }
        ended = false
        playWhenReady = true
        loadCurrent(force = true)
    }

    fun seekTo(positionMs: Long) {
        if (queue.isEmpty()) return
        if (loadedIndex != currentIndex()) {
            pendingStartMs = positionMs
        } else {
            output.seekTo(positionMs)
        }
        _position.value = positionMs
    }

    fun toggleShuffle() {
        if (queue.isEmpty()) {
            shuffle = !shuffle
            publish()
            return
        }
        val current = currentIndex()
        shuffle = !shuffle
        order = buildOrder(current)
        pos = order.indexOf(current)
        publish()
        saveQueue()
    }

    fun cycleRepeat() {
        repeat = when (repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }
        publish()
        extendIfLast()
    }

    fun skipToQueueItem(index: Int) {
        if (index !in queue.indices) return
        pos = order.indexOf(index)
        ended = false
        playWhenReady = true
        loadCurrent(force = true)
    }

    fun removeFromQueue(index: Int) {
        if (index !in queue.indices) return
        val wasCurrent = index == currentIndex()
        queue.removeAt(index)
        val at = order.indexOf(index)
        order.removeAt(at)
        for (i in order.indices) if (order[i] > index) order[i] = order[i] - 1
        if (loadedIndex > index) loadedIndex-- else if (loadedIndex == index) loadedIndex = -1
        if (queue.isEmpty()) {
            stopAndClear()
            return
        }
        if (at < pos || (at == pos && pos >= order.size)) pos = (pos - 1).coerceAtLeast(0)
        pos = pos.coerceIn(0, order.size - 1)
        if (wasCurrent) loadCurrent(force = true) else publish()
        saveQueue()
    }

    /** Stops playback and empties the queue. */
    fun stopAndClear() {
        loadJob?.cancel()
        output.stop()
        QueueStore.clear()
        queue = mutableListOf()
        order = mutableListOf()
        pos = -1
        loadedIndex = -1
        session = null
        playWhenReady = false
        ended = false
        playingPlaylistId = null
        _instantQueue.value = false
        durationMs = 0
        _position.value = 0
        publish()
    }

    fun clearError() {
        error = null
        _state.update { it.copy(error = null) }
    }

    /**
     * Saves the playing New Game mix (songs in the order they play, including any added by
     * auto-play) as a playlist called [name]. Null if there's nothing to save or no room.
     */
    fun saveInstantPlaylist(name: String): Playlist? {
        if (!_instantQueue.value) return null
        val songs = order.mapNotNull { queue.getOrNull(it) }.ifEmpty { queue }
        val playlist = Library.createPlaylistWith(name, songs) ?: return null
        _instantQueue.value = false
        playingPlaylistId = playlist.id
        Library.setLastPlaylist(playlist.id)
        return playlist
    }

    /** Keeps the current queue for "Playlist Stays Open" (when it's on). */
    fun saveQueue(now: Boolean = false) {
        if (!AppSettings.keepQueue.value) return
        if (queue.isEmpty()) {
            QueueStore.clear()
            return
        }
        QueueStore.save(
            QueueStore.Saved(
                tracks = queue.toList(),
                order = order.toList(),
                playPosition = pos.coerceAtLeast(0),
                positionMs = if (loadedIndex == currentIndex()) output.positionMs() else pendingStartMs,
                shuffle = shuffle,
                playlistId = playingPlaylistId,
                instant = _instantQueue.value,
            ),
            now = now,
        )
    }

    /** Turning "Playlist Stays Open" off forgets the saved queue. */
    fun forgetSavedQueue() = QueueStore.clear()

    // ---- Switching output (casting) ----

    /** Moves playback to [next] (a Cast device, or back to this PC), from the same spot. */
    fun switchOutput(next: Output, play: Boolean = playWhenReady) {
        if (next === output) return
        val at = if (loadedIndex == currentIndex()) output.positionMs() else pendingStartMs
        output.stop()
        output = next
        loadedIndex = -1
        pendingStartMs = at
        playWhenReady = play
        if (queue.isNotEmpty() && play) loadCurrent(force = true) else publish()
    }

    val isCasting: Boolean get() = output !== LocalOutput

    // ---- Internals ----

    private fun currentIndex(): Int = order.getOrNull(pos) ?: -1
    private fun hasNext() = pos + 1 < order.size || (repeat == RepeatMode.ALL && order.isNotEmpty())
    private fun hasPrevious() = pos > 0 || (repeat == RepeatMode.ALL && order.isNotEmpty())

    private fun loadCurrent(force: Boolean) {
        val index = currentIndex()
        val track = queue.getOrNull(index) ?: return
        if (!force && loadedIndex == index) return
        loadJob?.cancel()
        output.stop()
        session = null
        loadedIndex = index
        durationMs = track.durationSec.takeIf { it > 0 }?.times(1000) ?: 0
        val start = pendingStartMs
        pendingStartMs = 0
        _position.value = start
        buffering = true
        error = null
        publish()
        val play = playWhenReady
        loadJob = scope.launch {
            session = try {
                output.load(track, start, play)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                onLoadFailed(track, e)
                return@launch
            }
        }
        extendIfLast()
        saveQueue()
    }

    private var buffering = false

    private fun onReady(s: Any, duration: Long) {
        if (s !== session) return
        buffering = false
        if (duration > 0) durationMs = duration
        if (playWhenReady) recordPlayed()
        publish()
    }

    private fun onEnded(s: Any) {
        if (s !== session) return
        when {
            repeat == RepeatMode.ONE -> {
                loadCurrent(force = true)
            }
            pos + 1 < order.size -> {
                pos++
                loadCurrent(force = true)
            }
            repeat == RepeatMode.ALL && order.isNotEmpty() -> {
                pos = 0
                loadCurrent(force = true)
            }
            else -> {
                // The end of the queue (auto-play adds more before this, when it's on).
                ended = true
                playWhenReady = false
                loadedIndex = -1
                session = null
                _position.value = durationMs
                publish()
            }
        }
    }

    private fun onError(s: Any, e: Throwable) {
        if (s !== session) return
        val track = queue.getOrNull(currentIndex()) ?: return
        onLoadFailed(track, e)
    }

    /** A song that YouTube won't give is skipped; a connection problem pauses with a message. */
    private fun onLoadFailed(track: Track, e: Throwable) {
        buffering = false
        loadedIndex = -1
        session = null
        error = friendlyError(e)
        if (e is StreamUnavailableException && queue.getOrNull(currentIndex())?.id == track.id && hasNext()) {
            publish()
            next()
            return
        }
        playWhenReady = false
        publish()
    }

    /**
     * History lists songs that actually played, not ones that failed and were skipped. Playing the
     * song already at the top of History again doesn't add it twice.
     */
    private fun recordPlayed() {
        val track = queue.getOrNull(currentIndex()) ?: return
        if (Library.recents.value.firstOrNull()?.id == track.id) return
        Library.addRecent(track)
    }

    /**
     * "Auto-Play When Playlist Ends": as the last song in the queue starts, a batch of random songs
     * from across every soundtrack is added after it, so music carries on without a gap.
     */
    private fun extendIfLast() {
        if (!AppSettings.autoPlayAtEnd.value || repeat != RepeatMode.OFF || queue.isEmpty()) return
        if (pos + 1 < order.size) return
        val queued = queue.mapTo(HashSet()) { it.id }
        val picks = randomSongs(25, queued)
        if (picks.isEmpty()) return
        val first = queue.size
        queue.addAll(picks)
        order.addAll((first until queue.size).shuffled())
        publish()
    }

    /** A spread across titles: a random soundtrack, then a random song from it. */
    private fun randomSongs(count: Int, exclude: Set<String>): List<Track> {
        val byGame = SoundtrackIndex.tracks.value.filterKeys { AppSettings.isTitleShown(it) }.values.filter { it.isNotEmpty() }
        if (byGame.isEmpty()) return emptyList()
        val out = LinkedHashMap<String, Track>()
        var attempts = 0
        while (out.size < count && attempts < count * 20) {
            attempts++
            val song = byGame.random().random()
            if (song.id in exclude || song.id in out || !SafeInput.isVideoId(song.id)) continue
            if (song.durationSec in 1L..44L || Matching.isFillerTrack(song.title)) continue
            out[song.id] = song
        }
        return out.values.toList()
    }

    private fun publish() {
        val current = queue.getOrNull(currentIndex())
        _currentId.value = current?.id
        _state.value = PlayerUiState(
            current = current,
            isPlaying = playWhenReady && !ended && current != null,
            isBuffering = buffering && playWhenReady,
            durationMs = durationMs,
            shuffle = shuffle,
            repeatMode = repeat,
            queue = queue.toList(),
            currentIndex = currentIndex(),
            playOrder = order.toList(),
            playPosition = pos,
            hasNext = hasNext(),
            hasPrevious = hasPrevious(),
            error = error,
        )
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                // No wake-ups while nothing on screen shows the position or nothing is playing.
                _position.subscriptionCount.first { it > 0 }
                _state.first { it.isPlaying }
                if (loadedIndex == currentIndex() && session != null) _position.value = output.positionMs()
                delay(500)
            }
        }
    }

    private fun friendlyError(e: Throwable): String {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause is StreamHttpException) return "YouTube refused the stream (HTTP ${cause.code})."
            val message = cause.message.orEmpty()
            if ("not a bot" in message || "LOGIN_REQUIRED" in message) {
                return "YouTube is blocking playback on this network for now. Try again later or switch networks."
            }
            if (cause is StreamUnavailableException) return "This song isn't available on YouTube right now, so it was skipped."
            if (cause is javax.sound.sampled.LineUnavailableException) return "Couldn't open the sound device. Check your speakers or headphones."
            cause = cause.cause
        }
        return if (e is IOException) "No connection. Check your network." else "Couldn't play this song."
    }
}

/** Plays out of this PC's speakers. */
object LocalOutput : PlayerConnection.Output {
    private val engine = AudioEngine(object : AudioEngine.Listener {
        override fun onReady(session: Any, durationMs: Long) { PlayerConnection.Events.ready(session, durationMs) }
        override fun onEnded(session: Any) { PlayerConnection.Events.ended(session) }
        override fun onError(session: Any, error: Throwable) { PlayerConnection.Events.failed(session, error) }
    })

    override suspend fun load(track: Track, startMs: Long, play: Boolean): Any {
        // Opened even if this song is skipped meanwhile, so it can be closed again (not left downloading).
        val source = withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { SongStore.open(track.id) }
        if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
            source.close()
            throw kotlinx.coroutines.CancellationException("Skipped")
        }
        return engine.start(source, startMs, play)
    }

    override fun setPlaying(play: Boolean) = engine.setPlaying(play)
    override fun seekTo(ms: Long) = engine.seekTo(ms)
    override fun positionMs(): Long = engine.positionMs()
    override fun stop() = engine.stop()
}
