package com.lifestream.player.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
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

    override fun onCreate() {
        super.onCreate()
        SongCache.init(this)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(StreamDataSources.factory()))
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

    /** Pauses when the media volume is turned all the way down (switchable in Settings). */
    private class VolumeListener(private val player: ExoPlayer) : Player.Listener {
        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
            if (!AppSettings.pauseAtZeroVolume.value) return
            if ((volume <= 0 || muted) && player.playWhenReady) player.pause()
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
