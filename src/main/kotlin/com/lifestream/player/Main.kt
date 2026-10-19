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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
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
import com.lifestream.player.ui.components.Toasts
import com.lifestream.player.ui.components.TrackActions
import com.lifestream.player.ui.components.TrackActionsHost
import com.lifestream.player.ui.components.VillainQuotes
import com.lifestream.player.ui.screens.CreditsScreen
import com.lifestream.player.ui.screens.GameScreen
import com.lifestream.player.ui.screens.HomeReselect
import com.lifestream.player.ui.screens.HistoryScreen
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

/**
 * Only one LifeStream at a time: two would play over each other and both write the same
 * playlists and settings. Held (by Windows) for as long as this copy runs, and let go
 * automatically when it closes, even if it crashes.
 */
private object SingleInstance {
    private var channel: java.nio.channels.FileChannel? = null
    private var lock: java.nio.channels.FileLock? = null

    fun acquire(): Boolean = runCatching {
        val c = java.io.RandomAccessFile(java.io.File(AppDirs.data, "running.lock"), "rw").channel
        val l = c.tryLock()
        if (l == null) {
            c.close()
            false
        } else {
            channel = c
            lock = l
            true
        }
    }.getOrDefault(true) // if the check itself can't be made, start anyway

    /**
     * Opening LifeStream again while it's already running (say, hidden in the icon menu) brings the
     * running one's window back instead of starting a second copy. The second copy leaves a small
     * note in a folder the first one watches (Windows tells it straight away, no checking in a
     * loop), then quits.
     */
    private val signals get() = java.io.File(AppDirs.data, "signals")

    /** In the second copy: asks the running one to show itself; false if it didn't respond. */
    fun askRunningToShow(): Boolean = runCatching {
        val note = java.io.File(signals.apply { mkdirs() }, "show")
        note.writeText("show")
        // The running copy deletes the note when it has seen it.
        repeat(30) {
            if (!note.exists()) return@runCatching true
            Thread.sleep(100)
        }
        note.delete()
        false
    }.getOrDefault(false)

    /** In the running copy: calls [onShow] (on the window's thread) whenever asked to. */
    fun listen(onShow: () -> Unit) {
        Thread({
            runCatching {
                val dir = signals.apply { mkdirs() }
                java.io.File(dir, "show").delete()
                val watcher = dir.toPath().fileSystem.newWatchService()
                dir.toPath().register(
                    watcher,
                    java.nio.file.StandardWatchEventKinds.ENTRY_CREATE,
                    java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY,
                )
                while (true) {
                    val key = watcher.take()
                    key.pollEvents()
                    val note = java.io.File(dir, "show")
                    if (note.exists()) {
                        note.delete()
                        javax.swing.SwingUtilities.invokeLater(onShow)
                    }
                    if (!key.reset()) break
                }
            }
        }, "show-requests").apply { isDaemon = true }.start()
    }
}

/**
 * The audio decoder's native files are unpacked from the app on first use. They go in
 * LifeStream's own folder (not a ".javacpp" folder in your user folder, where they'd stay after
 * LifeStream is uninstalled). Copies earlier versions left in the old place are removed.
 */
private fun useOwnNativesFolder() {
    val dir = java.io.File(AppDirs.cache, "natives")
    System.setProperty("org.bytedeco.javacpp.cachedir", dir.absolutePath)
    runCatching {
        val old = java.io.File(System.getProperty("user.home"), ".javacpp/cache")
        listOf("ffmpeg-7.1-1.5.11-windows-x86_64.jar", "javacpp-1.5.11-windows-x86_64.jar").forEach { name ->
            java.io.File(old, name).takeIf { it.isDirectory }?.deleteRecursively()
        }
        // The folder itself, if that leaves it empty.
        if (old.list()?.isEmpty() == true) {
            old.delete()
            old.parentFile?.takeIf { it.list()?.isEmpty() == true }?.delete()
        }
    }
}

