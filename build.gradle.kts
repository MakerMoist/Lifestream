import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
    id("org.jetbrains.compose") version "1.8.2"
}

/** LifeStream for Windows. Its own version, separate from the Android app's. */
val appVersion = "0.1.0"
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
        jvmArgs += listOf("-Xss2m", "-XX:+UseSerialGC", "-Xmx768m", "-Dfile.encoding=UTF-8")
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "LifeStream"
            packageVersion = appVersion
            description = "Final Fantasy soundtracks, streamed"
            vendor = "MakerMoist"
            // The whole Java runtime: the extractor and FFmpeg's loader use parts a trimmed one leaves out.
            includeAllModules = true
            windows {
                iconFile.set(project.file("src/main/resources/icon.ico"))
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
