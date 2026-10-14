pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroupAndSubgroups("com.github.TeamNewPipe") }
        }
    }
}
rootProject.name = "probe"
