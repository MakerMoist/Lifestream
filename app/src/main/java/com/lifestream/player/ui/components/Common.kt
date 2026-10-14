package com.lifestream.player.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Check
import com.lifestream.player.playback.SongCache
import androidx.compose.ui.platform.LocalDensity
import com.lifestream.player.data.hiResArtwork
import com.lifestream.player.playback.SafeInput
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lifestream.player.data.Track
import com.lifestream.player.data.formatDuration
import com.lifestream.player.playback.PlayerConnection
import com.lifestream.player.ui.theme.LsColors

/** Album / track art with a crystal-gradient placeholder while loading or when missing. */
@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    corner: Dp = 8.dp,
    /** About how big it's shown; artwork is fetched at a matching size, not always full size. */
    size: Dp = 64.dp,
) {
    val shape = RoundedCornerShape(corner)
    val placeholder: @Composable () -> Unit = {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(LsColors.Panel, lerp(LsColors.Panel, LsColors.Crystal, 0.25f)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = LsColors.Crystal.copy(alpha = 0.6f))
        }
    }
    Box(modifier.clip(shape)) {
        // The placeholder sits underneath; artwork fades in over it the first time and appears
        // straight away when it's already in memory, so screens don't flash as you come back.
        placeholder()
        val safeUrl = remember(url) { SafeInput.safeArtwork(url) }
        if (safeUrl != null) {
            val context = LocalContext.current
            // Ask for artwork sized for where it's shown (small rows don't download 1200 px images);
            // if that size isn't available, fall back to the original.
            val px = with(LocalDensity.current) { size.roundToPx() }
            val bucket = listOf(180, 360, 720, 1200).firstOrNull { it >= px } ?: 1200
            val hiRes = remember(safeUrl, bucket) {
                SafeInput.safeArtwork(hiResArtwork(safeUrl, bucket, videoThumbs = bucket >= 720))
            }
            var useOriginal by remember(safeUrl) { mutableStateOf(hiRes == null) }
            val request = remember(safeUrl, useOriginal) {
                ImageRequest.Builder(context).data(if (useOriginal) safeUrl else hiRes).crossfade(200).build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { if (!useOriginal) useOriginal = true },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: String? = null,
    subtitle: String? = null,
    /** Set when the row is shown inside a playlist, so the menu can offer "Remove". */
    playlistId: String? = null,
) {
    // Only the playing song's id, so rows don't redraw for anything else the player does.
    val currentId by PlayerConnection.currentId.collectAsStateWithLifecycle()
    val isCurrent = currentId == track.id

    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { TrackActions.show(track, playlistId) })
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Text(
                leading,
                modifier = Modifier.width(28.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            Artwork(track.thumbnail, Modifier.size(52.dp), corner = 6.dp)
            if (isCurrent) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = LsColors.Mako)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) LsColors.Mako else MaterialTheme.colorScheme.onSurface,
            )
            val sub = listOf(subtitle ?: track.artist, formatDuration(track.durationSec)).filter { it.isNotBlank() }
            Text(
                sub.joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Saved on the phone in full: plays without streaming.
        val savedIds by SongCache.cachedIds.collectAsStateWithLifecycle()
        if (track.id in savedIds) {
            Icon(
                Icons.Rounded.Check,
                "Saved on this phone",
                tint = Color.Gray,
                modifier = Modifier.size(18.dp),
            )
        }
        Box {
            IconButton(onClick = { TrackActions.show(track, playlistId) }) {
                Icon(Icons.Rounded.MoreVert, "Song options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = LsColors.Gold,
    )
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = LsColors.Faint, modifier = Modifier.size(40.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A slimmer search box than Material's standard text field (44 dp tall instead of 56), used on
 * the Search tab and each title's page.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SlimSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors()
    val shape = RoundedCornerShape(22.dp)
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(44.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Search,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch() }),
        interactionSource = interaction,
    ) { inner ->
        androidx.compose.material3.OutlinedTextFieldDefaults.DecorationBox(
            value = value,
            innerTextField = inner,
            enabled = true,
            singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
            interactionSource = interaction,
            placeholder = {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(20.dp)) },
            trailingIcon = if (value.isNotEmpty()) {
                {
                    androidx.compose.material3.IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Rounded.Close, "Clear", Modifier.size(20.dp))
                    }
                }
            } else {
                null
            },
            colors = colors,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            container = {
                androidx.compose.material3.OutlinedTextFieldDefaults.ContainerBox(
                    enabled = true,
                    isError = false,
                    interactionSource = interaction,
                    colors = colors,
                    shape = shape,
                )
            },
        )
    }
}
