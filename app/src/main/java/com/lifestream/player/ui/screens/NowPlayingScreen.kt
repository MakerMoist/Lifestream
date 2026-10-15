package com.lifestream.player.ui.screens

import com.lifestream.player.playback.SafeInput
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.abs
import kotlinx.coroutines.launch
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.media3.common.Player
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lifestream.player.data.hiResArtwork
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import com.lifestream.player.data.formatDuration
import com.lifestream.player.playback.PlayerConnection
import com.lifestream.player.playback.PlayerUiState
import com.lifestream.player.ui.components.Artwork
import com.lifestream.player.ui.components.TrackActions
import com.lifestream.player.ui.components.menuAnchor
import com.lifestream.player.ui.components.rememberMenuAnchor
import com.lifestream.player.ui.components.SaveInstantPlaylistDialog
import com.lifestream.player.ui.components.SoftBlur
import androidx.compose.material.icons.rounded.Add
import com.lifestream.player.ui.theme.LsColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(onClose: () -> Unit) {
    val state by PlayerConnection.state.collectAsStateWithLifecycle()
    val track = state.current
    var showQueue by remember { mutableStateOf(false) }
    var savingInstant by remember { mutableStateOf(false) }
    val instantQueue by PlayerConnection.instantQueue.collectAsStateWithLifecycle()

    // Back closes the player and returns to the page underneath (e.g. the soundtrack).
    BackHandler { onClose() }

    // Nothing on this screen types. Take focus away from the screen underneath (e.g. the Search
    // box), so the keyboard doesn't pop up here, including when coming back to the app.
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        focus.clearFocus(force = true)
        keyboard?.hide()
        onPauseOrDispose { }
    }

    // Drag down to minimize: the screen follows your finger and closes past a quarter of the way
    // (or with a downward flick); otherwise it springs back. Drag up to open Up next: the screen
    // lifts a little (with resistance) and the list opens past a short distance or an upward flick.
    val scope = rememberCoroutineScope()
    val dragY = remember { Animatable(0f) }
    var heightPx by remember { mutableFloatStateOf(1f) }
    val closeFlingPx = with(LocalDensity.current) { 1000.dp.toPx() }
    val openQueuePx = with(LocalDensity.current) { 56.dp.toPx() }
    val close by rememberUpdatedState(onClose)

    // This screen floats above the app's Scaffold, so it must set its own text/icon colour;
    // otherwise everything falls back to black.
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            // Upward movement is damped: it's a pull, not a slide.
                            val step = if (dragY.value + dragAmount < 0f) dragAmount * 0.35f else dragAmount
                            scope.launch { dragY.snapTo((dragY.value + step).coerceAtLeast(-openQueuePx * 2)) }
                        },
                        onDragCancel = { scope.launch { dragY.animateTo(0f, spring()) } },
                        onDragEnd = {
                            val velocity = tracker.calculateVelocity().y
                            val shouldClose = dragY.value > heightPx * 0.25f ||
                                (velocity > closeFlingPx && dragY.value > heightPx * 0.05f)
                            // Up (a short pull, or a flick that was going up) opens Up next.
                            val openQueue = dragY.value < -openQueuePx * 0.5f ||
                                (velocity < -closeFlingPx && dragY.value < 0f)
                            when {
                                shouldClose -> close()
                                else -> {
                                    if (openQueue) showQueue = true
                                    scope.launch { dragY.animateTo(0f, spring(dampingRatio = 0.8f)) }
                                }
                            }
                        },
                    )
                }
                .graphicsLayer { translationY = dragY.value }
                .background(LsColors.Night)
                // Swallow taps so they don't fall through to the screen underneath.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            // Blurred artwork wash behind everything. A small copy is blurred once when it loads
            // (see SoftBlur) and stretched to fill the screen, instead of a live full-screen blur
            // that the GPU would redo whenever anything on this screen moves.
            val wash = remember(track?.thumbnail) { SafeInput.safeArtwork(track?.thumbnail) }
            if (wash != null) {
                val context = LocalContext.current
                val request = remember(wash) {
                    val small = SafeInput.safeArtwork(hiResArtwork(wash, 120, videoThumbs = false)) ?: wash
                    ImageRequest.Builder(context)
                        .data(small)
                        .size(96)
                        .transformations(SoftBlur())
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    alpha = 0.45f,
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(LsColors.Night.copy(alpha = 0.55f), LsColors.Night.copy(alpha = 0.92f), LsColors.Night)
                        )
                    ),
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close player") }
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
                    val dots = rememberMenuAnchor()
                    IconButton(
                        onClick = { track?.let { TrackActions.show(it, at = dots.at(center = true)) } },
                        enabled = track != null,
                        modifier = Modifier.menuAnchor(dots),
                    ) {
                        Icon(Icons.Rounded.MoreVert, "Song options")
                    }
                }

                Spacer(Modifier.weight(1f))

                SwipeableArtwork(state)

                Spacer(Modifier.weight(1f))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            track?.title ?: "",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(),
                        )
                        Text(
                            track?.artist ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Up next sits beside the title; an Instant Playlist also gets a save button above it.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (instantQueue) {
                            IconButton(onClick = { savingInstant = true }) {
                                Icon(Icons.Rounded.Add, "Save as playlist")
                            }
                        }
                        IconButton(onClick = { showQueue = true }) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Up next")
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                SeekBar(state)
                Spacer(Modifier.height(8.dp))
                Controls(state)
                Spacer(Modifier.height(24.dp))
            }
        }

    }

    if (savingInstant) SaveInstantPlaylistDialog(onDismiss = { savingInstant = false })

    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            QueueList(state)
        }
    }
}

