package com.lifestream.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifestream.player.data.AppSettings
import com.lifestream.player.playback.Casting
import com.lifestream.player.playback.PlayerConnection
import com.lifestream.player.ui.components.Artwork
import com.lifestream.player.ui.components.BackAndForthText
import com.lifestream.player.ui.components.CastButton
import com.lifestream.player.ui.screens.Controls
import com.lifestream.player.ui.screens.SeekBar
import com.lifestream.player.ui.theme.LsColors

/** The player bar's height (the sidebar lines Settings up with it). */
fun playerBarHeight(compact: Boolean) = if (compact) 86.dp else 96.dp

/**
 * The player across the bottom of the window: the song, its
 * controls and progress, Up Next, Cast and the volume. Click the song to open Now Playing.
 */
@Composable
fun PlayerBar(onOpen: (queue: Boolean) -> Unit, covered: Boolean) {
    val state by PlayerConnection.state.collectAsStateWithLifecycle()
    val compact by AppSettings.compactView.collectAsStateWithLifecycle()
    val track = state.current ?: return
    Column(Modifier.fillMaxWidth().background(LsColors.Panel)) {
        Row(
            Modifier.fillMaxWidth().height(playerBarHeight(compact)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The song: click it for Now Playing.
            Row(
                Modifier.weight(1f).clickable { onOpen(false) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val art = if (compact) 48.dp else 60.dp
                Artwork(track.thumbnail, Modifier.size(art), corner = 6.dp, size = art)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    BackAndForthText(
                        track.title,
                        MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        // Still while Now Playing covers the bar.
                        moving = !covered,
                    )
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // The controls and progress, in the middle.
            // The buttons sit a little higher, with a gap above the progress bar, so the loading
            // ring around Play (while a new song starts) has room and doesn't run into it.
            Column(Modifier.weight(1.4f).widthIn(max = 640.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Controls(state, small = true)
                Spacer(Modifier.height(7.dp))
                SeekBar(state, compact = true)
            }
            // Up Next, Cast, volume, and open the full player. The volume slider takes the room
            // that's left, so it stays usable as the window narrows.
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = { onOpen(true) }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Up Next") }
                CastButton()
                VolumeControl(Modifier.weight(1f, fill = false).widthIn(max = 220.dp))
            }
        }
    }
}

/**
 * The volume: a mute button, a slider and the level in per cent. The mouse wheel over any of it
 * moves the volume 2% a notch. While casting, the Cast device follows it.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun VolumeControl(modifier: Modifier = Modifier, large: Boolean = false) {
    val volume by AppSettings.volume.collectAsStateWithLifecycle()
    var beforeMute by remember { mutableFloatStateOf(1f) }
    fun set(v: Float) {
        val level = v.coerceIn(0f, 1f)
        AppSettings.setVolume(level)
        Casting.current.value?.setVolume(level) // a Cast device follows the slider
    }
    val colors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = LsColors.Crystal,
        inactiveTrackColor = Color.White.copy(alpha = 0.22f),
    )
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    BoxWithConstraints(
        modifier.onPointerEvent(PointerEventType.Scroll) { e ->
            val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
            if (dy != 0f) {
                // Up is louder; each notch is 2%, landing on even steps.
                val steps = Math.round(volume * 50f) - kotlin.math.sign(dy).toInt()
                set(steps / 50f)
                e.changes.forEach { it.consume() }
            }
        },
    ) {
        val showPercent = maxWidth >= 150.dp
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (volume > 0f) {
                    beforeMute = volume
                    set(0f)
                } else {
                    set(beforeMute.coerceAtLeast(0.1f))
                }
            }) {
                Icon(
                    when {
                        volume <= 0f -> Icons.AutoMirrored.Rounded.VolumeOff
                        volume < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                        else -> Icons.AutoMirrored.Rounded.VolumeUp
                    },
                    if (volume <= 0f) "Unmute" else "Mute",
                    modifier = Modifier.size(if (large) 26.dp else 22.dp),
                )
            }
            Slider(
                value = volume,
                onValueChange = { set(it) },
                colors = colors,
                interactionSource = interaction,
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = interaction,
                        colors = colors,
                        thumbSize = if (large) DpSize(18.dp, 18.dp) else DpSize(14.dp, 14.dp),
                    )
                },
                track = { s ->
                    SliderDefaults.Track(
                        sliderState = s,
                        colors = colors,
                        drawStopIndicator = null,
                        thumbTrackGapSize = 0.dp,
                        modifier = Modifier.height(if (large) 8.dp else 6.dp),
                    )
                },
                modifier = Modifier.weight(1f).widthIn(min = 56.dp).height(32.dp),
            )
            if (showPercent) {
                Text(
                    "${Math.round(volume * 100)}%",
                    style = if (large) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(if (large) 48.dp else 40.dp),
                )
            }
        }
    }
}