/** Everything LifeStream sets up before its window opens. */
fun startUp() {
    AppDirs.init()
    if (!SingleInstance.acquire()) {
        if (SingleInstance.askRunningToShow()) kotlin.system.exitProcess(0)
        runCatching {
            javax.swing.JOptionPane.showMessageDialog(
                null, "LifeStream is already open.", "LifeStream", javax.swing.JOptionPane.INFORMATION_MESSAGE,
            )
        }
        kotlin.system.exitProcess(0)
    }
    AppDirs.removeLeftovers()
    AppDirs.moveOldCache()
    // Saved however LifeStream ends, even if Windows shuts down or signs out while it's playing
    // from the icon menu.
    Runtime.getRuntime().addShutdownHook(Thread({ shutDown() }, "save-on-exit"))
    useOwnNativesFolder()
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

private val shutDownOnce = java.util.concurrent.atomic.AtomicBoolean(false)

/** Saves everything, so closing the window never loses a change. Runs once. */
fun shutDown() {
    if (!shutDownOnce.compareAndSet(false, true)) return
    runCatching { PlayerConnection.release() }
    runCatching { VillainQuotes.save() }
    runCatching { Library.flush() }
    runCatching { Prefs.flushAll() }
}

/**
 * Lets go of memory that's quick to rebuild (decoded pictures, search lookups) while the window
 * is minimized: LifeStream can keep playing in the background without holding on to it.
 */
private fun trimMemory() {
    com.lifestream.player.ui.components.ImageLoader.clearMemory()
    com.lifestream.player.ui.components.ArtCache.clear()
    SoundtrackIndex.trimCaches()
    com.lifestream.player.data.Matching.trimCaches()
    com.lifestream.player.data.Romaji.trimCaches()
    com.lifestream.player.data.TitleTranslator.trimCaches()
    System.gc() // gives the freed memory back to Windows
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
    data object History : Page
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
    Section(Page.History, "History", Icons.Rounded.History),
    Section(Page.Settings, "Settings", Icons.Rounded.Settings),
)

/**
 * Where you are: a sidebar section and the trail of pages opened within it. Every move is
 * remembered, so Back and Forward (the mouse's side buttons, Alt+arrows) retrace them like a
 * web browser, across sections too.
 */
private class Navigator {
    var section by mutableStateOf<Page>(Page.Home)
        private set
    var trails by mutableStateOf(sections.associate { it.root to listOf(it.root) })
        private set

    private class Spot(val section: Page, val trail: List<Page>)
    private val past = ArrayList<Spot>()
    private val future = ArrayList<Spot>()

    /** Pages Back or Forward can return to (kept as they were, scroll position and all). */
    var remembered by mutableStateOf<Set<Page>>(emptySet())
        private set

    val current: Page get() = trails.getValue(section).last()

    private fun here() = Spot(section, trails.getValue(section))

    private fun go(toSection: Page, trail: List<Page>, record: Boolean = true) {
        if (record) {
            val now = here()
            if (now.section == toSection && now.trail == trail) return
            past += now
            if (past.size > MAX_HISTORY) past.removeAt(0)
            future.clear()
        }
        section = toSection
        trails = trails + (toSection to trail)
        remembered = (past + future).flatMapTo(HashSet()) { it.trail }
    }

    fun open(page: Page) = go(section, trails.getValue(section) + page)

    /** A page's own back arrow: one page up within its section. */
    fun up() {
        val trail = trails.getValue(section)
        if (trail.size > 1) go(section, trail.dropLast(1))
    }

    /** Back to wherever you were before (false when there's nowhere earlier). */
    fun back(): Boolean {
        val spot = past.removeLastOrNull() ?: return false
        future += here()
        go(spot.section, spot.trail, record = false)
        return true
    }

    /** Forward again after going back (false when there's nothing to go forward to). */
    fun forward(): Boolean {
        val spot = future.removeLastOrNull() ?: return false
        past += here()
        go(spot.section, spot.trail, record = false)
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
                go(section, listOf(root)) // back to the section's first page
            } else {
                when (root) {
                    Page.Home -> HomeReselect.count++
                    Page.Settings -> TabReselect.settings++
                    Page.Library -> TabReselect.library++
                    Page.History -> TabReselect.history++
                    else -> Unit
                }
            }
        } else {
            go(root, trails.getValue(root))
        }
    }

    private companion object {
        const val MAX_HISTORY = 50
    }
}

/** Writes what went wrong to crash.log in LifeStream's data folder (nothing is sent anywhere). */
fun logCrash(where: String, t: Throwable) {
    runCatching {
        val dir = runCatching { AppDirs.data }.getOrNull()
            ?: java.io.File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "LifeStream").apply { mkdirs() }
        val text = "${java.time.LocalDateTime.now()} $where\n${t.stackTraceToString()}\n"
        java.io.File(dir, "crash.log").let { f ->
            if (f.length() > 256 * 1024) f.writeText("")
            f.appendText(text)
        }
    }
}