@Composable
private fun SeekBar(state: PlayerUiState) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    // Only this small bar watches the ticking position.
    val positionMs by PlayerConnection.position.collectAsStateWithLifecycle()
    val duration = state.durationMs.coerceAtLeast(1)
    val fraction = dragging ?: (positionMs.toFloat() / duration).coerceIn(0f, 1f)

    Column {
        Slider(
            value = fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { PlayerConnection.seekTo((it * duration).toLong()) }
                dragging = null
            },
            enabled = state.durationMs > 0,
            colors = SliderDefaults.colors(
                thumbColor = LsColors.Crystal,
                activeTrackColor = LsColors.Crystal,
                inactiveTrackColor = Color.White.copy(alpha = 0.15f),
            ),
        )
        Row(Modifier.fillMaxWidth()) {
            val shownPos = if (dragging != null) (fraction * duration).toLong() else positionMs
            Text(
                formatDuration(shownPos / 1000).ifEmpty { "0:00" },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatDuration(state.durationMs / 1000).ifEmpty { "--:--" },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun Controls(state: PlayerUiState) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { PlayerConnection.toggleShuffle() }) {
            Icon(
                Icons.Rounded.Shuffle,
                "Shuffle",
                tint = if (state.shuffle) LsColors.Mako else Color.White.copy(alpha = 0.8f),
            )
        }
        IconButton(onClick = { PlayerConnection.previous() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(36.dp))
        }
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(LsColors.Crystal)
                    .clickable { PlayerConnection.togglePlay() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (state.isPlaying) "Pause" else "Play",
                    tint = LsColors.OnPrimary,
                    modifier = Modifier.size(40.dp),
                )
            }
            if (state.isBuffering) {
                CircularProgressIndicator(Modifier.size(86.dp), color = LsColors.Mako, strokeWidth = 3.dp)
            }
        }
        IconButton(onClick = { PlayerConnection.next() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(36.dp))
        }
        IconButton(onClick = { PlayerConnection.cycleRepeat() }) {
            Icon(
                if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                "Repeat",
                tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) LsColors.Mako
                else Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun QueueList(state: PlayerUiState) {
    // Listed in play order, so "Up next" is what really plays next (shuffled or not).
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.playPosition.coerceAtLeast(0))
    Text(
        "Up next",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
    )
    LazyColumn(state = listState, modifier = Modifier.height(480.dp)) {
        itemsIndexed(state.playOrder) { _, index ->
            val track = state.queue.getOrNull(index) ?: return@itemsIndexed
            val isCurrent = index == state.currentIndex
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { PlayerConnection.skipToQueueItem(index) }
                    .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(track.thumbnail, Modifier.size(44.dp), corner = 6.dp)
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

/**
 * Album art you can swipe: left for the next song, right for the previous one. The art follows
 * your finger, slides away when you pass the threshold (or flick), and the new art slides in
 * from the other side. At either end of the queue it just stretches a little and springs back.
 */
@Composable
private fun SwipeableArtwork(state: PlayerUiState) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val current by rememberUpdatedState(state)

    // From the player itself, so shuffle order and repeat are taken into account.
    fun canNext(s: PlayerUiState) = s.hasNext

    fun canPrevious(s: PlayerUiState) = s.hasPrevious

    val flingPx = with(LocalDensity.current) { 900.dp.toPx() } // per second
    Artwork(
        state.current?.thumbnail,
        size = 400.dp,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            // Touches are measured here, outside the layer that moves and tilts the art, so the
            // art following your finger can't skew the distance or the fling direction.
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val s = current
                        val blocked = (dragAmount < 0 && offsetX.value <= 0f && !canNext(s)) ||
                            (dragAmount > 0 && offsetX.value >= 0f && !canPrevious(s))
                        // Rubber-band when there's nothing to swipe to.
                        val delta = if (blocked) dragAmount * 0.25f else dragAmount
                        scope.launch { offsetX.snapTo(offsetX.value + delta) }
                    },
                    onDragCancel = { scope.launch { offsetX.animateTo(0f, spring()) } },
                    onDragEnd = {
                        val velocity = tracker.calculateVelocity().x
                        val offset = offsetX.value
                        val threshold = widthPx * 0.3f
                        // A quick flick counts only in the direction the art was actually dragged
                        // (and at least a little way), so a stray flick can't go the wrong way.
                        val nudge = widthPx * 0.08f
                        val flickNext = velocity < -flingPx && offset < -nudge
                        val flickPrevious = velocity > flingPx && offset > nudge
                        val s = current
                        val goNext = (offset < -threshold || flickNext) && canNext(s)
                        val goPrevious = (offset > threshold || flickPrevious) && canPrevious(s)
                        scope.launch {
                            when {
                                goNext -> {
                                    offsetX.animateTo(-widthPx * 1.3f, tween(170))
                                    PlayerConnection.nextTrack()
                                    offsetX.snapTo(widthPx * 1.3f)
                                    offsetX.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f))
                                }
                                goPrevious -> {
                                    offsetX.animateTo(widthPx * 1.3f, tween(170))
                                    PlayerConnection.previousTrack()
                                    offsetX.snapTo(-widthPx * 1.3f)
                                    offsetX.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f))
                                }
                                else -> offsetX.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 500f))
                            }
                        }
                    },
                )
            }
            .graphicsLayer {
                val f = offsetX.value / widthPx
                translationX = offsetX.value
                rotationZ = f * 6f
                alpha = 1f - (abs(f) * 0.6f).coerceAtMost(0.6f)
            }
            .shadow(24.dp, RoundedCornerShape(16.dp)),
        corner = 16.dp,
    )
}
