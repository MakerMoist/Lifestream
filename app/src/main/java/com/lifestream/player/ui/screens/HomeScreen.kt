package com.lifestream.player.ui.screens

import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.em
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import com.lifestream.player.ui.components.CenteredButtonLabel
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lifestream.player.data.AppSettings
import com.lifestream.player.data.Recommendations
import com.lifestream.player.data.SoundtrackIndex
import com.lifestream.player.ui.components.ArtCache
import com.lifestream.player.ui.components.rememberArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.alpha
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifestream.player.data.Catalog
import com.lifestream.player.data.Game
import com.lifestream.player.data.Library
import com.lifestream.player.data.Series
import com.lifestream.player.data.Track
import com.lifestream.player.playback.PlayerConnection
import com.lifestream.player.ui.components.Artwork
import com.lifestream.player.ui.components.TrackActions
import com.lifestream.player.ui.components.menuAnchor
import com.lifestream.player.ui.components.SectionLabel
import com.lifestream.player.ui.components.VillainQuotes
import com.lifestream.player.ui.components.villainQuotes
import com.lifestream.player.ui.theme.LsColors

/** Bumped when the Home tab is tapped while already on Home. */
object HomeReselect {
    var count by mutableIntStateOf(0)

    /** True while Now Playing covers the whole screen, so Home doesn't animate unseen. */
    var covered by mutableStateOf(false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenGame: (Game) -> Unit, onOpenPlaylist: (String) -> Unit) {
    val recents by Library.recents.collectAsStateWithLifecycle()
    val vm: HomeViewModel = viewModel()
    val selecting = vm.selected.isNotEmpty()
    val index by SoundtrackIndex.tracks.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    // A new quote and new picks each time Home is shown, and when you pull down to refresh.
    // They stay put otherwise, so the row doesn't shuffle under your finger as songs change.
    var quoteIndex by remember { mutableIntStateOf(VillainQuotes.next()) }
    var seed by remember { mutableLongStateOf(System.nanoTime()) }
    var refreshing by remember { mutableStateOf(false) }
    var recommended by remember { mutableStateOf<List<Track>>(emptyList()) }
    // Titles turned off in Settings → Titles don't appear here (cards or picks).
    val hidden by AppSettings.hiddenTitles.collectAsStateWithLifecycle()
    LaunchedEffect(recents.isNotEmpty(), index.size, seed, hidden) {
        recommended = withContext(Dispatchers.Default) {
            Recommendations.recommend(recents, index.filterKeys { it !in hidden }, seed) { t -> SoundtrackIndex.gameOf(t)?.id }
        }
    }
    fun refresh() {
        if (refreshing) return
        refreshing = true
        quoteIndex = VillainQuotes.next()
        seed = System.nanoTime()
        SoundtrackIndex.ensureBuilt()
        scope.launch {
            delay(700) // long enough to see the spinner settle
            refreshing = false
        }
    }

    // Tapping Home while on Home refreshes, just like pulling down: back to the top, a new quote
    // and new picks.
    val reselectAtStart = remember { HomeReselect.count }
    LaunchedEffect(HomeReselect.count) {
        if (HomeReselect.count != reselectAtStart) {
            refresh()
            gridState.animateScrollToItem(0)
        }
    }

    // While Home is on screen, move to another quote every 7.5 seconds. This only runs while the
    // app is in front (RESUMED) and never holds the screen on, so the phone still dims and sleeps.
    // Keyed on the quote, so tapping or refreshing restarts the wait.
    val showQuotes by AppSettings.showQuotes.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(quoteIndex, showQuotes, HomeReselect.covered) {
        if (!showQuotes || HomeReselect.covered) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(7_500)
            quoteIndex = VillainQuotes.next()
        }
    }
    LaunchedEffect(Unit) { ArtCache.warm(context, Catalog.games.map { it.art }) }
    // Remember the place in the quote deck when Home goes out of sight (or the app is left).
    androidx.lifecycle.compose.LifecycleEventEffect(Lifecycle.Event.ON_STOP) { VillainQuotes.save() }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { VillainQuotes.save() } }

    // Back leaves selection mode instead of the app.
    BackHandler(enabled = selecting) { vm.clear() }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (selecting) 112.dp else 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier
                        .statusBarsPadding()
                        .padding(top = 8.dp, bottom = 4.dp),
                ) {
                    // Tap the quote for another one. The area is always the same height (room for
                    // four lines and the name), so changing quotes never moves the page below.
                    // Short quotes sit in the middle of it; the longest shrink a little to fit.
                    if (showQuotes) {
                        val quote = villainQuotes[quoteIndex]
                        val quoteHeight = with(LocalDensity.current) { (QuoteSize * QuoteLines * 1.32f).toDp() + 6.dp + 18.sp.toDp() }
                        val quoteColor = LocalContentColor.current
                        AnimatedContent(
                            targetState = quote,
                            transitionSpec = { fadeIn(tween(600, delayMillis = 250)) togetherWith fadeOut(tween(350)) },
                            contentAlignment = Alignment.CenterStart,
                            label = "quote",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(quoteHeight)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    quoteIndex = VillainQuotes.next()
                                },
                        ) { q ->
                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                                BasicText(
                                    "\u201C${q.text}\u201D",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontStyle = FontStyle.Italic,
                                        lineHeight = 1.32.em,
                                        color = quoteColor,
                                    ),
                                    maxLines = QuoteLines,
                                    autoSize = TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = QuoteSize, stepSize = 0.5.sp),
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "\u2014 ${q.who}, ${q.game}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = LsColors.Gold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    val lastPlaylistId by Library.lastPlaylistId.collectAsStateWithLifecycle()
                    // Two big buttons sharing the full width: New Game (a fresh random mix) and
                    // Load Game (the playlist you last played from).
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { vm.surprise() },
                            enabled = !vm.surprising,
                            modifier = Modifier.weight(1f).height(44.dp),
                        ) {
                            CenteredButtonLabel("New Game") {
                                if (vm.surprising) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(20.dp))
                                }
                            }
                        }
                        FilledTonalButton(
                            onClick = { lastPlaylistId?.let(onOpenPlaylist) },
                            enabled = lastPlaylistId != null,
                            modifier = Modifier.weight(1f).height(44.dp),
                        ) {
                            CenteredButtonLabel("Load Game") {
                                Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, Modifier.size(20.dp))
                            }
                        }
                    }
                    vm.surpriseNote?.let { note ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            color = LsColors.Mako,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (recommended.isNotEmpty()) {
                item(key = "recommended", span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        SectionLabel("Recommended", Modifier.padding(top = 8.dp))
                        RecommendedRow(recommended)
                    }
                }
            }

            listOf(Series.MAINLINE, Series.SPINOFF).forEach { series ->
                val games = Catalog.bySeries(series).filter { it.id !in hidden }
                if (games.isEmpty()) return@forEach
                item(key = "label-${series.name}", span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(series.label, Modifier.padding(top = 8.dp).weight(1f))
                        if (selecting) {
                            val allIn = games.all { it.id in vm.selected }
                            TextButton(onClick = { if (allIn) vm.deselect(games) else vm.select(games) }) {
                                Text(if (allIn) "Deselect all" else "Select all")
                            }
                        }
                    }
                }
                items(games, key = { it.id }) { game ->
                    GameCard(
                        game = game,
                        selected = game.id in vm.selected,
                        selecting = selecting,
                        onClick = { if (selecting) vm.toggle(game) else onOpenGame(game) },
                        onLongClick = { vm.toggle(game) },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selecting,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            SelectionBar(vm)
        }
    }
}

