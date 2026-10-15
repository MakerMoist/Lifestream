package com.lifestream.player.data

/** Song mixes shared by the phone screens and Android Auto. */
object Mixes {
    const val INSTANT_SIZE = 50

    /**
     * New Game: a random mix of up to [INSTANT_SIZE] songs from every title that's turned on in
     * Settings (no filler or very short clips), leaving out [excluding]. Empty until the song
     * index has songs.
     */
    fun instant(excluding: Set<String> = emptySet()): List<Track> = AppSettings.shownGames
        .mapNotNull { SoundtrackIndex.songsFor(it.id) }
        .flatten()
        .filterNot { Matching.isFillerTrack(it.title) || it.durationSec in 1L..44L }
        .filterNot { it.id in excluding }
        .shuffled()
        .distinctBy { it.id }
        .take(INSTANT_SIZE)
}
