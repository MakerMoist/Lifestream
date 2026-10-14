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
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var otherAudio: OtherAudioListener? = null

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
            .setMediaSourceFactory(DefaultMediaSourceFactory(StreamDataSources.factory()))
            // Audio is small, so buffer up to 3 minutes ahead: the phone's radio downloads in a few
            // bursts and can sleep in between, instead of waking every few seconds.
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(60_000, 180_000, 1_500, 3_000)
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

        player.addListener(RecoveryListener(player))
        player.addListener(VolumeListener(player))
        player.addListener(OtherAudioListener(this, player).also { otherAudio = it })
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                VolumeLeveler.songChanges++ // measure the new song's loudness from scratch
            }
        })

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

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        otherAudio?.release()
        otherAudio = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private inner class SessionCallback : MediaSession.Callback {
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
            val ours = controller.packageName == packageName
            return if (ours || controller.isTrusted) {
                MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
            } else {
                MediaSession.ConnectionResult.reject()
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.mapNotNull { it.withPlayableUri() }.toMutableList())
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
                if (!player.playWhenReady && player.mediaItemCount > 0 && player.playbackState != Player.STATE_IDLE) {
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
            val gone = player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_IDLE
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
     * Stream URLs expire and YouTube occasionally rejects a request. On an error we drop the
     * cached URL and retry the same track once from where it stopped; if it fails again we move on.
     */
    private class RecoveryListener(private val player: ExoPlayer) : Player.Listener {
        private val retried = mutableSetOf<String>()

        override fun onPlayerError(error: PlaybackException) {
            val id = player.currentMediaItem?.mediaId ?: return
            StreamResolver.invalidate(id)
            if (retried.add(id)) {
                player.prepare()
                player.play()
            } else if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // A different track is now current; let it earn its own retry later.
            retried.retainAll(setOfNotNull(mediaItem?.mediaId))
        }
    }
}
