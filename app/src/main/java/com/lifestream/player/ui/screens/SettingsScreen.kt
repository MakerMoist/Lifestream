package com.lifestream.player.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifestream.player.data.AppSettings
import com.lifestream.player.playback.SongCache
import com.lifestream.player.ui.components.SectionLabel
import com.lifestream.player.ui.theme.LsColors
import com.lifestream.player.ui.theme.Palette
import com.lifestream.player.ui.theme.Palettes
import com.lifestream.player.update.Updater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Updater.Release) : UpdateState
    data class Downloading(val release: Updater.Release, val progress: Float) : UpdateState
    data class NeedsPermission(val release: Updater.Release, val apk: File) : UpdateState
    data class ReadyToInstall(val release: Updater.Release, val apk: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class SettingsViewModel : ViewModel() {
    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    fun check() {
        if (state is UpdateState.Checking || state is UpdateState.Downloading) return
        state = UpdateState.Checking
        viewModelScope.launch {
            state = try {
                val latest = Updater.fetchLatest()
                if (Updater.isNewer(latest)) UpdateState.Available(latest) else UpdateState.UpToDate
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Failed(e.message ?: "Couldn't reach GitHub. Check your connection.")
            }
        }
    }

    fun download(context: Context, release: Updater.Release) {
        if (state is UpdateState.Downloading) return
        state = UpdateState.Downloading(release, 0f)
        val app = context.applicationContext
        viewModelScope.launch {
            state = try {
                val apk = Updater.download(app, release) { p -> state = UpdateState.Downloading(release, p) }
                if (Updater.canInstall(app)) {
                    Updater.launchInstaller(app, apk)
                    UpdateState.ReadyToInstall(release, apk)
                } else {
                    UpdateState.NeedsPermission(release, apk)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Failed(e.message ?: "Download failed.")
            }
        }
    }

    /** Space used by saved songs, in bytes (-1 until measured). */
    var cacheUsedBytes by mutableLongStateOf(-1L)
        private set
    var clearingCache by mutableStateOf(false)
        private set

    fun refreshCacheUsage() {
        viewModelScope.launch {
            cacheUsedBytes = withContext(Dispatchers.IO) { SongCache.usedBytes() }
        }
    }

    fun setCacheLimit(mb: Int) {
        AppSettings.setCacheLimitMb(mb)
        viewModelScope.launch {
            cacheUsedBytes = withContext(Dispatchers.IO) {
                SongCache.applyLimit(mb)
                SongCache.usedBytes()
            }
        }
    }

    fun clearCache() {
        if (clearingCache) return
        clearingCache = true
        viewModelScope.launch {
            cacheUsedBytes = withContext(Dispatchers.IO) {
                SongCache.clear()
                SongCache.usedBytes()
            }
            clearingCache = false
        }
    }

    /** Called when returning from the "install unknown apps" screen. */
    fun onResume(context: Context) {
        val s = state
        if (s is UpdateState.NeedsPermission && Updater.canInstall(context)) {
            Updater.launchInstaller(context.applicationContext, s.apk)
            state = UpdateState.ReadyToInstall(s.release, s.apk)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenThemes: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val context = LocalContext.current
    val continueFromSearch by AppSettings.continueFromSearch.collectAsStateWithLifecycle()
    val themeId by AppSettings.themeId.collectAsStateWithLifecycle()
    val searchKeyboard by AppSettings.searchKeyboard.collectAsStateWithLifecycle()
    val cacheLimitMb by AppSettings.cacheLimitMb.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.onResume(context)
        vm.refreshCacheUsage()
        onPauseOrDispose { }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
        )

        SectionLabel("Playback")
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Keep playing after a search", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "When you play a song from Search, continue with the rest of its soundtrack, shuffled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = continueFromSearch, onCheckedChange = { AppSettings.setContinueFromSearch(it) })
            }
        }

        SectionLabel("Search", Modifier.padding(top = 8.dp))
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Open the keyboard on Search", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Tapping the Search tab puts the cursor in the search box so you can start typing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = searchKeyboard, onCheckedChange = { AppSettings.setSearchKeyboard(it) })
            }
        }

        SectionLabel("Saved songs", Modifier.padding(top = 8.dp))
        SettingsCard {
            Text("Keep recently played songs", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Songs you play are saved on your phone, so playing them again doesn't stream them " +
                    "again — and songs played all the way through work offline. The oldest are " +
                    "removed when the space below is full.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSettings.CACHE_LIMIT_CHOICES.forEach { mb ->
                    FilterChip(
                        selected = cacheLimitMb == mb,
                        onClick = { vm.setCacheLimit(mb) },
                        label = { Text(cacheLimitLabel(mb)) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    vm.cacheUsedBytes < 0 -> "Measuring…"
                    cacheLimitMb == 0 -> "Songs aren't being saved. Using ${formatBytes(vm.cacheUsedBytes)}."
                    else -> "Using ${formatBytes(vm.cacheUsedBytes)} of ${cacheLimitLabel(cacheLimitMb)}."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { vm.clearCache() },
                enabled = !vm.clearingCache && vm.cacheUsedBytes != 0L,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (vm.clearingCache) "Clearing…" else "Clear cache")
            }
        }

        SectionLabel("Theme", Modifier.padding(top = 8.dp))
        ThemeRow(Palettes.byId(themeId), onClick = onOpenThemes)

        // App updates always stays last, with its check button always visible.
        SectionLabel("App updates", Modifier.padding(top = 8.dp))
        SettingsCard {
            Text("Lifestream ${Updater.currentVersionName}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            if (vm.state != UpdateState.Idle) {
                UpdateSection(vm, context)
                Spacer(Modifier.height(16.dp))
            }
            val busy = vm.state is UpdateState.Checking || vm.state is UpdateState.Downloading
            Button(onClick = { vm.check() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Check for updates")
            }
        }
    }
}

