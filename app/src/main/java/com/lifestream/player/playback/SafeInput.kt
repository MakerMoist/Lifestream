package com.lifestream.player.playback

import android.net.Uri

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

    fun isHttps(uri: Uri?): Boolean = uri?.scheme.equals("https", ignoreCase = true)

    /** YouTube's image CDNs, where every song's artwork comes from. */
    private val imageDomains = listOf("ytimg.com", "ggpht.com", "googleusercontent.com")

    fun isImageHost(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return imageDomains.any { h == it || h.endsWith(".$it") }
    }

    /**
     * Artwork must be an HTTPS image on YouTube's image servers — never file://, content://,
     * a local address or some other site.
     */
    fun safeArtwork(uri: Uri?): Uri? = uri?.takeIf { isHttps(it) && isImageHost(it.host) }

    fun safeArtwork(url: String?): String? =
        url?.takeIf { it.length <= 2048 }?.let { safeArtwork(Uri.parse(it))?.toString() }
}
