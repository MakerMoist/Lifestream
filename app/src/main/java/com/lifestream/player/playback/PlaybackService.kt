package com.lifestream.player.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executors
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import android.os.Bundle
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.lifestream.player.data.Matching
import com.lifestream.player.data.SoundtrackIndex
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.lifestream.player.MainActivity
import com.lifestream.player.data.AppSettings
import com.lifestream.player.R

/**
 * Runs the player in the background with a media notification, lock-screen controls,
 * headset buttons and audio focus handling.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null

    /** Android Auto's library requests (lists, search) are worked out off the main thread. */
    private val libraryExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    private val serviceScope = MainScope()
    private var otherAudio: OtherAudioListener? = null
    private var idleReleaser: IdleReleaser? = null
    private var recovery: RecoveryListener? = null

    /**
     * Shuffle choice for a queue Android Auto just asked for, applied once that queue has actually
     * arrived in the player (so shuffled play starts from the chosen song). Expires quickly.
     */
    private var pendingShuffle: Boolean? = null
    private var pendingShuffleAt = 0L

    override fun onCreate() {
        super.onCreate()
        SongCache.init(this)

        // The audio path gets the VolumeLeveler, which evens out loudness between songs.
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf<AudioProcessor>(VolumeLeveler()))
                .build()
        }

        val player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(StreamDataSources.factory())
                    // A weak connection gets time to recover before a song counts as failed.
                    .setLoadErrorHandlingPolicy(PatientLoadErrors())
            )
            // Audio is small, so buffer up to 3 minutes ahead: the phone's radio downloads in a few
            // bursts and can sleep in between, instead of waking every few seconds.
            .setLoadControl(
                DefaultLoadControl.Builder()
                    // After running dry, wait for 5 s of audio before carrying on, so a slow
                    // connection doesn't stutter (start-up still begins after 1.5 s).
                    .setBufferDurationsMs(60_000, 180_000, 1_500, 5_000)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            // Lets the player follow the phone's media volume (see VolumeListener).
            .setDeviceVolumeControlEnabled(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(RecoveryListener(player).also { recovery = it })
        player.addListener(VolumeListener(player))
        player.addListener(OtherAudioListener(this, player).also { otherAudio = it })
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                VolumeLeveler.songChanges++ // measure the new song's loudness from scratch
            }

            // Shuffled play always continues from the song that's playing.
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                if (shuffleModeEnabled) QueueCommands.shuffleFromCurrent(player)
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
                val shuffle = pendingShuffle ?: return
                pendingShuffle = null
                if (SystemClock.elapsedRealtime() - pendingShuffleAt > 5_000) return
                // Off then on, so the order is rebuilt from the chosen song.
                player.shuffleModeEnabled = false
                player.shuffleModeEnabled = shuffle
            }
        })
        player.addListener(AutoPlayListener(player))
        player.addListener(IdleReleaser(player).also { idleReleaser = it })

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification)
            }
        )

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaLibrarySession.Builder(this, player, SessionCallback())
            .setSessionActivity(openApp)
            .build()

        // Turning Android Auto on or off in Settings refreshes the car's screens right away.
        serviceScope.launch {
            AppSettings.androidAuto.drop(1).collect {
                session?.notifyChildrenChanged(AutoLibrary.ROOT, Int.MAX_VALUE, null)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Settings → System → "Close Fully From Recent Apps": swiping the app away stops the
        // music and ends the app completely.
        if (AppSettings.closeFromRecents.value) {
            closeCompletely()
            return
        }
        // Keep playing in the background only if something is actually playing; a queue that
        // finished or stopped on an error shouldn't keep the service (and its notification) alive.
        val player = session?.player
        val playing = player != null && player.playWhenReady && player.mediaItemCount > 0 &&
            player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED
        if (!playing) stopSelf()
    }

    /**
     * Stops playback, removes the notification and ends the app's process, like a force stop.
     * The process ends a moment later, so playlist and settings changes still being saved are
     * written first.
     */
    private fun closeCompletely() {
        session?.player?.run {
            playWhenReady = false
            stop()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Handler(Looper.getMainLooper()).postDelayed({
            android.os.Process.killProcess(android.os.Process.myPid())
        }, 700)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        libraryExecutor.shutdownNow()
        otherAudio?.release()
        otherAudio = null
        idleReleaser?.cancel()
        idleReleaser = null
        recovery?.cancel()
        recovery = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** LifeStream itself: its package name and its own app user id (which no other app shares). */
    private fun isOurs(controller: MediaSession.ControllerInfo) =
        controller.packageName == packageName && controller.uid == android.os.Process.myUid()

    private inner class SessionCallback : MediaLibrarySession.Callback {
        /**
         * The service has to be exported for system media controls, so check who's connecting:
         * Lifestream itself (UI + notification) and trusted system controllers (System UI media
         * controls, Bluetooth / car via the platform, anything holding MEDIA_CONTENT_CONTROL).
         * Other apps are turned away.
         */
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val ours = isOurs(controller)
            return when {
                // LifeStream's own UI may also place songs in the shuffle order.
                ours -> MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(
                        MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                            .add(QueueCommands.placeCommand)
                            .build()
                    )
                    .build()
                // System media controls, and Android Auto (which shows only a note while it's
                // turned off in Settings).
                controller.isTrusted || AutoLibrary.isAndroidAuto(this@PlaybackService, controller) ->
                    MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
                else -> MediaSession.ConnectionResult.reject()
            }
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == QueueCommands.PLACE && isOurs(controller)) {
                (session.player as? ExoPlayer)?.let { player ->
                    QueueCommands.place(
                        player,
                        args.getInt(QueueCommands.ARG_INDEX, -1),
                        args.getInt(QueueCommands.ARG_WHERE, QueueCommands.END),
                    )
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.mapNotNull { it.withPlayableUri() }.toMutableList())

        /**
         * A song, Instant Playlist or voice request from Android Auto becomes a full queue; plain
         * songs (from LifeStream itself) go through [onAddMediaItems] as before.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val fromLibrary = mediaItems.size == 1 && !SafeInput.isVideoId(mediaItems[0].mediaId)
            if (fromLibrary) {
                val request = AutoLibrary.resolve(mediaItems, startIndex)
                    ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing to play"))
                // Once the queue is in the player: shuffle (or not), starting from the chosen song.
                pendingShuffle = request.shuffle
                pendingShuffleAt = SystemClock.elapsedRealtime()
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(
                        request.tracks.map { it.toMediaItem() },
                        request.startIndex,
                        C.TIME_UNSET,
                    )
                )
            }
            return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
        }

        // ---- Android Auto's library ----

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // Android Auto is showing LifeStream's own screens (its library and full-screen player).
            if (AutoLibrary.isAndroidAuto(this@PlaybackService, browser)) AppSettings.markAndroidAutoSeen()
            // "Recently played" roots (used to resume playback from system media controls) aren't
            // offered; answering with the whole library there would be wrong.
            if (params?.isRecent == true) {
                return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(LibraryResult.ofItem(AutoLibrary.root(), params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = libraryExecutor.submit(java.util.concurrent.Callable<LibraryResult<ImmutableList<MediaItem>>> {
            val all = AutoLibrary.children(this@PlaybackService, parentId)
            if (all == null) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            else LibraryResult.ofItemList(pageOf(all, page, pageSize), params)
        })

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = libraryExecutor.submit(java.util.concurrent.Callable<LibraryResult<MediaItem>> {
            AutoLibrary.item(this@PlaybackService, mediaId)?.let { LibraryResult.ofItem(it, null) }
                ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        })

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = libraryExecutor.submit(java.util.concurrent.Callable<LibraryResult<Void>> {
            val count = AutoLibrary.search(query)
            // Session calls belong on the main thread.
            android.os.Handler(Looper.getMainLooper()).post {
                session.notifySearchResultChanged(browser, query, count, params)
            }
            LibraryResult.ofVoid(params)
        })

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = libraryExecutor.submit(java.util.concurrent.Callable<LibraryResult<ImmutableList<MediaItem>>> {
            LibraryResult.ofItemList(pageOf(AutoLibrary.searchResults(), page, pageSize), params)
        })

        private fun pageOf(all: List<MediaItem>, page: Int, pageSize: Int): ImmutableList<MediaItem> {
            if (page < 0 || pageSize <= 0 || pageSize == Int.MAX_VALUE) return ImmutableList.copyOf(all)
            val from = page.toLong() * pageSize
            if (from >= all.size) return ImmutableList.of()
            return ImmutableList.copyOf(all.subList(from.toInt(), minOf(all.size, from.toInt() + pageSize)))
        }
    }

    /**
     * After a long pause, lets go of the decoder, the buffered audio and the network connection
     * (the queue and position stay). Pressing play prepares it again from the same spot.
     */
    private class IdleReleaser(private val player: ExoPlayer) : Player.Listener {
        private val handler = Handler(Looper.getMainLooper())
        private val releaseNow = Runnable {
            if (!player.playWhenReady && player.playbackState != Player.STATE_IDLE) player.stop()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            handler.removeCallbacks(releaseNow)
            if (!playWhenReady) handler.postDelayed(releaseNow, RELEASE_AFTER_MS)
        }

        fun cancel() = handler.removeCallbacks(releaseNow)

        private companion object {
            const val RELEASE_AFTER_MS = 15 * 60 * 1000L
        }
    }

    /**
     * "Auto-Play When Playlist Ends": as the last song in the queue starts, a batch of random
     * songs from across every soundtrack is added after it, so music carries on without a gap.
     * If the queue already ended (e.g. the song index wasn't ready yet), it picks up from there.
     */
    private class AutoPlayListener(private val player: ExoPlayer) : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // A brand-new queue is still being set up (its shuffle switches on just after), so
            // adding songs now would get them shuffled in among it; wait until it's playing.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) return
            extend(resume = false)
        }

        // A new queue whose starting song is already its last (e.g. one song, or the last one in a
        // list) gets its follow-on songs once it's actually playing.
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) extend(resume = false)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) extend(resume = true)
        }

        private fun extend(resume: Boolean) {
            if (!AppSettings.autoPlayAtEnd.value) return
            if (player.repeatMode != Player.REPEAT_MODE_OFF || player.mediaItemCount == 0) return
            if (player.hasNextMediaItem()) return
            val queued = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toHashSet()
            val picks = randomSongs(BATCH, queued)
            if (picks.isEmpty()) return
            val before = QueueCommands.playOrder(player)
            val first = player.mediaItemCount
            player.addMediaItems(picks.map { it.toMediaItem() })
            val added = (first until player.mediaItemCount).toList()
            QueueCommands.appendShuffled(player, before, added)
            if (resume) {
                // The queue had already finished: start the first of the new songs.
                val next = QueueCommands.playOrder(player).firstOrNull { it in added } ?: first
                player.seekToDefaultPosition(next)
                player.prepare()
                player.play()
            }
        }

        /**
         * A spread across titles: pick a random soundtrack, then a random song from it, skipping
         * filler, short clips and songs already queued. Only the picked songs are checked (not the
         * whole index), so this stays quick on the main thread.
         */
        private fun randomSongs(count: Int, exclude: Set<String>): List<com.lifestream.player.data.Track> {
            val byGame = SoundtrackIndex.tracks.value
                .filterKeys { AppSettings.isTitleShown(it) }
                .values.filter { it.isNotEmpty() }
            if (byGame.isEmpty()) return emptyList()
            val out = LinkedHashMap<String, com.lifestream.player.data.Track>()
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

        private companion object {
            const val BATCH = 25
        }
    }

    /**
     * Pauses when the media volume is turned all the way down, and plays again when it's turned
     * back up (switchable in Settings). Only a pause made here is undone here: if you pause or
     * play yourself in the meantime, turning the volume up leaves it alone.
     */
    private class VolumeListener(private val player: ExoPlayer) : Player.Listener {
        private var pausedByVolume = false

        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
            if (!AppSettings.pauseAtZeroVolume.value) {
                pausedByVolume = false
                return
            }
            if (volume <= 0 || muted) {
                if (player.playWhenReady) {
                    player.pause()
                    pausedByVolume = true
                }
            } else if (pausedByVolume) {
                pausedByVolume = false
                if (!player.playWhenReady && player.mediaItemCount > 0) {
                    if (player.playbackState == Player.STATE_IDLE) player.prepare() // released while paused
                    player.play()
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) pausedByVolume = false
        }
    }

    /**
     * When another app takes over the sound for good (a video, a game, another music app), the
     * player pauses (short interruptions like calls and navigation are already resumed by the
     * player itself). This waits for that other audio to finish and then carries on. It listens
     * for changes in what's playing on the phone instead of polling, and gives up after a while.
     */
    private class OtherAudioListener(context: Context, private val player: ExoPlayer) : Player.Listener {
        private val audio = context.getSystemService(AudioManager::class.java)
        private val handler = Handler(Looper.getMainLooper())
        private var pausedAt = 0L // when we were pushed aside; 0 = not waiting
        private var otherPlayed = false // has the other app actually played anything yet?
        private var registered = false

        private val check = Runnable { maybeResume() }
        private val callback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                if (pausedAt == 0L) return
                if (audio?.isMusicActive == true) otherPlayed = true
                // Wait for things to settle (a video buffering, the next clip starting).
                handler.removeCallbacks(check)
                handler.postDelayed(check, 3_000)
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                pausedAt = SystemClock.elapsedRealtime()
                otherPlayed = audio?.isMusicActive == true
                if (!registered && audio != null) {
                    audio.registerAudioPlaybackCallback(callback, handler)
                    registered = true
                }
            } else {
                stopWaiting()
            }
        }

        private fun maybeResume() {
            if (pausedAt == 0L) return
            val gone = player.playWhenReady || player.mediaItemCount == 0
            if (gone || SystemClock.elapsedRealtime() - pausedAt > GIVE_UP_AFTER_MS) {
                stopWaiting()
                return
            }
            if (audio?.isMusicActive == true) {
                otherPlayed = true
                return // still playing; the next change brings us back here
            }
            // Only once something else actually played and has now stopped (an app that takes the
            // sound without playing anything, like a camera recording video, doesn't count).
            if (!otherPlayed) return
            stopWaiting()
            if (player.playbackState == Player.STATE_IDLE) player.prepare() // released while paused
            player.play()
        }

        private fun stopWaiting() {
            pausedAt = 0L
            otherPlayed = false
            handler.removeCallbacks(check)
            if (registered) {
                audio?.unregisterAudioPlaybackCallback(callback)
                registered = false
            }
        }

        fun release() = stopWaiting()

        private companion object {
            const val GIVE_UP_AFTER_MS = 2 * 60 * 60 * 1000L // 2 hours
        }
    }

    /**
     * How long a weak connection gets before a load counts as failed: up to 10 tries, waiting a
     * little longer each time (about a minute in all), instead of the player's default 3 quick
     * ones. A song YouTube won't serve, or an expired link, fails straight away so it can be
     * fetched afresh or skipped.
     */
    private class PatientLoadErrors : androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(10) {
        override fun getRetryDelayMsFor(
            info: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo,
        ): Long {
            var cause: Throwable? = info.exception
            while (cause != null) {
                if (cause is StreamUnavailableException) return C.TIME_UNSET
                if (cause is StreamHttpException && cause.code in 400..499) return C.TIME_UNSET
                cause = cause.cause
            }
            val standard = super.getRetryDelayMsFor(info)
            if (standard == C.TIME_UNSET) return C.TIME_UNSET
            return (info.errorCount * 1_500L).coerceAtMost(8_000L)
        }
    }

    /**
     * When a song stops on an error: an expired link is fetched afresh and the song carries on
     * from where it stopped. A connection problem gets several more tries, a few seconds apart,
     * before the song is skipped. If several songs in a row fail (no connection, or YouTube
     * refusing this network) it stops and shows the error instead of racing through the queue.
     */
    private class RecoveryListener(private val player: ExoPlayer) : Player.Listener {
        private val handler = Handler(Looper.getMainLooper())
        private val tries = HashMap<String, Int>()
        private var failedInARow = 0

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                failedInARow = 0
                player.currentMediaItem?.mediaId?.let { tries.remove(it) }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val id = player.currentMediaItem?.mediaId ?: return
            StreamResolver.invalidate(id)
            val attempt = (tries[id] ?: 0) + 1
            tries[id] = attempt
            val connection = isConnectionProblem(error)
            val allowed = if (connection) CONNECTION_RETRIES else 1
            if (attempt <= allowed) {
                // The first retry is immediate (usually an expired link); connection retries wait.
                val wait = if (attempt == 1 && !connection) 0L else RETRY_WAITS_MS[(attempt - 1).coerceAtMost(RETRY_WAITS_MS.lastIndex)]
                handler.postDelayed({
                    // Only if it's still this song, still stopped, and nobody paused meanwhile.
                    if (player.currentMediaItem?.mediaId == id && player.playerError != null && player.playWhenReady) {
                        player.prepare()
                        player.play()
                    }
                }, wait)
            } else if (++failedInARow < MAX_FAILED_IN_A_ROW && player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }

        /** Drops any retry still waiting (the player is going away). */
        fun cancel() = handler.removeCallbacksAndMessages(null)

        private fun isConnectionProblem(error: PlaybackException): Boolean {
            var cause: Throwable? = error
            while (cause != null) {
                if (cause is StreamUnavailableException || cause is StreamHttpException) return false
                cause = cause.cause
            }
            return error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                error.errorCode == PlaybackException.ERROR_CODE_TIMEOUT
        }

        private companion object {
            const val MAX_FAILED_IN_A_ROW = 3
            const val CONNECTION_RETRIES = 4
            val RETRY_WAITS_MS = longArrayOf(2_000, 5_000, 10_000, 20_000)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // A different track is now current; let it earn its own retries later.
            tries.keys.retainAll(setOfNotNull(mediaItem?.mediaId))
        }
    }
}
