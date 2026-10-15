package com.lifestream.player.ui.screens

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.activity.ComponentActivity
import android.content.ContextWrapper
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
import androidx.compose.runtime.remember
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

    private var autoChecked = false

    /** Set when an automatic check finds a new version, until the user answers the prompt. */
    var prompt by mutableStateOf<Updater.Release?>(null)
        private set

    /**
     * The check that runs when the app opens (once per launch). Quiet: nothing is shown unless
     * there's a new version, and a failed check is simply forgotten.
     */
    fun autoCheck() {
        if (autoChecked || state !is UpdateState.Idle) return
        autoChecked = true
        viewModelScope.launch {
            try {
                val latest = Updater.fetchLatest()
                if (Updater.isNewer(latest) && state is UpdateState.Idle) {
                    state = UpdateState.Available(latest)
                    prompt = latest
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    /**
     * The quiet check again, when Home is refreshed (pulled down or its tab tapped) and automatic
     * checks are on. Shows the update prompt only if there's a newer version; a failure stays silent.
     */
    fun recheck() {
        when (val s = state) {
            is UpdateState.Checking, is UpdateState.Downloading, is UpdateState.ReadyToInstall, is UpdateState.NeedsPermission -> return
            is UpdateState.Available -> {
                prompt = s.release
                return
            }
            else -> Unit
        }
        autoChecked = true
        viewModelScope.launch {
            try {
                val latest = Updater.fetchLatest()
                if (Updater.isNewer(latest) && state !is UpdateState.Downloading) {
                    state = UpdateState.Available(latest)
                    prompt = latest
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun dismissPrompt() {
        prompt = null
    }

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

/** One update checker for the whole app, shared by Settings and the update prompt. */
@Composable
fun rememberUpdateViewModel(): SettingsViewModel {
    val owner = LocalContext.current.findActivity() ?: error("No activity")
    return viewModel(viewModelStoreOwner = owner)
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Shown over the app when the automatic check finds a new version: asks first, then downloads,
 * verifies and hands it to Android's installer from right here.
 */
@Composable
fun UpdatePrompt() {
    val vm = rememberUpdateViewModel()
    val context = LocalContext.current
    val auto by AppSettings.autoUpdate.collectAsStateWithLifecycle()
    LaunchedEffect(auto) { if (auto) vm.autoCheck() }
    LifecycleResumeEffect(Unit) {
        vm.onResume(context)
        onPauseOrDispose { }
    }
    val release = vm.prompt ?: return
    val asking = vm.state is UpdateState.Available
    AlertDialog(
        onDismissRequest = { if (vm.state !is UpdateState.Downloading) vm.dismissPrompt() },
        icon = { Icon(Icons.Rounded.SystemUpdate, null) },
        title = { Text("Update Available") },
        text = {
            Column {
                if (asking) {
                    val size = if (release.apkSize > 0) " (${release.apkSize / (1024 * 1024)} MB)" else ""
                    Text(
                        "LifeStream ${release.versionName}$size is ready. You have ${Updater.currentVersionName}. " +
                            "Would you like to update now?",
                    )
                } else {
                    UpdateSection(vm, context, showActions = false)
                }
            }
        },
        confirmButton = {
            when (val s = vm.state) {
                is UpdateState.Available -> TextButton(onClick = { vm.download(context, s.release) }) { Text("Update") }
                is UpdateState.NeedsPermission ->
                    TextButton(onClick = { Updater.openInstallPermission(context) }) { Text("Allow") }
                is UpdateState.ReadyToInstall ->
                    TextButton(onClick = { Updater.launchInstaller(context, s.apk) }) { Text("Install") }
                is UpdateState.Downloading -> Unit
                else -> TextButton(onClick = { vm.dismissPrompt() }) { Text("OK") }
            }
        },
        dismissButton = {
            if (vm.state !is UpdateState.Downloading) {
                TextButton(onClick = { vm.dismissPrompt() }) { Text(if (asking) "Not Now" else "Close") }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenThemes: () -> Unit, onOpenTitles: () -> Unit) {
    val vm = rememberUpdateViewModel()
    val autoUpdate by AppSettings.autoUpdate.collectAsStateWithLifecycle()
    val pauseAtZeroVolume by AppSettings.pauseAtZeroVolume.collectAsStateWithLifecycle()
    val randomSongOrder by AppSettings.randomSongOrder.collectAsStateWithLifecycle()
    val showQuotes by AppSettings.showQuotes.collectAsStateWithLifecycle()
    val levelVolume by AppSettings.levelVolume.collectAsStateWithLifecycle()
    val autoPlayAtEnd by AppSettings.autoPlayAtEnd.collectAsStateWithLifecycle()
    val androidAuto by AppSettings.androidAuto.collectAsStateWithLifecycle()
    val closeFromRecents by AppSettings.closeFromRecents.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val continueFromSearch by AppSettings.continueFromSearch.collectAsStateWithLifecycle()
    val continueMode by AppSettings.continueMode.collectAsStateWithLifecycle()
    val keepQueue by AppSettings.keepQueue.collectAsStateWithLifecycle()
    val hiddenTitles by AppSettings.hiddenTitles.collectAsStateWithLifecycle()
    val themeId by AppSettings.themeId.collectAsStateWithLifecycle()
    val searchKeyboard by AppSettings.searchKeyboard.collectAsStateWithLifecycle()
    val cacheLimitMb by AppSettings.cacheLimitMb.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.onResume(context)
        vm.refreshCacheUsage()
        onPauseOrDispose { }
    }

    // Tapping the Settings tab while on Settings goes back to the top.
    val scroll = rememberScrollState()
    val reselectAtStart = remember { TabReselect.settings }
    LaunchedEffect(TabReselect.settings) {
        if (TabReselect.settings != reselectAtStart) scroll.animateScrollTo(0)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scroll)
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
                    Text("Keep Playing After Searching", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "When you play a song from Search, continue playing based on your setting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = continueFromSearch, onCheckedChange = { AppSettings.setContinueFromSearch(it) })
            }
            // What it continues with (only while it's on).
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = continueMode == AppSettings.ContinueMode.SAME_TITLE,
                    onClick = { AppSettings.setContinueMode(AppSettings.ContinueMode.SAME_TITLE) },
                    enabled = continueFromSearch,
                    label = { Text("Same Title Shuffled") },
                )
                FilterChip(
                    selected = continueMode == AppSettings.ContinueMode.ALL_TITLES,
                    onClick = { AppSettings.setContinueMode(AppSettings.ContinueMode.ALL_TITLES) },
                    enabled = continueFromSearch,
                    label = { Text("All Titles Shuffled") },
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pause On Zero Volume", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "When lowering the volume to zero, the current song pauses. Resumes playing after the volume increases.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = pauseAtZeroVolume, onCheckedChange = { AppSettings.setPauseAtZeroVolume(it) })
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Even Out Volume", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Raises quiet songs and lowers loud ones so every song plays at a similar volume.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = levelVolume, onCheckedChange = { AppSettings.setLevelVolume(it) })
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-Play When Playlist Ends", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "When the last song finishes, keep going with random songs from any title.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = autoPlayAtEnd, onCheckedChange = { AppSettings.setAutoPlayAtEnd(it) })
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Playlist Stays Open", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "When toggled, the last played playlist/Queue will continue after re-opening the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = keepQueue, onCheckedChange = {
                    AppSettings.setKeepQueue(it)
                    if (it) com.lifestream.player.playback.PlayerConnection.saveQueue() else com.lifestream.player.playback.PlayerConnection.forgetSavedQueue()
                })
            }
        }
        // Which titles appear on Home, in Search and in New Game (a page of its own, like Themes).
        NavRow(
            title = "Titles",
            subtitle = if (hiddenTitles.isEmpty()) "All titles are on" else "${hiddenTitles.size} turned off",
            onClick = onOpenTitles,
        )

        SectionLabel("Interface", Modifier.padding(top = 8.dp))
        ThemeRow(Palettes.byId(themeId), onClick = onOpenThemes)
        SettingsCard {
            Text("Song List Order", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Changes how the songs are organized when opening the title.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !randomSongOrder,
                    onClick = { AppSettings.setRandomSongOrder(false) },
                    label = { Text("Soundtrack Order") },
                )
                FilterChip(
                    selected = randomSongOrder,
                    onClick = { AppSettings.setRandomSongOrder(true) },
                    label = { Text("Shuffled") },
                )
            }
        }
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show Quotes", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Switch(checked = showQuotes, onCheckedChange = { AppSettings.setShowQuotes(it) })
            }
        }
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Open Keyboard on Search", style = MaterialTheme.typography.bodyLarge)
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

        // System sits at the bottom: Android Auto, saved songs, then app updates last.
        SectionLabel("System", Modifier.padding(top = 8.dp))
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Android Auto", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Show your titles, playlists and history in Android Auto, with its full-screen player.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = androidAuto, onCheckedChange = { AppSettings.setAndroidAuto(it) })
            }
            if (androidAuto) AndroidAutoSetupHint(context)
        }
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Close Fully From Recent Apps", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Swiping the app closed in your recent app list force closes it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = closeFromRecents, onCheckedChange = { AppSettings.setCloseFromRecents(it) })
            }
        }
        SettingsCard {
            Text("Saved Songs", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Cache recently streamed songs locally instead of using data. Speeds up listening but uses more storage.",
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
            var confirmClear by remember { mutableStateOf(false) }
            if (confirmClear) {
                AlertDialog(
                    onDismissRequest = { confirmClear = false },
                    title = { Text("Clear Cache?") },
                    text = {
                        Text(
                            "This deletes ${formatBytes(vm.cacheUsedBytes)} of saved songs from your phone. " +
                                "They'll stream again the next time you play them.",
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.clearCache()
                            confirmClear = false
                        }) { Text("Clear") }
                    },
                    dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
                )
            }
            OutlinedButton(
                onClick = { confirmClear = true },
                enabled = !vm.clearingCache && vm.cacheUsedBytes != 0L,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (vm.clearingCache) "Clearing…" else "Clear Cache")
            }
        }


        // App updates always stays last, with its check button always visible.
        SettingsCard {
            Text("LifeStream ${Updater.currentVersionName}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Check Automatically", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = autoUpdate, onCheckedChange = {
                    AppSettings.setAutoUpdate(it)
                    if (it) vm.check() // check right away when it's switched on
                })
            }
            Spacer(Modifier.height(12.dp))
            if (vm.state != UpdateState.Idle) {
                UpdateSection(vm, context)
                Spacer(Modifier.height(16.dp))
            }
            val busy = vm.state is UpdateState.Checking || vm.state is UpdateState.Downloading
            Button(onClick = { vm.check() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Check for Updates")
            }
        }
    }
}

