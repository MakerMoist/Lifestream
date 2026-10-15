package com.lifestream.player.ui.screens

import com.lifestream.player.ui.components.CenteredButtonLabel
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpenPlaylist: (Playlist) -> Unit) {
    val playlists by Library.playlists.collectAsStateWithLifecycle()
    val recents by Library.recents.collectAsStateWithLifecycle()
    // Swipe left and right between Playlists and History (or tap their tabs).
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val playlistList = rememberLazyListState()
    val historyList = rememberLazyListState()

    // Pull down on either page to refresh it, like Home.
    var refreshing by remember { mutableStateOf(false) }
    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            try {
                Library.refresh()
                delay(700) // long enough to see the spinner settle
            } finally {
                refreshing = false
            }
        }
    }

    // Tapping the Library tab while here returns to Playlists (and its top).
    val reselectAtStart = remember { TabReselect.library }
    LaunchedEffect(TabReselect.library) {
        if (TabReselect.library == reselectAtStart) return@LaunchedEffect
        if (pager.currentPage != 0) pager.animateScrollToPage(0) else playlistList.animateScrollToItem(0)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Text(
            "Your library",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
        )
        TabRow(selectedTabIndex = pager.currentPage, containerColor = LsColors.Night) {
            Tab(selected = pager.currentPage == 0, onClick = { scope.launch { pager.animateScrollToPage(0) } }, text = { Text("Playlists") })
            Tab(selected = pager.currentPage == 1, onClick = { scope.launch { pager.animateScrollToPage(1) } }, text = { Text("History") })
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            if (page == 0) {
                Column(Modifier.fillMaxSize()) {
                    PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = { refresh() },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        LazyColumn(Modifier.fillMaxSize(), state = playlistList) {
                            if (playlists.isEmpty()) {
                                item(key = "empty") {
                                    EmptyState(
                                        "No playlists yet",
                                        "Make one with the button below, or press and hold any song and choose \"Add to playlist\".",
                                    )
                                }
                            }
                            items(playlists, key = { it.id }) { p ->
                                PlaylistRow(p, onClick = { onOpenPlaylist(p) })
                            }
                        }
                    }
                    // Across the bottom of the screen, just above the player bar and tabs.
                    FilledTonalButton(
                        onClick = { creating = true },
                        enabled = Library.canCreatePlaylist(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New playlist")
                    }
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(Modifier.fillMaxSize(), state = historyList) {
                        if (recents.isEmpty()) {
                            item(key = "empty") { EmptyState("Nothing played yet", "Songs you play will show up here.") }
                        } else {
                            item(key = "actions") {
                                PlayShuffleRow(recents) {
                                    TextButton(onClick = { confirmClear = true }) { Text("Clear") }
                                }
                            }
                            itemsIndexed(recents, key = { _, t: Track -> t.id }) { index, track ->
                                TrackRow(track, onClick = { PlayerConnection.playTracks(recents, index) })
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
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

    if (creating) {
        PlaylistNameDialog(
            title = "New playlist",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { creating = false },
        ) { name ->
            Library.createPlaylist(name)
            creating = false
        }
    }
}

@Composable
private fun PlaylistRow(p: Playlist, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
private fun PlayShuffleRow(tracks: List<Track>, playlistId: String? = null, extra: @Composable () -> Unit = {}) {
    // Play and Shuffle share the full width (anything extra, like History's Clear, sits after).
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
        ) {
            CenteredButtonLabel("Play") { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp)) }
        }
        OutlinedButton(
            onClick = { PlayerConnection.playTracks(tracks, randomStart = true, fromPlaylist = playlistId) },
            enabled = tracks.isNotEmpty(),
            modifier = Modifier.weight(1f).height(44.dp),
        ) {
            CenteredButtonLabel("Shuffle") { Icon(Icons.Rounded.Shuffle, null, Modifier.size(20.dp)) }
        }
        extra()
    }
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

    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
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
                PlayShuffleRow(playlist.tracks, playlistId = playlist.id)
            }
        }
        if (playlist.tracks.isEmpty()) {
            item(key = "empty") {
                EmptyState("This playlist is empty", "Press and hold any song and choose \"Add to playlist\".")
            }
        }
        itemsIndexed(playlist.tracks, key = { _, t -> t.id }) { index, track ->
            TrackRow(
                track,
                playlistId = playlist.id,
                onClick = { PlayerConnection.playTracks(playlist.tracks, index, fromPlaylist = playlist.id) },
            )
        }
    }

    if (renaming) {
        PlaylistNameDialog(
            title = "Rename playlist",
            initial = playlist.name,
            confirmLabel = "Save",
            onDismiss = { renaming = false },
        ) { name ->
            Library.renamePlaylist(playlist.id, name)
            renaming = false
        }
    }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete \"${playlist.name}\"?") },
            text = { Text("This removes the playlist. Its songs stay in their soundtracks.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    Library.deletePlaylist(playlist.id)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
}
