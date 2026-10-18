package com.lifestream.player.ui.screens

import com.lifestream.player.ui.components.thinScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import com.lifestream.player.ui.components.rememberArt
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.rounded.History
import com.lifestream.player.data.SearchHistory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifestream.player.data.AppSettings
import com.lifestream.player.data.Game
import com.lifestream.player.data.Matching
import com.lifestream.player.data.SoundtrackIndex
import com.lifestream.player.playback.PlayerConnection
import com.lifestream.player.ui.components.EmptyState
import com.lifestream.player.ui.components.SectionLabel
import com.lifestream.player.ui.components.TrackRow
import com.lifestream.player.ui.components.SlimSearchField
import com.lifestream.player.ui.theme.LsColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Set when the Search tab is tapped, so the search box can take focus once. */
object SearchFocus {
    var requested by mutableStateOf(false)

    /** Set when the Search tab is tapped (switching to it or while on it): start a fresh search. */
    var clearRequested by mutableStateOf(false)
}


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
    val continueMode by AppSettings.continueMode.collectAsStateWithLifecycle()
    val recentSearches by SearchHistory.recent.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
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

    LaunchedEffect(SearchFocus.clearRequested) {
        if (!SearchFocus.clearRequested) return@LaunchedEffect
        SearchFocus.clearRequested = false
        vm.input = ""
    }

    LaunchedEffect(Unit) { SoundtrackIndex.ensureBuilt() }

    // Search as you type, re-running as more soundtracks finish indexing.
    // Re-run when a title is turned on or off in Settings → Titles, too.
    val hiddenTitles by AppSettings.hiddenTitles.collectAsStateWithLifecycle()
    LaunchedEffect(vm.input, index, hiddenTitles) {
        delay(150)
        val q = vm.input
        val (games, hits) = withContext(Dispatchers.Default) {
            val job = coroutineContext[kotlinx.coroutines.Job]
            SoundtrackIndex.searchGames(q) to SoundtrackIndex.searchTracks(q) { job?.isActive != false }
        }
        vm.games = games
        vm.hits = hits
        vm.resultsFor = q
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        SlimSearchField(
            value = vm.input,
            onValueChange = { vm.input = it.take(100) }, // song names are short; keeps matching cheap
            placeholder = "Search any title/name/game…",
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                .focusRequester(focusRequester),
            onSearch = {
                SearchHistory.add(vm.input)
                focus.clearFocus()
            },
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
            if (recentSearches.isEmpty()) {
                EmptyState(
                    title = "Search all enabled titles",
                    body = "Your recent searches will show up here.",
                )
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Recent searches", Modifier.weight(1f))
                TextButton(onClick = { confirmClear = true }, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Clear")
                }
            }
            val recentList = androidx.compose.foundation.lazy.rememberLazyListState()
            LazyColumn(Modifier.fillMaxSize().thinScrollbar(recentList), state = recentList) {
                items(recentSearches, key = { it }) { query ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.input = query
                                SearchHistory.add(query)
                                focus.clearFocus()
                            }
                            .padding(start = 20.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            query,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { SearchHistory.remove(query) }) {
                            Icon(Icons.Rounded.Close, "Remove \"$query\"", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (confirmClear) {
                AlertDialog(
                    onDismissRequest = { confirmClear = false },
                    title = { Text("Clear recent searches?") },
                    text = { Text("This removes all ${recentSearches.size} recent searches.") },
                    confirmButton = {
                        TextButton(onClick = {
                            SearchHistory.clear()
                            confirmClear = false
                        }) { Text("Clear") }
                    },
                    dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
                )
            }
            return@Column
        }

        val games = vm.games
        val hits = vm.hits
        if (games.isEmpty() && hits.isEmpty()) {
            // Only once this search has actually finished (not for the moment between typing and
            // its results, which used to flash "nothing found").
            if (vm.resultsFor == vm.input) {
                if (progress.running) {
                    EmptyState(
                        title = "Still indexing…",
                        body = "More soundtracks are being added; results will appear as they load.",
                    )
                } else {
                    EmptyState(title = "No Songs/Titles Found")
                }
            }
            return@Column
        }

        val resultList = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize().thinScrollbar(resultList), state = resultList) {
            if (games.isNotEmpty()) {
                item(key = "games-label") { SectionLabel("Soundtracks") }
                items(games, key = { "g-${it.id}" }) { game ->
                    GameRow(game, onClick = {
                        SearchHistory.add(vm.input)
                        onOpenGame(game)
                    })
                }
            }
            if (hits.isNotEmpty()) {
                item(key = "songs-label") { SectionLabel("Songs") }
                itemsIndexed(hits, key = { _, h -> "s-${h.game.id}-${h.track.id}" }) { i, hit ->
                    TrackRow(
                        hit.track,
                        subtitle = hit.game.title,
                        onClick = {
                            SearchHistory.add(vm.input)
                            if (continueFromSearch) {
                                val rest = when (continueMode) {
                                    // This song first, then the rest of its soundtrack in random order.
                                    AppSettings.ContinueMode.SAME_TITLE -> (SoundtrackIndex.songsFor(hit.game.id) ?: emptyList())
                                        .filter {
                                            it.id != hit.track.id &&
                                                Matching.songKey(it) != Matching.songKey(hit.track)
                                        }
                                        .shuffled()
                                    // This song first, then a random mix from every title that's on.
                                    AppSettings.ContinueMode.ALL_TITLES -> com.lifestream.player.data.Mixes.instant(excluding = setOf(hit.track.id))
                                }
                                // Already in order: no reshuffle, so the chosen song plays first.
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
            // The card art, already decoded at card size (the files themselves are 1080x800).
            val art = rememberArt(game.art)
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(game.colorA),
            ) {
                if (art != null) {
                    Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
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
