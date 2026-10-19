// A JVM-only harness that runs the app's real Catalog + Matching rules against YouTube Music
// and writes report.md. It runs in GitHub Actions (see .github/workflows/probe.yml).
plugins {
    kotlin("jvm") version "2.2.0"
    application
}

dependencies {
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
}

application {
    mainClass.set("probe.MainKt")
}

val appRoot = rootDir.resolve("../../app/src/main")
val appSrc = layout.buildDirectory.dir("appsrc")
val genDir = layout.buildDirectory.dir("gen")

// Pull the pure-Kotlin parts of the app in verbatim so the probe tests exactly what ships.
val copyAppSources by tasks.registering(Copy::class) {
    from(appRoot.resolve("java/com/lifestream/player/data")) {
        include("Catalog.kt", "Matching.kt", "Models.kt", "ArtistCredits.kt", "Romaji.kt", "VersionNames.kt")
        into("data")
    }
    from(appRoot.resolve("java/com/lifestream/player/net")) {
        include("Http.kt")
        into("net")
    }
    into(appSrc)
}

// Catalog.kt references R.drawable.art_*; give it a stand-in R.
val generateR by tasks.registering {
    val res = appRoot.resolve("res/drawable-nodpi")
    val out = genDir.map { it.file("R.kt") }
    inputs.dir(res)
    outputs.file(out)
    doLast {
        val names = res.list()!!.filter { it.endsWith(".webp") }.map { it.removeSuffix(".webp") }.sorted()
        val body = names.mapIndexed { i, n -> "        const val $n = ${i + 1}" }.joinToString("\n")
        out.get().asFile.apply {
            parentFile.mkdirs()
            writeText("package com.lifestream.player\n\nobject R {\n    object drawable {\n$body\n    }\n}\n")
        }
    }
}

kotlin {
    sourceSets["main"].kotlin.srcDir(appSrc)
    sourceSets["main"].kotlin.srcDir(genDir)
}

tasks.named("compileKotlin") { dependsOn(copyAppSources, generateR) }