@Composable
private fun SelectionBar(vm: HomeViewModel) {
    val count = vm.selected.size
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (count == 1) "1 soundtrack" else "$count soundtracks",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        vm.message ?: if (vm.loading) "Gathering tracks…" else "Play them as one queue",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (vm.message != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (vm.loading) {
                    CircularProgressIndicator(Modifier.padding(horizontal = 16.dp).size(24.dp), strokeWidth = 2.5.dp)
                } else {
                    IconButton(onClick = { vm.play(shuffle = true) }) {
                        Icon(Icons.Rounded.Shuffle, "Shuffle selected", tint = LsColors.Mako)
                    }
                    FilledIconButton(
                        onClick = { vm.play(shuffle = false) },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = LsColors.Crystal),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, "Play selected", tint = LsColors.Night)
                    }
                }
                IconButton(onClick = { vm.clear() }) {
                    Icon(Icons.Rounded.Close, "Cancel selection")
                }
            }
        }
    }
}

/** Songs picked from your history's soundtracks; tapping one plays the whole mix from there. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecommendedRow(songs: List<Track>) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 0.dp),
    ) {
        itemsIndexed(songs, key = { _, t -> t.id }) { index, track ->
            val anchor = com.lifestream.player.ui.components.rememberMenuAnchor()
            Column(
                Modifier
                    .menuAnchor(anchor)
                    .width(120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = {
                            // The song that's already playing opens Now Playing instead of restarting.
                            if (PlayerConnection.currentId.value == track.id) NowPlayingRequest.open()
                            else PlayerConnection.playTracks(songs, index)
                        },
                        onLongClick = { TrackActions.show(track, at = anchor.at()) },
                    ),
            ) {
                Artwork(track.thumbnail, Modifier.size(120.dp), size = 120.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    track.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
                track.gameId?.let { Catalog.game(it) }?.let { game ->
                    Text(
                        game.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameCard(
    game: Game,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    selecting: Boolean = false,
) {
    val shape = RoundedCornerShape(16.dp)
    // A shorter, wider card with rounded corners.
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.35f)
            .alpha(if (selecting && !selected) 0.55f else 1f)
            .then(if (selected) Modifier.border(3.dp, LsColors.Crystal, shape) else Modifier)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(game.colorA, LsColors.Night),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                )
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (game.art != 0) {
            val art = rememberArt(game.art)
            val artAlpha by animateFloatAsState(if (art != null) 1f else 0f, tween(220), label = "art")
            if (art != null) {
                Image(
                    bitmap = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    // The art is painted at the card's own shape, so it fits without cropping.
                    alignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().alpha(artAlpha),
                )
            }
            // Extra shade at the bottom so the title always reads cleanly over the art.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.3f to Color.Transparent,
                            1f to LsColors.Night.copy(alpha = 0.92f),
                        )
                    )
            )
        } else {
            CrystalMotif(game.colorB, Modifier.fillMaxSize())
        }
        if (selecting) {
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                if (selected) "Selected" else "Not selected",
                tint = if (selected) LsColors.Crystal else Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(26.dp),
            )
        }
        Text(
            game.numeral,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = if (game.numeral.length > 4) 20.sp else 26.sp,
            color = game.colorB,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 2f), 10f)),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                game.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${game.year} · ${game.composer}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A faint faceted crystal drawn in the corner of each card. */
@Composable
fun CrystalMotif(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.78f
        val top = h * 0.08f
        val bottom = h * 0.62f
        val half = w * 0.16f
        val mid = top + (bottom - top) * 0.42f
        val outline = Path().apply {
            moveTo(cx, top)
            lineTo(cx + half, mid)
            lineTo(cx, bottom)
            lineTo(cx - half, mid)
            close()
        }
        drawPath(outline, color.copy(alpha = 0.10f))
        drawPath(outline, color.copy(alpha = 0.35f), style = Stroke(width = 1.5.dp.toPx()))
        drawLine(color.copy(alpha = 0.25f), Offset(cx, top), Offset(cx, bottom), strokeWidth = 1.dp.toPx())
        drawLine(color.copy(alpha = 0.25f), Offset(cx - half, mid), Offset(cx + half, mid), strokeWidth = 1.dp.toPx())
    }
}

/** The Home quote's full text size and the most lines it may take (longer ones shrink to fit). */
private val QuoteSize = 19.sp
private const val QuoteLines = 4
