package com.lifestream.player.net

import java.io.IOException
import okhttp3.ResponseBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

object Http {
    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

    /** Metadata pages (search results, the YouTube player script) are a few MB at most. */
    const val MAX_METADATA_BYTES = 24L * 1024 * 1024

    /** Reads a text body, refusing anything larger than [max] bytes instead of exhausting memory. */
    fun readLimited(body: ResponseBody, max: Long): String {
        if (body.contentLength() > max) throw IOException("Response too large")
        val out = java.io.ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buf = ByteArray(32 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > max) throw IOException("Response too large")
                out.write(buf, 0, n)
            }
        }
        val charset = body.contentType()?.charset() ?: Charsets.UTF_8
        return String(out.toByteArray(), charset)
    }

    /** Where YouTube's metadata (search, albums, the player script) comes from. */
    private val extractorDomains = listOf(
        "youtube.com", "youtu.be", "googleapis.com", "google.com", "ytimg.com", "ggpht.com",
        "googleusercontent.com", "googlevideo.com", "gstatic.com",
    )

    fun isExtractorHost(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return extractorDomains.any { h == it || h.endsWith(".$it") }
    }

    /**
     * The client the YouTube extractor uses: the same connections, but every hop (redirects
     * included) must be HTTPS to YouTube's or Google's own servers, so nothing it parses can make
     * the app contact another site or the local network.
     */
    val extractorClient: OkHttpClient by lazy {
        client.newBuilder()
            .addNetworkInterceptor { chain ->
                val url = chain.request().url
                if (!url.isHttps || !isExtractorHost(url.host)) throw IOException("Refusing request to ${url.host}")
                chain.proceed(chain.request())
            }
            .build()
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // Never let a redirect downgrade an HTTPS request to plain HTTP.
            .followSslRedirects(false)
            .build()
    }
}

/** Bridges NewPipeExtractor's networking interface to OkHttp. */
class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val data = request.dataToSend()
        val body = when {
            data != null -> data.toRequestBody()
            method == "POST" || method == "PUT" || method == "PATCH" -> ByteArray(0).toRequestBody()
            else -> null
        }

        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(method, body)
            .header("User-Agent", Http.DESKTOP_USER_AGENT)

        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }

        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("Rate-limited by YouTube (HTTP 429)", request.url())
            }
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.let { Http.readLimited(it, Http.MAX_METADATA_BYTES) },
                response.request.url.toString(),
            )
        }
    }
}