fun main() {
    Thread.setDefaultUncaughtExceptionHandler { thread, t -> logCrash("thread ${thread.name}", t) }
    try {
        startApp()
    } catch (t: Throwable) {
        logCrash("start-up", t)
        throw t
    }
}

private fun startApp() {
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
    val trayIcon = appIcon(32) ?: icon

    application {
        AppExit.exit = ::exitApplication
        val windowState = rememberWindowState(size = startingSize(), position = WindowPosition(Alignment.Center))
        val playerState by PlayerConnection.state.collectAsState()
        MediaKeys.wanted = playerState.queue.isNotEmpty()
        val navigator = remember { Navigator() }
        // Hidden to the icon menu by the close button ("Minimize on Close"), or shown.
        var windowVisible by remember { mutableStateOf(true) }
        val awtWindow = remember { arrayOfNulls<java.awt.Window>(1) }
        fun showWindow() {
            windowVisible = true
            windowState.isMinimized = false
            // In front of whatever else is open, once it's showing again.
            javax.swing.SwingUtilities.invokeLater { awtWindow[0]?.let { it.toFront(); it.requestFocus() } }
        }
        LaunchedEffect(Unit) { SingleInstance.listen { showWindow() } }
        // Minimized or hidden for a minute: free what's quick to rebuild.
        LaunchedEffect(windowState.isMinimized, windowVisible) {
            if (!windowState.isMinimized && windowVisible) return@LaunchedEffect
            kotlinx.coroutines.delay(60_000)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { trimMemory() }
        }
        Window(
            onCloseRequest = {
                if (AppSettings.minimizeOnClose.value) {
                    // Keeps playing from the icon menu; Quit there closes it for good.
                    windowVisible = false
                    PlayerConnection.saveQueue(now = true)
                } else {
                    shutDown()
                    exitApplication()
                }
            },
            visible = windowVisible,
            title = "LifeStream",
            icon = icon,
            state = windowState,
            onKeyEvent = { e -> handleKey(e, navigator) },
        ) {
            window.minimumSize = java.awt.Dimension(720, 560)
            awtWindow[0] = window
            // Every size Windows asks for (title bar, taskbar, Alt+Tab) drawn smoothly at that size.
            LaunchedEffect(Unit) {
                val images = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    listOf(16, 20, 24, 32, 40, 48, 64, 128, 256).mapNotNull { appIconImage(it) }
                }
                if (images.isNotEmpty()) window.iconImages = images
            }
            // The title bar in the theme's colours (and again whenever the theme changes).
            val palette = com.lifestream.player.ui.theme.ThemeState.current
            LaunchedEffect(palette) {
                androidx.compose.runtime.withFrameNanos { } // the window is on screen by now
                com.lifestream.player.integrations.TitleBar.apply(window, palette.panel.toRgb(), palette.text.toRgb())
            }
            // The mouse's side buttons go back and forward on every page.
            androidx.compose.runtime.DisposableEffect(Unit) {
                val listener = java.awt.event.AWTEventListener { event ->
                    val e = event as? java.awt.event.MouseEvent ?: return@AWTEventListener
                    if (e.id != java.awt.event.MouseEvent.MOUSE_PRESSED) return@AWTEventListener
                    when (e.button) {
                        4 -> { e.consume(); goBack(navigator) }
                        5 -> { e.consume(); navigator.forward() }
                    }
                }
                java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(listener, java.awt.AWTEvent.MOUSE_EVENT_MASK)
                onDispose { java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(listener) }
            }
            CompositionLocalProvider(LocalViewModelStoreOwner provides AppViewModels) {
                LifestreamTheme { AppRoot(navigator) }
            }
        }
        if (trayIcon != null) {
            Tray(
                icon = trayIcon,
                tooltip = playerState.current?.let { "LifeStream: ${it.title}" } ?: "LifeStream",
                onAction = { showWindow() },
                menu = {
                    Item(if (playerState.isPlaying) "Pause" else "Play", onClick = { PlayerConnection.togglePlay() })
                    Item("Next", onClick = { PlayerConnection.next() })
                    Item("Previous", onClick = { PlayerConnection.previous() })
                    Separator()
                    Item("Show LifeStream", onClick = { showWindow() })
                    Item("Quit", onClick = {
                        shutDown()
                        exitApplication()
                    })
                },
            )
        }
    }
    // Everything is saved by now. Ends the process at once, so no background thread (a network
    // pool, a Cast search) can keep LifeStream running unseen after its window has closed.
    kotlin.system.exitProcess(0)
}

