package com.lifestream.player

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lifestream.player.data.Catalog
import com.lifestream.player.playback.PlayerConnection
import com.lifestream.player.ui.components.MiniPlayer
import com.lifestream.player.ui.screens.GameScreen
import com.lifestream.player.ui.screens.HomeReselect
import com.lifestream.player.ui.screens.HomeScreen
import com.lifestream.player.ui.screens.LibraryScreen
import com.lifestream.player.ui.screens.PlaylistScreen
import com.lifestream.player.ui.components.TrackActionsHost
import com.lifestream.player.ui.components.AppMenuHost
import com.lifestream.player.ui.screens.NowPlayingScreen
import com.lifestream.player.ui.screens.SearchScreen
import com.lifestream.player.ui.screens.SearchFocus
import com.lifestream.player.ui.screens.TabReselect
import com.lifestream.player.ui.screens.NowPlayingRequest
import com.lifestream.player.ui.screens.SettingsScreen
import com.lifestream.player.ui.screens.ThemesScreen
import com.lifestream.player.ui.screens.CreditsScreen
import com.lifestream.player.ui.screens.TitlesScreen
import com.lifestream.player.ui.screens.UpdatePrompt
import com.lifestream.player.ui.theme.LifestreamTheme
import com.lifestream.player.ui.theme.LsColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // LifeStream is always dark, so the status bar's clock, battery and notification icons stay
        // light (following the phone's light theme would make them dark and unreadable here).
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        PlayerConnection.connect(this)
        setContent {
            LifestreamTheme {
                LifestreamRoot()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // "Playlist Stays Open": keep where the queue is (song and spot) as the app is left.
        PlayerConnection.saveQueue(now = true)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) PlayerConnection.release()
    }
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel("home", "Home", Icons.Rounded.Home),
    TopLevel("search", "Search", Icons.Rounded.Search),
    TopLevel("library", "Library", Icons.Rounded.LibraryMusic),
    TopLevel("settings", "Settings", Icons.Rounded.Settings),
)

@Composable
private fun LifestreamRoot() {
    val nav = rememberNavController()
    // Which section of the bottom bar you're in (a game page opened from Home still belongs to Home).
    var section by rememberSaveable { mutableStateOf("home") }
    val playerState by PlayerConnection.state.collectAsStateWithLifecycle()
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    NotificationPermissionRequest()

    LaunchedEffect(playerState.error) {
        val error = playerState.error ?: return@LaunchedEffect
        snackbar.showSnackbar(error)
        PlayerConnection.clearError()
    }


    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    MiniPlayer(playerState, onOpen = { showPlayer = true })
                    BottomBar(nav, section, onSection = { section = it })
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = "home",
                modifier = Modifier
                    .padding(padding),
                // Quick cross-fades between screens instead of the long default.
                enterTransition = { fadeIn(tween(220, delayMillis = 60)) },
                exitTransition = { fadeOut(tween(140)) },
                popEnterTransition = { fadeIn(tween(220, delayMillis = 60)) },
                popExitTransition = { fadeOut(tween(140)) },
            ) {
                composable("home") {
                    HomeScreen(
                        onOpenGame = { nav.navigate("game/${it.id}") },
                        onOpenPlaylist = { id -> nav.navigate("playlist/$id") },
                    )
                }
                composable("search") {
                    SearchScreen(onOpenGame = { nav.navigate("game/${it.id}") })
                }
                composable("library") {
                    LibraryScreen(onOpenPlaylist = { nav.navigate("playlist/${it.id}") })
                }
                composable("playlist/{id}") { entry ->
                    val id = entry.arguments?.getString("id") ?: return@composable
                    PlaylistScreen(playlistId = id, onBack = { nav.popBackStack() })
                }
                composable("settings") {
                    SettingsScreen(
                        onOpenThemes = { nav.navigate("settings/themes") },
                        onOpenTitles = { nav.navigate("settings/titles") },
                        onOpenCredits = { nav.navigate("settings/credits") },
                    )
                }
                composable("settings/themes") { ThemesScreen(onBack = { nav.popBackStack() }) }
                composable("settings/titles") { TitlesScreen(onBack = { nav.popBackStack() }) }
                composable("settings/credits") { CreditsScreen(onBack = { nav.popBackStack() }) }
                composable("game/{id}") { entry ->
                    val game = entry.arguments?.getString("id")?.let { Catalog.game(it) }
                    if (game != null) {
                        GameScreen(game = game, onBack = { nav.popBackStack() })
                    }
                }
            }
        }

        // Tapping the song that's already playing (in any list) opens Now Playing.
        val openRequestAtStart = remember { NowPlayingRequest.count }
        LaunchedEffect(NowPlayingRequest.count) {
            if (NowPlayingRequest.count != openRequestAtStart) showPlayer = true
        }

        val playerOpen = showPlayer && playerState.current != null
        LaunchedEffect(playerOpen) { HomeReselect.covered = playerOpen }
        AnimatedVisibility(
            visible = playerOpen,
            // A fixed-length slide, so it always ends fully open (or fully closed).
            enter = slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(200)),
        ) {
            NowPlayingScreen(onClose = { showPlayer = false })
        }

        // Asks before installing a new version found by the automatic check.
        UpdatePrompt()

        // The song options menu (long-press / ⋮ on any song, and ⋮ on Now Playing).
        TrackActionsHost(onOpenGame = { game ->
            showPlayer = false
            nav.navigate("game/${game.id}")
        })
        // The same menu for playlists and Sort.
        AppMenuHost()
    }
}

/**
 * The bottom bar remembers which section you're in (a game page opened from Home still belongs to
 * Home). Tapping a different section switches to it with its saved state; tapping the section
 * you're already in returns to its first screen — e.g. Home from inside a game's song list.
 */
@Composable
private fun BottomBar(nav: NavHostController, section: String, onSection: (String) -> Unit) {
    val entry by nav.currentBackStackEntryAsState()
    val topRoutes = remember { topLevel.map { it.route }.toSet() }

    // Keep the highlight right when back navigation lands on another section's first screen.
    LaunchedEffect(entry) {
        entry?.destination?.route?.takeIf { it in topRoutes }?.let { onSection(it) }
    }

    NavigationBar(containerColor = LsColors.Night) {
        topLevel.forEach { item ->
            NavigationBarItem(
                selected = section == item.route,
                onClick = {
                    if (item.route == "search") {
                        SearchFocus.requested = true
                        SearchFocus.clearRequested = true
                    }
                    if (item.route == section) {
                        if (entry?.destination?.route == item.route) {
                            // Already on this section's first screen: nothing to navigate; Home
                            // refreshes as if pulled down.
                            when (item.route) {
                                "home" -> HomeReselect.count++
                                "settings" -> TabReselect.settings++
                                "library" -> TabReselect.library++
                            }
                        } else if (!nav.popBackStack(item.route, inclusive = false)) {
                            // Inside the section: go back to its first screen.
                            nav.navigate(item.route) { launchSingleTop = true }
                        }
                    } else {
                        onSection(item.route)
                        switchSection(nav, item.route)
                    }
                },
                icon = { Icon(item.icon, null) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = LsColors.Night,
                    indicatorColor = LsColors.Crystal,
                    selectedTextColor = LsColors.Crystal,
                ),
            )
        }
    }
}

/** Moves to another section of the bottom bar, keeping where you were in each. */
private fun switchSection(nav: NavHostController, route: String) {
    if (route == "search") {
        SearchFocus.requested = true
        SearchFocus.clearRequested = true
    }
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    // Ask only when it isn't granted, and once per visit (not again on every rotation).
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
