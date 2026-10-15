package com.lifestream.player.ui.screens

import androidx.compose.runtime.getValue
import com.lifestream.player.data.Track
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.ImageBitmap
import com.lifestream.player.ui.components.ArtCache
import com.lifestream.player.ui.components.rememberArt
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import com.lifestream.player.data.Matching
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifestream.player.data.Game
import com.lifestream.player.playback.PlayerConnection
import com.lifestream.player.ui.components.Artwork
import com.lifestream.player.ui.components.TrackRow
import com.lifestream.player.ui.components.SlimSearchField
import com.lifestream.player.ui.components.pagedItems
import com.lifestream.player.ui.theme.LsColors

@Composable
fun GameScreen(game: Game, onBack: () -> Unit) {
    val vm: GameViewModel = viewModel(key = "game-${game.id}") { GameViewModel(game) }
    val tracks = vm.tracks.items
    val focus = LocalFocusManager.current
    val filtering = vm.filter.isNotBlank()
    val matches by produceState(emptyList<Track>(), vm.filter, tracks) {
        val query = vm.filter
        if (query.isBlank()) {
            value = emptyList()
            return@produceState
        }
        // A short pause while typing, so each keystroke doesn't rescan the whole soundtrack.
        kotlinx.coroutines.delay(120)
        value = withContext(Dispatchers.Default) {
            val matchAll = Matching.queryTokens(query).isEmpty() && !Matching.hasJapanese(query)
            if (matchAll) tracks else tracks.filter { Matching.songScore(it, query) != null }
        }
    }
    // A search covers the whole soundtrack, so keep loading pages while one is active.
    LaunchedEffect(filtering, tracks.size, vm.tracks.loading) {
        if (filtering && !vm.tracks.endReached && !vm.tracks.loading) vm.tracks.loadMore()
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "header") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(game.colorA, LsColors.Night))),
            ) {
                if (game.art != 0) {
                    // The card's art (already decoded) shows at once; a screen-wide copy decoded
                    // in the background replaces it, so opening the page never waits on decoding.
                    val context = LocalContext.current
                    val card = rememberArt(game.art)
                    val wide by produceState<ImageBitmap?>(null, game.art) {
                        value = withContext(Dispatchers.IO) { ArtCache.loadWide(context, game.art) }
                    }
                    (wide ?: card)?.let { art ->
                        Image(
                            bitmap = art,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to LsColors.Night.copy(alpha = 0.4f),
                                    0.5f to LsColors.Night.copy(alpha = 0.6f),
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
                        // The big numeral is no longer shown, but its space is kept so the title and
                        // composer stay where they were.
                        Text(
                            "I",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 56.sp,
                            modifier = Modifier.alpha(0f).clearAndSetSemantics { },
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
                                // A title's own page plays in soundtrack order.
                                onClick = { PlayerConnection.playTracks(tracks, shuffle = false) },
                                enabled = tracks.isNotEmpty(),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Play all")
                            }
                            OutlinedButton(
                                onClick = { PlayerConnection.playTracks(tracks, randomStart = true) },
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

        item(key = "tracks-label") {
            Text(
                "ORIGINAL SOUNDTRACK",
                style = MaterialTheme.typography.labelSmall,
                color = LsColors.Gold,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
            )
        }

        item(key = "filter") {
            SlimSearchField(
                value = vm.filter,
                onValueChange = { vm.filter = it.take(100) },
                placeholder = "Search ${game.title}",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 4.dp),
                onSearch = { focus.clearFocus() },
            )
        }

        if (filtering) {
            if (matches.isEmpty() && vm.tracks.endReached) {
                item(key = "no-match") {
                    Text(
                        "No songs in ${game.title} match \"${vm.filter.trim()}\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            itemsIndexed(matches, key = { _, t -> "f-${t.id}-${Matching.songKey(t)}" }) { index, track ->
                TrackRow(track, onClick = { PlayerConnection.playTracks(matches, index, shuffle = false) })
            }
            if (!vm.tracks.endReached) {
                item(key = "filter-loading") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    }
                }
            }
        } else {
            pagedItems(
                vm.tracks,
                footerKey = "tracks-footer",
                key = { "t-${it.id}-${Matching.songKey(it)}" },
                emptyText = "No official soundtrack for this game on YouTube Music yet.",
            ) { index, track ->
                TrackRow(track, onClick = { PlayerConnection.playTracks(vm.tracks.items, index, shuffle = false) })
            }
        }
    }
}
