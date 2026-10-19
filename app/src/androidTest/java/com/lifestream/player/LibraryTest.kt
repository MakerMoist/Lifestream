package com.lifestream.player

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifestream.player.data.Library
import com.lifestream.player.data.Track
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Playlists (limit, names, add/remove, saving), history and Home's "Last Playlist". */
@RunWith(AndroidJUnit4::class)
class LibraryTest {
    private val created = mutableListOf<String>()
    private fun track(n: Int) = Track("lstrack%04d".format(n).take(11).padEnd(11, 'x'), "Song $n", "Artist", 180, null)

    @After
    fun cleanUp() {
        created.forEach { Library.deletePlaylist(it) }
    }

    private fun create(name: String) = Library.createPlaylist(name)?.also { created += it.id }

    private fun savedFile(): String {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "playlists.json")
        // Saved in the background a moment after a change.
        Thread.sleep(1_000)
        return file.readText()
    }

    @Test
    fun exportImportAndSort() {
        val p = create("Road Trip")!!
        Library.addToPlaylist(p.id, track(1).copy(title = "Beta", durationSec = 300))
        Library.addToPlaylist(p.id, track(2).copy(title = "alpha", durationSec = 60))
        Library.addToPlaylist(p.id, track(3).copy(title = "Gamma", durationSec = -1))
        val list = Library.playlist(p.id)!!
        fun order(sort: com.lifestream.player.data.PlaylistSort) = Library.sorted(list, sort).map { it.title }
        assertEquals(listOf("alpha", "Beta", "Gamma"), order(com.lifestream.player.data.PlaylistSort.ALPHABETICAL))
        assertEquals(listOf("Gamma", "alpha", "Beta"), order(com.lifestream.player.data.PlaylistSort.RECENTLY_ADDED))
        assertEquals(listOf("Beta", "alpha", "Gamma"), order(com.lifestream.player.data.PlaylistSort.OLDEST_ADDED))
        assertEquals(listOf("Beta", "alpha", "Gamma"), order(com.lifestream.player.data.PlaylistSort.LONGEST))
        assertEquals(listOf("alpha", "Beta", "Gamma"), order(com.lifestream.player.data.PlaylistSort.SHORTEST))
        Library.setSort(p.id, com.lifestream.player.data.PlaylistSort.ALPHABETICAL)
        assertEquals(com.lifestream.player.data.PlaylistSort.ALPHABETICAL, Library.sortOf(p.id))

        // An export comes back as a new playlist with the same songs (and a name of its own).
        val text = Library.exportText(p.id)!!
        val result = Library.importText(text)
        assertTrue(result is Library.ImportResult.Imported)
        val copy = (result as Library.ImportResult.Imported).playlist.also { created += it.id }
        assertEquals("Road Trip (2)", copy.name)
        assertEquals(list.tracks.map { it.id }, copy.tracks.map { it.id })
        // Anything else is refused: a changed export, another kind of file, nonsense.
        assertTrue(Library.importText(text.replace("Beta", "Bets")) is Library.ImportResult.NotAPlaylist)
        assertTrue(Library.importText("[1, 2, 3]") is Library.ImportResult.NotAPlaylist)
        assertTrue(Library.importText("{\"format\":\"something-else\"}") is Library.ImportResult.NotAPlaylist)
        assertTrue(Library.importText("not json at all") is Library.ImportResult.NotAPlaylist)
        Log.i("LSTEST", "Playlists: sort orders, export and import (own files only) work")
    }

    @Test
    fun playlistBasics() {
        val p = create("   My   mix  ")!!
        assertEquals("My mix", p.name) // tidied
        assertTrue(Library.addToPlaylist(p.id, track(1)))
        assertFalse("no duplicates", Library.addToPlaylist(p.id, track(1)))
        assertTrue(Library.addToPlaylist(p.id, track(2)))
        assertEquals(listOf(track(1).id, track(2).id), Library.playlist(p.id)!!.tracks.map { it.id })
        assertEquals(listOf(p.id), Library.playlistsWith(track(2).id).map { it.id })

        Library.removeFromPlaylist(p.id, track(1).id)
        assertEquals(listOf(track(2).id), Library.playlist(p.id)!!.tracks.map { it.id })
        assertTrue(Library.playlistsWith(track(1).id).isEmpty())

        Library.renamePlaylist(p.id, "   ")
        assertEquals("My mix", Library.playlist(p.id)!!.name) // a blank name keeps the old one
        Library.renamePlaylist(p.id, "x".repeat(100))
        assertEquals(Library.MAX_NAME_LENGTH, Library.playlist(p.id)!!.name.length)

        // Written to disk.
        val saved = JSONArray(savedFile())
        val entry = (0 until saved.length()).map { saved.getJSONObject(it) }.first { it.getString("id") == p.id }
        assertEquals(1, entry.getJSONArray("tracks").length())
        Log.i("LSTEST", "Playlists: create, add, remove, rename and save work")
    }

    @Test
    fun playlistLimitIsTwenty() {
        assertEquals(20, Library.MAX_PLAYLISTS)
        while (Library.canCreatePlaylist()) assertNotNull(create("P"))
        assertEquals(Library.MAX_PLAYLISTS, Library.playlists.value.size)
        assertNull(Library.createPlaylist("one too many"))
        assertNull(Library.createPlaylistWith("one too many", listOf(track(1))))
    }

    @Test
    fun instantPlaylistCanBeSaved() {
        val songs = listOf(track(1), track(2), track(1), track(3))
        val p = Library.createPlaylistWith("", songs)!!.also { created += it.id }
        assertEquals("Instant Playlist", p.name)
        assertEquals(3, p.tracks.size) // one copy of each song
    }

    @Test
    fun lastPlaylistIsRememberedAndForgottenWhenDeleted() {
        val p = create("Road trip")!!
        Library.setLastPlaylist(p.id)
        assertEquals(p.id, Library.lastPlaylistId.value)
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("library", android.content.Context.MODE_PRIVATE)
        assertEquals("kept after closing the app", p.id, prefs.getString("last_playlist", null))
        Library.setLastPlaylist("no-such-playlist")
        assertEquals(p.id, Library.lastPlaylistId.value)
        Library.deletePlaylist(p.id)
        created -= p.id
        assertNull(Library.lastPlaylistId.value)
    }

    @Test
    fun pullToRefreshUpdatesStoredSongs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val names = org.json.JSONObject(context.assets.open("official_titles.json").bufferedReader().use { it.readText() })
        val id = names.keys().next()
        val before = Library.recents.value
        try {
            Library.clearRecents()
            // Stored before official names and credits existed: a Japanese name and the label as artist.
            Library.addRecent(Track(id, "日本語の曲名", "SQUARE ENIX MUSIC", 200, null, "ff7r"))
            kotlinx.coroutines.runBlocking { Library.refresh() }
            val t = Library.recents.value.first()
            assertEquals(com.lifestream.player.data.OfficialTitles.forId(id), t.title)
            // The indexed version when the song is in the index, otherwise the soundtrack's credit.
            val indexed = com.lifestream.player.data.SoundtrackIndex.indexed(id)
            assertEquals(indexed?.artist ?: "Masashi Hamauzu & Mitsuto Suzuki", t.artist)
        } finally {
            Library.clearRecents()
            before.reversed().forEach { Library.addRecent(it) }
        }
    }

    @Test
    fun historyKeepsNewestFirstWithoutRepeats() {
        val before = Library.recents.value
        try {
            Library.clearRecents()
            Library.addRecent(track(1))
            Library.addRecent(track(2))
            Library.addRecent(track(1))
            assertEquals(listOf(track(1).id, track(2).id), Library.recents.value.map { it.id })
            repeat(60) { Library.addRecent(track(100 + it)) }
            assertEquals(50, Library.recents.value.size)
        } finally {
            Library.clearRecents()
            before.reversed().forEach { Library.addRecent(it) }
        }
    }

    @Test
    fun titlesTurnedOffStayOutOfSearchAndNewGame() {
        val settings = com.lifestream.player.data.AppSettings
        val before = settings.hiddenTitles.value
        try {
            settings.setTitleShown("fft", false)
            assertFalse(settings.isTitleShown("fft"))
            assertTrue(com.lifestream.player.data.SoundtrackIndex.searchGames("fft").none { it.id == "fft" })
            assertTrue(settings.shownGames.none { it.id == "fft" })
            assertTrue(com.lifestream.player.data.Mixes.instant().none { it.gameId == "fft" })
            settings.setTitleShown("fft", true)
            assertEquals("fft", com.lifestream.player.data.SoundtrackIndex.searchGames("fft").firstOrNull()?.id)
        } finally {
            settings.showAllTitles()
            before.forEach { settings.setTitleShown(it, false) }
        }
        Log.i("LSTEST", "Titles turned off are left out of Search and New Game")
    }

    @Test
    fun savedQueueReadsBack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, "last_queue.json")
        val previous = if (file.exists()) file.readBytes() else null
        try {
            val tracks = (0 until 5).map { track(900 + it) }
            com.lifestream.player.playback.QueueStore.save(
                context,
                com.lifestream.player.playback.QueueStore.Saved(tracks, 3, 42_000L, true, "pl-test", false),
                now = true,
            )
            Thread.sleep(1_000) // written in the background
            val back = com.lifestream.player.playback.QueueStore.load(context)!!
            assertEquals(tracks.map { it.id }, back.tracks.map { it.id })
            assertEquals(3, back.index)
            assertEquals(42_000L, back.positionMs)
            assertTrue(back.shuffle)
            assertEquals("pl-test", back.playlistId)
            // A damaged file is ignored rather than crashing the app.
            file.writeText("{not json")
            assertNull(com.lifestream.player.playback.QueueStore.load(context))
        } finally {
            if (previous != null) file.writeBytes(previous) else file.delete()
        }
        Log.i("LSTEST", "Playlist Stays Open: the saved queue reads back exactly")
    }
}
