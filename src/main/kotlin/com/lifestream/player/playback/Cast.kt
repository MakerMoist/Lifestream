package com.lifestream.player.playback

import com.lifestream.player.data.AppSettings
import com.lifestream.player.data.Track
import com.lifestream.player.data.hiResArtwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/** A Cast device on the home network (a Chromecast, Google/Nest speaker, a TV with Cast built in). */
data class CastDevice(val id: String, val name: String, val address: InetAddress, val port: Int)

/**
 * Finds Cast devices on the local network (they announce themselves with mDNS). Runs only while
 * the Cast picker is open, or while casting, and only with "Cast Support" on.
 */
object CastDevices {
    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()
    private val found = ConcurrentHashMap<String, CastDevice>()
    private var browsers: List<JmDNS> = emptyList()
    private const val TYPE = "_googlecast._tcp.local."

    @Synchronized
    fun startSearching() {
        if (browsers.isNotEmpty() || !AppSettings.castSupport.value) return
        val listener = object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                event.dns.requestServiceInfo(event.type, event.name, 3_000)
            }

            override fun serviceRemoved(event: ServiceEvent) {
                found.remove(event.name)
                publish()
            }

            override fun serviceResolved(event: ServiceEvent) {
                val info = event.info ?: return
                val address = info.inet4Addresses.firstOrNull() ?: return
                // Only devices on this network (a private address), never one somewhere else.
                if (!address.isSiteLocalAddress && !address.isLinkLocalAddress) return
                val name = info.getPropertyString("fn")?.take(60)?.ifBlank { null } ?: info.name.take(60)
                found[event.name] = CastDevice(event.name, name, address, info.port.takeIf { it in 1..65535 } ?: 8009)
                publish()
            }
        }
        browsers = localAddresses().mapNotNull { address ->
            runCatching { JmDNS.create(address).also { it.addServiceListener(TYPE, listener) } }.getOrNull()
        }
    }

    @Synchronized
    fun stopSearching() {
        val old = browsers
        browsers = emptyList()
        Thread { old.forEach { runCatching { it.close() } } }.apply { isDaemon = true }.start()
    }

    private fun publish() {
        _devices.value = found.values.sortedBy { it.name.lowercase() }
    }

    /** This PC's private IPv4 addresses on networks that are up. */
    fun localAddresses(): List<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { runCatching { it.isUp && !it.isLoopback && !it.isVirtual }.getOrDefault(false) }
            .flatMap { it.inetAddresses.toList() }
            .filter { it is Inet4Address && it.isSiteLocalAddress }
    }.getOrDefault(emptyList())
}

/**
 * Plays the queue on a Cast device. The device's own media player (Google's standard receiver)
 * fetches each song from [CastRelay] on this PC, which passes on the audio LifeStream fetches from
 * YouTube (YouTube's links only work for the computer that asked for them).
 */
class CastOutput(val device: CastDevice) : PlayerConnection.Output {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var channel: CastChannel? = null
    private var transportId: String? = null
    private var mediaSessionId: Int = -1
    @Volatile private var session: Any? = null
    @Volatile private var lastTimeMs = 0L
    @Volatile private var lastTimeAt = 0L
    @Volatile private var playing = false
    @Volatile private var reportedReady = false

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Connects and starts the media receiver on the device. Call off the UI thread. */
    fun connect() {
        val c = CastChannel(device) { ns, msg -> onMessage(ns, msg) }
        channel = c
        c.open()
        c.send(CastChannel.CONNECTION, "receiver-0", JSONObject().put("type", "CONNECT"))
        val launched = c.request(CastChannel.RECEIVER, "receiver-0", JSONObject().put("type", "LAUNCH").put("appId", MEDIA_APP)) { r ->
            r.optString("type") == "RECEIVER_STATUS" && appTransport(r) != null
        } ?: throw IOException("The Cast device didn't start its player.")
        val transport = appTransport(launched)!!
        transportId = transport
        c.send(CastChannel.CONNECTION, transport, JSONObject().put("type", "CONNECT"))
        if (!CastRelay.start(device.address)) throw IOException("This PC isn't on the same network as ${device.name}.")
        _connected.value = true
        setVolume(AppSettings.volume.value)
    }

