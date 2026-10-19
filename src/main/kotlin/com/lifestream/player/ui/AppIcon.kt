package com.lifestream.player.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * LifeStream's crystal icon as shapes rather than a picture, so it's drawn sharp and smooth at
 * whatever size it's shown: the sidebar, the title bar, the taskbar and the icon menu.
 * (The same design as the app's original icon: a crystal with a stream flowing through it.)
 */
object AppIcon {
    val vector: ImageVector by lazy {
        fun path(d: String) = PathParser().parsePathString(d).toNodes()
        ImageVector.Builder(
            name = "LifeStream",
            defaultWidth = 256.dp,
            defaultHeight = 256.dp,
            viewportWidth = 256f,
            viewportHeight = 256f,
        )
            // The rounded square behind it.
            .addPath(
                path("M52,0 H204 A52,52 0 0 1 256,52 V204 A52,52 0 0 1 204,256 H52 A52,52 0 0 1 0,204 V52 A52,52 0 0 1 52,0 Z"),
                fill = SolidColor(Color(0xFF0D1430)),
            )
            // The crystal: left facet, right facet, highlight.
            .addPath(path("M128,36 L82.25,121.78 L128,219 Z"), fill = SolidColor(Color(0xFF4FB8F0)))
            .addPath(path("M128,36 L173.75,121.78 L128,219 Z"), fill = SolidColor(Color(0xFF9FE3FF)))
            .addPath(path("M128,36 L145.16,110.34 L128,121.78 Z"), fill = SolidColor(Color(0xCCFFFFFF)))
            // The stream flowing through it.
            .addPath(
                path("M59.38,173.25 C93.69,138.94 116.56,184.69 150.88,150.38 C173.75,127.5 190.91,138.94 202.34,121.78"),
                stroke = SolidColor(Color(0xFF6EF2B5)),
                strokeLineWidth = 10f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
    }

    /**
     * The icon as a finished picture of exactly [px] × [px] pixels (for Windows' icons), copied
     * into a plain image Windows can use straight away.
     */
    fun image(px: Int, painter: Painter): java.awt.image.BufferedImage {
        val drawn = painter.toAwtImage(Density(1f), LayoutDirection.Ltr, Size(px.toFloat(), px.toFloat()))
        val out = java.awt.image.BufferedImage(px, px, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        try {
            g.drawImage(drawn, 0, 0, px, px, null)
        } finally {
            g.dispose()
        }
        return out
    }

    /** Every size Windows may ask for, so it never has to stretch or shrink one itself. */
    val windowsSizes = listOf(16, 20, 24, 32, 40, 48, 64, 96, 128, 256)

    /**
     * The icon for the icon menu by the clock, drawn at exactly the size that menu uses at this
     * display's scale (a picture of any other size would be stretched, or not shown at all).
     */
    fun trayImage(painter: Painter): java.awt.image.BufferedImage {
        val logical = runCatching { java.awt.SystemTray.getSystemTray().trayIconSize.width }.getOrDefault(16).coerceIn(16, 64)
        val scale = runCatching {
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX
        }.getOrDefault(1.0).coerceIn(1.0, 4.0)
        return image(Math.round(logical * scale).toInt(), painter)
    }
}
