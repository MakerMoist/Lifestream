package com.lifestream.player.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import java.io.BufferedOutputStream
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
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Hands a song's audio to a Cast device over the home Wi-Fi.
 *
 * YouTube's audio links only work for the phone that asked for them (and need YouTube's chunked
 * requests), so the Cast device can't fetch them itself. While casting, the phone fetches the
 * audio exactly as it does for its own playback and passes it on.
 *
 * Locked down: it listens only on the Wi-Fi address that shares a network with the Cast device,
 * answers only that device, only for addresses carrying a random 128-bit key made for this cast
 * session, only for real YouTube video ids, and stops as soon as casting ends.
 */
@OptIn(UnstableApi::class)
object CastRelay {
    /** YouTube's AAC audio (itag 140) plays on every Cast device. */
    const val MIME = "audio/mp4"
    private const val AAC_ITAG = 140
    private const val MAX_HEADER_BYTES = 8 * 1024
    private const val MAX_CLIENTS = 4
    private val mp4Itags = setOf(139, 140, 141, 256, 258, 325, 328)

    @Volatile private var server: ServerSocket? = null
    @Volatile private var base: String? = null
    @Volatile private var key: ByteArray = ByteArray(0)
    @Volatile private var allowed: InetAddress? = null
    private var workers: ThreadPoolExecutor? = null

    /** Connections being served, closed the moment casting ends (not left to finish a song). */
    private val clients = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Socket, Boolean>())

    /**
     * Starts serving for the Cast device at [device] (its address on the Wi-Fi). Returns false
     * if the phone isn't on the same network as it.
     */
    @Synchronized
    fun start(device: InetAddress?): Boolean {
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
        // One thread per connection (the Cast device opens a new one to seek), at most four.
        val pool = ThreadPoolExecutor(
            0, MAX_CLIENTS, 30, TimeUnit.SECONDS, SynchronousQueue(),
            { r -> Thread(r, "cast-relay").apply { isDaemon = true } },
        )
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
    }

    /** The address the Cast device plays [videoId] from, or null when not running. */
    fun urlFor(videoId: String): Uri? {
        if (!SafeInput.isVideoId(videoId)) return null
        return base?.let { Uri.parse(it + videoId) }
    }

    private fun acceptLoop(socket: ServerSocket, pool: ThreadPoolExecutor) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (e: IOException) {
                break
            }
            val from = client.inetAddress
            val expected = allowed
            // Only the Cast device (or, if its address isn't known, a device on the local network).
            val ok = if (expected != null) from == expected else from.isSiteLocalAddress || from.isLinkLocalAddress
            if (!ok || pool.isShutdown) {
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
            } catch (_: RejectedExecutionException) {
                clients -= client
                runCatching { client.close() } // too many at once
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
        // "/<key>/<video id>"
        val parts = request.path.trim('/').split('/')
        if (parts.size != 2 || !sameKey(parts[0]) || !SafeInput.isVideoId(parts[1])) return reply(out, 404, "Not Found")
        val videoId = parts[1]

        val source = StreamDataSources.castSource()
        try {
            val start = request.rangeStart ?: 0L
            val (length, total, mime) = openAt(source, videoId, start, request.rangeEnd)
            if (total != null && start >= total) return reply(out, 416, "Range Not Satisfiable", "Content-Range: bytes */$total\r\n")
            val partial = request.rangeStart != null && total != null
            val head = StringBuilder()
                .append(if (partial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
                .append("Content-Type: $mime\r\n")
                .append("Accept-Ranges: bytes\r\n")
                .append("Access-Control-Allow-Origin: *\r\n")
                .append("Cache-Control: no-store\r\n")
                .append("Connection: close\r\n")
            if (length >= 0) head.append("Content-Length: $length\r\n")
            if (partial) head.append("Content-Range: bytes $start-${start + length - 1}/$total\r\n")
            out.write(head.append("\r\n").toString().toByteArray(Charsets.US_ASCII))
            if (request.method == "GET") {
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = source.read(buf, 0, buf.size)
                    if (n == C.RESULT_END_OF_INPUT) break
                    out.write(buf, 0, n)
                }
            }
            out.flush()
        } catch (_: IOException) {
            // The Cast device hung up (it often does, to seek) or the network dropped.
        } finally {
            runCatching { source.close() }
        }
    }

    /** Opens the audio at [start]; returns (bytes to send, whole file size if known, its type). */
    private fun openAt(source: androidx.media3.datasource.DataSource, videoId: String, start: Long, end: Long?): Triple<Long, Long?, String> {
        var attempt = 0
        while (true) {
            val stream = StreamResolver.resolveStream(videoId, AAC_ITAG)
            val uri = Uri.parse(stream.url)
            val total = uri.getQueryParameter("clen")?.toLongOrNull()
            val wanted = if (end != null) end - start + 1 else C.LENGTH_UNSET.toLong()
            try {
                val length = source.open(DataSpec.Builder().setUri(uri).setPosition(start).setLength(wanted).build())
                // AAC comes in MP4; YouTube's other audio (Opus, Vorbis) in WebM.
                val mime = if (stream.itag in mp4Itags) MIME else "audio/webm"
                return Triple(length, total ?: if (length >= 0) start + length else null, mime)
            } catch (e: StreamHttpException) {
                runCatching { source.close() }
                // An expired link: get a fresh one once.
                if (attempt++ > 0 || e.code != 403) throw e
                StreamResolver.invalidate(videoId)
            }
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
            // Only a single "bytes=start-" or "bytes=start-end" range.
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

    /** This phone's Wi-Fi address on the same network as [device] (or any private one if unknown). */
    private fun localAddressFor(device: InetAddress?): InetAddress? {
        val candidates = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }.getOrDefault(emptyList())
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { it.interfaceAddresses }
            .filter { it.address is Inet4Address && it.address.isSiteLocalAddress }
        if (device is Inet4Address) {
            candidates.firstOrNull { sameSubnet(it.address, device, it.networkPrefixLength.toInt()) }?.let { return it.address }
            return null
        }
        return candidates.firstOrNull()?.address
    }

    private fun sameSubnet(a: InetAddress, b: InetAddress, prefix: Int): Boolean {
        if (prefix !in 1..32) return false
        val x = a.address.fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
        val y = b.address.fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
        val mask = if (prefix == 32) -1 else ((-1) shl (32 - prefix))
        return (x and mask) == (y and mask)
    }
}
