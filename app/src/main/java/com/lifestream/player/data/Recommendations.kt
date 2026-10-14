package com.lifestream.player.data

import kotlin.math.ceil
import kotlin.random.Random

/**
 * "Recommended for you" on Home: songs you haven't played yet from the soundtracks you've been
 * listening to, mixed across several titles so one game never fills the whole row.
 */
object Recommendations {

    /** Soundtracks that belong together, so a VII fan also hears Remake, Rebirth and Crisis Core. */
    private val families = listOf(
        setOf("ff7", "ff7r", "ff7int", "ff7rb", "ccr"),
        setOf("ff10", "ff10-2"),
        setOf("ff13", "ff13-2"),
        setOf("ct", "cx"),
    )

    /**
     * @param history recently played songs, newest first
     * @param index every soundtrack's songs (from [SoundtrackIndex])
     * @param seed changes the picks between visits while keeping one visit stable
     */
    fun recommend(
        history: List<Track>,
        index: Map<String, List<Track>>,
        seed: Long,
        limit: Int = 16,
        gameOf: (Track) -> String? = { it.gameId },
    ): List<Track> {
        if (history.isEmpty() || index.isEmpty()) return emptyList()
        val random = Random(seed)

        // How much each soundtrack figures in your history; recent plays count more.
        val weight = HashMap<String, Double>()
        history.forEachIndexed { i, track ->
            val game = gameOf(track) ?: return@forEachIndexed
            weight[game] = (weight[game] ?: 0.0) + 1.0 / (1 + i * 0.15)
        }
        if (weight.isEmpty()) return emptyList()

        // Related soundtracks get a share too, so the mix always spans several titles.
        val related = HashMap<String, Double>()
        for ((game, w) in weight) {
            families.firstOrNull { game in it }?.forEach { other ->
                if (other !in weight) related[other] = maxOf(related[other] ?: 0.0, w * 0.5)
            }
            Catalog.game(game)?.let { g ->
                val composer = g.composer.substringBefore(" ·").substringAfterLast(' ')
                Catalog.games
                    .filter { it.id != game && it.id !in weight && it.composer.contains(composer) }
                    .forEach { related[it.id] = maxOf(related[it.id] ?: 0.0, w * 0.3) }
            }
        }
        val ranked = (weight + related.filterKeys { it !in weight })
            .entries
            .filter { !index[it.key].isNullOrEmpty() }
            .sortedByDescending { it.value * (0.85 + random.nextDouble() * 0.3) }
            .map { it.key }
            .toMutableList()
        // Make sure there are at least a few different titles to draw from.
        if (ranked.size < 4) {
            Catalog.games.map { it.id }
                .filter { it !in ranked && !index[it].isNullOrEmpty() }
                .shuffled(random)
                .take(4 - ranked.size)
                .forEach { ranked += it }
        }
        val games = ranked.take(6)

        // Songs you've already played, by id and by name (one version of each song).
        val heardIds = history.map { it.id }.toSet()
        val heardKeys = history.map { Matching.songKey(it.title) }.toSet()

        val perGame = maxOf(2, ceil(limit / games.size.toDouble()).toInt())
        val pools = games.associateWith { game ->
            index[game].orEmpty()
                .filter { it.id !in heardIds && Matching.songKey(it.title) !in heardKeys }
                .filterNot { Matching.isFillerTrack(it.title) || it.durationSec in 1L..44L }
                .distinctBy { Matching.songKey(it.title) }
                .shuffled(random)
                .take(perGame)
                .map { if (it.gameId == null) it.copy(gameId = game) else it }
                .toMutableList()
        }

        // Take turns between soundtracks, favourite first.
        val out = mutableListOf<Track>()
        val seen = HashSet<String>()
        while (out.size < limit && pools.values.any { it.isNotEmpty() }) {
            for (game in games) {
                val next = pools[game]?.removeFirstOrNull() ?: continue
                if (seen.add(next.id)) out += next
                if (out.size >= limit) break
            }
        }
        return out
    }
}
