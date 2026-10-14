package com.lifestream.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifestream.player.playback.PlayerConnection
import com.lifestream.player.ui.components.Artwork
import com.lifestream.player.ui.components.TrackRow
import com.lifestream.player.ui.components.pagedItems

@Composable
fun AlbumScreen(url: String, onBack: () -> Unit) {
    val vm: AlbumViewModel = viewModel(key = "album-$url") { AlbumViewModel(url) }
    val album = AlbumHolder.get(url)
    val tracks = vm.tracks.items

    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "header") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth()) {
                    IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                }
                Artwork(
                    album?.thumbnail ?: tracks.firstOrNull()?.thumbnail,
                    Modifier.size(220.dp),
                    corner = 12.dp,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    album?.title ?: "Album",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                val sub = listOfNotNull(
                    album?.artist?.ifBlank { null },
                    album?.trackCount?.takeIf { it > 0 }?.let { "$it tracks" },
                )
                if (sub.isNotEmpty()) {
                    Text(
                        sub.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { PlayerConnection.playTracks(tracks) }, enabled = tracks.isNotEmpty()) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                    OutlinedButton(
                        onClick = { PlayerConnection.playTracks(tracks, shuffle = true) },
                        enabled = tracks.isNotEmpty(),
                    ) {
                        Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        pagedItems(vm.tracks, footerKey = "footer", key = { "t-${it.id}" }, emptyText = "This album is empty.") { index, track ->
            TrackRow(
                track,
                leading = "${index + 1}",
                onClick = { PlayerConnection.playTracks(vm.tracks.items, index) },
            )
        }
    }
}
