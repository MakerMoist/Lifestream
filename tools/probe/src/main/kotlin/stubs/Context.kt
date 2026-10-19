@file:Suppress("unused")

package android.content

/** Stand-in for the one Android type net/Http.kt mentions (never called by the probe). */
open class Context {
    open val applicationContext: Context get() = this
    open val cacheDir: java.io.File get() = java.io.File(System.getProperty("java.io.tmpdir"))
}
