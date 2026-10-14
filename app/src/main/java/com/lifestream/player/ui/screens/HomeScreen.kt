package com.lifestream.player.ui.screens

import androidx.compose.foundation.Canvas
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
import com.lifestream.player.data.Track
import com.lifestream.player.playback.PlayerConnection
import com.lifestream.player.ui.components.Artwork
import com.lifestream.player.ui.components.SectionLabel
import com.lifestream.player.ui.theme.LsColors

@Composable
fun HomeScreen(onOpenGame: (Game) -> Unit) {
    val recents by Library.recents.collectAsStateWithLifecycle()
    val favorites by Library.favorites.collectAsStateWithLifecycle()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
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
                    "Every Final Fantasy soundtrack, streamed in its own player.",
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

        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionLabel("Soundtracks", Modifier.padding(top = 8.dp))
        }

        items(Catalog.games, key = { it.id }) { game ->
            GameCard(game, onClick = { onOpenGame(game) })
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

@Composable
fun GameCard(game: Game, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.9f)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(game.colorA, Color(0xFF0A0E1C)),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                )
            )
            .clickable(onClick = onClick),
    ) {
        CrystalMotif(game.colorB, Modifier.fillMaxSize())
        Text(
            game.numeral,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp),
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = if (game.numeral.length > 4) 24.sp else 34.sp,
            color = game.colorB,
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