    private fun appTransport(status: JSONObject): String? {
        val apps = status.optJSONObject("status")?.optJSONArray("applications") ?: return null
        for (i in 0 until apps.length()) {
            val a = apps.optJSONObject(i) ?: continue
            if (a.optString("appId") == MEDIA_APP) return a.optString("transportId").takeIf { it.isNotBlank() && it.length < 100 }
        }
        return null
    }

    override suspend fun load(track: Track, startMs: Long, play: Boolean): Any = withContext(Dispatchers.IO) {
        val c = channel ?: throw IOException("Not connected to ${device.name}.")
        val transport = transportId ?: throw IOException("Not connected to ${device.name}.")
        val url = CastRelay.urlFor(track.id) ?: throw IOException("This PC isn't on the same network as ${device.name}.")
        // Started now, so the device's first request is quick.
        val source = SongStore.open(track.id)
        CastRelay.keep(source)
        val handle = Any()
        session = handle
        reportedReady = false
        mediaSessionId = -1
        val art = hiResArtwork(track.thumbnail, 720)?.let { SafeInput.safeArtwork(it) } ?: SafeInput.safeArtwork(track.thumbnail)
        val media = JSONObject()
            .put("contentId", url)
            .put("contentType", if (source.itag in MP4_ITAGS) "audio/mp4" else "audio/webm")
            .put("streamType", "BUFFERED")
            .put(
                "metadata",
                JSONObject().put("metadataType", 3).put("title", track.title).put("artist", track.artist)
                    .apply { if (art != null) put("images", JSONArray().put(JSONObject().put("url", art))) },
            )
        c.send(
            CastChannel.MEDIA, transport,
            JSONObject().put("type", "LOAD").put("media", media).put("autoplay", play).put("currentTime", startMs / 1000.0),
        )
        playing = play
        lastTimeMs = startMs
        lastTimeAt = System.currentTimeMillis()
        handle
    }

    private fun onMessage(namespace: String, msg: JSONObject) {
        if (namespace != CastChannel.MEDIA) {
            if (namespace == CastChannel.CONNECTION && msg.optString("type") == "CLOSE") lost()
            return
        }
        if (msg.optString("type") != "MEDIA_STATUS") return
        val status = msg.optJSONArray("status")?.optJSONObject(0) ?: return
        val s = session ?: return
        mediaSessionId = status.optInt("mediaSessionId", mediaSessionId)
        val state = status.optString("playerState")
        if (status.has("currentTime")) {
            lastTimeMs = (status.optDouble("currentTime", 0.0) * 1000).toLong()
            lastTimeAt = System.currentTimeMillis()
        }
        playing = state == "PLAYING" || state == "BUFFERING"
        if (!reportedReady && (state == "PLAYING" || state == "PAUSED")) {
            reportedReady = true
            val duration = status.optJSONObject("media")?.optDouble("duration", 0.0) ?: 0.0
            PlayerConnection.Events.ready(s, (duration * 1000).toLong())
        }
        if (state == "IDLE") {
            when (status.optString("idleReason")) {
                "FINISHED" -> PlayerConnection.Events.ended(s)
                "ERROR" -> PlayerConnection.Events.failed(s, IOException("${device.name} couldn't play this song."))
            }
        }
    }

    private fun mediaCommand(type: String, extra: JSONObject.() -> Unit = {}) {
        val c = channel ?: return
        val t = transportId ?: return
        if (mediaSessionId < 0) return
        scope.launch { runCatching { c.send(CastChannel.MEDIA, t, JSONObject().put("type", type).put("mediaSessionId", mediaSessionId).apply(extra)) } }
    }

    override fun setPlaying(play: Boolean) {
        lastTimeMs = positionMs()
        lastTimeAt = System.currentTimeMillis()
        playing = play
        mediaCommand(if (play) "PLAY" else "PAUSE")
    }

    override fun seekTo(ms: Long) {
        lastTimeMs = ms
        lastTimeAt = System.currentTimeMillis()
        mediaCommand("SEEK") { put("currentTime", ms / 1000.0) }
    }

