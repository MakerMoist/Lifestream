package com.lifestream.player.playback

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Validation for anything that ends up in a URL or comes from outside the app. */
object SafeInput {

    /** YouTube video ids are exactly 11 characters of [A-Za-z0-9_-]. */
    private val videoId = Regex("^[A-Za-z0-9_-]{11}$")

    fun isVideoId(id: String?): Boolean = id != null && videoId.matches(id)

    /** Only YouTube's media CDN: "googlevideo.com" itself or a real subdomain of it. */
    fun isGoogleVideoHost(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return h == "googlevideo.com" || h.endsWith(".googlevideo.com")
    }

    /** YouTube's image CDNs, where every song's artwork comes from. */
    private val imageDomains = listOf("ytimg.com", "ggpht.com", "googleusercontent.com")

    fun isImageHost(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return imageDomains.any { h == it || h.endsWith(".$it") }
    }

    /**
     * An HTTPS address on YouTube's media servers, read the same way by Java's URI parser and
     * OkHttp's (which makes the request), with nothing unusual in it.
     */
    fun isGoogleVideoUrl(url: String?): Boolean {
        if (url == null || url.length > 4096) return false
        if (url.any { it == '\\' || it == '@' || it.isWhitespace() || it.isISOControl() }) return false
        val parsed = url.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return false
        val javaHost = runCatching { java.net.URI(url).host }.getOrNull() ?: return false
        return javaHost.equals(parsed.host, ignoreCase = true) && isGoogleVideoHost(parsed.host)
    }

    /**
     * Artwork must be an HTTPS image on YouTube's image servers — never file:, a local address or
     * some other site. Java's URI parser and OkHttp's must agree on the host
     * ("https://other.site\@i.ytimg.com/" reads differently to each), so the address must be plain.
     */
    fun safeArtwork(url: String?): String? {
        if (url == null || url.length > 2048) return null
        if (url.any { it == '\\' || it == '@' || it.isWhitespace() || it.isISOControl() }) return null
        val okHost = url.toHttpUrlOrNull()?.takeIf { it.isHttps }?.host ?: return null
        val javaHost = runCatching { java.net.URI(url).host }.getOrNull() ?: return null
        if (!javaHost.equals(okHost, ignoreCase = true) || !isImageHost(okHost)) return null
        return url
    }

    /** A query parameter of a URL ("clen", "expire"…), or null. */
    fun queryParameter(url: String, name: String): String? = url.toHttpUrlOrNull()?.queryParameter(name)
}
