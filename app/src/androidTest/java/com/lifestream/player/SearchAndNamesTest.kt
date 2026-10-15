package com.lifestream.player

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifestream.player.data.ArtistCredits
import com.lifestream.player.data.Matching
import com.lifestream.player.data.OfficialTitles
import com.lifestream.player.data.Romaji
import com.lifestream.player.data.TitleTranslator
import com.lifestream.player.data.Track
import com.lifestream.player.data.hiResArtwork
import com.lifestream.player.data.tidySongTitle
import com.lifestream.player.playback.SafeInput
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/** Search matching, one-version-per-song keys, official names, translation and input checks. */
@RunWith(AndroidJUnit4::class)
class SearchAndNamesTest {

    // ---- Search ----

    private fun bruteWithin(needle: String, text: String, allowed: Int): Boolean {
        fun ed(a: String, b: String): Int {
            val d = Array(a.length + 1) { IntArray(b.length + 1) }
            for (i in 0..a.length) d[i][0] = i
            for (j in 0..b.length) d[0][j] = j
            for (i in 1..a.length) for (j in 1..b.length) {
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            return d[a.length][b.length]
        }
        if (ed(needle, text) <= allowed) return true
        for (len in (needle.length - allowed)..(needle.length + allowed)) {
            if (len <= 0 || len >= text.length) continue
            for (s in 0..(text.length - len)) if (ed(needle, text.substring(s, s + len)) <= allowed) return true
        }
        return false
    }

    @Test
    fun typoSearchAgreesWithAFullComparison() {
        // The quick pre-check must never hide a real match.
        val random = Random(7)
        val letters = "abcde"
        var matches = 0
        repeat(4000) {
            val needle = String(CharArray(random.nextInt(2, 12)) { letters[random.nextInt(letters.length)] })
            val text = String(CharArray(random.nextInt(1, 24)) { letters[random.nextInt(letters.length)] })
            val allowed = random.nextInt(1, 4)
            val expected = bruteWithin(needle, text, allowed)
            if (expected) matches++
            assertEquals("'$needle' in '$text' ($allowed)", expected, Matching.withinEdits(needle, text, allowed))
        }
        assertTrue(matches > 100)
    }

    @Test
    fun forgivingSongSearch() {
        assertNotNull(Matching.searchScore("one wing", "One-Winged Angel"))
        assertNotNull(Matching.searchScore("sephirot", "Sephiroth"))
        assertNotNull(Matching.searchScore("zanarkan", "To Zanarkand"))
        assertNotNull(Matching.searchScore("air buster", "Airbuster"))
        assertNotNull(Matching.searchScore("liberifatali", "Liberi Fatali"))
        assertNull(Matching.searchScore("chocobo", "One-Winged Angel"))
        // Exact names rank above partial ones.
        val exact = Matching.searchScore("prelude", "Prelude")!!
        val partial = Matching.searchScore("prelude", "Prelude to the Battle")!!
        assertTrue(exact > partial)
    }

    @Test
    fun soundsLikeFindsJapaneseNames() {
        assertTrue(Romaji.soundsLike("midgardsormr", "ミドガルズオルム"))
        assertFalse(Romaji.soundsLike("chocobo", "ミドガルズオルム"))
        val track = Track("aaaaaaaaaaa", "ミドガルズオルム", "", 100, null)
        assertNotNull(Matching.songScore(track, "midgardsormr"))
        assertNotNull(Matching.songScore(track, "ミドガルズ"))
    }

    @Test
    fun oneVersionOfEachSong() {
        val key = Matching.songKey("One-Winged Angel")
        assertEquals(key, Matching.songKey("One-Winged Angel (Remastered)"))
        assertEquals(key, Matching.songKey("01. One-Winged Angel"))
        assertEquals(key, Matching.songKey("FINAL FANTASY VII - One-Winged Angel [Piano Ver.]"))
        // A song sung in another language is its own recording.
        assertNotEquals(Matching.songKey("1000 Words"), Matching.songKey("1000 Words (English Version)"))
        // Soundtracks that name songs "Area - Song" keep the whole name.
        assertNotEquals(
            Matching.songKey("Spring Meadows - Cloud of Anxiety", fullTitle = true),
            Matching.songKey("Spring Meadows - Another Song", fullTitle = true),
        )
    }

    // ---- Names ----

    @Test
    fun uploadTagsAreRemoved() {
        assertEquals("Prelude", tidySongTitle("Prelude (Official Audio)"))
        assertEquals("Prelude", tidySongTitle("[Official] Prelude"))
        assertEquals("Nobuo Uematsu", ArtistCredits.canonical("【公式】植松伸夫TV"))
        assertEquals("Masashi Hamauzu & Mitsuto Suzuki", ArtistCredits.forSong("ff7r", "SQUARE ENIX MUSIC"))
    }

    @Test
    fun officialEnglishNamesAreUsed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val json = JSONObject(context.assets.open("official_titles.json").bufferedReader().use { it.readText() })
        assertTrue("names shipped: ${json.length()}", json.length() > 1000)
        val id = json.keys().next()
        val official = OfficialTitles.forId(id)
        assertNotNull(official)
        assertFalse(Matching.hasJapanese(official!!))
        val fixed = OfficialTitles.fix(Track(id, "日本語の曲名", "", 100, null))
        assertEquals(official, fixed.title)
        assertEquals("日本語の曲名", fixed.altTitle) // the Japanese name stays searchable
        Log.i("LSTEST", "Official English names: ${json.length()} shipped and applied")
    }

