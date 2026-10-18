package com.lifestream.player.ui.screens

import com.lifestream.player.ui.components.scrollWithBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifestream.player.data.Catalog
import com.lifestream.player.ui.components.SectionLabel

/** One credit: what it is, who it belongs to, and how it's licensed (or other notes). */
data class Credit(val name: String, val owner: String, val note: String? = null)

/**
 * Everything LifeStream is built with or draws on, credited to its owners, and the trademarks of
 * every game whose music it plays.
 */
object Credits {
    private const val APACHE = "Apache License 2.0"
    private const val SQUARE_ENIX = "Square Enix Holdings Co., Ltd."

    val sections: List<Pair<String, List<Credit>>> = listOf(
        "App & Platform" to listOf(
            Credit("Android", "Google LLC and the Android Open Source Project", APACHE),
            Credit("Kotlin", "JetBrains s.r.o. and Kotlin contributors", APACHE),
            Credit("Kotlin Coroutines", "JetBrains s.r.o. and contributors", APACHE),
            Credit(
                "Android Jetpack (AndroidX Core, Activity, Lifecycle, Navigation, AppCompat, Startup, Profile Installer, Emoji2)",
                "Google LLC and the Android Open Source Project", APACHE,
            ),
            Credit("Jetpack Compose (UI, Foundation, Material 3)", "Google LLC and the Android Open Source Project", APACHE),
            Credit("Material Icons", "Google LLC", APACHE),
        ),
        "Playback & Casting" to listOf(
            Credit("AndroidX Media3 (ExoPlayer, Session, DataSource, Database, Cast)", "Google LLC and the Android Open Source Project", APACHE),
            Credit("AndroidX MediaRouter", "Google LLC and the Android Open Source Project", APACHE),
            Credit("Google Cast SDK", "Google LLC", "Google APIs Terms of Service"),
            Credit("NewPipe Extractor", "Team NewPipe", "GNU General Public License v3.0"),
            Credit("Rhino (JavaScript engine)", "Mozilla Foundation and contributors", "Mozilla Public License 2.0"),
            Credit("jsoup", "Jonathan Hedley", "MIT License"),
            Credit("nanojson", "Matt Mastracci", APACHE),
            Credit("PrettyTime", "OCPsoft", APACHE),
        ),
        "Networking & Images" to listOf(
            Credit("OkHttp and Okio", "Square, Inc.", APACHE),
            Credit("Coil", "Coil Contributors", APACHE),
            Credit("Guava", "Google LLC", APACHE),
        ),
        "Translation" to listOf(
            Credit("ML Kit Translation (on-device)", "Google LLC", "ML Kit Terms of Service"),
            Credit("Google Play services", "Google LLC", "Google APIs Terms of Service"),
        ),
        "Music & Content" to listOf(
            Credit(
                "YouTube and YouTube Music", "Google LLC",
                "Songs and artwork are streamed from YouTube. Every recording belongs to its rights holders.",
            ),
            Credit(
                "Soundtracks and official song names", "Square Enix Co., Ltd. and each soundtrack's publisher",
                "Music by each title's composers, as credited in the app.",
            ),
            Credit(
                "Villain quotes", "Each game's publisher",
                "Checked against Wikiquote (Wikimedia Foundation, CC BY-SA 4.0), the Final Fantasy Wiki and Chrono Wiki (Fandom, CC BY-SA).",
            ),
        ),
        "Build & Tools" to listOf(
            Credit("Gradle", "Gradle, Inc.", APACHE),
            Credit("Android Gradle Plugin, Android SDK, R8 and Android Emulator", "Google LLC and the Android Open Source Project", APACHE),
            Credit("Core library desugaring (desugar_jdk_libs)", "Google LLC and Oracle (OpenJDK)", "GPL v2 with the Classpath Exception"),
            Credit("Eclipse Temurin (Java)", "Eclipse Foundation (Adoptium)", "GPL v2 with the Classpath Exception"),
            Credit("GitHub and GitHub Actions", "GitHub, Inc.", "Builds, releases and app updates"),
            Credit("checkout, setup-java, upload-artifact and download-artifact actions", "GitHub, Inc.", "MIT License"),
            Credit("Gradle setup action", "Gradle, Inc.", "MIT License"),
            Credit("OpenSSL", "The OpenSSL Project", APACHE),
            Credit("Python", "Python Software Foundation", "PSF License"),
            Credit("Pillow", "Jeffrey A. Clark and contributors", "MIT-CMU License"),
            Credit("NumPy", "NumPy Developers", "BSD 3-Clause License"),
            Credit("JUnit 4", "JUnit Team", "Eclipse Public License 1.0"),
            Credit("AndroidX Test", "Google LLC and the Android Open Source Project", APACHE),
        ),
    )

