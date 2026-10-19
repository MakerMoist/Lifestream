package com.lifestream.player.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifestream.player.data.formatDuration
import com.lifestream.player.data.hiResArtwork
import com.lifestream.player.playback.PlayerConnection
import com.lifestream.player.playback.PlayerUiState
import com.lifestream.player.playback.RepeatMode
import com.lifestream.player.playback.SafeInput
import com.lifestream.player.ui.BackHandler
import com.lifestream.player.ui.components.Artwork
import com.lifestream.player.ui.components.BackAndForthText
import com.lifestream.player.ui.components.CastButton
import com.lifestream.player.ui.components.ImageLoader
import com.lifestream.player.ui.components.SaveInstantPlaylistDialog
import com.lifestream.player.ui.components.TrackActions
import com.lifestream.player.ui.components.menuAnchor
import com.lifestream.player.ui.components.onRightClick
import androidx.compose.foundation.gestures.draggable
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import com.lifestream.player.ui.components.rememberMenuAnchor
import com.lifestream.player.ui.components.thinScrollbar
import com.lifestream.player.ui.theme.LsColors

/**
 * The full player: big artwork, the song, its controls and (beside them on a wide window) what
 * plays next. Esc or the arrow at the top closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(onClose: () -> Unit, openQueue: Boolean = false) {
    val state by PlayerConnection.state.collectAsStateWithLifecycle()
    val track = state.current
    var showQueue by remember { mutableStateOf(openQueue) }
    var savingInstant by remember { mutableStateOf(false) }
    val instantQueue by PlayerConnection.instantQueue.collectAsStateWithLifecycle()

    BackHandler { onClose() }
    // Registered after the one above, so Back closes Up Next first (then Now Playing).
    BackHandler(enabled = showQueue) { showQueue = false }

    // Drag it down (anywhere that isn't a list or a slider) to close it, like pulling a sheet away.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dragScope = androidx.compose.runtime.rememberCoroutineScope()
    val dragOffset = remember { androidx.compose.animation.core.Animatable(0f) }
    val closeDistance = with(density) { 140.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = dragOffset.value }
            .background(LsColors.Night)
            // Clicks don't fall through to the page underneath.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .draggable(
                orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
                state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                    dragScope.launch { dragOffset.snapTo((dragOffset.value + delta).coerceAtLeast(0f)) }
                },
                onDragStopped = { velocity ->
                    if (dragOffset.value > closeDistance || velocity > 1800f) {
                        onClose()
                    } else {
                        dragOffset.animateTo(0f, androidx.compose.animation.core.spring(stiffness = 600f))
                    }
                },
            ),
    ) {
        // Blurred artwork wash behind everything: a small copy, blurred once and stretched.
        val wash = remember(track?.thumbnail) { SafeInput.safeArtwork(track?.thumbnail) }
        if (wash != null) {
            val small = remember(wash) { SafeInput.safeArtwork(hiResArtwork(wash, 180, videoThumbs = false)) ?: wash }
            // Blurred once, at that small size, when the song changes: drawing it is then as cheap as
            // any picture (a live full-window blur would be redone by the graphics card every frame).
            val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, small) {
                value = ImageLoader.load(small, 96)?.let { ImageLoader.blurred(it, 4f) }
            }
            image?.let {
                Image(
                    it, null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.45f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(LsColors.Night.copy(alpha = 0.55f), LsColors.Night.copy(alpha = 0.92f), LsColors.Night))),
        )

        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                // As wide as the buttons on the right, so "Now Playing" stays centred.
                Spacer(Modifier.width(96.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NOW PLAYING", style = MaterialTheme.typography.labelSmall, color = LsColors.Gold)
                    if (state.queue.size > 1 && state.playPosition >= 0) {
                        Text(
                            "${state.playPosition + 1} of ${state.queue.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
                CastButton()
                val dots = rememberMenuAnchor()
                IconButton(
                    onClick = { track?.let { TrackActions.show(it, at = dots.at(center = true)) } },
                    enabled = track != null,
                    modifier = Modifier.menuAnchor(dots),
                ) { Icon(Icons.Rounded.MoreVert, "Song options") }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val wide = maxWidth > 900.dp
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    // The player itself, centred in its half (or the whole width).
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Column(
                            Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Artwork(
                                track?.thumbnail,
                                size = 520.dp,
                                corner = 16.dp,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .aspectRatio(1f)
                                    .shadow(24.dp, RoundedCornerShape(16.dp))
                                    // Right-click the artwork for the song's menu (the same as ⋮).
                                    .onRightClick { at -> track?.let { TrackActions.show(it, at = at) } },
                            )
                            Spacer(Modifier.height(24.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    BackAndForthText(
                                        track?.title ?: "",
                                        MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = MaterialTheme.typography.titleLarge.fontSize,
                                            lineHeight = MaterialTheme.typography.titleLarge.lineHeight,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                        ),
                                    )
                                    Text(
                                        track?.artist ?: "",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (instantQueue) {
                                    IconButton(onClick = { savingInstant = true }) { Icon(Icons.Rounded.Add, "Save as playlist") }
                                }
                                IconButton(onClick = { showQueue = !showQueue }) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.QueueMusic, "Up Next",
                                        tint = if (showQueue) LsColors.Mako else Color.White,
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            SeekBar(state)
                            Spacer(Modifier.height(8.dp))
                            Controls(state)
                            Spacer(Modifier.height(10.dp))
                            com.lifestream.player.ui.VolumeControl(Modifier.widthIn(max = 360.dp).fillMaxWidth(), large = true)
                        }
                    }
                    if (showQueue && wide) {
                        Spacer(Modifier.width(24.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.width(380.dp).fillMaxHeight().padding(vertical = 16.dp),
                        ) {
                            Column { QueueList(state) }
                        }
                    }
                }
                // A narrow window shows Up Next over the player instead of beside it.
                if (showQueue && !wide) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxSize().padding(vertical = 16.dp),
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { showQueue = false }) { Icon(Icons.Rounded.Close, "Close Up Next") }
                            }
                            QueueList(state)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        // Close, in the bottom-left corner.
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 16.dp),
        ) { Icon(Icons.Rounded.KeyboardArrowDown, "Close player") }
    }

    if (savingInstant) SaveInstantPlaylistDialog(onDismiss = { savingInstant = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeekBar(state: PlayerUiState, compact: Boolean = false) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    // Only this small bar watches the ticking position.
    val positionMs by PlayerConnection.position.collectAsStateWithLifecycle()
    val duration = state.durationMs.coerceAtLeast(1)
    val fraction = dragging ?: (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val seekColors = SliderDefaults.colors(
        thumbColor = LsColors.Crystal,
        activeTrackColor = LsColors.Crystal,
        inactiveTrackColor = Color.White.copy(alpha = 0.15f),
    )
    val seekInteraction = remember { MutableInteractionSource() }
    val shownPos = if (dragging != null) (fraction * duration).toLong() else positionMs
    @Composable
    fun time(ms: Long, placeholder: String) = Text(
        formatDuration(ms / 1000).ifEmpty { placeholder },
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.8f),
    )
    val slider: @Composable (Modifier) -> Unit = { modifier ->
        Slider(
            value = fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { PlayerConnection.seekTo((it * duration).toLong()) }
                dragging = null
            },
            enabled = state.durationMs > 0,
            colors = seekColors,
            interactionSource = seekInteraction,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = seekInteraction,
                    colors = seekColors,
                    enabled = state.durationMs > 0,
                    thumbSize = if (compact) DpSize(4.dp, 18.dp) else DpSize(4.dp, 30.dp),
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    colors = seekColors,
                    enabled = state.durationMs > 0,
                    drawStopIndicator = null,
                    modifier = Modifier.height(if (compact) 6.dp else 10.dp),
                )
            },
            modifier = modifier.height(if (compact) 24.dp else 34.dp),
        )
    }
    if (compact) {
        // The player bar's version: times on either side of a thin bar.
        Row(verticalAlignment = Alignment.CenterVertically) {
            time(shownPos, "0:00")
            Spacer(Modifier.width(10.dp))
            slider(Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            time(state.durationMs, "--:--")
        }
    } else {
        Column {
            slider(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth()) {
                time(shownPos, "0:00")
                Spacer(Modifier.weight(1f))
                time(state.durationMs, "--:--")
            }
        }
    }
}

@Composable
fun Controls(state: PlayerUiState, small: Boolean = false) {
    val big = if (small) 44.dp else 76.dp
    val skip = if (small) 28.dp else 36.dp
    Row(
        if (small) Modifier else Modifier.fillMaxWidth(),
        horizontalArrangement = if (small) Arrangement.spacedBy(6.dp) else Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { PlayerConnection.toggleShuffle() }) {
            Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (state.shuffle) LsColors.Mako else Color.White.copy(alpha = 0.8f))
        }
        IconButton(onClick = { PlayerConnection.previous() }, modifier = Modifier.size(skip + 16.dp)) {
            Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(skip))
        }
        // The loading ring is drawn around the button without taking up room of its own, so it
        // appearing while a song loads never pushes the other controls (or the player bar) about.
        Box(Modifier.size(big), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(big)
                    .clip(CircleShape)
                    .background(LsColors.Crystal)
                    .clickable { PlayerConnection.togglePlay() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (state.isPlaying) "Pause" else "Play",
                    tint = LsColors.OnPrimary,
                    modifier = Modifier.size(big * 0.53f),
                )
            }
            if (state.isBuffering) {
                CircularProgressIndicator(Modifier.requiredSize(big + 10.dp), color = LsColors.Mako, strokeWidth = 3.dp)
            }
        }
        IconButton(onClick = { PlayerConnection.next() }, modifier = Modifier.size(skip + 16.dp)) {
            Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(skip))
        }
        IconButton(onClick = { PlayerConnection.cycleRepeat() }) {
            Icon(
                if (state.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                "Repeat",
                tint = if (state.repeatMode != RepeatMode.OFF) LsColors.Mako else Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun QueueList(state: PlayerUiState) {
    // Listed in play order, so "Up Next" is what really plays next (shuffled or not).
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.playPosition.coerceAtLeast(0))
    LaunchedEffect(state.playPosition) { if (state.playPosition >= 0) listState.animateScrollToItem(state.playPosition) }
    Text("Up Next", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp))
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().thinScrollbar(listState)) {
        itemsIndexed(state.playOrder) { _, index ->
            val track = state.queue.getOrNull(index) ?: return@itemsIndexed
            val isCurrent = index == state.currentIndex
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { PlayerConnection.skipToQueueItem(index) }
                    .onRightClick { at -> TrackActions.show(track, at = at) }
                    .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(track.thumbnail, Modifier.size(44.dp), corner = 6.dp, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isCurrent) LsColors.Mako else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        track.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
                if (isCurrent) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = LsColors.Mako, modifier = Modifier.padding(12.dp))
                } else {
                    IconButton(onClick = { PlayerConnection.removeFromQueue(index) }) {
                        Icon(Icons.Rounded.Close, "Remove from queue", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
