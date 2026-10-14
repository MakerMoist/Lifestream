package com.lifestream.player.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.lifestream.player.BuildConfig
import com.lifestream.player.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Checks Lifestream's GitHub "latest" release for a newer build, downloads it, verifies it and
 * hands it to Android's installer.
 *
 * Trust: everything is fetched over HTTPS from GitHub only; the APK's SHA-256 must match the
 * release's version.json, and its signing certificate must match the installed app's (Android
 * enforces that too, but checking first gives a clear message instead of a failed install).
 */
object Updater {

    data class Release(
        val versionCode: Int,
        val versionName: String,
        val sha256: String,
        val apkUrl: String,
        val apkSize: Long,
    )

    private const val MAX_APK_BYTES = 300L * 1024 * 1024
    private const val MAX_JSON_BYTES = 2L * 1024 * 1024

    /**
     * Every hop of every update request — including redirects, which OkHttp would otherwise
     * follow anywhere — must stay on GitHub over HTTPS.
     */
    private val client by lazy {
        Http.client.newBuilder()
            .addNetworkInterceptor { chain ->
                requireGitHub(chain.request().url.toString())
                chain.proceed(chain.request())
            }
            .build()
    }

    /** Release text shown in the app: short, plain characters only. */
    private fun cleanVersionName(raw: String): String =
        raw.filter { it.isLetterOrDigit() || it in ".-+ " }.trim().take(32).ifBlank { "?" }

    val currentVersionCode: Int get() = BuildConfig.VERSION_CODE
    val currentVersionName: String get() = BuildConfig.VERSION_NAME

    /** Latest published release, or throws with a readable message. */
    suspend fun fetchLatest(): Release = withContext(Dispatchers.IO) {
        val api = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/tags/latest"
        val release = JSONObject(getText(api, accept = "application/vnd.github+json"))
        val assets = release.getJSONArray("assets")
        var versionUrl: String? = null
        var apkUrl: String? = null
        var apkSize = -1L
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            when (a.optString("name")) {
                "version.json" -> versionUrl = a.optString("browser_download_url")
                "Lifestream.apk" -> {
                    apkUrl = a.optString("browser_download_url")
                    apkSize = a.optLong("size", -1)
                }
            }
        }
        if (versionUrl == null || apkUrl == null) throw IOException("The latest release has no installable build yet.")
        val info = JSONObject(getText(versionUrl!!))
        val sha = info.optString("sha256").lowercase()
        if (!sha.matches(Regex("^[0-9a-f]{64}$"))) throw IOException("The release's checksum is missing or invalid.")
        val code = info.optInt("versionCode", -1)
        if (code <= 0) throw IOException("The release's version is missing or invalid.")
        Release(
            versionCode = code,
            versionName = cleanVersionName(info.optString("versionName", "?")),
            sha256 = sha,
            apkUrl = apkUrl!!,
            apkSize = apkSize,
        )
    }

    fun isNewer(release: Release) = release.versionCode > currentVersionCode

    /** Downloads the APK to app-private cache, verifying size, host and SHA-256 as it goes. */
    suspend fun download(context: Context, release: Release, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            requireGitHub(release.apkUrl)
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() } // only ever keep the one being installed
            val target = File(dir, "Lifestream-${release.versionCode}.apk")
            val partial = File(dir, "${target.name}.part")

            client.newCall(Request.Builder().url(release.apkUrl).header("User-Agent", "Lifestream").build())
                .execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code}).")
                    requireGitHub(response.request.url.toString()) // where redirects actually landed
                    val body = response.body ?: throw IOException("Download failed (empty response).")
                    val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize
                    if (total > MAX_APK_BYTES) throw IOException("Update is unexpectedly large.")
                    val digest = MessageDigest.getInstance("SHA-256")
                    var read = 0L
                    partial.outputStream().use { out ->
                        body.byteStream().use { input ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                read += n
                                if (read > MAX_APK_BYTES) throw IOException("Update is unexpectedly large.")
                                digest.update(buf, 0, n)
                                out.write(buf, 0, n)
                                if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (actual != release.sha256) {
                        partial.delete()
                        throw IOException("The download didn't match its checksum, so it was discarded.")
                    }
                }
            if (!partial.renameTo(target)) throw IOException("Couldn't save the update.")
            if (!signedLikeThisApp(context, target)) {
                target.delete()
                throw IOException("This build isn't signed with Lifestream's key, so it can't update the installed app.")
            }
            // The APK itself must be the version the release says it is, and newer than this one.
            val apkVersion = archiveVersionCode(context, target)
            if (apkVersion != release.versionCode.toLong() || apkVersion <= currentVersionCode) {
                target.delete()
                throw IOException("This build's version doesn't match the release, so it was discarded.")
            }
            target
        }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the system screen where the user allows Lifestream to install updates. */
    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Hands the verified APK to Android's package installer, which asks the user to confirm. */
    fun launchInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // ---- helpers ----

    private fun getText(url: String, accept: String? = null): String {
        requireGitHub(url)
        val request = Request.Builder().url(url).header("User-Agent", "Lifestream")
            .apply { if (accept != null) header("Accept", accept) }
            .build()
        client.newCall(request).execute().use { response ->
            requireGitHub(response.request.url.toString())
            when {
                response.code == 404 -> throw IOException("No published release found. Is the repository public?")
                response.code == 403 -> throw IOException("GitHub is rate-limiting update checks. Try again later.")
                !response.isSuccessful -> throw IOException("Update check failed (HTTP ${response.code}).")
            }
            val body = response.body ?: throw IOException("Update check failed (empty response).")
            return try {
                Http.readLimited(body, MAX_JSON_BYTES)
            } catch (e: IOException) {
                throw IOException("Unexpected response from GitHub.")
            }
        }
    }

    /** Only GitHub's API, site and asset CDN, over HTTPS. */
    private fun requireGitHub(url: String) {
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase() ?: ""
        val ok = uri.scheme == "https" && (
            host == "api.github.com" || host == "github.com" ||
                host == "githubusercontent.com" || host.endsWith(".githubusercontent.com")
            )
        if (!ok) throw IOException("Refusing to download from $host")
    }

    @Suppress("DEPRECATION")
    private fun archiveVersionCode(context: Context, apk: File): Long {
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0) ?: return -1
        if (info.packageName != context.packageName) return -1
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    }

    @Suppress("DEPRECATION")
    private fun signedLikeThisApp(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return true // Android still enforces it
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false
        if (archive.packageName != context.packageName) return false
        val installed = pm.getPackageInfo(context.packageName, flags)
        val mine = installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet() ?: return false
        val theirs = archive.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet() ?: return false
        return mine == theirs
    }
}