    override fun positionMs(): Long =
        if (playing) lastTimeMs + (System.currentTimeMillis() - lastTimeAt) else lastTimeMs

    /** The window's volume slider sets the device's volume while casting. */
    fun setVolume(level: Float) {
        val c = channel ?: return
        scope.launch {
            runCatching {
                c.send(CastChannel.RECEIVER, "receiver-0", JSONObject().put("type", "SET_VOLUME").put("volume", JSONObject().put("level", level.toDouble())))
            }
        }
    }

    override fun stop() {
        session = null
        mediaCommand("STOP")
    }

    /** Ends casting: the device's player is closed and the connection let go. */
    fun disconnect() {
        session = null
        val c = channel
        channel = null
        _connected.value = false
        scope.launch {
            runCatching { c?.send(CastChannel.RECEIVER, "receiver-0", JSONObject().put("type", "STOP")) }
            runCatching { c?.close() }
        }
        CastRelay.stop()
    }

    private fun lost() {
        if (channel == null) return
        Casting.onLost(this)
    }

    private companion object {
        const val MEDIA_APP = "CC1AD845" // Google's standard media receiver
        val MP4_ITAGS = setOf(139, 140, 141, 256, 258, 325, 328)
    }
}

/** The app's casting state: which device (if any), and switching playback to and from it. */
object Casting {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _current = MutableStateFlow<CastOutput?>(null)
    val current: StateFlow<CastOutput?> = _current.asStateFlow()
    private val _connecting = MutableStateFlow<CastDevice?>(null)
    val connecting: StateFlow<CastDevice?> = _connecting.asStateFlow()

    fun castTo(device: CastDevice) {
        if (_connecting.value != null) return
        _connecting.value = device
        scope.launch {
            val output = CastOutput(device)
            try {
                withContext(Dispatchers.IO) { output.connect() }
                _current.value?.disconnect()
                _current.value = output
                CastDevices.stopSearching()
                PlayerConnection.switchOutput(output)
            } catch (e: Exception) {
                runCatching { output.disconnect() }
                com.lifestream.player.ui.components.toast(e.message ?: "Couldn't connect to ${device.name}.")
            } finally {
                _connecting.value = null
            }
        }
    }

    /** Back to this PC's speakers, paused (so music doesn't suddenly play out loud). */
    fun stopCasting() {
        val output = _current.value ?: return
        _current.value = null
        PlayerConnection.switchOutput(LocalOutput, play = false)
        output.disconnect()
    }

    internal fun onLost(output: CastOutput) {
        scope.launch {
            if (_current.value !== output) return@launch
            _current.value = null
            PlayerConnection.switchOutput(LocalOutput, play = false)
            output.disconnect()
            com.lifestream.player.ui.components.toast("Disconnected from ${output.device.name}")
        }
    }
}

/**
 * The Cast protocol's control channel: TLS to the device on port 8009, carrying small JSON
 * messages in protobuf frames. Cast devices use their own self-signed certificates, so the
 * connection is encrypted but not checked against a certificate authority (the same as every
 * Cast sender); it's only ever opened to a device found on this network and picked by you.
 */
class CastChannel(private val device: CastDevice, private val onMessage: (String, JSONObject) -> Unit) {
    companion object {
        const val CONNECTION = "urn:x-cast:com.google.cast.tp.connection"
        const val HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat"
        const val RECEIVER = "urn:x-cast:com.google.cast.receiver"
        const val MEDIA = "urn:x-cast:com.google.cast.media"
        private const val SENDER = "sender-lifestream"
        private const val MAX_FRAME = 64 * 1024
    }

    private var socket: SSLSocket? = null
    private var out: DataOutputStream? = null
    private val nextId = AtomicInteger(1)
    private val waiting = ConcurrentHashMap<Int, (JSONObject) -> Boolean>()
    private val answers = ConcurrentHashMap<Int, JSONObject>()
    @Volatile private var closed = false