/** 1240 x 820, or a little smaller than the screen on small displays. */
private fun startingSize(): DpSize {
    val screen = runCatching { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }.getOrNull()
        ?: return DpSize(1240.dp, 820.dp)
    // AWT reports the work area in the same scaled units Compose uses for the window.
    return DpSize(minOf(1240, (screen.width * 0.92).toInt()).dp, minOf(820, (screen.height * 0.92).toInt()).dp)
}

private val iconBytes: ByteArray? by lazy {
    runCatching { SelfTest::class.java.classLoader.getResourceAsStream("icon.png")!!.use { it.readBytes() } }.getOrNull()
}

/** LifeStream's crystal icon at exactly [px] pixels, smoothly scaled (for the tray and the sidebar). */
private fun appIcon(px: Int): BitmapPainter? = runCatching {
    BitmapPainter(
        com.lifestream.player.ui.components.ImageLoader.decode(iconBytes!!, px)!!,
        filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
    )
}.getOrNull()

/** The same, as a picture Windows can use for the window's icons. */
private fun appIconImage(px: Int): java.awt.image.BufferedImage? = runCatching {
    com.lifestream.player.ui.components.ImageLoader.decode(iconBytes!!, px)!!.toAwtImage()
}.getOrNull()

private fun androidx.compose.ui.graphics.Color.toRgb(): Int =
    ((red * 255).toInt() shl 16) or ((green * 255).toInt() shl 8) or (blue * 255).toInt()

/** Back: closes a menu or Now Playing first, otherwise goes to the previous page. */
private fun goBack(navigator: Navigator) {
    when {
        TrackActions.target.value != null -> TrackActions.dismiss()
        AppMenu.request.value != null -> AppMenu.dismiss()
        BackDispatcher.back() -> Unit
        else -> navigator.back()
    }
}

/**
 * Esc or Alt+← goes back, Alt+→ forward; Space plays or pauses; Ctrl+←/→ change song; Ctrl+F
 * searches.
 */
private fun handleKey(e: androidx.compose.ui.input.key.KeyEvent, navigator: Navigator): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    return when {
        e.key == Key.Escape || (e.isAltPressed && e.key == Key.DirectionLeft) -> {
            goBack(navigator)
            true
        }
        e.isAltPressed && e.key == Key.DirectionRight -> {
            navigator.forward()
            true
        }
        // In a text box, Space types a space (and nothing else).
        e.key == Key.Spacebar && !com.lifestream.player.ui.Typing.active -> {
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

    LaunchedEffect(Unit) { Toasts.messages.collect { snackbar.showSnackbar(it) } }
    // The release build's launch check also pictures Settings in Compact View.
    LaunchedEffect(Unit) {
        if (System.getenv("LIFESTREAM_SELFTEST") == "settings") navigator.select(Page.Settings)
    }
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
            .background(MaterialTheme.colorScheme.background),
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
            NowPlayingOverlay(playerOpen, openQueue, onClose = { showPlayer = false })
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

/** Now Playing slides up over the pages (the sidebar stays put). */
@Composable
private fun NowPlayingOverlay(open: Boolean, openQueue: Boolean, onClose: () -> Unit) {
    AnimatedVisibility(
        visible = open,
        enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(180)),
        exit = slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180)),
    ) {
        NowPlayingScreen(onClose = onClose, openQueue = openQueue)
    }
}

/**
 * A title's page keeps its loaded song list (its view model) and scroll position while it's in a
 * section's trail of pages, and lets go of them once you've gone back past it.
 */
private class PageStores {
    private val owners = HashMap<String, ViewModelStoreOwner>()
    private val seen = HashSet<String>()

    fun owner(key: String): ViewModelStoreOwner = owners.getOrPut(key) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }

    fun visited(key: String) {
        seen += key
    }

    /** Forgets every page that's no longer in any trail; returns their keys. */
    fun prune(live: Set<String>): List<String> {
        val gone = seen.filter { it !in live }
        gone.forEach { key ->
            owners.remove(key)?.viewModelStore?.clear()
            seen -= key
        }
        return gone
    }
}

