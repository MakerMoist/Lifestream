package com.lifestream.player.playback

import kotlinx.coroutines.flow.first
import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.lifestream.player.data.Library
import com.lifestream.player.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val connected: Boolean = false,
    val current: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
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

/** The UI's handle on the background player. */
object PlayerConnection {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var connecting: Job? = null
    private var ticker: Job? = null

    private val _state = MutableStateFlow(PlayerUiState())
    /** Everything about playback except the position; changes only when something happens. */
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _position = MutableStateFlow(0L)
    /**
     * Playback position in ms. Kept separate from [state] because it ticks while playing: only
     * the progress bars watch it, so screens and song lists don't redraw twice a second.
     */
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _currentId = MutableStateFlow<String?>(null)
    /** The playing song's id; song rows watch this to highlight themselves. */
    val currentId: StateFlow<String?> = _currentId.asStateFlow()

    private val pending = mutableListOf<(MediaController) -> Unit>()
    private const val MAX_PENDING = 16
    private var appContext: Context? = null

    /** The queue as last built; rebuilt only when the playlist itself changes. */
    private var queueCache: List<Track> = emptyList()

    fun connect(context: Context) {
        if (controller != null || connecting?.isActive == true) return
        val appContext = context.applicationContext
        this.appContext = appContext
        connecting = scope.launch {
            val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
            val c = try {
                MediaController.Builder(appContext, token)
                    .setListener(object : MediaController.Listener {
                        // The player service can be stopped by Android while the app stays open;
                        // drop the dead connection so the next command reconnects.
                        override fun onDisconnected(controller: MediaController) {
                            scope.launch { onControllerLost(controller) }
                        }
                    })
                    .buildAsync().await()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Commands waiting for this connection would never run; drop them.
                pending.clear()
                _state.update { it.copy(error = "Couldn't start the player service.") }
                return@launch
            }
            controller = c
            c.addListener(listener)
            sync(c, rebuildQueue = true)
            // One failing command mustn't stop the rest (or crash the app).
            pending.toList().forEach { action -> runCatching { action(c) } }
            pending.clear()
            startTicker()
        }
    }

    fun release() {
        ticker?.cancel()
        connecting?.cancel()
        pending.clear()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        _state.update { it.copy(connected = false) }
    }

