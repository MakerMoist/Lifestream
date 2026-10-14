package com.lifestream.player.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.lifestream.player.data.Track

private const val EXTRA_DURATION = "ls_duration"

fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(StreamDataSources.trackUri(id))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(SafeInput.safeArtwork(thumbnail?.let { Uri.parse(it) }))
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(Bundle().apply { putLong(EXTRA_DURATION, durationSec) })
            .build()
    )
    .build()

fun MediaItem.toTrack(): Track = Track(
    id = mediaId,
    title = mediaMetadata.title?.toString() ?: "Unknown track",
    artist = mediaMetadata.artist?.toString() ?: "",
    durationSec = mediaMetadata.extras?.getLong(EXTRA_DURATION, -1L) ?: -1L,
    thumbnail = mediaMetadata.artworkUri?.toString(),
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
