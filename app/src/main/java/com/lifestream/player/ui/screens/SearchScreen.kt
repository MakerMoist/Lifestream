package com.lifestream.player.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifestream.player.data.AppSettings
import com.lifestream.player.data.Game
import com.lifestream.player.data.SoundtrackIndex
import com.lifestream.player.playback.PlayerConnection
import com.lifestream.player.ui.components.EmptyState
import com.lifestream.player.ui.components.SectionLabel
import com.lifestream.player.ui.components.TrackRow
import com.lifestream.player.ui.theme.LsColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Set when the Search tab is tapped, so the search box can take focus once. */
object SearchFocus {
    var requested by mutableStateOf(false)
}

private val suggestions = listOf(
    "One-Winged Angel", "To Zanarkand", "Liberi Fatali", "Dancing Mad", "Prelude",
    "Aerith's Theme", "Melodies of Life", "Eyes on Me", "Answers", "Find the Flame",
    "Battle at the Big Bridge", "Blinded by Light", "Somnus", "Corridors of Time", "Rebirth",
)

/**
 * Strictly Final Fantasy: searches only the app's own soundtrack index (every song from every
 * soundtrack in the catalog) and the catalog's game names. Nothing comes from open YouTube search.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(onOpenGame: (Game) -> Unit) {
    val vm: SearchViewModel = viewModel()
    val focus = LocalFocusManager.current
    val index by SoundtrackIndex.tracks.collectAsStateWithLifecycle()
    val progress by SoundtrackIndex.progress.collectAsStateWithLifecycle()
    val continueFromSearch by AppSettings.continueFromSearch.collectAsStateWithLifecycle()
    val searchKeyboard by AppSettings.searchKeyboard.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Tapping the Search tab opens the keyboard straight away (switchable in Settings).
    LaunchedEffect(SearchFocus.requested) {
        if (!SearchFocus.requested) return@LaunchedEffect
        SearchFocus.requested = false
        if (searchKeyboard) {
            try {
                focusRequester.requestFocus()
                keyboard?.show()
            } catch (_: IllegalStateException) {
                // The search box isn't on screen yet; nothing to focus.
            }
        }
    }

    LaunchedEffect(Unit) { SoundtrackIndex.ensureBuilt() }

    // Search as you type, re-running as more soundtracks finish indexing.
    LaunchedEffect(vm.input, index) {
        delay(150)
        val q = vm.input
        val (games, hits) = withContext(Dispatchers.Default) {
            SoundtrackIndex.searchGames(q) to SoundtrackIndex.searchTracks(q)
        }
        vm.games = games
        vm.hits = hits
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
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                .focusRequester(focusRequester),
            placeholder = { Text("Songs or games, e.g. \"Prelude\" or \"FF7\"") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (vm.input.isNotEmpty()) {
                    IconButton(onClick = { vm.input = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        )

        if (progress.running) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(
                    "Indexing soundtracks… ${progress.done} of ${progress.total}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { if (progress.total > 0) progress.done.toFloat() / progress.total else 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = LsColors.Mako,
                )
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }

        if (vm.input.isBlank()) {
            SectionLabel("Try")
            FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { s ->
                    AssistChip(onClick = { vm.input = s; focus.clearFocus() }, label = { Text(s) })
                }
            }
            return@Column
        }

        val games = vm.games
        val hits = vm.hits
        if (games.isEmpty() && hits.isEmpty()) {
            EmptyState(
                title = if (progress.running) "Still indexing…" else "No Final Fantasy music found",
                body = if (progress.running) "More soundtracks are being added; results will appear as they load."
                else "Search only covers the soundtracks in Lifestream.",
            )
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            if (games.isNotEmpty()) {
                item(key = "games-label") { SectionLabel("Soundtracks") }
                items(games, key = { "g-${it.id}" }) { game ->
                    GameRow(game, onClick = { onOpenGame(game) })
                }
            }
            if (hits.isNotEmpty()) {
                item(key = "songs-label") { SectionLabel("Songs") }
                itemsIndexed(hits, key = { _, h -> "s-${h.game.id}-${h.track.id}" }) { i, hit ->
                    TrackRow(
                        hit.track,
                        subtitle = hit.game.title,
                        onClick = {
                            if (continueFromSearch) {
                                // This song first, then the rest of its soundtrack in random order.
                                val rest = (SoundtrackIndex.tracksFor(hit.game.id) ?: emptyList())
                                    .filter { it.id != hit.track.id }
                                    .shuffled()
                                PlayerConnection.playTracks(listOf(hit.track) + rest, 0)
                            } else {
                                PlayerConnection.playTracks(hits.map { it.track }, i)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GameRow(game: Game, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (game.art != 0) {
            Image(
                painterResource(game.art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(game.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${game.year} · ${game.composer}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
