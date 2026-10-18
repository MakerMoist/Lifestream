package com.lifestream.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.unit.dp

/*
 * A narrow scrollbar along the right edge of a scrolling list, in the theme's main colour. It
 * stays faint while the list is still and brightens while it moves, and only shows when the list
 * is longer than the screen. Everything is worked out while drawing, so scrolling never
 * recomposes the list.
 */

private val ThumbWidth = 3.dp
private val EdgeGap = 2.dp
private val MinThumb = 28.dp

@Composable
private fun scrollbarAlpha(moving: Boolean): Float {
    val alpha by animateFloatAsState(
        targetValue = if (moving) 0.85f else 0.3f,
        animationSpec = if (moving) tween(120) else tween(700, delayMillis = 500),
        label = "scrollbar",
    )
    return alpha
}

/** Draws the thumb: [start] is how far through the content (0..1), [visible] how much shows (0..1). */
private fun ContentDrawScope.drawThumb(start: Float, visible: Float, color: Color, alpha: Float) {
    if (visible >= 0.999f || alpha <= 0f) return
    val track = size.height
    if (track <= 0f) return
    val length = (track * visible).coerceAtLeast(MinThumb.toPx()).coerceAtMost(track)
    val y = (track - length) * start.coerceIn(0f, 1f)
    val w = ThumbWidth.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width - w - EdgeGap.toPx(), y),
        size = Size(w, length),
        cornerRadius = CornerRadius(w / 2, w / 2),
        alpha = alpha,
    )
}

@Composable
fun Modifier.thinScrollbar(state: LazyListState): Modifier {
    val color = MaterialTheme.colorScheme.primary
    val alpha = scrollbarAlpha(state.isScrollInProgress)
    return drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val items = info.visibleItemsInfo
        val total = info.totalItemsCount
        if (items.isEmpty() || total == 0) return@drawWithContent
        // Rows can differ in height, so the list is measured in rows: how many fit, and how far
        // down the first one is (counting the part of it already scrolled past).
        val end = info.viewportEndOffset
        val first = items.first()
        val last = items.last()
        if (first.index == 0 && first.offset >= 0 && last.index == total - 1 && last.offset + last.size <= end) {
            return@drawWithContent // everything fits: no bar
        }
        val firstPart = if (first.size > 0) (-first.offset).coerceAtLeast(0).toFloat() / first.size else 0f
        val shown = items.count { it.offset >= 0 && it.offset + it.size <= end }.coerceAtLeast(1).toFloat()
        val position = first.index + firstPart
        drawThumb(position / (total - shown).coerceAtLeast(1f), shown / total, color, alpha)
    }
}

@Composable
fun Modifier.thinScrollbar(state: LazyGridState): Modifier {
    val color = MaterialTheme.colorScheme.primary
    val alpha = scrollbarAlpha(state.isScrollInProgress)
    return drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val items = info.visibleItemsInfo
        val total = info.totalItemsCount
        if (items.isEmpty() || total == 0) return@drawWithContent
        val end = info.viewportEndOffset
        val shown = items.count { it.offset.y >= 0 && it.offset.y + it.size.height <= end }.coerceAtLeast(1).toFloat()
        if (items.first().index == 0 && items.first().offset.y >= 0 && items.last().index == total - 1 &&
            items.last().offset.y + items.last().size.height <= end
        ) return@drawWithContent
        val first = items.first()
        val firstPart = if (first.size.height > 0) (-first.offset.y).coerceAtLeast(0).toFloat() / first.size.height else 0f
        val position = first.index + firstPart
        drawThumb(position / (total - shown).coerceAtLeast(1f), shown / total, color, alpha)
    }
}

/** For a plain scrolling column (put it before verticalScroll, so it measures the visible part). */
@Composable
fun Modifier.thinScrollbar(state: ScrollState): Modifier {
    val color = MaterialTheme.colorScheme.primary
    val alpha = scrollbarAlpha(state.isScrollInProgress)
    return drawWithContent {
        drawContent()
        val max = state.maxValue
        if (max <= 0 || max == Int.MAX_VALUE) return@drawWithContent
        val viewport = size.height
        drawThumb(state.value.toFloat() / max, viewport / (viewport + max), color, alpha)
    }
}

/** verticalScroll with the narrow scrollbar. */
@Composable
fun Modifier.scrollWithBar(state: ScrollState = androidx.compose.foundation.rememberScrollState()): Modifier =
    thinScrollbar(state).verticalScroll(state)
