package com.lifestream.player.ui.components

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation

/**
 * Blurs a small image once, on the CPU, when it's loaded (a few thousand pixels, well under a
 * millisecond). Used for Now Playing's backdrop instead of a live full-screen GPU blur, which had
 * to be redrawn on every frame something else on that screen changed (the scrolling title, the
 * seek bar). Also works on Android versions older than 12, where live blur isn't available.
 */
class SoftBlur(private val passes: Int = 3) : Transformation {
    override val cacheKey: String = "soft-blur-$passes"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        // Only meant for small images; anything unexpectedly large is left as it is.
        if (w <= 2 || h <= 2 || w.toLong() * h > 512L * 512) return input
        val a = IntArray(w * h)
        input.getPixels(a, 0, w, 0, 0, w, h)
        val b = IntArray(w * h)
        // Three box blurs in a row look like a Gaussian blur; the radius scales with the image.
        val radius = (minOf(w, h) / 11).coerceAtLeast(1)
        repeat(passes) {
            boxPass(a, b, w, h, radius, horizontal = true)
            boxPass(b, a, w, h, radius, horizontal = false)
        }
        return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888)
    }

    override fun equals(other: Any?) = other is SoftBlur && other.passes == passes

    override fun hashCode() = passes

    private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val n = if (horizontal) w else h
        val window = 2 * r + 1
        for (line in 0 until lines) {
            fun at(i: Int): Int {
                val c = i.coerceIn(0, n - 1)
                return if (horizontal) src[line * w + c] else src[c * w + line]
            }
            var sa = 0
            var sr = 0
            var sg = 0
            var sb = 0
            for (k in -r..r) {
                val p = at(k)
                sa += p ushr 24
                sr += (p shr 16) and 0xFF
                sg += (p shr 8) and 0xFF
                sb += p and 0xFF
            }
            for (i in 0 until n) {
                val out = ((sa / window) shl 24) or ((sr / window) shl 16) or ((sg / window) shl 8) or (sb / window)
                if (horizontal) dst[line * w + i] = out else dst[i * w + line] = out
                val gone = at(i - r)
                val come = at(i + r + 1)
                sa += (come ushr 24) - (gone ushr 24)
                sr += ((come shr 16) and 0xFF) - ((gone shr 16) and 0xFF)
                sg += ((come shr 8) and 0xFF) - ((gone shr 8) and 0xFF)
                sb += (come and 0xFF) - (gone and 0xFF)
            }
        }
    }
}
