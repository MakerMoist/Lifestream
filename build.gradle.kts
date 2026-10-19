import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
    id("org.jetbrains.compose") version "1.8.2"
}

/** LifeStream for Windows. Its own version, separate from the Android app's. */
val appVersion = "0.5.6"

/**
 * Windows installers only take plain numbers (major.minor.build), so a letter is folded into the
 * last number: 0.1.0 → 0.1.0, 0.1.0a → 0.1.1, 0.1.0b → 0.1.2, 0.1.1 → 0.1.100. Each release
 * still gets a higher number than the one before, so it installs over it.
 */
val installerVersion = Regex("""(\d+)\.(\d+)\.(\d+)([a-z]?)""").matchEntire(appVersion)!!.groupValues.let { g ->
    val letter = g[4].firstOrNull()?.let { it - 'a' + 1 } ?: 0
    "${g[1]}.${g[2]}.${g[3].toInt() * 100 + letter}"
}
group = "com.lifestream"
version = appVersion

kotlin {
    jvmToolchain(17)
}

// FFmpeg's native libraries for the system being built on (Windows for the release; Linux for
// the tests that run on Linux).
val ffmpegClassifier = when {
    System.getProperty("os.name").lowercase().contains("win") -> "windows-x86_64"
    else -> "linux-x86_64"
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.1")

    // YouTube Music metadata and audio links (the same extractor the Android app uses).
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20250517")
    implementation("com.google.code.gson:gson:2.13.1")

    // Audio decoding (AAC and Opus) through FFmpeg.
    implementation("org.bytedeco:javacv:1.5.11") { isTransitive = false }
    implementation("org.bytedeco:javacpp:1.5.11")
    implementation("org.bytedeco:ffmpeg:7.1-1.5.11")
    runtimeOnly("org.bytedeco:javacpp:1.5.11:$ffmpegClassifier")
    runtimeOnly("org.bytedeco:ffmpeg:7.1-1.5.11:$ffmpegClassifier")

    // Finding Cast devices on the network, and the media keys.
    implementation("org.jmdns:jmdns:3.6.1")
    implementation("net.java.dev.jna:jna-platform:5.17.0")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// The version shown in the app (Settings, update checks) comes from here.
val generateVersion by tasks.registering {
    val out = layout.buildDirectory.dir("generated/version")
    val v = appVersion
    inputs.property("version", v)
    outputs.dir(out)
    doLast {
        val f = out.get().file("com/lifestream/player/BuildInfo.kt").asFile
        f.parentFile.mkdirs()
        f.writeText(
            """
            package com.lifestream.player

            object BuildInfo {
                const val VERSION = "$v"
                const val UPDATE_REPO = "MakerMoist/Lifestream"
            }
            """.trimIndent()
        )
    }
}
sourceSets["main"].kotlin.srcDir(generateVersion)

compose.desktop {
    application {
        mainClass = "com.lifestream.player.MainKt"
        // A small, single-threaded garbage collector that starts small and hands unused memory back
        // to Windows promptly (a music player shouldn't sit on hundreds of MB it isn't using).
        jvmArgs += listOf(
            "-XX:+UseSerialGC", "-Xms32m", "-Xmx512m", "-XX:MaxNewSize=64m",
            "-XX:MinHeapFreeRatio=10", "-XX:MaxHeapFreeRatio=30",
            // The quick code compiler only: a music player doesn't need the heavy optimizing one,
            // whose working memory (and its store of compiled code) is one of the larger costs.
            "-XX:TieredStopAtLevel=1", "-XX:CICompilerCount=1", "-XX:ReservedCodeCacheSize=48m",
            "-Dfile.encoding=UTF-8",
            // Hardening: other programs can't attach to LifeStream and load code into it, and Java's
            // built-in object deserialization (which LifeStream never uses, and a common way in for
            // attacks) refuses everything.
            "-XX:+DisableAttachMechanism",
            "-Djdk.serialFilter=!*",
        )
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "LifeStream"
            packageVersion = installerVersion
            description = "LifeStream"
            vendor = "MakerMoist"
            // The whole Java runtime: the extractor and FFmpeg's loader use parts a trimmed one leaves out.
            includeAllModules = true
            windows {
                iconFile.set(project.file("packaging/icon.ico"))
                menu = true
                menuGroup = "LifeStream"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Fixed, so each new version installs over the last.
                upgradeUuid = "3f4b8a2e-6c1d-4e7a-9b25-0d8e5a7c1f63"
            }
        }
    }
}
