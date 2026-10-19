package com.lifestream.player.ui.screens

import com.lifestream.player.ui.components.thinScrollbar
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.foundation.layout.PaddingValues
import com.lifestream.player.ui.components.CenteredButtonLabel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AvTimer
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.foundation.combinedClickable
import com.lifestream.player.ui.components.onRightClick
import com.lifestream.player.ui.components.menuAnchor
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifestream.player.data.Library
import com.lifestream.player.data.Playlist
import com.lifestream.player.data.Track
import com.lifestream.player.playback.PlayerConnection
import com.lifestream.player.ui.components.Artwork
import com.lifestream.player.ui.components.EmptyState
import com.lifestream.player.ui.components.PlaylistNameDialog
import com.lifestream.player.ui.components.TrackRow
import com.lifestream.player.ui.theme.LsColors

/** Your playlists (History has its own page). */
@Composable
fun LibraryScreen(onOpenPlaylist: (Playlist) -> Unit) {
    val playlists by Library.playlists.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    // Hold a playlist for its menu: Export Playlist, Rename, Delete.
    var renamingList by remember { mutableStateOf<Playlist?>(null) }
    var deletingList by remember { mutableStateOf<Playlist?>(null) }
    // Saving and opening files go through Windows' own file dialogs: the file stays on this PC
    // (or wherever you choose to keep it); nothing is uploaded or downloaded. The file is the same
    // as the Android app's, so playlists move between the two.
    val playlistList = rememberLazyListState()

    // Clicking Library while here goes back to the top and brings stored songs up to date.
    val reselectAtStart = remember { TabReselect.library }
    LaunchedEffect(TabReselect.library) {
        if (TabReselect.library == reselectAtStart) return@LaunchedEffect
        scope.launch { Library.refresh() }
        playlistList.animateScrollToItem(0)
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Library",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth().thinScrollbar(playlistList), state = playlistList) {
            if (playlists.isEmpty()) {
                item(key = "empty") {
                    EmptyState("No playlists yet")
                }
            }
            items(playlists, key = { it.id }) { p ->
                PlaylistRow(
                    p,
                    onClick = { onOpenPlaylist(p) },
                    onLongClick = { at ->
                        com.lifestream.player.ui.components.AppMenu.show(at, listOf(
                            com.lifestream.player.ui.components.MenuItem(Icons.Rounded.FileUpload, "Export Playlist") {
                                com.lifestream.player.ui.components.AppMenu.dismiss()
                                com.lifestream.player.ui.FilePickers.save("Export Playlist", exportFileName(p.name), Library.EXPORT_EXTENSION)
                                    ?.let { file -> scope.launch { exportPlaylist(p, file) } }
                            },
                            com.lifestream.player.ui.components.MenuItem(Icons.Rounded.Edit, "Rename") {
                                com.lifestream.player.ui.components.AppMenu.dismiss()
                                renamingList = p
                            },
                            com.lifestream.player.ui.components.MenuItem(Icons.Rounded.Delete, "Delete", danger = true) {
                                com.lifestream.player.ui.components.AppMenu.dismiss()
                                deletingList = p
                            },
                        ))
                    },
                )
            }
        }
        // Across the bottom: Import Playlist (a file exported from LifeStream), then New Playlist.
        OutlinedButton(
            onClick = {
                com.lifestream.player.ui.FilePickers.open("Import Playlist", Library.EXPORT_EXTENSION)
                    ?.let { file -> scope.launch { importPlaylist(file) } }
            },
            enabled = Library.canCreatePlaylist(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp),
        ) {
            Icon(Icons.Rounded.FileDownload, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Import Playlist")
        }
        FilledTonalButton(
            onClick = { creating = true },
            enabled = Library.canCreatePlaylist(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
        ) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("New Playlist")
        }
    }

    renamingList?.let { p ->
        PlaylistNameDialog(
            title = "Rename Playlist",
            initial = p.name,
            confirmLabel = "Save",
            onDismiss = { renamingList = null },
        ) { name ->
            Library.renamePlaylist(p.id, name)
            renamingList = null
        }
    }

    deletingList?.let { p ->
        DeletePlaylistDialog(p, onDismiss = { deletingList = null }) {
            deletingList = null
            Library.deletePlaylist(p.id)
        }
    }

    if (creating) {
        PlaylistNameDialog(
            title = "New Playlist",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { creating = false },
        ) { name ->
            Library.createPlaylist(name)
            creating = false
        }
    }
}

