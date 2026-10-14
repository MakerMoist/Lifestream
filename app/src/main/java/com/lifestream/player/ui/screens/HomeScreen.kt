package com.lifestream.player.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifestream.player.data.Catalog
import com.lifestream.player.data.Game
import com.lifestream.player.data.Library
import com.lifestream.player.data.Series
import com.lifestream.player.data.Track
import com.lifestream.player.playback.PlayerConnection
import com.lifestream.player.ui.components.Artwork
import com.lifestream.player.ui.components.SectionLabel
import com.lifestream.player.ui.theme.LsColors

@Composable
fun HomeScreen(onOpenGame: (Game) -> Unit) {
    val recents by Library.recents.collectAsStateWithLifecycle()
    val favorites by Library.favorites.collectAsStateWithLifecycle()
    val vm: HomeViewModel = viewModel()
    val selecting = vm.selected.isNotEmpty()

    // Back leaves selection mode instead of the app.
    BackHandler(enabled = selecting) { vm.clear() }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (selecting) 112.dp else 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier
                        .statusBarsPadding()
                        .padding(top = 20.dp, bottom = 4.dp),
                ) {
                    Text("LIFESTREAM", style = MaterialTheme.typography.labelSmall, color = LsColors.Gold)
                    Spacer(Modifier.height(4.dp))
                    Text("Music of the crystals", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Every Final Fantasy soundtrack, streamed in its own player. " +
                            "Press and hold a soundtrack to pick several and play them together.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onOpenGame(Catalog.games.random()) }) {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Surprise me")
                        }
                        if (favorites.isNotEmpty()) {
                            FilledTonalButton(onClick = { PlayerConnection.playTracks(favorites, shuffle = true) }) {
                                Icon(Icons.Rounded.Favorite, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Shuffle favorites")
                            }
                        }
                    }
                }
            }

            if (recents.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        SectionLabel("Jump back in", Modifier.padding(top = 8.dp))
                        RecentsRow(recents)
                    }
                }
            }

            listOf(Series.MAINLINE, Series.SPINOFF).forEach { series ->
                val games = Catalog.bySeries(series)
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

@Composable
private fun RecentsRow(recents: List<Track>) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 0.dp),
    ) {
        itemsIndexed(recents.take(20), key = { _, t -> t.id }) { index, track ->
            Column(
                Modifier
                    .width(120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { PlayerConnection.playTracks(recents, index) },
            ) {
                Artwork(track.thumbnail, Modifier.size(120.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    track.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
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
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.9f)
            .alpha(if (selecting && !selected) 0.55f else 1f)
            .then(if (selected) Modifier.border(3.dp, LsColors.Crystal, shape) else Modifier)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(game.colorA, Color(0xFF0A0E1C)),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                )
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (game.art != 0) {
            Image(
                painter = painterResource(game.art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Extra shade at the bottom so the title always reads cleanly over the art.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color(0xE60A0E1C),
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
                .padding(14.dp),
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = if (game.numeral.length > 4) 24.sp else 34.sp,
            color = game.colorB,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 2f), 10f)),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp),
        ) {
            Text(
                game.title,
                style = MaterialTheme.typography.titleMedium,
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
