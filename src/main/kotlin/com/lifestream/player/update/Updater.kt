package com.lifestream.player.update

import com.lifestream.player.BuildInfo
import com.lifestream.player.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Checks LifeStream for Windows' own release ("windows-latest", next to the Android app's on the
 * same releases page) for a newer version, downloads its installer, verifies it and runs it.
 *
 * Trust: everything comes over HTTPS from GitHub only, and the installer's SHA-256 must match
 * the one published with the release (version-windows.json) before it's run.
 */
object Updater {

    data class Release(
        val versionName: String,
        val sha256: String,
        val installerUrl: String,
    )

    private const val MAX_INSTALLER_BYTES = 400L * 1024 * 1024
    private const val MAX_JSON_BYTES = 64L * 1024
    private val sha256Hex = Regex("^[0-9a-f]{64}$")
    private val versionPattern = Regex("^(\\d{1,4})\\.(\\d{1,4})\\.(\\d{1,4})([a-z]?)$")
    private val base = "https://github.com/${BuildInfo.UPDATE_REPO}/releases/download/windows-latest/"

    /** Every hop of every update request (redirects included) must stay on GitHub over HTTPS. */
    private val client by lazy {
        Http.client.newBuilder()
            .cache(null)
            .addNetworkInterceptor { chain ->
                requireGitHub(chain.request().url.toString())
                chain.proceed(chain.request())
            }
            .build()
    }

    val currentVersionName: String get() = BuildInfo.VERSION

    /** The latest published release, or throws with a readable message. */
    suspend fun fetchLatest(): Release = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(base + "version-windows.json").header("User-Agent", "LifeStream").build()
        val call = client.newCall(request)
        call.timeout().timeout(30, TimeUnit.SECONDS)
        val text = call.execute().use { response ->
            requireGitHub(response.request.url.toString())
            when {
                response.code == 404 -> throw IOException("No Windows release has been published yet.")
                !response.isSuccessful -> throw IOException("Update check failed (HTTP ${response.code}).")
            }
            val body = response.body ?: throw IOException("Update check failed (empty response).")
            try {
                Http.readLimited(body, MAX_JSON_BYTES)
            } catch (e: IOException) {
                throw IOException("Unexpected response from GitHub.")
            }
        }
        val info = try {
            JSONObject(text.trim().removePrefix("\uFEFF"))
        } catch (e: org.json.JSONException) {
            throw IOException("The release's version file couldn't be read.")
        }
        val version = info.optString("versionName").trim()
        if (!versionPattern.matches(version)) throw IOException("The release's version is missing or invalid.")
        val sha = info.optString("msiSha256").lowercase()
        if (!sha.matches(sha256Hex)) throw IOException("The release's checksum is missing or invalid.")
        Release(version, sha, base + "LifeStream-Windows-Setup.msi")
    }

    fun isNewer(release: Release): Boolean = compareVersions(release.versionName, currentVersionName) > 0

    /** "0.1.10" is after "0.1.9", and "0.1.0a" after "0.1.0". */
    fun compareVersions(a: String, b: String): Int {
        fun parts(v: String): List<Int>? = versionPattern.matchEntire(v)?.groupValues?.let { g ->
            listOf(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].firstOrNull()?.code ?: 0)
        }
        val x = parts(a) ?: return -1
        val y = parts(b) ?: return 1
        for (i in x.indices) if (x[i] != y[i]) return x[i].compareTo(y[i])
        return 0
    }

    /** Downloads the installer to a private temporary folder, verifying size, host and SHA-256. */
    suspend fun download(release: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        requireGitHub(release.installerUrl)
        val dir = File(System.getProperty("java.io.tmpdir"), "LifeStream-Update").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // only ever keep the one being installed
        val target = File(dir, "LifeStream-Windows-Setup-${release.versionName}.msi")
        val partial = File(dir, "${target.name}.part")
        try {
            client.newCall(Request.Builder().url(release.installerUrl).header("User-Agent", "LifeStream").build())
                .execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code}).")
                    requireGitHub(response.request.url.toString())
                    val body = response.body ?: throw IOException("Download failed (empty response).")
                    val total = body.contentLength()
                    if (total > MAX_INSTALLER_BYTES) throw IOException("The update is unexpectedly large.")
                    val digest = MessageDigest.getInstance("SHA-256")
                    var read = 0L
                    var lastPercent = -1
                    partial.outputStream().use { out ->
                        body.byteStream().use { input ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                read += n
                                if (read > MAX_INSTALLER_BYTES) throw IOException("The update is unexpectedly large.")
                                digest.update(buf, 0, n)
                                out.write(buf, 0, n)
                                if (total > 0) {
                                    val percent = (read * 100 / total).toInt().coerceIn(0, 100)
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        onProgress(percent / 100f)
                                    }
                                }
                            }
                        }
                    }
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (actual != release.sha256) throw IOException("The download didn't match its checksum, so it was discarded.")
                }
        } catch (e: Exception) {
            partial.delete() // a failed or cancelled download doesn't stay on disk
            throw e
        }
        if (!partial.renameTo(target)) throw IOException("Couldn't save the update.")
        target
    }

    /**
     * Runs the verified installer (Windows asks to confirm) and closes LifeStream so it can be
     * replaced. Only a file this app downloaded and verified, in its own update folder, is run.
     */
    fun install(installer: File) {
        val dir = File(System.getProperty("java.io.tmpdir"), "LifeStream-Update").canonicalFile
        val file = installer.canonicalFile
        if (file.parentFile != dir || !file.name.endsWith(".msi") || !file.isFile) throw IOException("Unexpected installer.")
        val msiexec = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32\\msiexec.exe")
        ProcessBuilder(msiexec.path, "/i", file.path).start()
    }

    /**
     * True for the portable (unzipped) app: it isn't where the installer puts LifeStream, so an
     * update is a new zip from the releases page rather than the installer.
     */
    val isPortable: Boolean by lazy {
        val app = System.getProperty("jpackage.app-path")?.let { runCatching { File(it).canonicalPath.lowercase() }.getOrNull() }
            ?: return@lazy false // run from the build, not a packaged app
        val installed = listOfNotNull(System.getenv("LOCALAPPDATA"), System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)"))
            .mapNotNull { runCatching { File(it).canonicalPath.lowercase() + File.separator }.getOrNull() }
        installed.none { app.startsWith(it) }
    }

    /** Opens the releases page in the browser (for the portable app). */
    fun openReleasesPage() {
        runCatching { java.awt.Desktop.getDesktop().browse(URI("https://github.com/${BuildInfo.UPDATE_REPO}/releases/tag/windows-latest")) }
    }

    /** Only GitHub's site and asset CDN, over HTTPS. */
    private fun requireGitHub(url: String) {
        val uri = runCatching { URI(url) }.getOrNull()
        val host = uri?.host?.lowercase() ?: ""
        val ok = uri?.scheme == "https" && uri.userInfo == null && (
            host == "github.com" || host == "githubusercontent.com" || host.endsWith(".githubusercontent.com")
            )
        if (!ok) throw IOException("Refusing to download from $host")
    }
}