    /**
     * Each title's trademark line, by catalog id. Every title in the catalog needs one (a test
     * checks), so a newly added soundtrack is credited here too.
     */
    val gameTrademarks: Map<String, String> = buildMap {
        val finalFantasy = "FINAL FANTASY is a registered trademark of $SQUARE_ENIX"
        for (id in listOf(
            "ff1", "ff2", "ff3", "ff4", "ff5", "ff6", "ff7", "fft", "ff7r", "ff7rb", "ff8", "ff9", "ff10",
            "ff10-2", "ff11", "ff12", "ff13", "ff13-2", "lr", "ff14", "ff15", "ff16", "ac",
        )) put(id, finalFantasy)
        put("sop", "$finalFantasy (developed with Team NINJA of KOEI TECMO GAMES)")
        put("ccr", "CRISIS CORE and FINAL FANTASY are registered trademarks of $SQUARE_ENIX")
        put("ct", "CHRONO TRIGGER is a registered trademark of $SQUARE_ENIX")
        put("cx", "CHRONO CROSS is a registered trademark of $SQUARE_ENIX")
        put("lo", "Lost Odyssey is a trademark of Microsoft Corporation (developed by Mistwalker and feelplus)")
        put("coe33", "Clair Obscur: Expedition 33 is a trademark of Sandfall Interactive (published by Kepler Interactive)")
    }

    val otherTrademarks: List<Credit> = listOf(
        Credit("Android, Android Auto, Google Cast, Chromecast, Google Play, YouTube and YouTube Music", "Trademarks of Google LLC"),
        Credit("GitHub", "A trademark of GitHub, Inc."),
        Credit("Kotlin", "A trademark of the Kotlin Foundation"),
    )

    const val DISCLAIMER = "LifeStream is an unofficial, fan-made player. It isn't affiliated with, sponsored or " +
        "endorsed by Square Enix, Koei Tecmo, Microsoft, Sandfall Interactive, Kepler Interactive or Google. " +
        "All trademarks and music belong to their respective owners."
}

/** Settings → Credits & Trademarks. */
@Composable
fun CreditsScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .scrollWithBar()
            .padding(bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
            }
            Text("Credits & Trademarks", style = MaterialTheme.typography.headlineSmall)
        }
        for ((heading, credits) in Credits.sections) {
            SectionLabel(heading, Modifier.padding(top = 8.dp))
            CreditCard(credits)
        }
        SectionLabel("Trademarks", Modifier.padding(top = 8.dp))
        // Every game in the app, in catalog order, then the platforms.
        val games = Catalog.games.mapNotNull { g -> Credits.gameTrademarks[g.id]?.let { Credit(g.title, it) } }
        CreditCard(games + Credits.otherTrademarks)
        Text(
            Credits.DISCLAIMER,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
        )
    }
}

@Composable
private fun CreditCard(credits: List<Credit>) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (c in credits) {
                Column {
                    Text(c.name, style = MaterialTheme.typography.bodyLarge)
                    Text(c.owner, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (c.note != null) {
                        Text(c.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
