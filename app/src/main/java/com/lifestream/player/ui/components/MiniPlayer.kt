package com.lifestream.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifestream.player.playback.PlayerConnection
import com.lifestream.player.playback.PlayerUiState
import com.lifestream.player.ui.theme.LsColors

@Composable
fun MiniPlayer(state: PlayerUiState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val track = state.current ?: return
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            // Slide right to close: stops the song and clears the queue. Only a deliberate
            // sideways slide counts: a swipe that starts going up or down (scrolling past the
            // bar) is ignored, and it takes half the bar's width (or a quick flick a third of the
            // way) to close. Touches are measured before the sliding layer, so the bar moving
            // under your finger doesn't skew them.
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    tracker.resetTracking()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val slop = viewConfiguration.touchSlop
                    var dx = 0f
                    var dy = 0f
                    var sliding = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val step = change.position - change.previousPosition
                        dx += step.x
                        dy += step.y
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (!sliding) {
                            // Up or down first: not a close; let it go.
                            if (kotlin.math.abs(dy) > slop) break
                            // Clearly sideways (to the right) and well past the touch slop.
                            if (dx > slop * 2 && dx > kotlin.math.abs(dy) * 3) sliding = true else continue
                        }
                        change.consume()
                        scope.launch { offsetX.snapTo((offsetX.value + step.x).coerceAtLeast(0f)) }
                    }
                    if (!sliding) {
                        if (offsetX.value != 0f) scope.launch { offsetX.animateTo(0f, spring()) }
                        return@awaitEachGesture
                    }
                    run {
                        val velocity = tracker.calculateVelocity().x
                        val close = offsetX.value > widthPx * 0.5f ||
                            (velocity > 2_500f && offsetX.value > widthPx * 0.33f)
                        scope.launch {
                            if (close) {
                                offsetX.animateTo(widthPx, tween(160))
                                PlayerConnection.stopAndClear()
                                // Stay off-screen until the cleared queue reaches the UI, so the
                                // bar doesn't flash back for a frame (or come back if it failed).
                                // (Spelled out: inside the gesture block the name means the touch-scope version.)
                                kotlinx.coroutines.withTimeoutOrNull(1500) { PlayerConnection.state.first { it.current == null } }
                                offsetX.snapTo(0f)
                            } else {
                                offsetX.animateTo(0f, spring(dampingRatio = 0.7f))
                            }
                        }
                    }
                }
            }
            .graphicsLayer {
                translationX = offsetX.value
                alpha = 1f - (offsetX.value / widthPx).coerceIn(0f, 1f) * 0.8f
            }
            .clickable(onClick = onOpen),
    ) {
        Column {
            // Read in the drawing step only, so the ticking position never recomposes this bar.
            val position = PlayerConnection.position.collectAsStateWithLifecycle()
            val duration = state.durationMs
            LinearProgressIndicator(
                progress = { if (duration > 0) (position.value.toFloat() / duration).coerceIn(0f, 1f) else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = LsColors.Mako,
                trackColor = Color.Transparent,
            )
            Row(
                Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(track.thumbnail, Modifier.size(44.dp), corner = 6.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    // A long title glides to its end and back so it can be read; the artist line
                    // stays still (cut off if too long).
                    BackAndForthText(track.title, MaterialTheme.typography.bodyMedium)
                    Text(
                        track.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // A small previous button beside play.
                IconButton(onClick = { PlayerConnection.previous() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(20.dp))
                }
                Box(contentAlignment = Alignment.Center) {
                    if (state.isBuffering) {
                        CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 2.dp, color = LsColors.Crystal)
                    }
                    IconButton(onClick = { PlayerConnection.togglePlay() }) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (state.isPlaying) "Pause" else "Play",
                        )
                    }
                }
                IconButton(onClick = { PlayerConnection.next() }) {
                    Icon(Icons.Rounded.SkipNext, "Next")
                }
            }
        }
    }
}
