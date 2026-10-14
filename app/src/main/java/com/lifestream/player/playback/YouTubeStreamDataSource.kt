package com.lifestream.player.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import com.lifestream.player.net.Http
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/** Thrown when YouTube's media server rejects a request (most often an expired URL → 403). */
class StreamHttpException(val code: Int) : IOException("Media server answered HTTP $code")

/**
 * Lifestream's own network layer for the player.
 *
 * YouTube's media servers throttle or reject long open-ended downloads from non-browser
 * clients, so audio is fetched in bounded chunks using the `range` query parameter, the same
 * way YouTube's own clients do. Each chunk is a short POST request; ExoPlayer just sees one
 * continuous stream.
 */
@OptIn(UnstableApi::class)
class YouTubeStreamDataSource(private val client: OkHttpClient) : BaseDataSource(true) {

    private companion object {
        const val CHUNK_SIZE = 1L * 1024 * 1024 // 1 MiB ≈ 50 s of 160 kbps audio
        const val UNKNOWN = -1L
        val POST_BODY = byteArrayOf(0x78, 0)
    }

    private var dataSpec: DataSpec? = null
    private var delegate: DataSource? = null

    private var baseUrl = ""
    private var userAgent = Http.DESKTOP_USER_AGENT
    private var isWebClient = false

    private var position = 0L
    private var end = UNKNOWN // exclusive
    private var chunkRemaining = 0L
    private var response: okhttp3.Response? = null
    private var stream: InputStream? = null
    private var requestNumber = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        val uri = dataSpec.uri

        // Only ever fetch over HTTPS.
        if (!SafeInput.isHttps(uri)) throw IOException("Refusing non-HTTPS stream URL")

        // Anything that isn't a YouTube media server goes through a plain HTTPS source.
        if (!SafeInput.isGoogleVideoHost(uri.host)) {
            val plain = DefaultHttpDataSource.Factory()
                .setUserAgent(Http.DESKTOP_USER_AGENT)
                .setAllowCrossProtocolRedirects(false)
                .createDataSource()
            delegate = plain
            return plain.open(dataSpec)
        }

        transferInitializing(dataSpec)
        baseUrl = uri.toString()
        userAgent = when {
            YoutubeParsingHelper.isAndroidStreamingUrl(baseUrl) -> YoutubeParsingHelper.getAndroidUserAgent(null)
            YoutubeParsingHelper.isIosStreamingUrl(baseUrl) -> YoutubeParsingHelper.getIosUserAgent(null)
            YoutubeParsingHelper.isVisionOsStreamingUrl(baseUrl) -> YoutubeParsingHelper.getVisionOsUserAgent(null)
            else -> Http.DESKTOP_USER_AGENT
        }
        isWebClient = YoutubeParsingHelper.isWebStreamingUrl(baseUrl)
        requestNumber = 0

        position = dataSpec.position
        val contentLength = uri.getQueryParameter("clen")?.toLongOrNull()
        val requestedEnd = if (dataSpec.length != C.LENGTH_UNSET.toLong()) position + dataSpec.length else null
        end = listOfNotNull(contentLength, requestedEnd).minOrNull() ?: UNKNOWN

        if (end != UNKNOWN && position >= end) {
            chunkRemaining = 0
            opened = true
            transferStarted(dataSpec)
            return 0
        }

        openChunk()
        opened = true
        transferStarted(dataSpec)
        return if (end != UNKNOWN) end - position else C.LENGTH_UNSET.toLong()
    }

    private fun openChunk() {
        val last = if (end != UNKNOWN) minOf(position + CHUNK_SIZE, end) - 1 else position + CHUNK_SIZE - 1
        val separator = if (baseUrl.contains('?')) '&' else '?'
        val url = "$baseUrl${separator}range=$position-$last&rn=${requestNumber++}"

        val request = Request.Builder()
            .url(url)
            .post(POST_BODY.toRequestBody())
            .header("User-Agent", userAgent)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .apply {
                if (isWebClient) {
                    header("Origin", "https://www.youtube.com")
                    header("Referer", "https://www.youtube.com/")
                }
            }
            .build()

        val resp = client.newCall(request).execute()
        if (!resp.isSuccessful) {
            resp.close()
            if (resp.code == 416 && end == UNKNOWN) {
                // We've walked past the end of a stream whose length we didn't know.
                end = position
                chunkRemaining = 0
                return
            }
            throw StreamHttpException(resp.code)
        }

        val body = resp.body ?: run {
            resp.close()
            throw IOException("Empty response from media server")
        }

        if (end == UNKNOWN) {
            // "Content-Range: bytes 0-1048575/3456789" → learn the total size
            resp.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()?.let { end = it }
        }

        val requested = last - position + 1
        var bodyLength = body.contentLength()
        val input = body.byteStream()

        // Defensive: if the server ignored the range and sent the whole file, skip ahead.
        if (resp.code == 200 && position > 0 && bodyLength > requested) {
            var toSkip = position
            while (toSkip > 0) {
                val skipped = input.skip(toSkip)
                if (skipped <= 0) {
                    resp.close()
                    throw EOFException("Couldn't skip to $position")
                }
                toSkip -= skipped
            }
            bodyLength -= position
        }

        chunkRemaining = if (bodyLength >= 0) bodyLength else requested
        response = resp
        stream = input
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        delegate?.let { return it.read(buffer, offset, length) }
        if (length == 0) return 0
        if (end != UNKNOWN && position >= end) return C.RESULT_END_OF_INPUT

        if (chunkRemaining <= 0L) {
            closeChunk()
            openChunk()
            if (chunkRemaining <= 0L) return C.RESULT_END_OF_INPUT
        }

        val toRead = minOf(length.toLong(), chunkRemaining).toInt()
        val read = stream?.read(buffer, offset, toRead) ?: -1
        if (read == -1) {
            if (end == UNKNOWN) return C.RESULT_END_OF_INPUT
            throw EOFException("Stream ended early at byte $position of $end")
        }
        position += read
        chunkRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = delegate?.uri ?: dataSpec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        delegate?.responseHeaders ?: response?.headers?.toMultimap() ?: emptyMap()

    override fun close() {
        delegate?.let {
            delegate = null
            it.close()
            return
        }
        closeChunk()
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private fun closeChunk() {
        try {
            stream?.close()
        } catch (_: IOException) {
        }
        response?.close()
        stream = null
        response = null
    }
}

/** Builds the DataSource chain used by the player: lifestream://track/<id> → YouTube audio bytes. */
@OptIn(UnstableApi::class)
object StreamDataSources {
    const val SCHEME = "lifestream"

    fun trackUri(videoId: String): Uri {
        require(SafeInput.isVideoId(videoId)) { "Invalid track id" }
        return Uri.parse("$SCHEME://track/$videoId")
    }

    fun factory(): DataSource.Factory {
        val upstream = DataSource.Factory { YouTubeStreamDataSource(Http.client) }
        return ResolvingDataSource.Factory(upstream) { spec ->
            if (spec.uri.scheme == SCHEME) {
                val id = spec.uri.lastPathSegment
                if (!SafeInput.isVideoId(id)) throw IOException("Invalid track id")
                spec.withUri(Uri.parse(StreamResolver.resolve(id!!)))
            } else {
                // Only our own lifestream:// items are ever played.
                throw IOException("Unsupported media URI scheme: ${spec.uri.scheme}")
            }
        }
    }
}
