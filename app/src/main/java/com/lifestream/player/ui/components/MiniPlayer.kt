package com.lifestream.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import kotlinx.coroutines.withTimeoutOrNull
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
            // Slide right to close: stops the song and clears the queue.
            .graphicsLayer {
                translationX = offsetX.value
                alpha = 1f - (offsetX.value / widthPx).coerceIn(0f, 1f) * 0.8f
            }
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        scope.launch { offsetX.snapTo((offsetX.value + dragAmount).coerceAtLeast(0f)) }
                    },
                    onDragCancel = { scope.launch { offsetX.animateTo(0f, spring()) } },
                    onDragEnd = {
                        val close = offsetX.value > widthPx * 0.35f || tracker.calculateVelocity().x > 1500f
                        scope.launch {
                            if (close) {
                                offsetX.animateTo(widthPx, tween(160))
                                PlayerConnection.stopAndClear()
                                // Stay off-screen until the cleared queue reaches the UI, so the
                                // bar doesn't flash back for a frame (or come back if it failed).
                                withTimeoutOrNull(1500) { PlayerConnection.state.first { it.current == null } }
                                offsetX.snapTo(0f)
                            } else {
                                offsetX.animateTo(0f, spring(dampingRatio = 0.7f))
                            }
                        }
                    },
                )
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
                    Text(
                        track.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
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
