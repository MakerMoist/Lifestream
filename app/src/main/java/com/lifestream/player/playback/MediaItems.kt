package com.lifestream.player.playback

import com.lifestream.player.data.hiResArtwork
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.lifestream.player.data.Track

private const val EXTRA_DURATION = "ls_duration"
private const val EXTRA_GAME = "ls_game"

fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(StreamDataSources.trackUri(id))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            // Lock screen / notification art: a sharper album image where the server can size it.
            .setArtworkUri(SafeInput.safeArtwork((hiResArtwork(thumbnail, 544, videoThumbs = false) ?: thumbnail)?.let { Uri.parse(it) }))
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(Bundle().apply {
                putLong(EXTRA_DURATION, durationSec)
                gameId?.let { putString(EXTRA_GAME, it) }
            })
            .build()
    )
    .build()

fun MediaItem.toTrack(): Track = Track(
    id = mediaId,
    // Capped like everything else that's stored (items can come from other controllers).
    title = (mediaMetadata.title?.toString() ?: "Unknown track").take(Track.MAX_TEXT),
    artist = (mediaMetadata.artist?.toString() ?: "").take(Track.MAX_TEXT),
    durationSec = mediaMetadata.extras?.getLong(EXTRA_DURATION, -1L) ?: -1L,
    thumbnail = mediaMetadata.artworkUri?.toString(),
    gameId = mediaMetadata.extras?.getString(EXTRA_GAME),
)

/**
 * Items sent from a controller lose their URI on the way into the session; rebuild it from the
 * id. Returns null for anything that isn't a valid YouTube video id, and strips any artwork that
 * isn't a plain HTTPS URL (so a controller can't point the notification at a local file).
 */
fun MediaItem.withPlayableUri(): MediaItem? {
    if (!SafeInput.isVideoId(mediaId)) return null
    val metadata = mediaMetadata.buildUpon()
        .setArtworkUri(SafeInput.safeArtwork(mediaMetadata.artworkUri))
        .setArtworkData(null, null)
        .build()
    return buildUpon()
        .setUri(StreamDataSources.trackUri(mediaId))
        .setMediaMetadata(metadata)
        .build()
}
