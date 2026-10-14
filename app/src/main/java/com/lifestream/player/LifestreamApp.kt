package com.lifestream.player

import com.lifestream.player.data.SearchHistory
import android.app.Application
import com.lifestream.player.data.AppSettings
import com.lifestream.player.data.Library
import com.lifestream.player.data.OfficialTitles
import com.lifestream.player.playback.SongCache
import com.lifestream.player.data.SoundtrackIndex
import com.lifestream.player.data.TitleTranslator
import com.lifestream.player.net.Http
import com.lifestream.player.net.OkHttpDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class LifestreamApp : Application() {
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
        TitleTranslator.prefetch()
    }
}