@Composable
private fun UpdateSection(vm: SettingsViewModel, context: Context) {
    when (val s = vm.state) {
        UpdateState.Idle -> Unit

        UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Checking GitHub…", style = MaterialTheme.typography.bodyMedium)
        }

        UpdateState.UpToDate -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = LsColors.Mako)
                Spacer(Modifier.width(8.dp))
                Text("You're on the latest version.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        is UpdateState.Available -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val size = if (s.release.apkSize > 0) " (${s.release.apkSize / (1024 * 1024)} MB)" else ""
            Text(
                "Version ${s.release.versionName} is available$size.",
                style = MaterialTheme.typography.bodyMedium,
                color = LsColors.Gold,
            )
            Button(onClick = { vm.download(context, s.release) }) {
                Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Download and install")
            }
        }

        is UpdateState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Downloading ${s.release.versionName}… ${(s.progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = { s.progress },
                modifier = Modifier.fillMaxWidth(),
                color = LsColors.Mako,
            )
        }

        is UpdateState.NeedsPermission -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Downloaded and verified. Android needs your OK for Lifestream to install updates — " +
                    "turn on \"Allow from this source\", then come back.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { Updater.openInstallPermission(context) }) { Text("Allow installing updates") }
        }

        is UpdateState.ReadyToInstall -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Version ${s.release.versionName} is ready. Confirm the install when Android asks.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { Updater.launchInstaller(context, s.apk) }) { Text("Install") }
        }

        is UpdateState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(s.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun cacheLimitLabel(mb: Int): String = when {
    mb == 0 -> "Off"
    mb >= 1024 -> "${mb / 1024} GB"
    else -> "$mb MB"
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        bytes <= 0L -> "0 MB"
        mb < 1 -> "<1 MB"
        mb >= 1024 -> String.format(java.util.Locale.US, "%.1f GB", mb / 1024)
        else -> "${mb.toInt()} MB"
    }
}

/** The current theme, with a tap through to the Themes page. */
@Composable
private fun ThemeRow(palette: Palette, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Themes", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${palette.name} · ${palette.inspiredBy}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            listOf(palette.primary, palette.accent, palette.gold).forEach { color ->
                Box(
                    Modifier
                        .padding(start = 6.dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}
