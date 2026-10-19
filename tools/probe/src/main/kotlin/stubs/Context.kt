// Stand-in for the one Android type Http.kt mentions (its init, which the probe never calls).
@file:Suppress("unused")

package android.content

open class Context {
    open val applicationContext: Context get() = this
    open val cacheDir: java.io.File get() = java.io.File(System.getProperty("java.io.tmpdir"))
}
