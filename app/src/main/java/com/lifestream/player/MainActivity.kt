package com.lifestream.player

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import com.lifestream.player.ui.screens.AlbumHolder
import com.lifestream.player.ui.screens.AlbumScreen
import com.lifestream.player.ui.screens.GameScreen
import com.lifestream.player.ui.screens.HomeScreen
import com.lifestream.player.ui.screens.LibraryScreen
import com.lifestream.player.ui.screens.NowPlayingScreen
import com.lifestream.player.ui.screens.SearchScreen
import com.lifestream.player.ui.theme.LifestreamTheme
import com.lifestream.player.ui.theme.LsColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        PlayerConnection.connect(this)
        setContent {
            LifestreamTheme {
                LifestreamRoot()
            }
        }
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
)

@Composable
private fun LifestreamRoot() {
    val nav = rememberNavController()
    val playerState by PlayerConnection.state.collectAsStateWithLifecycle()
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    NotificationPermissionRequest()

    LaunchedEffect(playerState.error) {
        val error = playerState.error ?: return@LaunchedEffect
        snackbar.showSnackbar(error)
        PlayerConnection.clearError()
    }

    BackHandler(enabled = showPlayer) { showPlayer = false }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    MiniPlayer(playerState, onOpen = { showPlayer = true })
                    BottomBar(nav)
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = "home",
                modifier = Modifier.padding(padding),
            ) {
                composable("home") {
                    HomeScreen(onOpenGame = { nav.navigate("game/${it.id}") })
                }
                composable("search") {
                    SearchScreen(onOpenAlbum = { album ->
                        AlbumHolder.put(album)
                        nav.navigate("album/${Uri.encode(album.url)}")
                    })
                }
                composable("library") { LibraryScreen() }
                composable("game/{id}") { entry ->
                    val game = entry.arguments?.getString("id")?.let { Catalog.game(it) }
                    if (game != null) {
                        GameScreen(
                            game = game,
                            onBack = { nav.popBackStack() },
                            onOpenAlbum = { album ->
                                AlbumHolder.put(album)
                                nav.navigate("album/${Uri.encode(album.url)}")
                            },
                        )
                    }
                }
                composable("album/{url}") { entry ->
                    val raw = entry.arguments?.getString("url") ?: return@composable
                    val url = if (raw.startsWith("http")) raw else Uri.decode(raw)
                    AlbumScreen(url = url, onBack = { nav.popBackStack() })
                }
            }
        }

        AnimatedVisibility(
            visible = showPlayer && playerState.current != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            NowPlayingScreen(onClose = { showPlayer = false })
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    NavigationBar(containerColor = LsColors.Night) {
        topLevel.forEach { item ->
            NavigationBarItem(
                selected = current == item.route,
                onClick = {
                    nav.navigate(item.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
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

@Composable
private fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}
