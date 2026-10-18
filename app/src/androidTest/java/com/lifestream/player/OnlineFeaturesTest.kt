package com.lifestream.player

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lifestream.player.data.Catalog
import com.lifestream.player.data.Matching
import com.lifestream.player.data.Mixes
import com.lifestream.player.data.Recommendations
import com.lifestream.player.data.SoundtrackIndex
import com.lifestream.player.data.Track
import com.lifestream.player.playback.SafeInput
import com.lifestream.player.ui.components.SoftBlur
import com.lifestream.player.update.Updater
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Features that use the network (the soundtrack index from YouTube Music, update checks), plus
 * recommendations and the Now Playing backdrop. Network parts are skipped if the service can't
 * be reached from here.
 */
@RunWith(AndroidJUnit4::class)
class OnlineFeaturesTest {

    @Test
    fun soundtrackIndexBuildsAndSearchFindsItsSongs() {
        SoundtrackIndex.ensureBuilt()
        Log.i("LSTEST", "Soundtrack index test started (${SoundtrackIndex.progress.value})")
        val deadline = System.currentTimeMillis() + 240_000
        while (SoundtrackIndex.tracks.value.size < 6 && System.currentTimeMillis() < deadline) Thread.sleep(1_000)
        val index = SoundtrackIndex.tracks.value
        if (index.isEmpty()) Log.i("LSTEST", "SKIPPED soundtrack index: nothing loaded (${SoundtrackIndex.progress.value})")
        assumeTrue("YouTube Music unreachable from here", index.isNotEmpty())

        var songs = 0
        var japanese = 0
        for ((gameId, list) in index) {
            assertTrue("$gameId is a catalog title", Catalog.games.any { it.id == gameId })
            assertEquals("$gameId: one entry per recording", list.size, list.map { it.id }.toSet().size)
            for (t in list) {
                assertTrue("valid id ${t.id}", SafeInput.isVideoId(t.id))
                assertEquals(gameId, t.gameId)
                assertTrue("artwork from YouTube's servers", t.thumbnail == null || SafeInput.safeArtwork(t.thumbnail) != null)
                if (Matching.hasJapanese(t.title)) japanese++
                songs++
            }
            val one = SoundtrackIndex.songsFor(gameId)!!
            assertEquals(one.size, one.map { Matching.songKey(it) }.toSet().size)
        }

        // Every song can be found by its own name.
        val sample = index.values.flatten().filter { Matching.queryTokens(it.title).isNotEmpty() }.shuffled().take(600)
        for (t in sample) {
            val hits = SoundtrackIndex.searchTracks(t.title, limit = 500)
            assertTrue("'${t.title}' finds itself", hits.any { it.track.id == t.id })
        }
        // A title's page is reachable by its name and shorthand.
        val anyGame = Catalog.game(index.keys.first())!!
        assertTrue(SoundtrackIndex.searchGames(anyGame.title).contains(anyGame))
        assertTrue(SoundtrackIndex.gameOf(index.values.first().first()) != null)
        Log.i("LSTEST", "Soundtrack index: ${index.size} titles, $songs songs ($japanese still in Japanese); search finds every one of ${sample.size} songs checked by name")

        // Saved to the phone and read back exactly (the file is written and read piece by piece).
        assertEquals("saved index reads back the same", null, SoundtrackIndex.savedCopyDifference())

        // Instant Playlist and auto-play draw from it.
        val mix = Mixes.instant()
        assertTrue(mix.size <= Mixes.INSTANT_SIZE)
        assertEquals(mix.size, mix.map { it.id }.toSet().size)
        assertTrue(mix.none { Matching.isFillerTrack(it.title) })
        // Every indexed title that's turned on gets in (one song each before any gets a second).
        val titles = com.lifestream.player.data.AppSettings.shownGames.count { g ->
            SoundtrackIndex.songsFor(g.id)?.any { !Matching.isFillerTrack(it.title) && it.durationSec !in 1L..44L } == true
        }
        assertEquals(minOf(titles, Mixes.INSTANT_SIZE), mix.mapNotNull { it.gameId }.toSet().size)
    }

    @Test
    fun recommendationsSkipWhatYouHeardAndMixTitles() {
        fun t(game: String, n: Int) = Track("rec%s%05d".format(game.take(3).padEnd(3, 'x'), n).take(11), "$game song $n", "A", 200, null, game)
        val index = listOf("ff7", "ff10", "ff6", "ff9", "ff8").associateWith { g -> (0 until 30).map { t(g, it) } }
        val history = (0 until 5).map { index.getValue("ff7")[it] }
        val picks = Recommendations.recommend(history, index, seed = 42, limit = 16)
        assertTrue(picks.isNotEmpty() && picks.size <= 16)
        assertTrue("nothing already heard", picks.none { p -> history.any { it.id == p.id } })
        assertEquals(picks.size, picks.map { it.id }.toSet().size)
        assertTrue("several titles", picks.mapNotNull { it.gameId }.toSet().size >= 2)
        assertEquals("same visit, same picks", picks, Recommendations.recommend(history, index, seed = 42, limit = 16))
    }

    @Test
    fun updateCheckReadsTheRealRelease(): Unit = runBlocking {
        val release = try {
            Updater.fetchLatest()
        } catch (e: IOException) {
            Log.i("LSTEST", "SKIPPED update check: ${e.message}")
            assumeTrue("GitHub unreachable or rate-limited: ${e.message}", false)
            return@runBlocking
        }
        assertTrue(release.versionCode > 0)
        assertTrue(release.sha256.matches(Regex("^[0-9a-f]{64}$")))
        assertTrue(release.apkUrl.startsWith("https://github.com/"))
        Log.i("LSTEST", "Update check: latest release is ${release.versionName} (${release.versionCode})")
    }

    @Test
    fun backdropBlurIsSmoothAndCheap(): Unit = runBlocking {
        val blur = SoftBlur()
        // A flat colour stays the same colour.
        val flat = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 40, 90)) }
        val flatOut = blur.transform(flat, coil.size.Size.ORIGINAL)
        assertEquals(96, flatOut.width)
        assertEquals(Color.rgb(200, 40, 90), flatOut.getPixel(10, 50))
        // A hard edge is softened.
        val edge = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        for (x in 0 until 96) for (y in 0 until 96) edge.setPixel(x, y, if (x < 48) Color.BLACK else Color.WHITE)
        val soft = blur.transform(edge, coil.size.Size.ORIGINAL)
        val mid = Color.red(soft.getPixel(48, 48))
        assertTrue("edge blurred: $mid", mid in 60..200)
        assertEquals(0, Color.red(soft.getPixel(0, 48)))
        assertEquals(255, Color.red(soft.getPixel(95, 48)))
        // Large images are left alone rather than processed.
        val big = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        assertSame(big, blur.transform(big, coil.size.Size.ORIGINAL))
    }
}
