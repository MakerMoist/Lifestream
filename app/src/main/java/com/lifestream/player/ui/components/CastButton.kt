package com.lifestream.player.ui.components

import android.content.Context
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteControllerDialog
import androidx.mediarouter.media.MediaRouteSelector
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.lifestream.player.ui.theme.LsColors

/**
 * Opens the list of Cast devices (or, while casting, the controls for the one in use). Shown only
 * on phones with Google Play services, which Cast needs.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val castContext = remember {
        runCatching { CastContext.getSharedInstance(context.applicationContext) }.getOrNull()
    } ?: return
    var connected by remember { mutableStateOf(castContext.castState == CastState.CONNECTED) }
    DisposableEffect(castContext) {
        val listener = CastStateListener { state -> connected = state == CastState.CONNECTED }
        castContext.addCastStateListener(listener)
        onDispose { castContext.removeCastStateListener(listener) }
    }
    IconButton(onClick = { showCastDialog(context, castContext, connected) }, modifier = modifier) {
        Icon(
            if (connected) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
            if (connected) "Casting: device controls" else "Cast to a device",
            tint = if (connected) LsColors.Mako else LocalContentColor.current,
        )
    }
}

private fun showCastDialog(context: Context, castContext: CastContext, connected: Boolean) {
    // The device picker is Android's own (AppCompat) dialog, dark to match the app.
    val themed = ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)
    runCatching {
        if (connected) {
            MediaRouteControllerDialog(themed).show()
        } else {
            MediaRouteChooserDialog(themed).apply {
                routeSelector = castContext.mergedSelector ?: MediaRouteSelector.EMPTY
            }.show()
        }
    }
}
