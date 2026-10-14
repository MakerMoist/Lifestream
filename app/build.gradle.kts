import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.lifestream.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lifestream.player"
        minSdk = 26
        targetSdk = 35
        // CI passes its run number so every published build has a higher version than the last;
        // the in-app updater compares against it.
        val buildNumber = System.getenv("LIFESTREAM_VERSION_CODE")?.toIntOrNull() ?: 1
        versionCode = buildNumber
        versionName = System.getenv("LIFESTREAM_VERSION_NAME")
            ?.takeIf { it.matches(Regex("^\\d+\\.\\d+\\.\\d+$")) } ?: "1.1.0-dev"
        buildConfigField("String", "UPDATE_REPO", "\"MakerMoist/Lifestream\"")
    }

    // The release key comes from CI secrets via environment variables and is never committed.
    val releaseKeystore = System.getenv("LIFESTREAM_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                // Trim stray whitespace picked up when the secrets were pasted.
                storePassword = System.getenv("LIFESTREAM_KEYSTORE_PASSWORD")?.trim()
                keyAlias = System.getenv("LIFESTREAM_KEY_ALIAS")?.trim()
                keyPassword = System.getenv("LIFESTREAM_KEY_PASSWORD")?.trim()
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            // The extractor relies on reflection (Rhino JS engine), so keep shrinking off.
            isMinifyEnabled = false
            // Local builds without the key fall back to the debug key so they still install.
            signingConfig = if (releaseKeystore != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // NewPipeExtractor uses newer java.* APIs than older Android versions ship.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module",
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Our own player engine: Media3 ExoPlayer + MediaSession (background + lock-screen controls)
    implementation("androidx.media3:media3-exoplayer:1.7.1")
    implementation("androidx.media3:media3-session:1.7.1")
    implementation("androidx.media3:media3-datasource:1.7.1")
    implementation("androidx.media3:media3-database:1.7.1")

    // Resolves YouTube audio stream URLs without any YouTube app or API key
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Japanese → English titles, translated on the device
    implementation("com.google.mlkit:translate:17.0.3")

    // Images / async
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")
    implementation("com.google.guava:guava:33.4.0-android")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
}
