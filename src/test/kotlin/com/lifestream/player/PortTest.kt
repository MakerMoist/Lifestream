package com.lifestream.player

import com.lifestream.player.data.AppDirs
import com.lifestream.player.data.Library
import com.lifestream.player.data.Track
import com.lifestream.player.playback.LevelerCore
import com.lifestream.player.update.Updater
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class PortTest {
    @Test
    fun versionsCompareLikeReleases() {
        assertTrue(Updater.compareVersions("0.1.10", "0.1.9") > 0)
        assertTrue(Updater.compareVersions("0.1.0a", "0.1.0") > 0)
        assertTrue(Updater.compareVersions("0.2.0", "0.1.9b") > 0)
        assertEquals(0, Updater.compareVersions("0.1.0", "0.1.0"))
        assertTrue(Updater.compareVersions("nonsense", "0.1.0") < 0)
        println("LSTEST Updater: version order checks pass")
    }

    @Test
    fun playlistFilesWorkBothWays() {
        val dir = Files.createTempDirectory("ls").toFile()
        AppDirs.init(dir, dir)
        Library.init()
        val tracks = listOf(
            Track("AbCdEfGhIjK", "One-Winged Angel", "Nobuo Uematsu", 437, "https://i.ytimg.com/vi/AbCdEfGhIjK/hqdefault.jpg", "ff7"),
            Track("Mn37d5ne2Zw", "Stand by Me", "Florence + the Machine", 246, null, "ff15"),
        )
        val p = Library.createPlaylistWith("Road Trip", tracks)!!
        val text = Library.exportText(p.id)!!
        // The same file the Android app writes and reads: format, version, name, tracks and check.
        val o = JSONObject(text)
        assertEquals("lifestream-playlist", o.getString("format"))
        assertEquals(1, o.getInt("version"))
        val back = Library.readImport(text)!!
        assertEquals("Road Trip", back.first)
        assertEquals(tracks.map { it.id }, back.second.map { it.id })
        // A changed file is refused, as on Android.
        assertNull(Library.readImport(text.replace("Road Trip", "Road Trap")))
        println("LSTEST Playlists: export files read back, and edited ones are refused")
    }

    @Test
    fun levelingNeverClips() {
        val core = LevelerCore(48000, 2, startGainDb = 10.0)
        var peak = 0.0
        val frame = DoubleArray(2)
        for (i in 0 until 48000 * 3) {
            val x = 0.99 * Math.sin(2 * Math.PI * 440 * i / 48000.0)
            frame[0] = x
            frame[1] = x
            core.process(frame)
            if (i > 4800) peak = maxOf(peak, Math.abs(frame[0]), Math.abs(frame[1]))
        }
        assertTrue("peak $peak", peak <= 1.0)
        println("LSTEST Leveler: loud audio peaks at %.3f (never clips)".format(peak))
    }
}
