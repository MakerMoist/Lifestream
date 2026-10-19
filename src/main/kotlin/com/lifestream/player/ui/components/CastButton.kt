package com.lifestream.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifestream.player.data.AppSettings
import com.lifestream.player.playback.CastDevices
import com.lifestream.player.playback.Casting
import com.lifestream.player.ui.theme.LsColors

/**
 * Opens the list of Cast devices on your network (or, while casting, the choice to stop).
 * Shown while Settings → System → "Cast Support" is on.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    val enabled by AppSettings.castSupport.collectAsStateWithLifecycle()
    val current by Casting.current.collectAsStateWithLifecycle()
    if (!enabled && current == null) return
    var open by remember { mutableStateOf(false) }
    val connected = current != null
    IconButton(onClick = { open = true }, modifier = modifier) {
        Icon(
            if (connected) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
            if (connected) "Casting to ${current?.device?.name}" else "Cast to a device",
            tint = if (connected) LsColors.Mako else LocalContentColor.current,
        )
    }
    if (open) CastDialog(onDismiss = { open = false })
}

@Composable
private fun CastDialog(onDismiss: () -> Unit) {
    val current by Casting.current.collectAsStateWithLifecycle()
    val connecting by Casting.connecting.collectAsStateWithLifecycle()
    val devices by CastDevices.devices.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        if (Casting.current.value == null) CastDevices.startSearching()
        onDispose { CastDevices.stopSearching() } // only looks while this list is open
    }
    val casting = current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (casting != null) casting.device.name else "Cast to a Device") },
        text = {
            if (casting != null) {
                Text("LifeStream is playing on ${casting.device.name}. Stop casting to play on this PC again.")
            } else {
                Column(Modifier.scrollWithBar(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (devices.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Looking for Cast devices on your network…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    devices.forEach { d ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = connecting == null) {
                                    Casting.castTo(d)
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Speaker, null, tint = LsColors.Crystal)
                            Spacer(Modifier.width(12.dp))
                            Text(d.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (casting != null) {
                TextButton(onClick = {
                    Casting.stopCasting()
                    onDismiss()
                }) { Text("Stop Casting") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        dismissButton = if (casting != null) {
            { TextButton(onClick = onDismiss) { Text("Close") } }
        } else {
            null
        },
    )
}
