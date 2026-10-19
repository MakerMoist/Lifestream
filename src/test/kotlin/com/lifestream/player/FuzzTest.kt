package com.lifestream.player

import com.lifestream.player.data.AppDirs
import com.lifestream.player.data.Library
import com.lifestream.player.data.Track
import com.lifestream.player.playback.SafeInput
import com.lifestream.player.ui.components.ImageLoader
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.random.Random

/** Hostile and malformed input: every check has to hold, and nothing may crash. */
class FuzzTest {
    @Test
    fun addressChecksCantBeTricked() {
        val goodArt = listOf(
            "https://i.ytimg.com/vi/AbCdEfGhIjK/hqdefault.jpg",
            "https://lh3.googleusercontent.com/abc=w120-h120-l90-rj",
            "https://yt3.ggpht.com/abc",
        )
        val badArt = listOf(
            "http://i.ytimg.com/vi/x/hq.jpg", // not HTTPS
            "https://i.ytimg.com.evil.com/x", // look-alike host
            "https://evilytimg.com/x",
            "https://evil.com\\@i.ytimg.com/x", // parsers disagree on the host
            "https://evil.com#@i.ytimg.com/x",
            "https://i.ytimg.com@evil.com/x",
            "https://i.ytimg.com:443@evil.com/x",
            "https://user:pass@i.ytimg.com/x",
            "file:///C:/Windows/win.ini",
            "javascript:alert(1)",
            "https://127.0.0.1/x",
            "https://[::1]/x",
            " https://i.ytimg.com/x",
            "https://i.ytimg.com/x\nHost: evil.com",
            "https://i.ytimg.com/x\u0000",
            "https://i.ytimg.com\u3002evil.com/x",
            "https://" + "a".repeat(5000) + ".ytimg.com/x",
        )
        goodArt.forEach { assertEquals(it, SafeInput.safeArtwork(it)) }
        badArt.forEach { assertNull(it, SafeInput.safeArtwork(it)) }

        assertTrue(SafeInput.isGoogleVideoUrl("https://rr3---sn-abc.googlevideo.com/videoplayback?itag=140"))
        listOf(
            "http://rr3---sn-abc.googlevideo.com/videoplayback",
            "https://googlevideo.com.evil.com/x",
            "https://evil.com\\@rr3.googlevideo.com/x",
            "https://rr3.googlevideo.com@evil.com/x",
            "https://notgooglevideo.com/x",
        ).forEach { assertFalse(it, SafeInput.isGoogleVideoUrl(it)) }

        assertTrue(SafeInput.isVideoId("AbCdEfGhIjK"))
        listOf("", "AbCdEfGhIj", "AbCdEfGhIjKl", "../../etc/pa", "AbCdEfGhIj%", "AbCdEfGhIj\n").forEach {
            assertFalse(it, SafeInput.isVideoId(it))
        }
        println("LSTEST Fuzz: address checks hold against ${badArt.size + 5} known tricks")
    }

    @Test
    fun brokenOrHostileFilesAreRefusedQuietly() {
        val dir = Files.createTempDirectory("ls").toFile()
        AppDirs.init(dir, dir)
        Library.init()
        val rnd = Random(42)
        val samples = buildList {
            repeat(300) { add(String(ByteArray(rnd.nextInt(0, 400)) { rnd.nextInt(256).toByte() }, Charsets.ISO_8859_1)) }
            add("[".repeat(200_000)) // nesting deep enough to overflow a naive reader
            add("{\"format\":\"lifestream-playlist\",\"version\":1,\"name\":\"x\",\"tracks\":\"" + "[".repeat(50_000) + "\",\"check\":\"0\"}")
            add("{\"format\":\"lifestream-playlist\",\"version\":1,\"name\":\"\\u202Eevil\",\"tracks\":\"[]\",\"check\":\"0\"}")
            add("null")
            add("{}")
        }
        samples.forEach { assertNull(Library.readImport(it)) }
        // Odd songs in a file are skipped or tidied, never trusted as they are.
        val odd = JSONObject().put("id", "AbCdEfGhIjK").put("title", "a\u202Eb\nc").put("artist", "x".repeat(10_000))
            .put("duration", Long.MAX_VALUE).put("thumb", "file:///C:/x")
        val t = Track.fromJson(odd)!!
        assertFalse(t.title.contains('\u202E') || t.title.contains('\n'))
        assertTrue(t.artist.length <= Track.MAX_TEXT)
        assertTrue(t.durationSec <= 24L * 3600)
        assertNull(t.thumbnail)
        assertNull(Track.fromJson(JSONObject().put("id", "../../../x")))
        println("LSTEST Fuzz: ${samples.size} broken or hostile playlist files refused without a crash")
    }

    @Test
    fun junkPicturesDontCrash() {
        val rnd = Random(7)
        repeat(200) {
            val bytes = ByteArray(rnd.nextInt(0, 2000)) { rnd.nextInt(256).toByte() }
            // A real picture header followed by junk, too.
            if (it % 2 == 0 && bytes.size > 8) byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A).copyInto(bytes)
            // Junk is refused; anything that does happen to read as a picture stays within bounds.
            ImageLoader.decode(bytes, 200)?.let { img -> assertTrue(img.width <= 200 && img.height <= 800) }
        }
        println("LSTEST Fuzz: 200 junk pictures handled without a crash")
    }
}
