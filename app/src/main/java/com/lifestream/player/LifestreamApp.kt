package com.lifestream.player

import android.app.Application
import com.lifestream.player.data.Library
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
        Library.init(this)
        TitleTranslator.prefetch()
    }
}
