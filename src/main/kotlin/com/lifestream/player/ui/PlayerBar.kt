package com.lifestream.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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

/**
 * The player across the bottom of the window (the Android app's mini player): the song, its
 * controls and progress, Up Next, Cast and the volume. Click the song to open Now Playing.
 */
@Composable
fun PlayerBar(onOpen: (queue: Boolean) -> Unit, covered: Boolean) {
    val state by PlayerConnection.state.collectAsStateWithLifecycle()
    val compact by AppSettings.compactView.collectAsStateWithLifecycle()
    val track = state.current ?: return
    Column(Modifier.fillMaxWidth().background(LsColors.Panel)) {
        Row(
            Modifier.fillMaxWidth().height(if (compact) 72.dp else 88.dp).padding(horizontal = 12.dp),
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
            Column(Modifier.weight(1.4f).widthIn(max = 640.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Controls(state, small = true)
                SeekBar(state, compact = true)
            }
            // Up Next, Cast, volume, and open the full player.
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onOpen(true) }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Up Next") }
                CastButton()
                VolumeControl()
                IconButton(onClick = { onOpen(false) }) { Icon(Icons.Rounded.KeyboardArrowUp, "Open Now Playing") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeControl() {
    val volume by AppSettings.volume.collectAsStateWithLifecycle()
    var beforeMute by remember { mutableFloatStateOf(1f) }
    fun set(v: Float) {
        AppSettings.setVolume(v)
        Casting.current.value?.setVolume(v) // a Cast device follows the slider
    }
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
        )
    }
    val colors = SliderDefaults.colors(
        thumbColor = LsColors.Crystal,
        activeTrackColor = LsColors.Crystal,
        inactiveTrackColor = Color.White.copy(alpha = 0.15f),
    )
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(Modifier.width(110.dp)) {
        Slider(
            value = volume,
            onValueChange = { set(it) },
            colors = colors,
            interactionSource = interaction,
            thumb = { SliderDefaults.Thumb(interactionSource = interaction, colors = colors, thumbSize = DpSize(4.dp, 18.dp)) },
            track = { s -> SliderDefaults.Track(sliderState = s, colors = colors, drawStopIndicator = null, modifier = Modifier.height(6.dp)) },
            modifier = Modifier.height(24.dp),
        )
    }
}
