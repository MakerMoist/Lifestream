package com.lifestream.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifestream.player.data.Album
import com.lifestream.player.playback.PlayerConnection
import com.lifestream.player.ui.components.SectionLabel
import com.lifestream.player.ui.components.TrackRow
import com.lifestream.player.ui.components.pagedItems
import com.lifestream.player.ui.theme.LsColors

private val suggestions = listOf(
    "One-Winged Angel", "To Zanarkand", "Liberi Fatali", "Dancing Mad", "Prelude",
    "Aerith's Theme", "Melodies of Life", "Eyes on Me", "Answers", "Find the Flame",
    "Battle at the Big Bridge", "Blinded by Light", "Somnus", "Terra's Theme", "Main Theme",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(onOpenAlbum: (Album) -> Unit) {
    val vm: SearchViewModel = viewModel()
    val focus = LocalFocusManager.current

    fun go(text: String = vm.input) {
        focus.clearFocus()
        vm.submit(text)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        OutlinedTextField(
            value = vm.input,
            onValueChange = { vm.input = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            placeholder = { Text("Songs, albums, composers…") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (vm.input.isNotEmpty()) {
                    IconButton(onClick = { vm.input = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { go() }),
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            FilterChip(
                selected = vm.ffOnly,
                onClick = {
                    vm.ffOnly = !vm.ffOnly
                    if (vm.submitted != null) go()
                },
                label = { Text("Final Fantasy only") },
            )
        }

        val songs = vm.songs
        val albums = vm.albums
        if (songs == null || albums == null) {
            SectionLabel("Try")
            FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { s ->
                    AssistChip(onClick = { go(s) }, label = { Text(s) })
                }
            }
            return@Column
        }

        TabRow(selectedTabIndex = vm.tab, containerColor = LsColors.Night) {
            Tab(selected = vm.tab == 0, onClick = { vm.tab = 0 }, text = { Text("Songs") })
            Tab(selected = vm.tab == 1, onClick = { vm.tab = 1 }, text = { Text("Albums") })
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (vm.tab == 0) {
                pagedItems(songs, footerKey = "songs-footer", key = { "s-${it.id}" }) { index, track ->
                    TrackRow(track, onClick = { PlayerConnection.playTracks(songs.items, index) })
                }
            } else {
                pagedItems(albums, footerKey = "albums-footer", key = { "a-${it.url}" }) { _, album ->
                    AlbumRow(album, onClick = { onOpenAlbum(album) })
                }
            }
        }
    }
}

