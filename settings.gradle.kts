pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // NewPipeExtractor (and its nanojson dependency) are published on JitPack. JitPack builds
        // anyone's GitHub repo on demand, so it may only supply TeamNewPipe's packages — nothing
        // else can be substituted from there.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroupAndSubgroups("com.github.TeamNewPipe") }
        }
    }
}

rootProject.name = "Lifestream"
include(":app")