    @Test
    fun glossaryTranslatesFinalFantasyNames() {
        assertEquals("One-Winged Angel", TitleTranslator.toEnglish("片翼の天使"))
        assertEquals("To Zanarkand", TitleTranslator.toEnglish("ザナルカンドにて"))
        assertTrue(TitleTranslator.applyGlossary("ビビのテーマ").startsWith("Vivi"))
        assertTrue(TitleTranslator.applyGlossary("ビビ・オルニティア").startsWith("Vivi"))
        // Short names aren't cut out of longer words ("vivid", "vampire").
        assertEquals("ビビッド", TitleTranslator.applyGlossary("ビビッド"))
        assertEquals("ヴァンパイア", TitleTranslator.applyGlossary("ヴァンパイア"))
        assertEquals("Prelude", TitleTranslator.toEnglish("Prelude")) // English stays untouched
    }

    @Test
    fun onDeviceTranslation() {
        // Downloads Google's offline Japanese model (~30 MB) the first time; skipped if unavailable.
        val out = TitleTranslator.toEnglish("静かな森")
        assumeTrue("translation model unavailable here", !Matching.hasJapanese(out))
        assertTrue(out.isNotBlank())
        TitleTranslator.release()
        // Works again after the model was let go.
        assertFalse(Matching.hasJapanese(TitleTranslator.toEnglish("青い海")))
        Log.i("LSTEST", "On-device translation works: 静かな森 → $out")
    }

    // ---- Input checks ----

    @Test
    fun onlyRealVideoIds() {
        assertTrue(SafeInput.isVideoId("dQw4w9WgXcQ"))
        assertFalse(SafeInput.isVideoId("dQw4w9WgXc"))
        assertFalse(SafeInput.isVideoId("dQw4w9WgXc/"))
        assertFalse(SafeInput.isVideoId("../../etc/x"))
        assertFalse(SafeInput.isVideoId(null))
        assertNull(Track.fromJson(JSONObject().put("id", "bad id")))
        val t = Track.fromJson(JSONObject().put("id", "dQw4w9WgXcQ").put("thumb", "http://example.com/a.jpg"))!!
        assertNull("plain-HTTP artwork dropped", t.thumbnail)
    }

    @Test
    fun onlyYouTubeServers() {
        assertTrue(SafeInput.isGoogleVideoHost("rr1---sn-abc.googlevideo.com"))
        assertFalse(SafeInput.isGoogleVideoHost("evilgooglevideo.com"))
        assertFalse(SafeInput.isGoogleVideoHost("googlevideo.com.evil.net"))
        assertNotNull(SafeInput.safeArtwork("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"))
        assertNotNull(SafeInput.safeArtwork("https://lh3.googleusercontent.com/abc=w120-h120"))
        assertNull(SafeInput.safeArtwork("http://i.ytimg.com/vi/x/hqdefault.jpg"))
        assertNull(SafeInput.safeArtwork("https://ytimg.com.evil.net/a.jpg"))
        assertNull(SafeInput.safeArtwork("file:///data/data/com.lifestream.player/a.jpg"))
        assertNull(SafeInput.safeArtwork("content://media/external/images/1"))
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w544-h544-l90-rj",
            hiResArtwork("https://lh3.googleusercontent.com/abc=w120-h120-l90-rj", 544),
        )
    }
}