@Composable
private fun UpdateSection(vm: SettingsViewModel, context: Context, showActions: Boolean = true) {
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
            if (showActions) {
                Button(onClick = { vm.download(context, s.release) }) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Download and Install")
                }
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
                "Downloaded and verified. Android needs your OK for LifeStream to install updates — " +
                    "turn on \"Allow from this source\", then come back.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (showActions) {
                Button(onClick = { Updater.openInstallPermission(context) }) { Text("Allow Installing Updates") }
            }
        }

        is UpdateState.ReadyToInstall -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Version ${s.release.versionName} is ready. Confirm the install when Android asks.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (showActions) Button(onClick = { Updater.launchInstaller(context, s.apk) }) { Text("Install") }
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

private const val ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead"

/**
 * Android Auto only shows apps installed from the Play Store. Until it has opened LifeStream's
 * library (its own screens and full-screen player), it treats LifeStream as a phone app: the
 * player card still shows on the car's screen, but tapping it opens LifeStream on the phone.
 * This explains the one-time Android Auto setting that fixes that.
 */
@Composable
private fun AndroidAutoSetupHint(context: Context) {
    val seen by AppSettings.androidAutoSeen.collectAsStateWithLifecycle()
    Spacer(Modifier.height(12.dp))
    if (seen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = LsColors.Mako, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Android Auto has opened LifeStream's library.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Text(
        "If tapping LifeStream in the car opens it on your phone instead, Android Auto is hiding it " +
            "because it isn't from the Play Store. To allow it (once):",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    listOf(
        "Open Android Auto's settings (button below, then \"Additional settings in the app\").",
        "Scroll down and tap \"Version and permission info\" 10 times to unlock developer mode.",
        "Open the ⋮ menu → Developer settings, and turn on \"Unknown sources\".",
        "Reconnect your phone to the car. LifeStream then appears in the car's app list.",
    ).forEachIndexed { i, step ->
        Text(
            "${i + 1}. $step",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
    val installed = remember {
        runCatching { context.packageManager.getApplicationInfo(ANDROID_AUTO_PACKAGE, 0) }.isSuccess
    }
    if (installed) {
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:$ANDROID_AUTO_PACKAGE"),
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Open Android Auto Settings") }
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

/** A setting that opens a page of its own (like Themes). */
@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
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
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
