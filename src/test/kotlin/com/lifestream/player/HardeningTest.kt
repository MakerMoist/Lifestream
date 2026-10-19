package com.lifestream.player

import com.lifestream.player.data.writeAtomically
import com.lifestream.player.playback.SongSource
import com.lifestream.player.ui.components.ImageLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.util.zip.CRC32

class HardeningTest {
    @Test
    fun savesReplaceFilesWholeAndLeaveNothingBehind() {
        val dir = Files.createTempDirectory("ls").toFile()
        val f = java.io.File(dir, "playlists.json")
        assertTrue(writeAtomically(f, "first"))
        // Many saves of one file at once (a background save and the one on closing) take turns.
        val threads = (1..8).map { n -> Thread { repeat(20) { writeAtomically(f, "save $n") } } }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertTrue(f.readText().startsWith("save "))
        assertEquals(listOf("playlists.json"), dir.list()!!.toList()) // no temporary files left
        println("LSTEST Storage: overlapping saves leave one whole file and no leftovers")
    }

    /** A PNG header claiming 40000 x 40000 pixels (a "decompression bomb"): refused unread. */
    @Test
    fun hugeImagesAreRefusedBeforeDecoding() {
        fun chunk(out: DataOutputStream, type: String, data: ByteArray) {
            out.writeInt(data.size)
            val body = type.toByteArray() + data
            out.write(body)
            out.writeInt(CRC32().apply { update(body) }.value.toInt())
        }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            val ihdr = ByteArrayOutputStream().also { b ->
                DataOutputStream(b).use { d ->
                    d.writeInt(40_000); d.writeInt(40_000)
                    d.write(byteArrayOf(8, 6, 0, 0, 0))
                }
            }.toByteArray()
            chunk(out, "IHDR", ihdr)
            chunk(out, "IDAT", java.util.zip.Deflater().run {
                setInput(ByteArray(64)); finish()
                val buf = ByteArray(256); val n = deflate(buf); buf.copyOf(n)
            })
            chunk(out, "IEND", ByteArray(0))
        }
        assertNull(ImageLoader.decode(bytes.toByteArray(), 200))
        println("LSTEST Images: a picture claiming 40000x40000 pixels is refused before decoding")
    }

    @Test
    fun songFilesAreOnlyTidiedOnceNothingHasThemOpen() {
        val dir = Files.createTempDirectory("ls").toFile()
        val file = java.io.File(dir, "AbCdEfGhIjK.140.part").apply { writeBytes(ByteArray(1000)) }
        val source = SongSource("AbCdEfGhIjK", 140, file, 1000, 1000, true)
        var released = 0
        source.onRelease = { released++ }
        val stream = source.open()
        source.close()
        assertEquals(0, released) // still being read
        stream.close()
        assertEquals(1, released)
        stream.close()
        assertEquals(1, released) // once only
        assertFalse(runCatching { source.open() }.isSuccess) // closed sources can't be reopened
        println("LSTEST Songs: files are tidied only after the last reader lets go, exactly once")
    }
}
