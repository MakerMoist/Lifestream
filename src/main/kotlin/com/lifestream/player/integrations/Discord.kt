package com.lifestream.player.integrations

import com.lifestream.player.data.AppSettings
import com.lifestream.player.data.SoundtrackIndex
import com.lifestream.player.data.hiResArtwork
import com.lifestream.player.playback.PlayerConnection
import com.lifestream.player.playback.SafeInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Settings → System → "Discord Integration": shows the playing song under your name in Discord
 * ("Listening to LifeStream"), like Spotify does. Off by default.
 *
 * It talks only to the Discord app on this PC, through its local connection (a Windows named
 * pipe); nothing goes over the network from here. It sends the song's name, artist, title and
 * artwork address while something is playing, and clears it when the music stops or the setting
 * is turned off.
 */
object Discord {
    /**
     * The Discord application ("LifeStream") that the status is shown as. Discord requires one,
     * made in its Developer Portal; until it's set here, the setting does nothing.
     */
    const val APPLICATION_ID = ""

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var wanted: JSONObject? = null
    @Volatile private var dirty = false
    private var worker: Thread? = null

    val available: Boolean get() = APPLICATION_ID.isNotEmpty() && System.getProperty("os.name").lowercase().contains("win")

    fun start() {
        // Without an application id there's nothing Discord would accept: nothing runs at all.
        if (!available) return
        scope.launch {
            combine(PlayerConnection.state, AppSettings.discordIntegration) { s, on -> s to on }
                .distinctUntilChanged()
                .collect { (s, on) ->
                    val track = s.current
                    wanted = if (!on || track == null || !s.isPlaying) null else activity(track, s.durationMs)
                    dirty = true
                    if (on && available) ensureWorker()
                }
        }
        // A seek moves the song's start time: check now and then that Discord's clock still matches.
        scope.launch {
            var lastPosition = -1L
            var lastAt = 0L
            while (true) {
                // Asleep (no wake-ups) while the setting is off or nothing is playing.
                AppSettings.discordIntegration.first { it }
                PlayerConnection.state.first { it.isPlaying }
                delay(5_000)
                val s = PlayerConnection.state.value
                if (!AppSettings.discordIntegration.value || !s.isPlaying || s.current == null) {
                    lastPosition = -1
                    continue
                }
                val now = System.currentTimeMillis()
                val position = PlayerConnection.position.value
                if (lastPosition >= 0 && kotlin.math.abs((position - lastPosition) - (now - lastAt)) > 3_000) {
                    wanted = activity(s.current!!, s.durationMs)
                    dirty = true
                }
                lastPosition = position
                lastAt = now
            }
        }
    }

    private fun activity(track: com.lifestream.player.data.Track, durationMs: Long): JSONObject {
        val now = System.currentTimeMillis()
        val start = now - PlayerConnection.position.value
        val game = SoundtrackIndex.gameOf(track)
        val art = SafeInput.safeArtwork(hiResArtwork(track.thumbnail, 360, videoThumbs = false) ?: track.thumbnail)
        return JSONObject()
            .put("type", 2) // "Listening to"
            .put("details", track.title.take(120).padEnd(2))
            .put("state", (track.artist.ifBlank { "LifeStream" }).take(120).padEnd(2))
            .put(
                "timestamps",
                JSONObject().put("start", start).apply { if (durationMs > 0) put("end", start + durationMs) },
            )
            .put(
                "assets",
                JSONObject().apply {
                    if (art != null) put("large_image", art)
                    put("large_text", (game?.title ?: "LifeStream").take(120).padEnd(2))
                },
            )
    }

    @Synchronized
    private fun ensureWorker() {
        if (worker?.isAlive == true) return
        worker = Thread(::run, "discord").apply { isDaemon = true; start() }
    }

    /** Keeps a connection to Discord while the setting is on, sending the status when it changes. */
    private fun run() {
        var pipe: Pipe? = null
        var lastSent = 0L
        try {
            while (AppSettings.discordIntegration.value || pipe != null) {
                if (!AppSettings.discordIntegration.value) {
                    // Turned off: clear the status, then let go.
                    runCatching { pipe?.setActivity(null) }
                    pipe?.close()
                    pipe = null
                    break
                }
                if (pipe == null) {
                    pipe = Pipe.connect()
                    if (pipe == null) {
                        Thread.sleep(20_000) // Discord isn't running; look again later
                        continue
                    }
                    dirty = true
                }
                try {
                    pipe.pump()
                    // Discord allows only a few updates every 20 seconds.
                    if (dirty && System.currentTimeMillis() - lastSent > 4_000) {
                        dirty = false
                        lastSent = System.currentTimeMillis()
                        pipe.setActivity(wanted)
                    }
                } catch (e: IOException) {
                    pipe.close()
                    pipe = null
                }
                Thread.sleep(250)
            }
        } catch (_: InterruptedException) {
        } finally {
            pipe?.close()
        }
    }

    /** One connection to the Discord app (its local IPC pipe). */
    private class Pipe(private val file: RandomAccessFile) {
        companion object {
            fun connect(): Pipe? {
                for (i in 0..9) {
                    val file = runCatching { RandomAccessFile("\\\\.\\pipe\\discord-ipc-$i", "rw") }.getOrNull() ?: continue
                    val pipe = Pipe(file)
                    try {
                        pipe.write(0, JSONObject().put("v", 1).put("client_id", APPLICATION_ID))
                        // Wait for Discord's READY (or an error) before anything else.
                        val until = System.currentTimeMillis() + 5_000
                        while (System.currentTimeMillis() < until) {
                            val frame = pipe.read()
                            if (frame != null) {
                                if (frame.first == 1 && frame.second.optString("evt") == "READY") return pipe
                                throw IOException("Discord refused the connection")
                            }
                            Thread.sleep(50)
                        }
                        throw IOException("Discord didn't answer")
                    } catch (e: Exception) {
                        pipe.close()
                    }
                }
                return null
            }
        }

        fun setActivity(activity: JSONObject?) {
            val args = JSONObject().put("pid", ProcessHandle.current().pid())
            args.put("activity", activity ?: JSONObject.NULL)
            write(1, JSONObject().put("cmd", "SET_ACTIVITY").put("args", args).put("nonce", UUID.randomUUID().toString()))
        }

        /** Reads anything Discord sent (answers, pings), so its side never fills up. */
        fun pump() {
            while (true) {
                val frame = read() ?: return
                when (frame.first) {
                    2 -> throw IOException("Discord closed the connection")
                    3 -> write(4, frame.second) // ping → pong
                }
            }
        }

        private fun write(op: Int, payload: JSONObject) {
            val data = payload.toString().toByteArray(Charsets.UTF_8)
            val buf = ByteBuffer.allocate(8 + data.size).order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(op).putInt(data.size).put(data)
            file.write(buf.array())
        }

        /**
         * A whole frame if one is waiting, else null. (A Windows pipe's "length" is how much is
         * waiting to be read, so this never blocks; a blocking read would also hold up writes.)
         */
        private fun read(): Pair<Int, JSONObject>? {
            if (file.length() < 8) return null
            val header = ByteArray(8)
            file.readFully(header)
            val b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val op = b.int
            val size = b.int
            if (size < 0 || size > 64 * 1024) throw IOException("Bad frame from Discord")
            val data = ByteArray(size)
            file.readFully(data)
            val json = runCatching { JSONObject(String(data, Charsets.UTF_8)) }.getOrDefault(JSONObject())
            return op to json
        }

        fun close() {
            runCatching { file.close() }
        }
    }
}
