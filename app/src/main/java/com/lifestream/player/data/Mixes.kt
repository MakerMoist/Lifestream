package com.lifestream.player.data

/** Song mixes shared by the phone screens and Android Auto. */
object Mixes {
    const val INSTANT_SIZE = 50

    /**
     * New Game: a random mix of up to [INSTANT_SIZE] songs from every title that's turned on in
     * Settings (no filler or very short clips), leaving out [excluding]. Empty until the song
     * index has songs.
     *
     * Every title gets its turn: songs are dealt one per title per round, titles in random order,
     * so a title with a few dozen songs is as likely to appear as one with hundreds (picking from
     * all songs pooled together let the biggest soundtracks crowd the small ones out). Works from
     * the catalog, so a newly added soundtrack is included as soon as it's indexed.
     */
    fun instant(excluding: Set<String> = emptySet()): List<Track> {
        val seen = HashSet<String>(excluding)
        val pools = AppSettings.shownGames.shuffled()
            .mapNotNull { game ->
                SoundtrackIndex.songsFor(game.id)
                    ?.filterNot { Matching.isFillerTrack(it.title) || it.durationSec in 1L..44L || it.id in excluding }
                    ?.shuffled()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { ArrayDeque(it) }
            }
        val out = ArrayList<Track>(INSTANT_SIZE)
        while (out.size < INSTANT_SIZE && pools.any { it.isNotEmpty() }) {
            for (pool in pools) {
                if (out.size >= INSTANT_SIZE) break
                while (pool.isNotEmpty()) {
                    val song = pool.removeFirst()
                    if (seen.add(song.id)) {
                        out += song
                        break
                    }
                }
            }
        }
        return out.shuffled()
    }

    /**
     * A New Game-sized playlist that starts with [first]: then [rest] (shuffled, or as listed
     * with [keepOrder]), topped up from New Game's mix of every enabled title until it has
     * [INSTANT_SIZE] songs. Used when a song is started from History or Recommended.
     */
    fun startingWith(first: Track, rest: List<Track>, keepOrder: Boolean = false): List<Track> {
        val seen = hashSetOf(first.id)
        val out = mutableListOf(first)
        for (t in if (keepOrder) rest else rest.shuffled()) {
            if (out.size >= INSTANT_SIZE) break
            if (seen.add(t.id)) out += t
        }
        if (out.size < INSTANT_SIZE) {
            for (t in instant(excluding = seen)) {
                if (out.size >= INSTANT_SIZE) break
                if (seen.add(t.id)) out += t
            }
        }
        return out
    }
}
