pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        // NewPipeExtractor (and its nanojson dependency) are published on JitPack. JitPack builds
        // anyone's GitHub repo on demand, so it may only supply TeamNewPipe's packages.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroupAndSubgroups("com.github.TeamNewPipe") }
        }
    }
}

rootProject.name = "LifeStream"
