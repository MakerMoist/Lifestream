package com.lifestream.player.playback

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
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val error: String? = null,
)

/** The UI's handle on the background player. */
object PlayerConnection {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var connecting: Job? = null
    private var ticker: Job? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val pending = mutableListOf<(MediaController) -> Unit>()

    fun connect(context: Context) {
        if (controller != null || connecting?.isActive == true) return
        val appContext = context.applicationContext
        connecting = scope.launch {
            val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
            val c = try {
                MediaController.Builder(appContext, token).buildAsync().await()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(error = "Couldn't start the player service.") }
                return@launch
            }
            controller = c
            c.addListener(listener)
            sync(c)
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

    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) action(c) else pending += action
    }

    // ---- Commands ----

    fun playTracks(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        withController { c ->
            c.shuffleModeEnabled = shuffle
            val start = if (shuffle) tracks.indices.random() else startIndex.coerceIn(tracks.indices)
            c.setMediaItems(tracks.map { it.toMediaItem() }, start, 0L)
            c.prepare()
            c.play()
        }
    }

    fun playNext(track: Track) = withController { c ->
        if (c.mediaItemCount == 0) {
            playTracks(listOf(track))
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem())
        }
    }

    fun addToQueue(track: Track) = withController { c ->
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
        _state.update { s -> s.copy(positionMs = positionMs) }
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

    fun clearError() = _state.update { it.copy(error = null) }

    // ---- State sync ----

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            controller?.let { sync(it) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.let { Library.addRecent(it.toTrack()) }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(error = friendlyError(error)) }
        }
    }

    private fun sync(c: MediaController) {
        val queue = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toTrack() }
        val duration = c.duration.takeIf { it > 0 }
            ?: c.currentMediaItem?.toTrack()?.durationSec?.takeIf { it > 0 }?.times(1000)
            ?: 0L
        _state.update {
            it.copy(
                connected = true,
                current = c.currentMediaItem?.toTrack(),
                isPlaying = c.isPlaying,
                isBuffering = c.playbackState == Player.STATE_BUFFERING,
                positionMs = c.currentPosition.coerceAtLeast(0),
                durationMs = duration,
                shuffle = c.shuffleModeEnabled,
                repeatMode = c.repeatMode,
                queue = queue,
                currentIndex = c.currentMediaItemIndex,
                error = if (c.playerError == null) null else it.error,
            )
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                controller?.let { c ->
                    if (c.isPlaying) {
                        _state.update { it.copy(positionMs = c.currentPosition.coerceAtLeast(0)) }
                    }
                }
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