    private fun onControllerLost(lost: MediaController) {
        if (controller !== lost) return
        lost.removeListener(listener)
        // A dead connection still holds a thread and binder resources until it's released.
        runCatching { lost.release() }
        controller = null
        _state.update { it.copy(connected = false, isPlaying = false, isBuffering = false) }
    }

    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null && c.isConnected) {
            action(c)
            return
        }
        if (c != null) onControllerLost(c)
        // Only the last few taps matter while (re)connecting; never let this grow without bound.
        if (pending.size >= MAX_PENDING) pending.removeAt(0)
        pending += action
        appContext?.let { connect(it) }
    }

    // ---- Commands ----

    private val _instantQueue = MutableStateFlow(false)

    /** True while the queue is an Instant Playlist, which can be saved as a playlist of your own. */
    val instantQueue: StateFlow<Boolean> = _instantQueue.asStateFlow()

    /**
     * Saves the playing Instant Playlist (songs in the order they play, including any added by
     * auto-play) as a playlist called [name]. Null if there's nothing to save or no room.
     */
    fun saveInstantPlaylist(name: String): com.lifestream.player.data.Playlist? {
        if (!_instantQueue.value) return null
        val s = _state.value
        val songs = s.playOrder.mapNotNull { s.queue.getOrNull(it) }.ifEmpty { s.queue }
        val playlist = Library.createPlaylistWith(name, songs) ?: return null
        _instantQueue.value = false
        playingPlaylistId = playlist.id
        Library.setLastPlaylist(playlist.id)
        return playlist
    }

    /**
     * The first song of the queue LifeStream last started. If the queue is replaced from
     * elsewhere (Android Auto, a headset, the system), what it was started from no longer applies.
     */
    private var queueFirstId: String? = null

    /** The playlist the current queue was started from, if any (see [onAddedToPlaylist]). */
    var playingPlaylistId: String? = null
        private set

    /**
     * Plays [tracks] from [startIndex]. Shuffle is on unless the caller says otherwise (a title's
     * own page plays in soundtrack order); shuffled play always continues from the starting song.
     * [randomStart] starts on a random song instead (the Shuffle buttons).
     */
    fun playTracks(
        tracks: List<Track>,
        startIndex: Int = 0,
        shuffle: Boolean = true,
        randomStart: Boolean = false,
        fromPlaylist: String? = null,
        /** Set by Home's Instant Playlist, so the queue can be saved as a playlist. */
        instant: Boolean = false,
    ) {
        // Drop anything without a valid video id (e.g. a corrupted saved favourite).
        val chosen = tracks.getOrNull(startIndex)
        val valid = tracks.filter { SafeInput.isVideoId(it.id) }
        if (valid.isEmpty()) return
        fromPlaylist?.let { Library.setLastPlaylist(it) }
        withController { c ->
            // Recorded when the queue is actually handed over (this may wait for a reconnect).
            playingPlaylistId = fromPlaylist
            _instantQueue.value = instant
            queueFirstId = valid.first().id
            // Off first, then on after the new queue is in: the player service builds the shuffle
            // order from the starting song when shuffle switches on.
            c.shuffleModeEnabled = false
            val start = if (randomStart) valid.indices.random() else valid.indexOf(chosen).coerceAtLeast(0)
            c.setMediaItems(valid.map { it.toMediaItem() }, start, 0L)
            c.shuffleModeEnabled = shuffle
            c.prepare()
            c.play()
        }
    }

    /** Asks the player service to put queue item [index] at [where] in the shuffled play order. */
    private fun place(c: MediaController, index: Int, where: Int) {
        if (!c.shuffleModeEnabled) return
        if (!c.isSessionCommandAvailable(QueueCommands.placeCommand)) return
        c.sendCustomCommand(QueueCommands.placeCommand, QueueCommands.args(index, where))
    }

    /**
     * A song was just added to [playlistId]. If that playlist is what's playing, the song joins the
     * queue too, at a random spot after the current song.
     */
    fun onAddedToPlaylist(playlistId: String, track: Track) {
        if (playlistId != playingPlaylistId || !SafeInput.isVideoId(track.id)) return
        withController { c ->
            if (c.mediaItemCount == 0) return@withController
            if ((0 until c.mediaItemCount).any { c.getMediaItemAt(it).mediaId == track.id }) return@withController
            val position = ((c.currentMediaItemIndex + 1)..c.mediaItemCount).random()
            c.addMediaItem(position, track.toMediaItem())
            place(c, position, QueueCommands.RANDOM_LATER)
        }
    }

    fun playNext(track: Track) = withController { c ->
        if (!SafeInput.isVideoId(track.id)) return@withController
        if (c.mediaItemCount == 0) {
            playTracks(listOf(track))
        } else {
            val position = c.currentMediaItemIndex + 1
            c.addMediaItem(position, track.toMediaItem())
            place(c, position, QueueCommands.NEXT)
        }
    }

    fun addToQueue(track: Track) = withController { c ->
        if (!SafeInput.isVideoId(track.id)) return@withController
        if (c.mediaItemCount == 0) {
            playTracks(listOf(track))
        } else {
            c.addMediaItem(track.toMediaItem())
            place(c, c.mediaItemCount - 1, QueueCommands.END)
        }
    }

    fun togglePlay() = withController { c ->
        when {
            c.playbackState == Player.STATE_IDLE -> { c.prepare(); c.play() }
            c.playbackState == Player.STATE_ENDED -> {
                // From the top of the play order (with shuffle on, that isn't queue item 0).
                val first = c.currentTimeline.takeUnless { it.isEmpty }?.getFirstWindowIndex(c.shuffleModeEnabled) ?: 0
                c.seekToDefaultPosition(first.coerceAtLeast(0))
                c.play()
            }
            c.isPlaying -> c.pause()
            else -> c.play()
        }
    }

    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }

    /** Always changes track (used by swipes); the buttons keep "restart, then go back" behaviour. */
    fun nextTrack() = withController { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }

    fun previousTrack() = withController {
        if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() else it.seekTo(0)
    }

    fun seekTo(positionMs: Long) = withController {
        it.seekTo(positionMs)
        _position.value = positionMs
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun skipToQueueItem(index: Int) = withController {
        if (index in 0 until it.mediaItemCount) {
            it.seekToDefaultPosition(index)
            it.play()
        }
    }

    fun removeFromQueue(index: Int) = withController {
        if (index !in 0 until it.mediaItemCount) return@withController
        // Taking out the queue's first song is still the same queue (an Instant Playlist stays one).
        if (index == 0 && queueFirstId != null) {
            queueFirstId = if (it.mediaItemCount > 1) it.getMediaItemAt(1).mediaId else null
        }
        it.removeMediaItem(index)
    }

    /** Stops playback and empties the queue (swiping the mini player away). */
    fun stopAndClear() = withController {
        queueFirstId = null
        playingPlaylistId = null
        _instantQueue.value = false
        it.stop()
        it.clearMediaItems()
    }

    fun clearError() = _state.update { it.copy(error = null) }

    // ---- State sync ----

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            controller?.let { sync(it, rebuildQueue = events.contains(Player.EVENT_TIMELINE_CHANGED)) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (controller?.isPlaying == true) recordPlayed(mediaItem)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) recordPlayed(controller?.currentMediaItem)
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(error = friendlyError(error)) }
        }
    }

    /**
     * History lists songs that actually played, not ones that failed and were skipped. Pausing
     * and resuming the song already at the top of History doesn't add it again.
     */
    private fun recordPlayed(item: MediaItem?) {
        val track = item?.toTrack() ?: return
        if (Library.recents.value.firstOrNull()?.id == track.id) return
        Library.addRecent(track)
    }

    private fun sync(c: MediaController, rebuildQueue: Boolean = false) {
        // Rebuilding a long queue on every event (position, buffering…) made big queues sluggish.
        if (rebuildQueue || queueCache.size != c.mediaItemCount) {
            queueCache = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toTrack() }
            if (queueFirstId != null && queueCache.firstOrNull()?.id != queueFirstId) {
                queueFirstId = null
                playingPlaylistId = null
                _instantQueue.value = false
            }
        }
        val queue = queueCache
        // Cheap (a walk over the queue), and it must follow every shuffle-order change.
        val order = QueueCommands.playOrder(c)
        val duration = c.duration.takeIf { it > 0 }
            ?: c.currentMediaItem?.toTrack()?.durationSec?.takeIf { it > 0 }?.times(1000)
            ?: 0L
        _position.value = c.currentPosition.coerceAtLeast(0)
        _currentId.value = c.currentMediaItem?.mediaId
        _state.update {
            it.copy(
                connected = true,
                current = c.currentMediaItem?.toTrack(),
                isPlaying = c.isPlaying,
                isBuffering = c.playbackState == Player.STATE_BUFFERING,
                durationMs = duration,
                shuffle = c.shuffleModeEnabled,
                repeatMode = c.repeatMode,
                queue = queue,
                currentIndex = c.currentMediaItemIndex,
                playOrder = order,
                playPosition = order.indexOf(c.currentMediaItemIndex),
                hasNext = c.hasNextMediaItem(),
                hasPrevious = c.hasPreviousMediaItem(),
                error = if (c.playerError == null) null else it.error,
            )
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                // Sleep (no wake-ups at all) while nothing on screen shows the position — e.g. the
                // app is in the background — and while nothing is playing.
                _position.subscriptionCount.first { it > 0 }
                _state.first { it.isPlaying }
                controller?.let { c -> _position.value = c.currentPosition.coerceAtLeast(0) }
                delay(500)
            }
        }
    }

    private fun friendlyError(error: PlaybackException): String {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is StreamHttpException) return "YouTube refused the stream (HTTP ${cause.code})."
            val message = cause.message.orEmpty()
            if ("not a bot" in message || "LOGIN_REQUIRED" in message) {
                return "YouTube is blocking playback on this network for now. Try again later or switch networks."
            }
            cause = cause.cause
        }
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "No connection. Check your network."
            else -> "Couldn't play this track."
        }
    }
}
