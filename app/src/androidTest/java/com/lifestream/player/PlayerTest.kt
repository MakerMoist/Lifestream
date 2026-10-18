package com.lifestream.player

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifestream.player.data.AppSettings
import com.lifestream.player.data.Track
import com.lifestream.player.playback.AutoLibrary
import com.lifestream.player.playback.PlaybackService
import com.lifestream.player.playback.QueueCommands
import com.lifestream.player.playback.toMediaItem
import com.lifestream.player.playback.toTrack
import com.lifestream.player.playback.withPlayableUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Queue order (shuffle from the chosen song, Add to queue), media items, Android Auto. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlayerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun track(n: Int) = Track("lsqueue%04d".format(n), "Song $n", "Artist", 180, "https://i.ytimg.com/vi/lsqueue%04d/hqdefault.jpg".format(n), "ff7")

    private fun onMain(block: () -> Unit) {
        var error: Throwable? = null
        instrumentation.runOnMainSync { try { block() } catch (t: Throwable) { error = t } }
        error?.let { throw it }
    }

    @Test
    fun shuffledPlayStartsFromTheChosenSongAndPlacesNewSongs() = onMain {
        val player = ExoPlayer.Builder(context).build()
        try {
            player.setMediaItems((0 until 20).map { track(it).toMediaItem() })
            player.seekToDefaultPosition(7)
            player.shuffleModeEnabled = true
            QueueCommands.shuffleFromCurrent(player)
            var order = QueueCommands.playOrder(player)
            assertEquals("starts from the chosen song", 7, order.first())
            assertEquals("every song once", 20, order.toSet().size)

            // Add to queue lands right after the current song (it plays next).
            player.addMediaItem(track(20).toMediaItem())
            QueueCommands.place(player, 20, QueueCommands.NEXT)
            order = QueueCommands.playOrder(player)
            assertEquals(7, order[0])
            assertEquals(20, order[1])

            // The same song can be queued again and again: each copy plays, back to back.
            // (Copies go on the end of the queue list, so the indexes used below don't move.)
            val again = track(20)
            repeat(2) {
                player.addMediaItem(again.toMediaItem())
                QueueCommands.place(player, player.mediaItemCount - 1, QueueCommands.NEXT)
            }
            order = QueueCommands.playOrder(player)
            assertEquals(List(3) { again.id }, order.drop(1).take(3).map { player.getMediaItemAt(it).mediaId })
            player.removeMediaItem(22)
            player.removeMediaItem(21)

            // Placing a song at the end of the play order.
            player.addMediaItem(track(21).toMediaItem())
            QueueCommands.place(player, 21, QueueCommands.END)
            assertEquals(21, QueueCommands.playOrder(player).last())

            // Auto-play's songs go after everything already queued.
            val before = QueueCommands.playOrder(player)
            player.addMediaItems((22 until 30).map { track(it).toMediaItem() })
            QueueCommands.appendShuffled(player, before, (22 until 30).toList())
            val after = QueueCommands.playOrder(player)
            assertEquals(before, after.take(before.size))
            assertEquals((22 until 30).toSet(), after.drop(before.size).toSet())

            // Shuffle off: plain queue order.
            player.shuffleModeEnabled = false
            assertEquals((0 until 30).toList(), QueueCommands.playOrder(player))
        } finally {
            player.release()
        }
    }

    @Test
    fun mediaItemsRoundTrip() {
        val t = track(1)
        val item = t.toMediaItem()
        assertEquals("lifestream", item.localConfiguration!!.uri.scheme)
        val back = item.toTrack()
        assertEquals(t.id, back.id)
        assertEquals(t.title, back.title)
        assertEquals(t.gameId, back.gameId)
        assertEquals(t.durationSec, back.durationSec)
        // Items from outside: anything but a real video id is refused, local artwork stripped.
        assertNull(MediaItem.Builder().setMediaId("not a video id").build().withPlayableUri())
        val sneaky = MediaItem.Builder().setMediaId("dQw4w9WgXcQ").setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder()
                .setArtworkUri(android.net.Uri.parse("file:///data/data/com.lifestream.player/files/playlists.json"))
                .build()
        ).build().withPlayableUri()!!
        assertNull(sneaky.mediaMetadata.artworkUri)
    }

    @Test
    fun androidAutoLibrary() {
        val was = AppSettings.androidAuto.value
        try {
            AppSettings.setAndroidAuto(false)
            val off = AutoLibrary.children(context, AutoLibrary.ROOT)!!
            assertEquals(1, off.size) // just the note saying it's off
            assertNull(AutoLibrary.resolve(listOf(MediaItem.Builder().setMediaId("instant").build()), 0))

            AppSettings.setAndroidAuto(true)
            val tabs = AutoLibrary.children(context, AutoLibrary.ROOT)!!.map { it.mediaMetadata.title.toString() }
            assertEquals(listOf("Home", "Titles", "Playlists", "History"), tabs)
            val titles = AutoLibrary.children(context, "titles")!!
            assertEquals(com.lifestream.player.data.Catalog.games.size, titles.size)
            assertTrue(titles.all { it.mediaMetadata.isBrowsable == true && it.mediaMetadata.artworkUri != null })
            assertNotNull(AutoLibrary.children(context, "home"))
            // Made-up or malformed ids from the car are refused.
            assertNull(AutoLibrary.children(context, "nonsense"))
            assertNull(AutoLibrary.resolve(listOf(MediaItem.Builder().setMediaId("song/title/ff7/../../x").build()), 0))
            assertNull(AutoLibrary.resolve(listOf(MediaItem.Builder().setMediaId("song/" + "a".repeat(500)).build()), 0))
            Log.i("LSTEST", "Android Auto: library tabs, titles and id checks work")
        } finally {
            AppSettings.setAndroidAuto(was)
        }
    }

    @Test
    fun libraryRootAnswersBrowsersButNotRecentRequests() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val browser = androidx.media3.session.MediaBrowser.Builder(context, token).buildAsync().get(20, TimeUnit.SECONDS)
        try {
            val normalFuture = arrayOfNulls<com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.LibraryResult<MediaItem>>>(2)
            onMain {
                normalFuture[0] = browser.getLibraryRoot(null)
                normalFuture[1] = browser.getLibraryRoot(
                    androidx.media3.session.MediaLibraryService.LibraryParams.Builder().setRecent(true).build()
                )
            }
            val normal = normalFuture[0]!!.get(10, TimeUnit.SECONDS)
            val recent = normalFuture[1]!!.get(10, TimeUnit.SECONDS)
            assertEquals(androidx.media3.session.LibraryResult.RESULT_SUCCESS, normal.resultCode)
            assertEquals(AutoLibrary.ROOT, normal.value!!.mediaId)
            assertTrue("recent roots aren't offered", recent.resultCode != androidx.media3.session.LibraryResult.RESULT_SUCCESS)
            // Only the real Android Auto counts as "Android Auto has opened the library".
            assertTrue("the app's own browser isn't Android Auto", !AppSettings.androidAutoSeen.value)
        } finally {
            onMain { browser.release() }
        }
    }

    @Test
    fun playerServiceAcceptsTheAppAndKeepsShuffleCommandPrivate() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val controller = future.get(20, TimeUnit.SECONDS)
        try {
            var available = false
            onMain { available = controller.isSessionCommandAvailable(QueueCommands.placeCommand) }
            assertTrue("LifeStream's own UI gets the shuffle command", available)
            onMain {
                controller.setMediaItems((0 until 5).map { track(it).toMediaItem() }, 2, 0L)
                controller.shuffleModeEnabled = true
            }
            // The service builds the shuffle order from the chosen song.
            Thread.sleep(1_000)
            var first = -1
            var current = -1
            onMain {
                first = controller.currentTimeline.getFirstWindowIndex(true)
                current = controller.currentMediaItemIndex
                assertEquals(Player.REPEAT_MODE_OFF, controller.repeatMode)
            }
            assertEquals(current, first)
            onMain {
                controller.stop()
                controller.clearMediaItems()
            }
            Log.i("LSTEST", "Player service: connects, shuffle starts from the chosen song")
        } finally {
            onMain { controller.release() }
        }
    }
}