@Composable
private fun Pages(navigator: Navigator) {
    val holder = rememberSaveableStateHolder()
    val stores = remember { PageStores() }
    val page = navigator.current
    LaunchedEffect(navigator.trails, navigator.remembered) {
        val live = (navigator.trails.values.flatten() + navigator.remembered).mapTo(HashSet()) { it.toString() }
        stores.prune(live).forEach { holder.removeState(it) }
    }
    val key = page.toString()
    stores.visited(key)
    // A title's page gets its own view model store (the main pages share the app's).
    val owner: ViewModelStoreOwner = if (page is Page.Game) stores.owner(key) else AppViewModels
    holder.SaveableStateProvider(key) { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        // Every page uses the whole width of the window.
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                when (page) {
                    Page.Home -> HomeScreen(
                        onOpenGame = { navigator.open(Page.Game(it.id)) },
                        onOpenPlaylist = { id -> navigator.open(Page.Playlist(id)) },
                    )
                    Page.Search -> SearchScreen(onOpenGame = { navigator.open(Page.Game(it.id)) })
                    Page.Library -> LibraryScreen(onOpenPlaylist = { navigator.open(Page.Playlist(it.id)) })
                    Page.History -> HistoryScreen()
                    Page.Settings -> SettingsScreen(
                        onOpenThemes = { navigator.open(Page.Themes) },
                        onOpenTitles = { navigator.open(Page.Titles) },
                        onOpenCredits = { navigator.open(Page.Credits) },
                    )
                    Page.Themes -> ThemesScreen(onBack = { navigator.up() })
                    Page.Titles -> TitlesScreen(onBack = { navigator.up() })
                    Page.Credits -> CreditsScreen(onBack = { navigator.up() })
                    is Page.Game -> Catalog.game(page.id)?.let { GameScreen(game = it, onBack = { navigator.up() }) }
                    is Page.Playlist -> PlaylistScreen(playlistId = page.id, onBack = { navigator.up() })
                }
            }
        }
    } }
}

/**
 * The navigation down the left side of the window. At the top, LifeStream's icon is Home (with
 * "Home" under it) and right beside it (under it in Compact View) the Compact View switch; then Search, Library and History;
 * Settings always sits at the bottom. Compact View narrows it to icons with small labels.
 */
@Composable
private fun Sidebar(navigator: Navigator, compact: Boolean, onSelect: (Page) -> Unit) {
    Surface(color = LsColors.Panel, modifier = Modifier.fillMaxHeight().width(if (compact) 84.dp else 240.dp)) {
        Column(
            Modifier.fillMaxHeight().padding(horizontal = if (compact) 8.dp else 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            val onHome = navigator.section == Page.Home
            // The icon is drawn at exactly the size it's shown, so its edges stay smooth.
            val iconPx = with(androidx.compose.ui.platform.LocalDensity.current) { 40.dp.roundToPx() }
            val logo = remember(iconPx) { appIcon(iconPx) }
            val home: @Composable () -> Unit = {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (onHome) LsColors.Crystal.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { onSelect(Page.Home) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    logo?.let { Image(it, "Home", Modifier.size(40.dp)) }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Home",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
                        color = if (onHome) LsColors.Crystal else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Switches Compact View on (and, in the narrow strip, back off).
            val toggle: @Composable () -> Unit = {
                IconButton(onClick = { AppSettings.setCompactView(!compact) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (compact) Icons.Rounded.Menu else Icons.AutoMirrored.Rounded.MenuOpen,
                        if (compact) "Turn off Compact View" else "Turn on Compact View",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (compact) {
                // The narrow strip has no room beside the icon: the switch sits under it.
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 6.dp)) {
                    home()
                    Spacer(Modifier.height(4.dp))
                    toggle()
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp).fillMaxWidth()) {
                    home()
                    Spacer(Modifier.width(4.dp))
                    toggle()
                }
            }
            sections.filter { it.root != Page.Home && it.root != Page.Settings }.forEach { s ->
                SidebarItem(s, navigator.section == s.root, compact) { onSelect(s.root) }
            }
            Spacer(Modifier.weight(1f))
            sections.first { it.root == Page.Settings }.let { s ->
                SidebarItem(s, navigator.section == s.root, compact) { onSelect(s.root) }
            }
        }
    }
}

@Composable
private fun SidebarItem(s: Section, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(if (compact) 16.dp else 12.dp)
    val background = if (selected) LsColors.Crystal else androidx.compose.ui.graphics.Color.Transparent
    val content = if (selected) LsColors.Night else MaterialTheme.colorScheme.onSurfaceVariant
    if (compact) {
        Column(
            Modifier.clip(shape).clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 2.dp),
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
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(s.icon, null, tint = content)
            Spacer(Modifier.width(14.dp))
            Text(s.label, style = MaterialTheme.typography.titleSmall, color = content)
        }
    }
}
