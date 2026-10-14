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

    /** Artwork must be a plain HTTPS image URL — never file://, content:// or anything local. */
    fun safeArtwork(uri: Uri?): Uri? = uri?.takeIf { isHttps(it) && !it.host.isNullOrBlank() }
}
