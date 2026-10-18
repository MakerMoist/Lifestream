package com.lifestream.player.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifestream.player.data.Game
import com.lifestream.player.data.Library
import com.lifestream.player.data.SoundtrackIndex
import com.lifestream.player.data.Track
import com.lifestream.player.playback.PlayerConnection
import com.lifestream.player.playback.SafeInput
import com.lifestream.player.ui.theme.LsColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The options menu for a song, shared by every song row (long-press or ⋮), Home's picks and Now
 * Playing. It pops up right where it was opened (the finger's spot, or the ⋮ button) and lists
 * the song's options.
 */
object TrackActions {
    /**
     * [playlistId] is set when the song is shown inside a playlist, to offer "Remove". [at] is
     * where the menu opens, in window coordinates (the middle of the screen if unknown).
     */
    data class Target(val track: Track, val playlistId: String? = null, val at: Offset? = null)

    private val _target = MutableStateFlow<Target?>(null)
    val target: StateFlow<Target?> = _target.asStateFlow()

    fun show(track: Track, playlistId: String? = null, at: Offset? = null) {
        _target.value = Target(track, playlistId, at)
    }

    fun dismiss() {
        _target.value = null
    }
}

/**
 * Remembers where something on screen is and where it was last touched, so a menu can open at
 * that spot. Attach with [menuAnchor]; read with [at].
 */
class MenuAnchor {
    internal var coordinates: LayoutCoordinates? = null
    internal var touch: Offset? = null

    /** The last touch (or the middle, with [center]), in window coordinates. */
    fun at(center: Boolean = false): Offset? {
        val c = coordinates?.takeIf { it.isAttached } ?: return null
        val middle = Offset(c.size.width / 2f, c.size.height / 2f)
        return c.localToWindow(if (center) middle else touch ?: middle)
    }
}

@Composable
fun rememberMenuAnchor() = remember { MenuAnchor() }

/** Tracks this element's position and where a finger last went down on it (without using the touch). */
fun Modifier.menuAnchor(anchor: MenuAnchor): Modifier = this
    .onGloballyPositioned { anchor.coordinates = it }
    .pointerInput(anchor) {
        awaitEachGesture {
            anchor.touch = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).position
        }
    }

