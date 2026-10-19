package com.lifestream.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.lifestream.player.data.AppDirs
import com.lifestream.player.data.AppSettings
import com.lifestream.player.data.Catalog
import com.lifestream.player.data.Library
import com.lifestream.player.data.Prefs
import com.lifestream.player.data.SearchHistory
import com.lifestream.player.data.SoundtrackIndex
import com.lifestream.player.integrations.Discord
import com.lifestream.player.integrations.MediaKeys
import com.lifestream.player.net.Http
import com.lifestream.player.net.OkHttpDownloader
import com.lifestream.player.playback.PlayerConnection
import com.lifestream.player.playback.SongStore
import com.lifestream.player.playback.StreamResolver
import com.lifestream.player.ui.BackDispatcher
import com.lifestream.player.ui.PlayerBar
import com.lifestream.player.ui.components.AppMenu
import com.lifestream.player.ui.components.AppMenuHost
import com.lifestream.player.ui.components.ArtCache
import com.lifestream.player.ui.components.Toasts
import com.lifestream.player.ui.components.TrackActions
import com.lifestream.player.ui.components.TrackActionsHost
import com.lifestream.player.ui.components.VillainQuotes
import com.lifestream.player.ui.screens.CreditsScreen
import com.lifestream.player.ui.screens.GameScreen
import com.lifestream.player.ui.screens.HomeReselect
import com.lifestream.player.ui.screens.HomeScreen
import com.lifestream.player.ui.screens.LibraryScreen
import com.lifestream.player.ui.screens.NowPlayingRequest
import com.lifestream.player.ui.screens.NowPlayingScreen
import com.lifestream.player.ui.screens.PlaylistScreen
import com.lifestream.player.ui.screens.SearchFocus
import com.lifestream.player.ui.screens.SearchScreen
import com.lifestream.player.ui.screens.SettingsScreen
import com.lifestream.player.ui.screens.TabReselect
import com.lifestream.player.ui.screens.ThemesScreen
import com.lifestream.player.ui.screens.TitlesScreen
import com.lifestream.player.ui.screens.UpdatePrompt
import com.lifestream.player.ui.theme.LifestreamTheme
import com.lifestream.player.ui.theme.LsColors
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

/** Everything LifeStream sets up before its window opens. */
fun startUp() {
    AppDirs.init()
    Http.init(AppDirs.cache)
    StreamResolver.init(AppDirs.cache)
    // The extractor talks to YouTube's servers through our own client (HTTPS to YouTube only).
    NewPipe.init(OkHttpDownloader(Http.extractorClient), Localization("en", "US"), ContentCountry("US"))
    AppSettings.init()
    Library.init()
    SearchHistory.init()
    VillainQuotes.init()
    SongStore.init()
    SoundtrackIndex.init()
}

/** Saves everything, so closing the window never loses a change. */
fun shutDown() {
    runCatching { PlayerConnection.release() }
    runCatching { VillainQuotes.save() }
    runCatching { Library.flush() }
    runCatching { Prefs.flushAll() }
}

/** Closes the app from anywhere (e.g. before an update installs). */
object AppExit {
    var exit: (() -> Unit)? = null
    fun request() {
        shutDown()
        exit?.invoke()
    }
}

private object AppViewModels : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/** The pages LifeStream shows. Each sidebar section keeps its own trail of pages. */
sealed interface Page {
    data object Home : Page
    data object Search : Page
    data object Library : Page
    data object Settings : Page
    data class Game(val id: String) : Page
    data class Playlist(val id: String) : Page
    data object Themes : Page
    data object Titles : Page
    data object Credits : Page
}

private class Section(val root: Page, val label: String, val icon: ImageVector)

private val sections = listOf(
    Section(Page.Home, "Home", Icons.Rounded.Home),
    Section(Page.Search, "Search", Icons.Rounded.Search),
    Section(Page.Library, "Library", Icons.Rounded.LibraryMusic),
    Section(Page.Settings, "Settings", Icons.Rounded.Settings),
)

private class Navigator {
    var section by mutableStateOf<Page>(Page.Home)
    var trails by mutableStateOf(sections.associate { it.root to listOf(it.root) })

    val current: Page get() = trails.getValue(section).last()

    fun open(page: Page) {
        trails = trails + (section to (trails.getValue(section) + page))
    }

    /** One page back within the section; false when already at its first page. */
    fun back(): Boolean {
        val trail = trails.getValue(section)
        if (trail.size <= 1) return false
        trails = trails + (section to trail.dropLast(1))
        return true
    }