    fun open() {
        val trustCastDevice = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustCastDevice), SecureRandom()) }
        val s = ssl.socketFactory.createSocket() as SSLSocket
        s.connect(InetSocketAddress(device.address, device.port), 5_000)
        s.soTimeout = 30_000
        s.startHandshake()
        socket = s
        out = DataOutputStream(BufferedOutputStream(s.outputStream))
        Thread({ readLoop(DataInputStream(s.inputStream)) }, "cast-read").apply { isDaemon = true }.start()
        Thread({
            while (!closed) {
                runCatching { send(HEARTBEAT, "receiver-0", JSONObject().put("type", "PING")) }
                Thread.sleep(5_000)
            }
        }, "cast-heartbeat").apply { isDaemon = true }.start()
    }

    @Synchronized
    fun send(namespace: String, destination: String, payload: JSONObject) {
        if (closed) throw IOException("Cast connection closed")
        if (!payload.has("requestId") && namespace != CONNECTION && namespace != HEARTBEAT) payload.put("requestId", nextId.getAndIncrement())
        val frame = encode(SENDER, destination, namespace, payload.toString())
        val o = out ?: throw IOException("Cast connection closed")
        o.writeInt(frame.size)
        o.write(frame)
        o.flush()
    }

    /** Sends and waits (up to 10 s) for the device's answer that [matches]. */
    fun request(namespace: String, destination: String, payload: JSONObject, matches: (JSONObject) -> Boolean): JSONObject? {
        val id = nextId.getAndIncrement()
        payload.put("requestId", id)
        waiting[id] = matches
        send(namespace, destination, payload)
        val until = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < until && !closed) {
            answers.remove(id)?.let { waiting.remove(id); return it }
            Thread.sleep(50)
        }
        waiting.remove(id)
        return null
    }

    private fun readLoop(input: DataInputStream) {
        try {
            while (!closed) {
                val size = input.readInt()
                if (size <= 0 || size > MAX_FRAME) throw IOException("Bad Cast frame")
                val bytes = ByteArray(size)
                input.readFully(bytes)
                val (namespace, payload) = decode(bytes) ?: continue
                val json = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                if (namespace == HEARTBEAT && json.optString("type") == "PING") {
                    runCatching { send(HEARTBEAT, "receiver-0", JSONObject().put("type", "PONG")) }
                    continue
                }
                val requestId = json.optInt("requestId", 0)
                waiting[requestId]?.let { matches -> if (matches(json)) answers[requestId] = json }
                // Answers to a launch also come without the request's id; check them too.
                if (requestId == 0) waiting.forEach { (id, m) -> if (m(json)) answers[id] = json }
                runCatching { onMessage(namespace, json) }
            }
        } catch (e: Exception) {
            if (!closed) {
                closed = true
                runCatching { onMessage(CONNECTION, JSONObject().put("type", "CLOSE")) }
            }
        }
    }

    fun close() {
        closed = true
        runCatching { socket?.close() }
    }

    // ---- The protobuf frame (CastMessage), written and read by hand: six simple fields. ----

    private fun encode(source: String, destination: String, namespace: String, payload: String): ByteArray {
        val b = java.io.ByteArrayOutputStream()
        fun varint(v: Long) {
            var x = v
            while (x >= 0x80) {
                b.write(((x and 0x7F) or 0x80).toInt())
                x = x ushr 7
            }
            b.write(x.toInt())
        }
        fun string(field: Int, s: String) {
            val bytes = s.toByteArray(Charsets.UTF_8)
            varint(((field shl 3) or 2).toLong())
            varint(bytes.size.toLong())
            b.write(bytes)
        }
        varint((1 shl 3).toLong()); varint(0) // protocol_version: CASTV2_1_0
        string(2, source)
        string(3, destination)
        string(4, namespace)
        varint((5 shl 3).toLong()); varint(0) // payload_type: STRING
        string(6, payload)
        return b.toByteArray()
    }

    /** (namespace, text payload), or null for anything else. */
    private fun decode(bytes: ByteArray): Pair<String, String>? {
        var i = 0
        fun varint(): Long {
            var shift = 0
            var result = 0L
            while (i < bytes.size && shift < 64) {
                val b = bytes[i++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
            throw IOException("Bad varint")
        }
        var namespace: String? = null
        var payload: String? = null
        while (i < bytes.size) {
            val key = varint()
            val field = (key ushr 3).toInt()
            when ((key and 7).toInt()) {
                0 -> varint()
                2 -> {
                    val len = varint().toInt()
                    if (len < 0 || i + len > bytes.size) return null
                    val s = String(bytes, i, len, Charsets.UTF_8)
                    i += len
                    when (field) {
                        4 -> namespace = s
                        6 -> payload = s
                    }
                }
                else -> return null
            }
        }
        return if (namespace != null && payload != null) namespace to payload else null
    }
}

/**
 * Hands a song's audio to the Cast device over the home network.
 *
 * Locked down: it listens only on this PC's address on the same network as the Cast device,
 * answers only that device, only for addresses carrying a random 128-bit key made for this cast
 * session, only for real YouTube video ids, and stops as soon as casting ends.
 */
object CastRelay {
    private const val MAX_HEADER_BYTES = 8 * 1024
    private const val MAX_CLIENTS = 4
    private val mp4Itags = setOf(139, 140, 141, 256, 258, 325, 328)

    @Volatile private var server: ServerSocket? = null
    @Volatile private var base: String? = null
    @Volatile private var key: ByteArray = ByteArray(0)
    @Volatile private var allowed: InetAddress? = null
    private var workers: ThreadPoolExecutor? = null
    private val clients = java.util.Collections.newSetFromMap(ConcurrentHashMap<Socket, Boolean>())
    /** The song being cast, kept open (and downloading) while the device plays it. */
    @Volatile private var kept: SongSource? = null

    @Synchronized
    fun start(device: InetAddress): Boolean {
        stop()
        val local = localAddressFor(device) ?: return false
        val socket = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(local, 0), 8)
            }
        } catch (e: IOException) {
            return false
        }
        val secret = ByteArray(16).also { SecureRandom().nextBytes(it) }
        key = secret
        allowed = device
        server = socket
        base = "http://${local.hostAddress}:${socket.localPort}/${secret.toHex()}/"
        val pool = ThreadPoolExecutor(0, MAX_CLIENTS, 30, TimeUnit.SECONDS, SynchronousQueue()) { r ->
            Thread(r, "cast-relay").apply { isDaemon = true }
        }
        workers = pool
        Thread({ acceptLoop(socket, pool) }, "cast-relay-accept").apply { isDaemon = true }.start()
        return true
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        server = null
        base = null
        allowed = null
        key = ByteArray(0)
        workers?.shutdownNow()
        workers = null
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        kept?.close()
        kept = null
    }

    fun keep(source: SongSource) {
        val old = kept
        kept = source
        if (old != null && old !== source) old.close()
    }

    fun urlFor(videoId: String): String? {
        if (!SafeInput.isVideoId(videoId)) return null
        return base?.let { it + videoId }
    }

    private fun acceptLoop(socket: ServerSocket, pool: ThreadPoolExecutor) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (e: IOException) {
                break
            }
            if (client.inetAddress != allowed || pool.isShutdown) {
                runCatching { client.close() }
                continue
            }
            try {
                clients += client
                pool.execute {
                    try {
                        client.use { serve(it) }
                    } finally {
                        clients -= client
                    }
                }
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                clients -= client
                runCatching { client.close() }
            }
        }
    }

    private class Request(val method: String, val path: String, val rangeStart: Long?, val rangeEnd: Long?)

    private fun serve(client: Socket) {
        client.soTimeout = 20_000
        client.tcpNoDelay = true
        val out = BufferedOutputStream(client.getOutputStream(), 64 * 1024)
        val request = readRequest(client.getInputStream()) ?: return reply(out, 400, "Bad Request")
        if (request.method != "GET" && request.method != "HEAD") return reply(out, 405, "Method Not Allowed")
        val parts = request.path.trim('/').split('/')
        if (parts.size != 2 || !sameKey(parts[0]) || !SafeInput.isVideoId(parts[1])) return reply(out, 404, "Not Found")
        val videoId = parts[1]
        val source = kept?.takeIf { it.videoId == videoId && !it.closed } ?: SongStore.open(videoId).also { keep(it) }
        val total = try {
            source.awaitTotal()
        } catch (e: IOException) {
            return reply(out, 502, "Bad Gateway")
        }
        val start = request.rangeStart ?: 0L
        if (start >= total) return reply(out, 416, "Range Not Satisfiable", "Content-Range: bytes */$total\r\n")
        val end = minOf(request.rangeEnd ?: (total - 1), total - 1)
        val length = end - start + 1
        val partial = request.rangeStart != null
        val mime = if (source.itag in mp4Itags) "audio/mp4" else "audio/webm"
        val head = StringBuilder()
            .append(if (partial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
            .append("Content-Type: $mime\r\n")
            .append("Accept-Ranges: bytes\r\n")
            .append("Access-Control-Allow-Origin: *\r\n")
            .append("Cache-Control: no-store\r\n")
            .append("Connection: close\r\n")
            .append("Content-Length: $length\r\n")
        if (partial) head.append("Content-Range: bytes $start-$end/$total\r\n")
        try {
            out.write(head.append("\r\n").toString().toByteArray(Charsets.US_ASCII))
            if (request.method == "GET") {
                source.open().use { input ->
                    var skip = start
                    while (skip > 0) {
                        val n = input.skip(skip)
                        if (n <= 0) break
                        skip -= n
                    }
                    var left = length
                    val buf = ByteArray(64 * 1024)
                    while (left > 0) {
                        val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                        if (n < 0) break
                        out.write(buf, 0, n)
                        left -= n
                    }
                }
            }
            out.flush()
        } catch (_: IOException) {
            // The device hung up (it often does, to seek) or the network dropped.
        }
    }

    private fun readRequest(input: InputStream): Request? {
        val bytes = java.io.ByteArrayOutputStream()
        var last4 = 0
        while (bytes.size() < MAX_HEADER_BYTES) {
            val b = input.read()
            if (b < 0) return null
            bytes.write(b)
            last4 = (last4 shl 8) or b
            if (last4 == 0x0D0A0D0A) break
        }
        if (last4 != 0x0D0A0D0A) return null
        val lines = bytes.toString(Charsets.US_ASCII.name()).split("\r\n")
        val first = lines.firstOrNull()?.split(' ') ?: return null
        if (first.size != 3 || !first[2].startsWith("HTTP/1.")) return null
        var rangeStart: Long? = null
        var rangeEnd: Long? = null
        for (line in lines.drop(1)) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            if (!line.substring(0, colon).trim().equals("Range", ignoreCase = true)) continue
            val m = Regex("^bytes=(\\d{1,15})-(\\d{0,15})$").matchEntire(line.substring(colon + 1).trim()) ?: return null
            rangeStart = m.groupValues[1].toLong()
            rangeEnd = m.groupValues[2].takeIf { it.isNotEmpty() }?.toLong()
            if (rangeEnd != null && rangeEnd < rangeStart) return null
        }
        return Request(first[0], first[1], rangeStart, rangeEnd)
    }

    private fun reply(out: BufferedOutputStream, code: Int, text: String, extra: String = "") {
        runCatching {
            out.write("HTTP/1.1 $code $text\r\n${extra}Content-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
            out.flush()
        }
    }

    /** Compares the key without leaking how much of it matched. */
    private fun sameKey(given: String): Boolean {
        val expected = key.toHex().toByteArray()
        return expected.isNotEmpty() && MessageDigest.isEqual(expected, given.toByteArray())
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    /** This PC's address on the same network as [device]. */
    private fun localAddressFor(device: InetAddress): InetAddress? {
        val candidates = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }.getOrDefault(emptyList())
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { it.interfaceAddresses }
            .filter { it.address is Inet4Address && it.address.isSiteLocalAddress }
        if (device !is Inet4Address) return null
        return candidates.firstOrNull { sameSubnet(it.address, device, it.networkPrefixLength.toInt()) }?.address
    }

    private fun sameSubnet(a: InetAddress, b: InetAddress, prefix: Int): Boolean {
        if (prefix !in 1..32) return false
        val x = a.address.fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
        val y = b.address.fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
        val mask = if (prefix == 32) -1 else ((-1) shl (32 - prefix))
        return (x and mask) == (y and mask)
    }
}