/** Placed once at the root of the app; renders the menu and its dialogs. */
@Composable
fun TrackActionsHost(onOpenGame: (Game) -> Unit) {
    val target by TrackActions.target.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var choosingFor by remember { mutableStateOf<Track?>(null) }
    var namingFor by remember { mutableStateOf<Track?>(null) }
    var removingFor by remember { mutableStateOf<Track?>(null) }
    // Asked before a song is taken out of a playlist.
    var confirmRemove by remember { mutableStateOf<Pair<Track, com.lifestream.player.data.Playlist>?>(null) }
    var savingInstant by remember { mutableStateOf(false) }

    // The menu stays drawn while it fades out after closing.
    var shown by remember { mutableStateOf<TrackActions.Target?>(null) }
    LaunchedEffect(target) { if (target != null) shown = target }
    val progress by animateFloatAsState(
        targetValue = if (target != null) 1f else 0f,
        animationSpec = tween(if (target != null) 170 else 120),
        label = "menu",
        finishedListener = { if (it == 0f) shown = null },
    )
    // Back closes the menu first. Registered at the moment the menu opens, so it comes before
    // every other back action (the screen underneath, the navigation stack).
    val backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val menuOpen = target != null
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(menuOpen, backDispatcher, lifecycleOwner) {
        val callback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = TrackActions.dismiss()
        }
        if (menuOpen) backDispatcher?.addCallback(lifecycleOwner, callback)
        onDispose { callback.remove() }
    }

    shown?.let { t ->
        SongMenu(
            target = t,
            progress = progress,
            onDismiss = { TrackActions.dismiss() },
            onAddToPlaylist = { choosingFor = it },
            onRemoveFromSeveral = { removingFor = it },
            onRemoveFrom = { track, playlist -> confirmRemove = track to playlist },
            onSaveInstant = { savingInstant = true },
            onOpenGame = onOpenGame,
        )
    }

    choosingFor?.let { track ->
        AddToPlaylistDialog(
            track = track,
            onDismiss = { choosingFor = null },
            onNewPlaylist = {
                choosingFor = null
                namingFor = track
            },
        )
    }

    if (savingInstant) SaveInstantPlaylistDialog(onDismiss = { savingInstant = false })

    removingFor?.let { track ->
        RemoveFromPlaylistDialog(
            track = track,
            onDismiss = { removingFor = null },
            onPick = { playlist ->
                removingFor = null
                confirmRemove = track to playlist
            },
        )
    }

    confirmRemove?.let { (track, playlist) ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove song?") },
            text = { Text("Remove \u201C${track.title}\u201D from ${playlist.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    Library.removeFromPlaylist(playlist.id, track.id)
                    toast(context, "Removed from ${playlist.name}")
                    confirmRemove = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }

    namingFor?.let { track ->
        PlaylistNameDialog(
            title = "New Playlist",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { namingFor = null },
        ) { name ->
            val p = Library.createPlaylist(name)
            if (p != null) {
                Library.addToPlaylist(p.id, track)
                toast(context, "Added to ${p.name}")
            }
            namingFor = null
        }
    }
}

private class MenuItem(val icon: ImageVector, val label: String, val danger: Boolean = false, val onClick: () -> Unit)

/** Where the menu's corner sits on screen, worked out when it's placed (read while drawing). */
private class MenuOrigin {
    var origin: TransformOrigin = TransformOrigin(0f, 0f)
}

@Composable
private fun SongMenu(
    target: TrackActions.Target,
    progress: Float,
    onDismiss: () -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onRemoveFromSeveral: (Track) -> Unit,
    onRemoveFrom: (Track, com.lifestream.player.data.Playlist) -> Unit,
    onSaveInstant: () -> Unit,
    onOpenGame: (Game) -> Unit,
) {
    val context = LocalContext.current
    val track = target.track
    val game = remember(track) { SoundtrackIndex.gameOf(track) }
    val instantQueue by PlayerConnection.instantQueue.collectAsStateWithLifecycle()
    val playlists by Library.playlists.collectAsStateWithLifecycle()
    val isPlaying = PlayerConnection.state.value.current?.id == track.id

    fun close() = TrackActions.dismiss()

    val items = remember(track, target.playlistId, playlists, instantQueue, isPlaying, game) {
        buildList {
            // Queued right after the song that's playing (it plays next), for any song, including
            // the one that's playing (to hear it again).
            add(MenuItem(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") {
                PlayerConnection.addToQueue(track)
                toast(context, "Added to queue")
                close()
            })
            add(MenuItem(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") {
                close()
                onAddToPlaylist(track)
            })
            // An Instant Playlist can be kept: offered on the playing song's menu (Now Playing's ⋮).
            if (instantQueue && isPlaying) {
                add(MenuItem(Icons.Rounded.Add, "Save as playlist") {
                    close()
                    onSaveInstant()
                })
            }
            // Offered wherever the song is shown once it's in a playlist; inside a playlist it
            // removes it from that one.
            val containing = Library.playlistsWith(track.id)
            val here = target.playlistId?.takeIf { pid -> containing.any { it.id == pid } }
            when {
                here != null -> add(MenuItem(Icons.Rounded.RemoveCircleOutline, "Remove from this playlist", danger = true) {
                    close()
                    onRemoveFrom(track, containing.first { it.id == here })
                })
                containing.size == 1 -> add(MenuItem(Icons.Rounded.RemoveCircleOutline, "Remove from playlist", danger = true) {
                    close()
                    onRemoveFrom(track, containing.first())
                })
                containing.size > 1 -> add(MenuItem(Icons.Rounded.RemoveCircleOutline, "Remove from playlist…", danger = true) {
                    close()
                    onRemoveFromSeveral(track)
                })
            }
            add(MenuItem(Icons.Rounded.Share, "Share") {
                shareTrack(context, track)
                close()
            })
            if (game != null) {
                add(MenuItem(Icons.Rounded.Album, "Open ${game.title}") {
                    close()
                    onOpenGame(game)
                })
            }
        }
    }

    val density = LocalDensity.current
    val bars = WindowInsets.systemBars
    val menuOrigin = remember { MenuOrigin() }
    var host by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { host = it },
    ) {
        // A light shade behind the menu; tapping anywhere outside closes it.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = progress }
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        )
        Layout(
            content = {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 12.dp,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .graphicsLayer {
                            val scale = 0.82f + 0.18f * progress
                            scaleX = scale
                            scaleY = scale
                            alpha = progress
                            transformOrigin = menuOrigin.origin
                        }
                        // Taps on the menu itself don't fall through and close it.
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                ) {
                    MenuContent(items)
                }
            },
        ) { measurables, constraints ->
            val margin = with(density) { 12.dp.roundToPx() }
            val top = bars.getTop(density) + margin
            val bottom = bars.getBottom(density) + margin
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val menuWidth = minOf(with(density) { 272.dp.roundToPx() }, width - 2 * margin)
            val menu = measurables.first().measure(
                Constraints(
                    minWidth = menuWidth,
                    maxWidth = menuWidth,
                    maxHeight = (height - top - bottom).coerceAtLeast(0),
                )
            )
            // The spot it was opened from, in this layer's coordinates.
            val point = target.at?.let { p -> host?.takeIf { it.isAttached }?.windowToLocal(p) }
                ?: Offset(width / 2f, height / 2f)
            // Opens to the right of the finger and below it, flipping where there isn't room.
            val openLeft = point.x + menu.width > width - margin
            val openUp = point.y + menu.height > height - bottom
            val x = (if (openLeft) point.x - menu.width else point.x).toInt().coerceIn(margin, (width - margin - menu.width).coerceAtLeast(margin))
            val y = (if (openUp) point.y - menu.height else point.y).toInt().coerceIn(top, (height - bottom - menu.height).coerceAtLeast(top))
            // Grows out of the corner nearest the spot it was opened from.
            menuOrigin.origin = TransformOrigin(
                ((point.x - x) / menu.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                ((point.y - y) / menu.height.coerceAtLeast(1)).coerceIn(0f, 1f),
            )
            layout(width, height) { menu.place(x, y) }
        }
    }
}

