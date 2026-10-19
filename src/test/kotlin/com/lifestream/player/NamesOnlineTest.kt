package com.lifestream.player

import com.lifestream.player.data.Catalog
import com.lifestream.player.data.Matching
import com.lifestream.player.data.MusicRepository
import com.lifestream.player.data.Track
import com.lifestream.player.net.Http
import com.lifestream.player.net.OkHttpDownloader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.nio.file.Files

/**
 * Titles loaded from YouTube Music exactly as the app loads them: English names (no song left in
 * Japanese), the DS soundtracks on Final Fantasy III and IV's pages, and the songs that show the
 * soundtrack's cover. Skipped if YouTube Music can't be reached from here.
 */
class NamesOnlineTest {

    private fun songs(id: String): List<Track> = runBlocking {
        val source = MusicRepository.gameTracks(Catalog.game(id)!!)
        val out = mutableListOf<Track>()
        var next: Any? = null
        var pages = 0
        do {
            val page = source.load(next)
            out += page.items
            next = page.next
            pages++
        } while (next != null && pages < 300)
        // One of each song, as the song index keeps them.
        out.distinctBy { it.id }.distinctBy { Matching.songKey(it) }
    }

    @Test
    fun englishNamesDsSongsAndCovers() {
        Http.init(Files.createTempDirectory("lsnames").toFile())
        NewPipe.init(OkHttpDownloader(Http.extractorClient), Localization("en", "US"), ContentCountry("US"))
        val ff3 = runCatching { songs("ff3") }.getOrElse {
            println("LSTEST SKIPPED names: YouTube Music unreachable (${it.javaClass.simpleName})")
            assumeTrue(false)
            return
        }

        for ((id, list) in listOf("ff3" to ff3, "ff4" to songs("ff4"))) {
            val ds = list.filter { it.title.endsWith("(DS)") }
            println("LSTEST Names: $id has ${list.size} songs, ${ds.size} marked (DS), e.g. ${ds.take(3).joinToString(" / ") { it.title }}")
            assertTrue("$id has its DS songs", ds.size >= 40)
            assertEquals("$id: no song listed twice", list.size, list.map { Matching.songKey(it) }.toSet().size)
            assertTrue("$id: DS songs show the original's cover", ds.all { it.thumbnail == list.first().thumbnail })
        }

        val x2 = songs("ff10-2")
        val realEmotion = x2.first { it.title.equals("real Emotion", ignoreCase = true) }
        assertEquals("real Emotion shows X-2's cover", x2.first().thumbnail, realEmotion.thumbnail)

        val lo = songs("lo")
        assertTrue("Lost Odyssey shows its sharp cover", lo.all { it.thumbnail == Catalog.game("lo")!!.coverArt })

        val japanese = mutableMapOf<String, List<Track>>()
        for (id in listOf("ff14", "ff7r", "ff7rb", "ff15", "ff13-2", "lr")) {
            songs(id).filter { Matching.hasJapanese(it.title) }.takeIf { it.isNotEmpty() }?.let { japanese[id] = it }
        }
        listOf("ff3" to ff3, "ff10-2" to x2, "lo" to lo).forEach { (id, l) ->
            l.filter { Matching.hasJapanese(it.title) }.takeIf { it.isNotEmpty() }?.let { japanese[id] = it }
        }
        println("LSTEST Names: still in Japanese: " + if (japanese.isEmpty()) "none" else
            japanese.entries.joinToString { (g, l) -> "$g ${l.size} (${l.take(3).joinToString(" / ") { "${it.id} ${it.title}" }})" })
        assertTrue("every song has an English name", japanese.isEmpty())
    }
}
