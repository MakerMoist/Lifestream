package com.lifestream.player

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lifestream.player.data.AppSettings
import com.lifestream.player.playback.SongCache
import com.lifestream.player.playback.StreamDataSources
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.random.Random

/** Saved songs: written while playing, replayed offline, size limit, off switch, clearing. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class SongCacheTest {
    private val savedLimit = AppSettings.cacheLimitMb.value

    @Before
    fun setUp() {
        if (AppSettings.cacheLimitMb.value == 0) AppSettings.setCacheLimitMb(AppSettings.DEFAULT_CACHE_LIMIT_MB)
        SongCache.applyLimit(AppSettings.cacheLimitMb.value)
        SongCache.clear()
    }

    @After
    fun tearDown() {
        AppSettings.setCacheLimitMb(if (savedLimit in AppSettings.CACHE_LIMIT_CHOICES) savedLimit else AppSettings.DEFAULT_CACHE_LIMIT_MB)
        SongCache.applyLimit(AppSettings.cacheLimitMb.value)
        SongCache.clear()
    }

    private fun song(seed: Int, size: Int) = Random(seed).nextBytes(size)

    private fun from(bytes: ByteArray) = DataSource.Factory { ByteArrayDataSource(bytes) }

    /** Stands in for no connection at all. */
    private val offline = DataSource.Factory {
        object : BaseDataSource(true) {
            override fun open(dataSpec: DataSpec): Long = throw IOException("offline")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = throw IOException("offline")
            override fun getUri() = null
            override fun close() {}
        }
    }

    private fun spec(id: String, position: Long = 0) =
        DataSpec.Builder().setUri(StreamDataSources.trackUri(id)).setPosition(position).build()

    private fun read(upstream: DataSource.Factory, spec: DataSpec, limit: Int = Int.MAX_VALUE): ByteArray {
        val source = SongCache.wrap(upstream).createDataSource()
        val out = ByteArrayOutputStream()
        try {
            source.open(spec)
            val buf = ByteArray(16 * 1024)
            while (out.size() < limit) {
                val n = source.read(buf, 0, minOf(buf.size, limit - out.size()))
                if (n == C.RESULT_END_OF_INPUT) break
                out.write(buf, 0, n)
            }
        } finally {
            source.close()
        }
        return out.toByteArray()
    }

    @Test
    fun playedSongIsSavedAndPlaysOffline() {
        val id = "lstest00001"
        val data = song(1, 400_000)
        assertArrayEquals(data, read(from(data), spec(id)))
        assertTrue("whole song saved", SongCache.isFullyCached(id))
        assertTrue(SongCache.usedBytes() >= data.size)

        // Again with no connection: comes from the phone, including from the middle (a seek).
        assertArrayEquals(data, read(offline, spec(id)))
        assertArrayEquals(data.copyOfRange(123_456, data.size), read(offline, spec(id, 123_456)))

        // The song list's "saved" check mark follows within a few seconds.
        val deadline = System.currentTimeMillis() + 15_000
        while (id !in SongCache.cachedIds.value && System.currentTimeMillis() < deadline) Thread.sleep(200)
        assertTrue("check mark shown", id in SongCache.cachedIds.value)
        Log.i("LSTEST", "Saved songs: a played song is stored and replays offline")
    }

    @Test
    fun partlyPlayedSongIsNotMarkedSaved() {
        val id = "lstest00002"
        val data = song(2, 500_000)
        val part = read(from(data), spec(id), limit = 100_000)
        assertArrayEquals(data.copyOfRange(0, 100_000), part)
        assertFalse(SongCache.isFullyCached(id))
        // Finishing it later completes the saved copy.
        assertArrayEquals(data, read(from(data), spec(id)))
        assertTrue(SongCache.isFullyCached(id))
    }

    @Test
    fun sizeLimitDropsOldestSongsFirst() {
        SongCache.applyLimit(1) // 1 MB
        val a = song(3, 600_000)
        val b = song(4, 600_000)
        val c = song(5, 600_000)
        read(from(a), spec("lstest0000a"))
        read(from(b), spec("lstest0000b"))
        read(from(c), spec("lstest0000c"))
        assertTrue("within the limit: ${SongCache.usedBytes()}", SongCache.usedBytes() <= 1024L * 1024)
        assertTrue("newest kept", SongCache.isFullyCached("lstest0000c"))
        assertFalse("oldest dropped", SongCache.isFullyCached("lstest0000a"))
        // Raising the limit again keeps what's there.
        SongCache.applyLimit(AppSettings.DEFAULT_CACHE_LIMIT_MB)
        assertTrue(SongCache.isFullyCached("lstest0000c"))
        Log.i("LSTEST", "Saved songs: the size limit drops the least recently played first")
    }

    @Test
    fun recentlyReplayedSongSurvivesEviction() {
        SongCache.applyLimit(2) // 2 MB
        val a = song(6, 700_000)
        val b = song(7, 700_000)
        read(from(a), spec("lstest0000d"))
        read(from(b), spec("lstest0000e"))
        Thread.sleep(20)
        read(offline, spec("lstest0000d")) // played again: now the most recent
        read(from(song(8, 700_000)), spec("lstest0000f"))
        assertTrue("replayed song kept", SongCache.isFullyCached("lstest0000d"))
        assertFalse("least recently played dropped", SongCache.isFullyCached("lstest0000e"))
    }

    @Test
    fun offSwitchSavesNothingAndClearsSpace() {
        read(from(song(9, 200_000)), spec("lstest0000g"))
        assertTrue(SongCache.usedBytes() > 0)
        AppSettings.setCacheLimitMb(0)
        SongCache.applyLimit(0)
        assertEquals(0L, SongCache.usedBytes())
        val data = song(10, 200_000)
        assertArrayEquals(data, read(from(data), spec("lstest0000h")))
        assertFalse(SongCache.isFullyCached("lstest0000h"))
        assertEquals(0L, SongCache.usedBytes())
    }

    @Test
    fun clearRemovesEverything() {
        read(from(song(11, 300_000)), spec("lstest0000i"))
        assertTrue(SongCache.isFullyCached("lstest0000i"))
        SongCache.clear()
        assertEquals(0L, SongCache.usedBytes())
        assertFalse(SongCache.isFullyCached("lstest0000i"))
    }

    @Test
    fun audioFormatIsRememberedWithTheSong() {
        val id = "lstest0000j"
        read(from(song(12, 100_000)), spec(id), limit = 50_000)
        SongCache.rememberItag(id, 251)
        assertEquals(251, SongCache.rememberedItag(id))
        SongCache.rememberItag(id, 140)
        assertEquals(140, SongCache.rememberedItag(id))
    }
}