    fun select(root: Page) {
        if (root == Page.Search) {
            SearchFocus.requested = true
            SearchFocus.clearRequested = true
        }
        if (root == section) {
            val trail = trails.getValue(section)
            if (trail.size > 1) {
                trails = trails + (section to listOf(root)) // back to the section's first page
            } else {
                when (root) {
                    Page.Home -> HomeReselect.count++
                    Page.Settings -> TabReselect.settings++
                    Page.Library -> TabReselect.library++
                    else -> Unit
                }
            }
        } else {
            section = root
        }
    }
}

fun main() {
    startUp()
    PlayerConnection.connect()
    Discord.start()
    MediaKeys.start { key ->
        javax.swing.SwingUtilities.invokeLater {
            when (key) {
                MediaKeys.Key.PLAY_PAUSE -> PlayerConnection.togglePlay()
                MediaKeys.Key.NEXT -> PlayerConnection.next()
                MediaKeys.Key.PREVIOUS -> PlayerConnection.previous()
                MediaKeys.Key.STOP -> if (PlayerConnection.state.value.isPlaying) PlayerConnection.togglePlay()
            }
        }
    }
    if (System.getenv("LIFESTREAM_SELFTEST") == "1") SelfTest.start()
    val icon = appIcon(256)

    application {
        AppExit.exit = ::exitApplication
        val windowState = rememberWindowState(size = DpSize(1240.dp, 820.dp), position = WindowPosition(Alignment.Center))
        val playerState by PlayerConnection.state.collectAsStateWithLifecycle()
        MediaKeys.wanted = playerState.queue.isNotEmpty()
        val navigator = remember { Navigator() }
        Window(
            onCloseRequest = {
                shutDown()
                exitApplication()
            },
            title = "LifeStream",
            icon = icon,
            state = windowState,
            onKeyEvent = { e -> handleKey(e, navigator) },
        ) {
            window.minimumSize = java.awt.Dimension(720, 560)
            CompositionLocalProvider(LocalViewModelStoreOwner provides AppViewModels) {
                LifestreamTheme { AppRoot(navigator) }
            }
        }
        if (icon != null) {
            Tray(
                icon = icon,
                tooltip = playerState.current?.let { "LifeStream: ${it.title}" } ?: "LifeStream",
                onAction = { windowState.isMinimized = false },
                menu = {
                    Item(if (playerState.isPlaying) "Pause" else "Play", onClick = { PlayerConnection.togglePlay() })
                    Item("Next", onClick = { PlayerConnection.next() })
                    Item("Previous", onClick = { PlayerConnection.previous() })
                    Separator()
                    Item("Show LifeStream", onClick = { windowState.isMinimized = false })
                    Item("Quit", onClick = {
                        shutDown()
                        exitApplication()
                    })
                },
            )
        }
    }
}

/** LifeStream's crystal icon, for the window, the tray and the sidebar. */
private fun appIcon(px: Int): BitmapPainter? = runCatching {
    val bytes = SelfTest::class.java.classLoader.getResourceAsStream("icon.png")!!.use { it.readBytes() }
    BitmapPainter(com.lifestream.player.ui.components.ImageLoader.decode(bytes, px)!!)
}.getOrNull()

/** Esc goes back; Space plays or pauses; Ctrl+←/→ change song; Ctrl+F searches. */
private fun handleKey(e: androidx.compose.ui.input.key.KeyEvent, navigator: Navigator): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    return when {
        e.key == Key.Escape -> {
            when {
                TrackActions.target.value != null -> TrackActions.dismiss()
                AppMenu.request.value != null -> AppMenu.dismiss()
                BackDispatcher.back() -> Unit
                else -> navigator.back()
            }
            true
        }
        e.key == Key.Spacebar -> {
            PlayerConnection.togglePlay()
            true
        }
        e.isCtrlPressed && e.key == Key.DirectionRight -> {
            PlayerConnection.next()
            true
        }
        e.isCtrlPressed && e.key == Key.DirectionLeft -> {
            PlayerConnection.previous()
            true
        }
        e.isCtrlPressed && e.key == Key.F -> {
            navigator.select(Page.Search)
            true
        }
        else -> false
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AppRoot(navigator: Navigator) {
    val compact by AppSettings.compactView.collectAsStateWithLifecycle()
    val playerState by PlayerConnection.state.collectAsStateWithLifecycle()
    var showPlayer by remember { mutableStateOf(false) }
    var openQueue by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { ArtCache.warm(Catalog.games.map { it.art }) }
    LaunchedEffect(Unit) { Toasts.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(playerState.error) {
        val error = playerState.error ?: return@LaunchedEffect
        snackbar.showSnackbar(error)
        PlayerConnection.clearError()
    }
    // Clicking the song that's already playing (in any list) opens Now Playing.
    val openRequestAtStart = remember { NowPlayingRequest.count }
    LaunchedEffect(NowPlayingRequest.count) {
        if (NowPlayingRequest.count != openRequestAtStart) {
            openQueue = false
            showPlayer = true
        }
    }
    val playerOpen = showPlayer && playerState.current != null
    LaunchedEffect(playerOpen) { HomeReselect.covered = playerOpen }

    Row(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // The mouse's back button goes back, like Esc.
            .onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) { e ->
                if (e.button == PointerButton.Back && !BackDispatcher.back()) navigator.back()
            },
    ) {
        Sidebar(navigator, compact, onSelect = {
            showPlayer = false
            navigator.select(it)
        })
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Pages(navigator)
                }
                PlayerBar(
                    onOpen = { queue ->
                        openQueue = queue
                        showPlayer = true
                    },
                    covered = playerOpen,
                )
            }
            AnimatedVisibility(
                visible = playerOpen,
                enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(180)),
                exit = slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180)),
            ) {
                NowPlayingScreen(onClose = { showPlayer = false }, openQueue = openQueue)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
        }
    }
    UpdatePrompt()
    TrackActionsHost(onOpenGame = { game ->
        showPlayer = false
        navigator.open(Page.Game(game.id))
    })
    AppMenuHost()
}

