package com.lifestream.player

import com.lifestream.player.data.SearchHistory
import android.app.Application
import com.lifestream.player.data.AppSettings
import com.lifestream.player.data.Library
import com.lifestream.player.data.OfficialTitles
import com.lifestream.player.playback.SongCache
import com.lifestream.player.data.SoundtrackIndex
import com.lifestream.player.net.Http
import com.lifestream.player.net.OkHttpDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class LifestreamApp : Application(), coil.ImageLoaderFactory {

    /**
     * Artwork loading with sensible limits: decoded images may use up to 10% of the app's memory
     * (the default is 25%), opaque artwork is stored at half the bytes per pixel, the disk copy is
     * capped at 64 MB (default up to 250 MB), and the app's existing network connections are
     * reused instead of opening separate ones.
     */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(0.10).build() }
        .diskCache {
            coil.disk.DiskCache.Builder()
                .directory(cacheDir.resolve("artwork"))
                .maxSizeBytes(64L * 1024 * 1024)
                .build()
        }
        // Same connection pool as the rest of the app, but every hop (redirects included) must
        // stay on YouTube's image servers over HTTPS.
        .okHttpClient {
            Http.client.newBuilder()
                .addNetworkInterceptor { chain ->
                    val url = chain.request().url
                    if (!url.isHttps || !com.lifestream.player.playback.SafeInput.isImageHost(url.host)) {
                        throw java.io.IOException("Artwork from an unexpected host")
                    }
                    chain.proceed(chain.request())
                }
                .build()
        }
        .allowRgb565(true)
        // Album art at a given address never changes; don't re-download it because of short
        // cache headers.
        .respectCacheHeaders(false)
        .build()

    override fun onCreate() {
        super.onCreate()
        // The extractor talks to YouTube's servers through our own OkHttp client.
        // Ask YouTube for English metadata; anything still in Japanese is translated on the device.
        NewPipe.init(OkHttpDownloader(Http.client), Localization("en", "US"), ContentCountry("US"))
        AppSettings.init(this)
        OfficialTitles.init(this)
        Library.init(this)
        SearchHistory.init(this)
        SongCache.init(this)
        SoundtrackIndex.init(this)
        Thread({ runCatching { com.lifestream.player.update.Updater.cleanUpOldDownloads(this) } }, "update-cleanup").start()
    }

    /**
     * When Android is short on memory and LifeStream is in the background (e.g. just playing music),
     * let go of the decoded card art so the app is less likely to be stopped.
     */
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // The app's screens are hidden (often while music keeps playing): drop search and
        // translation caches, which are quick to rebuild.
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            com.lifestream.player.data.Matching.trimCaches()
            com.lifestream.player.data.Romaji.trimCaches()
            com.lifestream.player.data.TitleTranslator.trimCaches()
            SoundtrackIndex.trimCaches()
        }
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            com.lifestream.player.ui.components.ArtCache.clear()
        }
    }
}
