package com.lifestream.player

import android.app.Application
import com.lifestream.player.data.Library
import com.lifestream.player.net.Http
import com.lifestream.player.net.OkHttpDownloader
import org.schabi.newpipe.extractor.NewPipe

class LifestreamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The extractor talks to YouTube's servers through our own OkHttp client.
        NewPipe.init(OkHttpDownloader(Http.client))
        Library.init(this)
    }
}
