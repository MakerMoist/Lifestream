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
    fun playlistLimitIsFifteen() {
        assertEquals(15, Library.MAX_PLAYLISTS)
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
            assertEquals("Masashi Hamauzu & Mitsuto Suzuki", t.artist)
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
}