@Composable
private fun Pages(navigator: Navigator) {
    val holder = rememberSaveableStateHolder()
    val page = navigator.current
    holder.SaveableStateProvider(page.toString()) {
        // Settings-like pages read best at a comfortable width, centred on a wide window.
        val narrow = page == Page.Settings || page == Page.Themes || page == Page.Titles || page == Page.Credits
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(if (narrow) Modifier.widthIn(max = 860.dp).fillMaxSize() else Modifier.fillMaxSize()) {
                when (page) {
                    Page.Home -> HomeScreen(
                        onOpenGame = { navigator.open(Page.Game(it.id)) },
                        onOpenPlaylist = { id -> navigator.open(Page.Playlist(id)) },
                    )
                    Page.Search -> SearchScreen(onOpenGame = { navigator.open(Page.Game(it.id)) })
                    Page.Library -> LibraryScreen(onOpenPlaylist = { navigator.open(Page.Playlist(it.id)) })
                    Page.Settings -> SettingsScreen(
                        onOpenThemes = { navigator.open(Page.Themes) },
                        onOpenTitles = { navigator.open(Page.Titles) },
                        onOpenCredits = { navigator.open(Page.Credits) },
                    )
                    Page.Themes -> ThemesScreen(onBack = { navigator.back() })
                    Page.Titles -> TitlesScreen(onBack = { navigator.back() })
                    Page.Credits -> CreditsScreen(onBack = { navigator.back() })
                    is Page.Game -> Catalog.game(page.id)?.let { GameScreen(game = it, onBack = { navigator.back() }) }
                    is Page.Playlist -> PlaylistScreen(playlistId = page.id, onBack = { navigator.back() })
                }
            }
        }
    }
}

/**
 * The navigation down the left side of the window: icons with their names, or (Compact View)
 * icons only in a narrow strip.
 */
@Composable
private fun Sidebar(navigator: Navigator, compact: Boolean, onSelect: (Page) -> Unit) {
    Surface(color = LsColors.Panel, modifier = Modifier.fillMaxHeight().width(if (compact) 76.dp else 220.dp)) {
        Column(
            Modifier.padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 18.dp, start = if (compact) 0.dp else 6.dp)) {
                val logo = remember { appIcon(96) }
                logo?.let { Image(it, null, Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))) }
                if (!compact) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "LifeStream",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            sections.forEach { s ->
                val selected = navigator.section == s.root
                val shape = RoundedCornerShape(if (compact) 16.dp else 12.dp)
                val background = if (selected) LsColors.Crystal else androidx.compose.ui.graphics.Color.Transparent
                val content = if (selected) LsColors.Night else MaterialTheme.colorScheme.onSurfaceVariant
                if (compact) {
                    Column(
                        Modifier.clip(shape).clickable { onSelect(s.root) }.padding(vertical = 6.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.clip(shape).background(background).padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) { Icon(s.icon, s.label, tint = content) }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            s.label,
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
                            color = if (selected) LsColors.Crystal else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(background)
                            .clickable { onSelect(s.root) }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(s.icon, null, tint = content)
                        Spacer(Modifier.width(14.dp))
                        Text(s.label, style = MaterialTheme.typography.titleSmall, color = content)
                    }
                }
            }
        }
    }
}
