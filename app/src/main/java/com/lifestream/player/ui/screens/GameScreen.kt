package com.lifestream.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifestream.player.data.Album
import com.lifestream.player.data.Game
import com.lifestream.player.playback.PlayerConnection
import com.lifestream.player.ui.components.Artwork
import com.lifestream.player.ui.components.TrackRow
import com.lifestream.player.ui.components.pagedItems
import com.lifestream.player.ui.theme.LsColors

@Composable
fun GameScreen(game: Game, onBack: () -> Unit, onOpenAlbum: (Album) -> Unit) {
    val vm: GameViewModel = viewModel(key = "game-${game.id}") { GameViewModel(game) }
    val tracks = vm.tracks.items

    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "header") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(game.colorA, LsColors.Night))),
            ) {
                if (game.art != 0) {
                    Image(
                        painter = painterResource(game.art),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color(0x660A0E1C),
                                    0.5f to Color(0x990A0E1C),
                                    1f to LsColors.Night,
                                )
                            )
                    )
                } else {
                    CrystalMotif(
                        game.colorB,
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                    )
                }
                Column(
                    Modifier
                        .statusBarsPadding()
                        .padding(bottom = 16.dp),
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            game.numeral,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 56.sp,
                            color = game.colorB,
                            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 2f), 12f)),
                        )
                        Text(game.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                        Text(
                            "${game.year} · ${game.composer}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { PlayerConnection.playTracks(tracks) },
                                enabled = tracks.isNotEmpty(),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Play all")
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
                    }
                }
            }
        }

        item(key = "tabs") {
            TabRow(
                selectedTabIndex = vm.tab,
                containerColor = LsColors.Night,
            ) {
                Tab(selected = vm.tab == 0, onClick = { vm.tab = 0 }, text = { Text("Tracks") })
                Tab(selected = vm.tab == 1, onClick = { vm.tab = 1 }, text = { Text("Albums") })
            }
        }

        if (vm.tab == 0) {
            pagedItems(vm.tracks, footerKey = "tracks-footer", key = { "t-${it.id}" }) { index, track ->
                TrackRow(track, onClick = { PlayerConnection.playTracks(vm.tracks.items, index) })
            }
        } else {
            pagedItems(vm.albums, footerKey = "albums-footer", key = { "a-${it.url}" }) { _, album ->
                AlbumRow(album, onClick = { onOpenAlbum(album) })
            }
        }
    }
}

@Composable
fun AlbumRow(album: Album, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(album.thumbnail, Modifier.size(64.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                album.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            val sub = listOfNotNull(
                album.artist.ifBlank { null },
                album.trackCount.takeIf { it > 0 }?.let { "$it tracks" },
            )
            Text(
                sub.joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