/** Songs you've played, newest first (the sidebar's History, under Library). */
@Composable
fun HistoryScreen() {
    val recents by Library.recents.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    val historyList = rememberLazyListState()

    // Clicking History while here goes back to the top and brings stored songs up to date.
    val reselectAtStart = remember { TabReselect.history }
    LaunchedEffect(TabReselect.history) {
        if (TabReselect.history == reselectAtStart) return@LaunchedEffect
        scope.launch { Library.refresh() }
        historyList.animateScrollToItem(0)
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "History",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth().thinScrollbar(historyList), state = historyList) {
            if (recents.isEmpty()) {
                item(key = "empty") { EmptyState("Nothing played yet", "Songs you play will show up here.") }
            } else {
                item(key = "actions") {
                    PlayShuffleRow(recents, onClear = { confirmClear = true })
                }
                itemsIndexed(recents, key = { _, t: Track -> t.id }) { _, track ->
                    // This song, then the rest of History in its order, topped up with random
                    // songs from every enabled title to 50 (not offered to save as a playlist:
                    // it's your History).
                    TrackRow(track, onClick = {
                        PlayerConnection.playTracks(
                            com.lifestream.player.data.Mixes.startingWith(track, recents, keepOrder = true),
                            0, shuffle = false, instant = false,
                        )
                    })
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear History?") },
            text = { Text("This removes all ${recents.size} songs from your history. Your playlists and saved songs aren't affected.") },
            confirmButton = {
                TextButton(onClick = {
                    Library.clearRecents()
                    confirmClear = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

/** "Road Trip.lsplaylist", without characters file names can't have. */
private fun exportFileName(name: String): String {
    val base = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").trim().trimEnd('.').ifBlank { "Playlist" }
    // Names Windows keeps for devices ("CON", "NUL", "COM1"…) can't be used for a file.
    val reserved = Regex("^(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])$", RegexOption.IGNORE_CASE).matches(base)
    return (if (reserved) "$base Playlist" else base) + Library.EXPORT_EXTENSION
}

private suspend fun exportPlaylist(p: Playlist, file: java.io.File) {
    val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val text = Library.exportText(p.id) ?: return@withContext false
        runCatching { file.writeText(text, Charsets.UTF_8); true }.getOrDefault(false)
    }
    com.lifestream.player.ui.components.toast(if (ok) "Exported \u201C${p.name}\u201D" else "Couldn't save the playlist file")
}

/** Reads a chosen file and adds it as a playlist, if (and only if) it's a LifeStream export. */
private suspend fun importPlaylist(file: java.io.File) {
    val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            // Its name must end in .lsplaylist, and it must be small enough to be one.
            if (!file.name.endsWith(Library.EXPORT_EXTENSION, ignoreCase = true) || !file.isFile ||
                file.length() > Library.MAX_EXPORT_BYTES
            ) {
                return@runCatching Library.ImportResult.NotAPlaylist
            }
            val bytes = file.inputStream().use { it.readNBytes((Library.MAX_EXPORT_BYTES + 1).toInt()) }
            if (bytes.size > Library.MAX_EXPORT_BYTES) return@runCatching Library.ImportResult.NotAPlaylist
            // Read and checked here, off the UI thread; only adding it happens there.
            val (listName, songs) = Library.readImport(String(bytes, Charsets.UTF_8))
                ?: return@runCatching Library.ImportResult.NotAPlaylist
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { Library.addImported(listName, songs) }
        }.getOrDefault(Library.ImportResult.NotAPlaylist)
    }
    val message = when (result) {
        is Library.ImportResult.Imported -> "Imported \u201C${result.playlist.name}\u201D"
        Library.ImportResult.TooMany -> "You have ${Library.MAX_PLAYLISTS} playlists, the most allowed."
        Library.ImportResult.NotAPlaylist -> "That isn't a playlist exported from LifeStream."
    }
    com.lifestream.player.ui.components.toast(message)
}

/** Asked before a playlist is deleted. */
@Composable
private fun DeletePlaylistDialog(p: Playlist, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"${p.name}\"?") },
        text = { Text("This removes the playlist. Its songs stay in their soundtracks.") },
        confirmButton = {
            TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun PlaylistRow(p: Playlist, onClick: () -> Unit, onLongClick: (androidx.compose.ui.geometry.Offset?) -> Unit) {
    val anchor = com.lifestream.player.ui.components.rememberMenuAnchor()
    Row(
        Modifier
            .fillMaxWidth()
            .menuAnchor(anchor)
            .combinedClickable(onClick = onClick, onLongClick = { onLongClick(anchor.at()) })
            // Right-click opens the same menu.
            .onRightClick { at -> onLongClick(at ?: anchor.at()) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(60.dp)) {
            val art = p.tracks.firstOrNull()?.thumbnail
            if (art != null) {
                Artwork(art, Modifier.size(60.dp), size = 60.dp)
            } else {
                Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = LsColors.Crystal)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (p.tracks.size == 1) "1 song" else "${p.tracks.size} songs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlayShuffleRow(tracks: List<Track>, playlistId: String? = null, onClear: (() -> Unit)? = null, sortable: Boolean = false) {
    // Play and Shuffle share the width evenly; History's Clear is a small plain button after them.
    val padding = PaddingValues(horizontal = 10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = { PlayerConnection.playTracks(tracks, fromPlaylist = playlistId) },
            enabled = tracks.isNotEmpty(),
            modifier = Modifier.weight(1f).height(44.dp),
            contentPadding = padding,
        ) {
            CenteredButtonLabel("Play") { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp)) }
        }
        OutlinedButton(
            onClick = { PlayerConnection.playTracks(tracks, randomStart = true, fromPlaylist = playlistId) },
            enabled = tracks.isNotEmpty(),
            modifier = Modifier.weight(1f).height(44.dp),
            contentPadding = padding,
        ) {
            CenteredButtonLabel("Shuffle") { Icon(Icons.Rounded.Shuffle, null, Modifier.size(20.dp)) }
        }
        if (sortable && playlistId != null) {
            // Styled like History's Clear; opens the same kind of menu as a song's ⋮.
            val anchor = com.lifestream.player.ui.components.rememberMenuAnchor()
            TextButton(
                onClick = { showSortMenu(playlistId, anchor.at(center = true)) },
                enabled = tracks.size > 1,
                modifier = Modifier.height(44.dp).menuAnchor(anchor),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Sort", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (onClear != null) {
            TextButton(
                onClick = onClear,
                enabled = tracks.isNotEmpty(),
                modifier = Modifier.height(44.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Sort's menu: each order, with a check beside the current one. */
private fun showSortMenu(playlistId: String, at: androidx.compose.ui.geometry.Offset?) {
    val current = Library.sortOf(playlistId)
    val icons = mapOf(
        com.lifestream.player.data.PlaylistSort.ALPHABETICAL to Icons.Rounded.SortByAlpha,
        com.lifestream.player.data.PlaylistSort.MOST_PLAYED to Icons.AutoMirrored.Rounded.TrendingUp,
        com.lifestream.player.data.PlaylistSort.RECENTLY_ADDED to Icons.Rounded.Schedule,
        com.lifestream.player.data.PlaylistSort.OLDEST_ADDED to Icons.Rounded.History,
        com.lifestream.player.data.PlaylistSort.LONGEST to Icons.Rounded.Timer,
        com.lifestream.player.data.PlaylistSort.SHORTEST to Icons.Rounded.AvTimer,
    )
    com.lifestream.player.ui.components.AppMenu.show(
        at,
        com.lifestream.player.data.PlaylistSort.entries.map { mode ->
            com.lifestream.player.ui.components.MenuItem(
                if (mode == current) Icons.Rounded.Check else icons.getValue(mode),
                mode.label,
            ) {
                com.lifestream.player.ui.components.AppMenu.dismiss()
                Library.setSort(playlistId, mode)
            }
        },
    )
}

/** One playlist: play / shuffle, rename, delete, and its songs (hold a song to remove it). */
@Composable
fun PlaylistScreen(playlistId: String, onBack: () -> Unit) {
    val playlists by Library.playlists.collectAsStateWithLifecycle()
    val playlist = playlists.firstOrNull { it.id == playlistId }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    if (playlist == null) {
        // Deleted (or never existed): leave.
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }
    // Opening a playlist counts as using it, for Home's "Last Playlist".
    androidx.compose.runtime.LaunchedEffect(playlist.id) { Library.setLastPlaylist(playlist.id) }
    // Listed (and played) in the order chosen with Sort.
    val sorts by Library.sorts.collectAsStateWithLifecycle()
    val sort = sorts[playlist.id] ?: com.lifestream.player.data.PlaylistSort.OLDEST_ADDED
    val shown = remember(playlist, sort) { Library.sorted(playlist, sort) }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyColumn(
        Modifier
            .fillMaxSize()
            .thinScrollbar(listState),
        state = listState,
    ) {
        item(key = "header") {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, "Rename playlist") }
                    IconButton(onClick = { deleting = true }) { Icon(Icons.Rounded.Delete, "Delete playlist") }
                }
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Text(
                    if (playlist.tracks.size == 1) "1 song" else "${playlist.tracks.size} songs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                PlayShuffleRow(shown, playlistId = playlist.id, sortable = true)
            }
        }
        if (playlist.tracks.isEmpty()) {
            item(key = "empty") {
                EmptyState("This playlist is empty", "Press and hold any song and choose \"Add to playlist\".")
            }
        }
        itemsIndexed(shown, key = { _, t -> t.id }) { index, track ->
            TrackRow(
                track,
                playlistId = playlist.id,
                onClick = { PlayerConnection.playTracks(shown, index, fromPlaylist = playlist.id) },
            )
        }
    }

    if (renaming) {
        PlaylistNameDialog(
            title = "Rename Playlist",
            initial = playlist.name,
            confirmLabel = "Save",
            onDismiss = { renaming = false },
        ) { name ->
            Library.renamePlaylist(playlist.id, name)
            renaming = false
        }
    }

    if (deleting) {
        DeletePlaylistDialog(playlist, onDismiss = { deleting = false }) {
            deleting = false
            Library.deletePlaylist(playlist.id)
        }
    }
}