@Composable
private fun MenuContent(items: List<MenuItem>) {
    // Just the actions: the song is the one that was held or tapped, right beside the menu.
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
    ) {
        items.forEach { item -> MenuRow(item) }
    }
}

@Composable
private fun MenuRow(item: MenuItem) {
    Row(
        Modifier
            .fillMaxWidth()
            // Ignored while the menu is already closing (a second quick tap mustn't repeat it).
            .clickable { if (TrackActions.target.value != null) item.onClick() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (item.danger) MaterialTheme.colorScheme.error else LsColors.Crystal
        Icon(item.icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(item.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AddToPlaylistDialog(track: Track, onDismiss: () -> Unit, onNewPlaylist: () -> Unit) {
    val playlists by Library.playlists.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            // Scrolls when there are many playlists.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                playlists.forEach { p ->
                    val already = p.tracks.any { it.id == track.id }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !already) {
                                val added = Library.addToPlaylist(p.id, track)
                                if (added) PlayerConnection.onAddedToPlaylist(p.id, track)
                                toast(context, if (added) "Added to ${p.name}" else "${p.name} is full")
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (p.tracks.size == 1) "1 song" else "${p.tracks.size} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (already) Icon(Icons.Rounded.Check, "Already added", tint = LsColors.Mako)
                    }
                }
                if (Library.canCreatePlaylist()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNewPlaylist)
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = LsColors.Crystal)
                        Spacer(Modifier.width(12.dp))
                        Text("New playlist", style = MaterialTheme.typography.bodyLarge, color = LsColors.Crystal)
                    }
                } else {
                    Text(
                        "You have ${Library.MAX_PLAYLISTS} playlists, the most allowed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Names and saves the playing Instant Playlist as a playlist of your own. */
@Composable
fun SaveInstantPlaylistDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    if (!Library.canCreatePlaylist()) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            toast(context, "You have ${Library.MAX_PLAYLISTS} playlists, the most allowed.")
            onDismiss()
        }
        return
    }
    PlaylistNameDialog(
        title = "Save as playlist",
        initial = "",
        confirmLabel = "Save",
        onDismiss = onDismiss,
    ) { name ->
        val saved = PlayerConnection.saveInstantPlaylist(name)
        toast(context, if (saved != null) "Saved as ${saved.name}" else "Couldn't save this playlist")
        onDismiss()
    }
}

/** Picks which playlist to take a song out of, when it's in more than one. */
@Composable
private fun RemoveFromPlaylistDialog(track: Track, onDismiss: () -> Unit, onPick: (com.lifestream.player.data.Playlist) -> Unit) {
    val playlists by Library.playlists.collectAsStateWithLifecycle()
    val containing = playlists.filter { p -> p.tracks.any { it.id == track.id } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove from playlist") },
        text = {
            // Scrolls when there are many playlists.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                containing.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(p) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (p.tracks.size == 1) "1 song" else "${p.tracks.size} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Rounded.RemoveCircleOutline, "Remove", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Name entry for creating or renaming a playlist. */
@Composable
fun PlaylistNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(Library.MAX_NAME_LENGTH) },
                singleLine = true,
                placeholder = { Text("Playlist name") },
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm(name) }),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Sends the song's YouTube link through Android's share sheet. */
fun shareTrack(context: Context, track: Track) {
    if (!SafeInput.isVideoId(track.id)) return
    val byLine = track.artist.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, track.title)
        .putExtra(Intent.EXTRA_TEXT, "${track.title}$byLine\n${track.youTubeUrl}")
    context.startActivity(Intent.createChooser(send, "Share song").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun toast(context: Context, text: String) {
    Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
}
