package com.lifestream.player.ui.screens

import com.lifestream.player.ui.components.scrollWithBar
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifestream.player.data.AppSettings
import com.lifestream.player.ui.theme.Palette
import com.lifestream.player.ui.theme.Palettes

/** Settings → Themes: pick the app's colours. Changes apply instantly and are remembered. */
@Composable
fun ThemesScreen(onBack: () -> Unit) {
    val themeId by AppSettings.themeId.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .scrollWithBar()
            .padding(bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
            }
            Text("Themes", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            "Each theme is inspired by a Final Fantasy world.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        )
        ThemePicker(selectedId = themeId, onSelect = { AppSettings.setTheme(it) })
    }
}

/** Two-column grid of theme swatches; each shows the theme's own background and colours. */
@Composable
private fun ThemePicker(selectedId: String, onSelect: (String) -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Palettes.all.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { palette ->
                    ThemeSwatch(
                        palette = palette,
                        isSelected = palette.id == selectedId,
                        onClick = { onSelect(palette.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ThemeSwatch(palette: Palette, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = palette.night,
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) palette.primary else palette.panel,
        ),
        modifier = modifier.semantics { this.selected = isSelected },
    ) {
        Column(
            Modifier
                .background(Brush.verticalGradient(listOf(palette.panel, palette.night)))
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf(palette.primary, palette.accent, palette.gold).forEach { color ->
                    Box(
                        Modifier
                            .padding(end = 6.dp)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (isSelected) {
                    Icon(Icons.Rounded.CheckCircle, "Selected", tint = palette.primary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                palette.name,
                style = MaterialTheme.typography.titleSmall,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                palette.inspiredBy,
                style = MaterialTheme.typography.bodySmall,
                color = palette.mist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
