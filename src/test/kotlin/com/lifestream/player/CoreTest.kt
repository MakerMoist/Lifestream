package com.lifestream.player

import com.lifestream.player.data.AppDirs
import com.lifestream.player.data.Catalog
import com.lifestream.player.data.Matching
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class CoreTest {
    @Test
    fun catalogAndArtAreComplete() {
        assertTrue(Catalog.games.size >= 25)
        for (g in Catalog.games) {
            assertTrue("art for ${g.id}", g.art.isNotEmpty() && javaClass.classLoader.getResource(g.art) != null)
        }
        println("LSTEST Catalog: ${Catalog.games.size} titles, every one with its card art")
    }

    @Test
    fun songNamesMatchTheAndroidRules() {
        assertEquals(Matching.songKey("One-Winged Angel"), Matching.songKey("One-Winged Angel (Remastered)"))
        assertEquals(Matching.songKey("Noel's Theme"), Matching.songKey("Noel's Theme_guitar demo version"))
    }

    @Test
    fun storageSurvivesARestart() {
        val dir = Files.createTempDirectory("ls").toFile()
        AppDirs.init(dir, dir)
        val p = com.lifestream.player.data.Prefs.named("t")
        p.edit().putString("a", "b").putInt("n", 3).commit()
        val text = java.io.File(dir, "t.json").readText()
        assertTrue(text.contains("\"a\":\"b\""))
    }
}
