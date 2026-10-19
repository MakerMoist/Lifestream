package com.lifestream.player

import androidx.compose.material3.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.lifestream.player.data.AppDirs
import com.lifestream.player.data.AppSettings
import com.lifestream.player.data.Library
import com.lifestream.player.data.SearchHistory
import com.lifestream.player.data.SoundtrackIndex
import com.lifestream.player.net.Http
import com.lifestream.player.playback.SongStore
import com.lifestream.player.playback.StreamResolver
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

fun startUp() {
    AppDirs.init()
    Http.init(AppDirs.cache)
    StreamResolver.init(AppDirs.cache)
    NewPipe.init(com.lifestream.player.net.OkHttpDownloader(Http.extractorClient), Localization("en", "US"), ContentCountry("US"))
    AppSettings.init()
    Library.init()
    SearchHistory.init()
    SongStore.init()
    SoundtrackIndex.init()
}

fun main() {
    startUp()
    application {
        Window(onCloseRequest = ::exitApplication, title = "LifeStream") {
            Text("LifeStream ${BuildInfo.VERSION}")
        }
    }
}
