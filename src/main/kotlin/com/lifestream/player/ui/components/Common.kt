package com.lifestream.player.ui.components

import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.outlined.DownloadForOffline
import com.lifestream.player.playback.SongStore
import com.lifestream.player.data.AppSettings
import androidx.compose.ui.platform.LocalDensity
import com.lifestream.player.data.hiResArtwork
import com.lifestream.player.playback.SafeInput
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image
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
            // Ask for artwork sized for where it's shown (small rows don't download 1200 px images);
            // if that size isn't available, fall back to the original.
            val px = with(LocalDensity.current) { size.roundToPx() }
            val bucket = listOf(180, 360, 720, 1200).firstOrNull { it >= px } ?: 1200
            val hiRes = remember(safeUrl, bucket) {
                SafeInput.safeArtwork(hiResArtwork(safeUrl, bucket, videoThumbs = bucket >= 720))
            }
            // Keyed on the picture: when the same spot shows another song (the player bar, Now
            // Playing), it starts again from that song's picture instead of keeping the old one.
            androidx.compose.runtime.key(safeUrl, bucket) {
                val image by androidx.compose.runtime.produceState(ImageLoader.cached(hiRes ?: safeUrl, bucket)) {
                    if (value == null) value = (hiRes?.let { ImageLoader.load(it, bucket) }) ?: ImageLoader.load(safeUrl, bucket)
                }
                image?.let { image ->
                    Image(
                        image, null,
                        contentScale = ContentScale.Crop,
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
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
    // The menu opens where the row was pressed, or at the ⋮ button.
    val rowAnchor = rememberMenuAnchor()
    val dotsAnchor = rememberMenuAnchor()
    val compact by AppSettings.compactView.collectAsStateWithLifecycle()
    val art = if (compact) 40.dp else 52.dp

    Row(
        modifier
            .menuAnchor(rowAnchor)
            .fillMaxWidth()
            // Tapping the song that's already playing opens Now Playing instead of restarting it.
            .combinedClickable(
                onClick = { if (isCurrent) com.lifestream.player.ui.screens.NowPlayingRequest.open() else onClick() },
                onLongClick = { TrackActions.show(track, playlistId, rowAnchor.at()) },
            )
            // Right-click opens the song's menu where the pointer is.
            .onRightClick { at -> TrackActions.show(track, playlistId, at ?: rowAnchor.at()) }
            .padding(start = 16.dp, end = 4.dp, top = if (compact) 4.dp else 8.dp, bottom = if (compact) 4.dp else 8.dp),
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
            Artwork(track.thumbnail, Modifier.size(art), corner = 6.dp, size = art)
            if (isCurrent) {
                Box(
                    Modifier
                        .size(art)
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
        // In at least one of your playlists, and saved on this PC in full (plays without
        // streaming), snug against the ⋮ menu.
        val inPlaylists by com.lifestream.player.data.Library.inPlaylists.collectAsStateWithLifecycle()
        val savedIds by SongStore.cachedIds.collectAsStateWithLifecycle()
        val inAPlaylist = track.id in inPlaylists
        val saved = track.id in savedIds
        if (inAPlaylist || saved) {
            Row(
                Modifier.padding(start = 6.dp).offset(x = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (inAPlaylist) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAddCheck, "In a playlist", tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
                if (saved) {
                    Icon(Icons.Outlined.DownloadForOffline, "Saved on this PC", tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }
        }
        Box {
            IconButton(
                onClick = { TrackActions.show(track, playlistId, dotsAnchor.at(center = true)) },
                modifier = Modifier.menuAnchor(dotsAnchor),
            ) {
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
fun EmptyState(title: String, body: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = LsColors.Faint, modifier = Modifier.size(40.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (body != null) {
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
        modifier = modifier.height(44.dp).then(com.lifestream.player.ui.Typing.field()),
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
                androidx.compose.material3.OutlinedTextFieldDefaults.Container(
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

/** A dialog title kept on one line: it shrinks a little to fit instead of wrapping. */
@Composable
fun OneLineDialogTitle(text: String) {
    val style = MaterialTheme.typography.headlineSmall
    androidx.compose.foundation.text.BasicText(
        text,
        maxLines = 1,
        style = style.copy(color = androidx.compose.material3.LocalContentColor.current),
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
            minFontSize = 14.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp,
        ),
    )
}

/** A big button's icon and label, centered together on the button. */
@Composable
fun CenteredButtonLabel(text: String, icon: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

/**
 * One line of text that, when it doesn't fit, scrolls slowly to its end, rests, scrolls back to
 * the start, rests, and repeats, so the whole thing can be read. Text that fits stays still.
 */
@Composable
fun BackAndForthText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    /** False holds it still at the start (e.g. while something covers it), so it doesn't redraw unseen. */
    moving: Boolean = true,
) {
    val scroll = androidx.compose.foundation.rememberScrollState()
    val speed = with(LocalDensity.current) { 32.dp.toPx() } // per second
    val overflow = scroll.maxValue
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.LaunchedEffect(text, overflow, moving) {
        scroll.scrollTo(0)
        if (overflow <= 0 || !moving) return@LaunchedEffect
        val glide = androidx.compose.animation.core.tween<Float>(
            durationMillis = (overflow / speed * 1000).toInt().coerceAtLeast(600),
            easing = androidx.compose.animation.core.LinearEasing,
        )
        // Only while the app is on screen: no wake-ups every few seconds in the background.
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            scroll.scrollTo(0)
            while (true) {
                kotlinx.coroutines.delay(2_500)
                scroll.animateScrollTo(overflow, glide)
                kotlinx.coroutines.delay(2_500)
                scroll.animateScrollTo(0, glide)
            }
        }
    }
    Text(
        text,
        style = style,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.horizontalScroll(scroll, enabled = false),
    )
}
