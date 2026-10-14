package com.lifestream.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifestream.player.data.Library
import com.lifestream.player.data.Track
import com.lifestream.player.playback.PlayerConnection
import com.lifestream.player.ui.components.EmptyState
import com.lifestream.player.ui.components.TrackRow
import com.lifestream.player.ui.theme.LsColors

@Composable
fun LibraryScreen() {
    val favorites by Library.favorites.collectAsStateWithLifecycle()
    val recents by Library.recents.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

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
        TabRow(selectedTabIndex = tab, containerColor = LsColors.Night) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Favorites") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("History") })
        }

        val list = if (tab == 0) favorites else recents
        if (list.isEmpty()) {
            if (tab == 0) {
                EmptyState("No favorites yet", "Tap ⋮ on any track and add it to your favorites.")
            } else {
                EmptyState("Nothing played yet", "Tracks you play will show up here.")
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "actions") {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(onClick = { PlayerConnection.playTracks(list) }) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play all")
                    }
                    OutlinedButton(onClick = { PlayerConnection.playTracks(list, shuffle = true) }) {
                        Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                    if (tab == 1) {
                        TextButton(onClick = { Library.clearRecents() }) { Text("Clear") }
                    }
                }
            }
            itemsIndexed(list, key = { _, t: Track -> t.id }) { index, track ->
                TrackRow(track, onClick = { PlayerConnection.playTracks(list, index) })
            }
        }
    }
}
