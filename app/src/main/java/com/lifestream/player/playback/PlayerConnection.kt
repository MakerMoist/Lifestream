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
                _state.update { it.copy(error = "Couldn't start the player service.") }
                return@launch
            }
            controller = c
            c.addListener(listener)
            sync(c, rebuildQueue = true)
            pending.forEach { it(c) }
            pending.clear()
            startTicker()
        }
    }

    fun release() {
        ticker?.cancel()
        connecting?.cancel()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        _state.update { it.copy(connected = false) }
    }

    private fun onControllerLost(lost: MediaController) {
        if (controller !== lost) return
        lost.removeListener(listener)
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
        pending += action
        appContext?.let { connect(it) }
    }

    // ---- Commands ----

    fun playTracks(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        // Drop anything without a valid video id (e.g. a corrupted saved favourite).
        val chosen = tracks.getOrNull(startIndex)
        val valid = tracks.filter { SafeInput.isVideoId(it.id) }
        if (valid.isEmpty()) return
        withController { c ->
            c.shuffleModeEnabled = shuffle
            val start = if (shuffle) valid.indices.random() else valid.indexOf(chosen).coerceAtLeast(0)
            c.setMediaItems(valid.map { it.toMediaItem() }, start, 0L)
            c.prepare()
            c.play()
        }
    }

    fun playNext(track: Track) = withController { c ->
        if (!SafeInput.isVideoId(track.id)) return@withController
        if (c.mediaItemCount == 0) {
            playTracks(listOf(track))
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem())
        }
    }

    fun addToQueue(track: Track) = withController { c ->
        if (!SafeInput.isVideoId(track.id)) return@withController
        if (c.mediaItemCount == 0) playTracks(listOf(track)) else c.addMediaItem(track.toMediaItem())
    }

    fun togglePlay() = withController { c ->
        when {
            c.playbackState == Player.STATE_IDLE -> { c.prepare(); c.play() }
            c.playbackState == Player.STATE_ENDED -> { c.seekToDefaultPosition(0); c.play() }
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
        if (index in 0 until it.mediaItemCount) it.removeMediaItem(index)
    }

    /** Stops playback and empties the queue (swiping the mini player away). */
    fun stopAndClear() = withController {
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
            mediaItem?.let { Library.addRecent(it.toTrack()) }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(error = friendlyError(error)) }
        }
    }

    private fun sync(c: MediaController, rebuildQueue: Boolean = false) {
        // Rebuilding a long queue on every event (position, buffering…) made big queues sluggish.
        if (rebuildQueue || queueCache.size != c.mediaItemCount) {
            queueCache = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toTrack() }
        }
        val queue = queueCache
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
            cause = cause.cause
        }
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "No connection. Check your network."
            else -> "Couldn't play this track."
        }
    }
}
