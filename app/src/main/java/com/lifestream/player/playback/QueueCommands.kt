package com.lifestream.player.playback

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.SessionCommand

/**
 * Keeps shuffle order sensible. The player shuffles a queue into a random order but doesn't put
 * the current song first, so starting a shuffled queue from a tapped song could end after a few
 * songs, and "Play next" or added songs could land behind the current one. The app asks the
 * player service (which owns the shuffle order) to place songs where they belong.
 */
object QueueCommands {
    /** Custom session command, accepted only from LifeStream's own UI. */
    const val PLACE = "com.lifestream.player.PLACE_IN_SHUFFLE"
    const val ARG_INDEX = "index"
    const val ARG_WHERE = "where"

    const val NEXT = 0 // right after the current song
    const val END = 1 // after everything else
    const val RANDOM_LATER = 2 // somewhere after the current song

    val placeCommand = SessionCommand(PLACE, Bundle.EMPTY)

    fun args(index: Int, where: Int) = Bundle().apply {
        putInt(ARG_INDEX, index)
        putInt(ARG_WHERE, where)
    }

    /** Queue indexes in the order they'll play (shuffled or not). */
    fun playOrder(player: Player): List<Int> {
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

    /** Shuffled play starts from the current song, with everything else in random order after it. */
    @OptIn(UnstableApi::class)
    fun shuffleFromCurrent(player: ExoPlayer) {
        val n = player.mediaItemCount
        if (!player.shuffleModeEnabled || n < 2) return
        val current = player.currentMediaItemIndex.coerceIn(0, n - 1)
        val rest = (0 until n).filter { it != current }.shuffled()
        setOrder(player, listOf(current) + rest)
    }

    /** Moves queue item [index] to [where] in the shuffled play order. */
    @OptIn(UnstableApi::class)
    fun place(player: ExoPlayer, index: Int, where: Int) {
        if (!player.shuffleModeEnabled || index !in 0 until player.mediaItemCount) return
        val order = playOrder(player).filter { it != index }.toMutableList()
        val current = order.indexOf(player.currentMediaItemIndex)
        val position = when (where) {
            NEXT -> current + 1
            END -> order.size
            else -> ((current + 1)..order.size).random()
        }.coerceIn(0, order.size)
        order.add(position, index)
        setOrder(player, order)
    }

    /** Adds [newIndexes] (just appended to the queue) after everything already queued, shuffled. */
    @OptIn(UnstableApi::class)
    fun appendShuffled(player: ExoPlayer, before: List<Int>, newIndexes: List<Int>) {
        if (!player.shuffleModeEnabled) return
        setOrder(player, before + newIndexes.shuffled())
    }

    @OptIn(UnstableApi::class)
    private fun setOrder(player: ExoPlayer, order: List<Int>) {
        // Only a complete order (each queue item exactly once) is valid.
        if (order.size != player.mediaItemCount || order.toSet().size != order.size) return
        player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), System.nanoTime()))
    }
}
