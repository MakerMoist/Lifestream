package com.lifestream.player.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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
 * The options menu for a song, shared by every song row (long-press or ⋮) and Now Playing:
 * Play next · Add to queue · Add to playlist · Open title · Share.
 */
object TrackActions {
    /** [playlistId] is set when the song is shown inside a playlist, to offer "Remove". */
    data class Target(val track: Track, val playlistId: String? = null)

    private val _target = MutableStateFlow<Target?>(null)
    val target: StateFlow<Target?> = _target.asStateFlow()

    fun show(track: Track, playlistId: String? = null) {
        _target.value = Target(track, playlistId)
    }

    fun dismiss() {
        _target.value = null
    }
}

/** Placed once at the root of the app; renders the menu and its dialogs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsHost(onOpenGame: (Game) -> Unit) {
    val target by TrackActions.target.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var choosingFor by remember { mutableStateOf<Track?>(null) }
    var namingFor by remember { mutableStateOf<Track?>(null) }

    target?.let { t ->
        val track = t.track
        val game = remember(track) { SoundtrackIndex.gameOf(track) }
        ModalBottomSheet(
            onDismissRequest = { TrackActions.dismiss() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(track.thumbnail, Modifier.size(52.dp), corner = 6.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        game?.title ?: track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            ActionRow(Icons.Rounded.QueuePlayNext, "Play next") {
                PlayerConnection.playNext(track)
                toast(context, "Playing next")
                TrackActions.dismiss()
            }
            ActionRow(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") {
                PlayerConnection.addToQueue(track)
                toast(context, "Added to queue")
                TrackActions.dismiss()
            }
            ActionRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") {
                choosingFor = track
                TrackActions.dismiss()
            }
            t.playlistId?.let { pid ->
                ActionRow(Icons.Rounded.RemoveCircleOutline, "Remove from this playlist") {
                    Library.removeFromPlaylist(pid, track.id)
                    TrackActions.dismiss()
                }
            }
            if (game != null) {
                ActionRow(Icons.Rounded.Album, "Open ${game.title}") {
                    TrackActions.dismiss()
                    onOpenGame(game)
                }
            }
            ActionRow(Icons.Rounded.Share, "Share") {
                shareTrack(context, track)
                TrackActions.dismiss()
            }
            Spacer(Modifier.navigationBarsPadding().height(12.dp))
        }
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

    namingFor?.let { track ->
        PlaylistNameDialog(
            title = "New playlist",
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

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = LsColors.Crystal)
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Column {
                playlists.forEach { p ->
                    val already = p.tracks.any { it.id == track.id }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !already) {
                                Library.addToPlaylist(p.id, track)
                                toast(context, "Added to ${p.name}")
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
