package com.lifestream.player

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lifestream.player.playback.CastRelay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.Socket

/** The Cast relay answers only requests carrying its key, and only plain GET/HEAD for real ids. */
@RunWith(AndroidJUnit4::class)
class CastRelayTest {
    @After
    fun stop() = CastRelay.stop()

    private fun status(host: String, port: Int, request: String): Int? = runCatching {
        Socket(host, port).use { s ->
            s.soTimeout = 5_000
            s.getOutputStream().write(request.toByteArray())
            val line = s.getInputStream().bufferedReader().readLine() ?: return null
            line.split(' ')[1].toInt()
        }
    }.getOrNull()

    @Test
    fun onlyAnswersItsOwnKey() {
        assertNull("no address while stopped", CastRelay.urlFor("dQw4w9WgXcQ"))
        // No known Cast device: any device on the local network may ask (the emulator is one).
        if (!CastRelay.start(null)) {
            Log.i("LSTEST", "SKIPPED Cast relay: no local network address here")
            return
        }
        val url = CastRelay.urlFor("dQw4w9WgXcQ")!!
        assertNull("only real video ids", CastRelay.urlFor("../etc/x"))
        val host = url.host!!
        val port = url.port
        val key = url.pathSegments[0]
        assertEquals(32, key.length)
        assertEquals(404, status(host, port, "GET /0123456789abcdef0123456789abcdef/dQw4w9WgXcQ HTTP/1.1\r\n\r\n"))
        assertEquals(404, status(host, port, "GET /$key/not-an-id HTTP/1.1\r\n\r\n"))
        assertEquals(405, status(host, port, "POST /$key/dQw4w9WgXcQ HTTP/1.1\r\n\r\n"))
        assertEquals(400, status(host, port, "GET /$key/dQw4w9WgXcQ HTTP/1.1\r\nRange: bytes=9-1\r\n\r\n"))
        assertEquals(400, status(host, port, "nonsense\r\n\r\n"))
        // Stopping closes it: the key and the address stop working.
        CastRelay.stop()
        assertNull(CastRelay.urlFor("dQw4w9WgXcQ"))
        assertNull(status(host, port, "GET /$key/dQw4w9WgXcQ HTTP/1.1\r\n\r\n"))
        // A fresh start makes a new key.
        assertTrue(CastRelay.start(null))
        assertNotNull(CastRelay.urlFor("dQw4w9WgXcQ"))
        assertTrue(CastRelay.urlFor("dQw4w9WgXcQ")!!.pathSegments[0] != key)
        Log.i("LSTEST", "Cast relay: answers only its own key, only GET/HEAD, and closes when casting ends")
    }
}
