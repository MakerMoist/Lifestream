package com.lifestream.player.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifestream.player.data.AppSettings
import com.lifestream.player.data.Catalog
import com.lifestream.player.data.Game
import com.lifestream.player.data.Series
import com.lifestream.player.ui.components.SectionLabel
import com.lifestream.player.ui.components.rememberArt

/**
 * Settings → Titles: turn titles on or off. Titles that are off don't appear on Home, in Search,
 * in New Game or in auto-play; songs from them that are already in playlists stay there.
 */
@Composable
fun TitlesScreen(onBack: () -> Unit) {
    val hidden by AppSettings.hiddenTitles.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
                Text("Titles", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (hidden.isNotEmpty()) {
                    TextButton(onClick = { AppSettings.showAllTitles() }, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Turn all on")
                    }
                }
            }
            Text(
                "Titles you turn off don't show up on Home, in Search or in New Game.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
        }
        for (series in Series.entries) {
            item(key = "label-${series.name}") { SectionLabel(series.label, Modifier.padding(top = 8.dp)) }
            items(Catalog.bySeries(series), key = { it.id }) { game ->
                TitleRow(game, shown = game.id !in hidden) { AppSettings.setTitleShown(game.id, it) }
            }
        }
    }
}

@Composable
private fun TitleRow(game: Game, shown: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!shown) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 54.dp, height = 40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(game.colorA),
        ) {
            if (game.art != 0) {
                rememberArt(game.art)?.let { art ->
                    Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            game.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            color = if (shown) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Switch(checked = shown, onCheckedChange = onChange)
    }
}
