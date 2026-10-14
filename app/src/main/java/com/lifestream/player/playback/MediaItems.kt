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
            .setArtworkUri(thumbnail?.let { Uri.parse(it) })
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

/** Items sent from the UI lose their URI on the way into the session; rebuild it from the id. */
fun MediaItem.withPlayableUri(): MediaItem =
    buildUpon().setUri(StreamDataSources.trackUri(mediaId)).build()
