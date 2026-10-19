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
import androidx.compose.runtime.remember
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

/**
 * Tracks where a lazy list's rows sit, in pixels, as they scroll past. Rows can be very different
 * heights (Home's quote, its Recommended row, section labels and cards), so counting rows makes
 * the thumb jump; remembering each row's position once it's been seen keeps it moving smoothly,
 * and rows not seen yet are estimated from the average.
 */
private class RowTracker {
    private val tops = HashMap<Int, Int>()
    private val bottoms = HashMap<Int, Int>()
    private var total = -1

    /** Returns (how far through, how much shows), or null when everything fits. */
    fun measure(
        indexes: IntArray, offsets: IntArray, sizes: IntArray,
        total: Int, viewportStart: Int, viewportEnd: Int,
    ): Pair<Float, Float>? {
        val n = indexes.size
        if (n == 0 || total == 0) return null
        if (total != this.total) {
            tops.clear(); bottoms.clear(); this.total = total
        }
        val viewport = (viewportEnd - viewportStart).toFloat()
        val lastFits = indexes[n - 1] == total - 1 && offsets[n - 1] + sizes[n - 1] <= viewportEnd
        val firstAtTop = indexes[0] == 0 && offsets[0] >= viewportStart
        if (firstAtTop && lastFits) return null

        // How far the list has scrolled: from a row whose position is already known, else
        // from the top row, else estimated.
        var scroll: Float? = null
        for (k in 0 until n) {
            val known = tops[indexes[k]] ?: continue
            scroll = (known - offsets[k]).toFloat()
            break
        }
        if (scroll == null) {
            scroll = if (indexes[0] == 0) -offsets[0].toFloat() else {
                val perRow = sizes.average().toFloat().coerceAtLeast(1f)
                tops.clear(); bottoms.clear()
                indexes[0] * perRow - offsets[0]
            }
        }
        for (k in 0 until n) {
            val top = (scroll + offsets[k]).toInt()
            tops[indexes[k]] = top
            bottoms[indexes[k]] = top + sizes[k]
        }
        // The whole list's height: exact once the last row has been seen, otherwise what's known
        // plus the average for the rest.
        val maxIndex = tops.keys.maxOrNull() ?: 0
        val knownBottom = bottoms.values.maxOrNull() ?: 0
        val content = if (maxIndex >= total - 1) knownBottom.toFloat()
        else knownBottom + (total - 1 - maxIndex) * (knownBottom.toFloat() / (maxIndex + 1))
        if (content <= viewport) return null
        val start = if (lastFits) 1f else (scroll / (content - viewport)).coerceIn(0f, 1f)
        return start to (viewport / content)
    }
}

@Composable
fun Modifier.thinScrollbar(state: LazyListState): Modifier {
    val color = MaterialTheme.colorScheme.primary
    val alpha = scrollbarAlpha(state.isScrollInProgress)
    val tracker = remember(state) { RowTracker() }
    return drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val items = info.visibleItemsInfo
        val result = tracker.measure(
            IntArray(items.size) { items[it].index },
            IntArray(items.size) { items[it].offset },
            IntArray(items.size) { items[it].size },
            info.totalItemsCount, info.viewportStartOffset, info.viewportEndOffset,
        ) ?: return@drawWithContent
        drawThumb(result.first, result.second, color, alpha)
    }
}

@Composable
fun Modifier.thinScrollbar(state: LazyGridState): Modifier {
    val color = MaterialTheme.colorScheme.primary
    val alpha = scrollbarAlpha(state.isScrollInProgress)
    val tracker = remember(state) { RowTracker() }
    return drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val items = info.visibleItemsInfo
        val result = tracker.measure(
            IntArray(items.size) { items[it].index },
            IntArray(items.size) { items[it].offset.y },
            IntArray(items.size) { items[it].size.height },
            info.totalItemsCount, info.viewportStartOffset, info.viewportEndOffset,
        ) ?: return@drawWithContent
        drawThumb(result.first, result.second, color, alpha)
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
