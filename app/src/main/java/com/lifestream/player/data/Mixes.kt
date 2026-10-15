package com.lifestream.player.data

/** Song mixes shared by the phone screens and Android Auto. */
object Mixes {
    const val INSTANT_SIZE = 150

    /**
     * Instant Playlist: a random mix of up to [INSTANT_SIZE] songs from every soundtrack in the
     * song index (no filler or very short clips). Empty until the index has songs.
     */
    fun instant(): List<Track> = Catalog.games
        .mapNotNull { SoundtrackIndex.songsFor(it.id) }
        .flatten()
        .filterNot { Matching.isFillerTrack(it.title) || it.durationSec in 1L..44L }
        .shuffled()
        .distinctBy { it.id }
        .take(INSTANT_SIZE)
}
