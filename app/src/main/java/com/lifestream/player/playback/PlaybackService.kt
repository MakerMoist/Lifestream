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
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import com.google.android.gms.cast.framework.CastContext
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
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
/** How far rewind and fast-forward move. */
private const val SEEK_STEP_MS = 10_000L

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null

    /** The phone's own player, and the Cast device's while casting (see [switchToCast]). */
    private var localPlayer: ExoPlayer? = null
    private var castPlayer: CastPlayer? = null

    /** Android Auto's library requests (lists, search) are worked out off the main thread. */
    private val libraryExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    private val serviceScope = MainScope()
    private var otherAudio: OtherAudioListener? = null
    private var idleReleaser: IdleReleaser? = null
    private var wifiLock: DownloadWifiLock? = null
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
            // Audio is small, so buffer well ahead (see below): the phone's radio downloads in a few
            // bursts and can sleep in between, instead of waking every few seconds.
            .setLoadControl(
                DefaultLoadControl.Builder()
                    // Reads well ahead of the playing song: always at least 5 minutes (most of a
                    // song) and up to 15, so a slow or patchy connection doesn't pause it. After
                    // running dry, wait for 8 s of audio before carrying on so it doesn't stutter
                    // (start-up still begins after 1.5 s). Audio is small: 15 minutes of YouTube's
                    // usual 128-160 kbps is 14-18 MB, so memory is capped at 20 MB.
                    .setBufferDurationsMs(300_000, 900_000, 1_500, 8_000)
                    .setTargetBufferBytes(20 * 1024 * 1024)
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
            // Keeps the phone awake while playing (not the screen). Wi-Fi is kept at full power
            // only while a song is downloading (see DownloadWifiLock), not for the whole song:
            // the player buffers minutes ahead, so it downloads in short bursts.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            // Rewind / fast-forward (shown by Android Auto and media controls) move 10 seconds,
            // matching their "10" icons.
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
            .build()

        player.addListener(RecoveryListener(player).also { recovery = it })
        player.addListener(DownloadWifiLock(this).also { wifiLock = it })
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
        watchNetwork(player)
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
            .setMediaButtonPreferences(mediaButtons())
            .build()
        localPlayer = player
        // Cast starts only while "Cast Support" is on (no Cast framework running otherwise).
        if (AppSettings.castSupport.value) setUpCasting()
        serviceScope.launch {
            AppSettings.castSupport.drop(1).collect { on ->
                if (on) setUpCasting() else endCastSession()
            }
        }

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
        networkWatch?.let { runCatching { getSystemService(android.net.ConnectivityManager::class.java)?.unregisterNetworkCallback(it) } }
        networkWatch = null
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        castPlayer = null
        CastRelay.stop()
        serviceScope.cancel()
        libraryExecutor.shutdownNow()
        otherAudio?.release()
        otherAudio = null
        idleReleaser?.cancel()
        idleReleaser = null
        wifiLock?.release()
        wifiLock = null
        recovery?.cancel()
        recovery = null
        session?.run {
            localPlayer?.takeIf { it !== player }?.release()
            player.release()
            release()
        }
        session = null
        localPlayer = null
        super.onDestroy()
    }

    // ---------------- Casting ----------------

    /**
     * Google Cast, when the phone has Google Play services. Starting a Cast session moves the
     * queue to the Cast device (the phone relays the audio, see [CastRelay]); ending it brings the
     * queue back to the phone, paused, at the same song and spot.
     */
    private fun setUpCasting() {
        if (castPlayer != null) return
        val castContext = runCatching { CastContext.getSharedInstance(this) }.getOrNull() ?: return
        val cast = runCatching {
            CastPlayer(castContext, androidx.media3.cast.DefaultMediaItemConverter(), SEEK_STEP_MS, SEEK_STEP_MS)
        }.getOrNull() ?: return
        castPlayer = cast
        cast.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() = switchToCast()
            override fun onCastSessionUnavailable() = switchToLocal()
        })
        if (cast.isCastSessionAvailable) switchToCast()
    }

    /** Ends casting (the music comes back to the phone, see [switchToLocal]). */
    private fun endCastSession() {
        if (castPlayer == null) return
        runCatching { CastContext.getSharedInstance(this).sessionManager.endCurrentSession(true) }
    }

    @Suppress("DEPRECATION")
    private fun castDeviceAddress(): java.net.InetAddress? = runCatching {
        CastContext.getSharedInstance(this).sessionManager.currentCastSession?.castDevice?.inetAddress
    }.getOrNull()

    /** A queue item as the Cast device plays it: from the phone's relay. Null if it can't be. */
    private fun castItem(item: MediaItem): MediaItem? {
        val url = CastRelay.urlFor(item.mediaId) ?: return null
        return item.buildUpon().setUri(url).setMimeType(CastRelay.MIME).build()
    }

    /** Items coming in from a controller, made playable by whichever player is in charge. */
    private fun forCurrentPlayer(item: MediaItem): MediaItem? =
        if (castPlayer != null && session?.player === castPlayer) castItem(item) else item

    private fun switchToCast() {
        val cast = castPlayer ?: return
        val local = localPlayer ?: return
        val s = session ?: return
        if (s.player === cast) return
        // "Cast Support" is off in Settings: don't take over a session (e.g. one being resumed).
        if (!AppSettings.castSupport.value) {
            endCastSession()
            return
        }
        if (!CastRelay.start(castDeviceAddress())) {
            // Not on the same Wi-Fi as the Cast device: keep playing on the phone.
            android.widget.Toast.makeText(this, "Casting needs the phone on the same Wi-Fi as the Cast device.", android.widget.Toast.LENGTH_LONG).show()
            endCastSession()
            return
        }
        // The queue in the order it's playing (shuffle included), up to 200 songs around this one.
        val order = playOrder(local)
        val here = order.indexOf(local.currentMediaItemIndex).coerceAtLeast(0)
        val from = (here - 20).coerceAtLeast(0)
        val window = order.subList(from, minOf(order.size, from + 200))
        val items = window.mapNotNull { castItem(local.getMediaItemAt(it)) }
        val position = local.currentPosition
        val play = local.playWhenReady
        s.player = cast
        if (items.size == window.size && items.isNotEmpty()) {
            cast.repeatMode = local.repeatMode
            cast.setMediaItems(items, here - from, position)
            cast.playWhenReady = play
            cast.prepare()
        }
        local.playWhenReady = false
        local.stop()
    }

    private fun switchToLocal() {
        val cast = castPlayer ?: return
        val local = localPlayer ?: return
        val s = session ?: return
        if (s.player !== cast) {
            CastRelay.stop()
            return
        }
        val items = (0 until cast.mediaItemCount).map { cast.getMediaItemAt(it) }
            .filter { SafeInput.isVideoId(it.mediaId) }
        val current = cast.currentMediaItem?.mediaId
        val index = items.indexOfFirst { it.mediaId == current }.coerceAtLeast(0)
        val position = cast.currentPosition.coerceAtLeast(0)
        s.player = local
        if (items.isNotEmpty()) {
            local.setMediaItems(items.map { it.toTrack().toMediaItem() }, index, position)
            local.prepare()
        }
        // Back on the phone, paused, so the music doesn't suddenly play out of the phone's speaker.
        local.playWhenReady = false
        CastRelay.stop()
    }

    /** Queue positions in the order they play. */
    private fun playOrder(player: Player): List<Int> {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val shuffle = player.shuffleModeEnabled
        val out = ArrayList<Int>(timeline.windowCount)
        var i = timeline.getFirstWindowIndex(shuffle)
        while (i != C.INDEX_UNSET && out.size < timeline.windowCount) {
            out += i
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, shuffle)
        }
        return out
    }

    private var networkWatch: android.net.ConnectivityManager.NetworkCallback? = null

    /**
     * Moving between Wi-Fi and mobile data: connections made on the old network are dropped at
     * once (instead of hanging until they time out), and a song that failed during the switch
     * carries on from the same spot.
     */
    private fun watchNetwork(player: ExoPlayer) {
        val cm = getSystemService(android.net.ConnectivityManager::class.java) ?: return
        val main = Handler(Looper.getMainLooper())
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            private var current: android.net.Network? = null

            override fun onAvailable(network: android.net.Network) {
                val switched = current != null && current != network
                current = network
                if (!switched) return
                com.lifestream.player.net.Http.client.connectionPool.evictAll()
                main.post {
                    if (player.playerError != null && player.mediaItemCount > 0 && player.playWhenReady) player.prepare()
                }
            }

            // The network in use went away: its connections are dead. (The next network to appear
            // still counts as a switch, since [current] keeps the old one.)
            override fun onLost(network: android.net.Network) {
                if (network == current) com.lifestream.player.net.Http.client.connectionPool.evictAll()
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }.onSuccess { networkWatch = callback }
    }

    /**
     * The control layout for Android Auto and the system media controls: Back and Next always hold
     * the main spots beside play/pause (some cars otherwise put rewind and fast-forward there), with
     * 10-second rewind and fast-forward in the smaller spots next to them.
     */
    private fun mediaButtons(): List<CommandButton> = listOf(
        CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
            .setDisplayName("Previous")
            .setSlots(CommandButton.SLOT_BACK)
            .build(),
        CommandButton.Builder(CommandButton.ICON_NEXT)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
            .setDisplayName("Next")
            .setSlots(CommandButton.SLOT_FORWARD)
            .build(),
        CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .setDisplayName("Back 10 seconds")
            .setSlots(CommandButton.SLOT_BACK_SECONDARY)
            .build(),
        CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .setDisplayName("Forward 10 seconds")
            .setSlots(CommandButton.SLOT_FORWARD_SECONDARY)
            .build(),
    )

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
            Futures.immediateFuture(mediaItems.mapNotNull { it.withPlayableUri()?.let(::forCurrentPlayer) }.toMutableList())

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
                        request.tracks.mapNotNull { forCurrentPlayer(it.toMediaItem()) },
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
            // In Longs, so a huge page size from a controller can't overflow into a crash.
            val to = minOf(all.size.toLong(), from + pageSize).toInt()
            return ImmutableList.copyOf(all.subList(from.toInt(), to))
        }
    }

    /**
     * Holds Wi-Fi at full power while the player is downloading, and lets it save power the rest
     * of the time. (The player's own "network" wake mode held it for as long as music played,
     * though downloads only happen in short bursts minutes apart.) On mobile data it does nothing.
     */
    private class DownloadWifiLock(context: Context) : Player.Listener {
        private val lock = runCatching {
            context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
                ?.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "LifeStream:download")
                ?.apply { setReferenceCounted(false) }
        }.getOrNull()

        override fun onIsLoadingChanged(isLoading: Boolean) {
            runCatching { if (isLoading) lock?.acquire() else release() }
        }

        fun release() {
            runCatching { if (lock?.isHeld == true) lock.release() }
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
