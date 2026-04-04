package com.cineplayer.android

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cineplayer.android.ui.screens.*
import com.cineplayer.android.ui.theme.CinePlayerTheme
import com.cineplayer.android.viewmodels.PlayerViewModel

class MainActivity : ComponentActivity() {
    private var playerViewModel: PlayerViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CinePlayerTheme {
                CinePlayerNavigation(onPlayerViewModelCreated = { playerViewModel = it })
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val vm = playerViewModel ?: return
        if (vm.exoPlayer.isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerViewModel?.setPipMode(isInPictureInPictureMode)
    }
}

private sealed class NavScreen(val route: String) {
    data object Library : NavScreen("library")
    data object Settings : NavScreen("settings")
    data object Player : NavScreen("player/{itemId}")
    data object Series : NavScreen("series/{seriesId}")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CinePlayerNavigation(onPlayerViewModelCreated: (PlayerViewModel) -> Unit) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val bottomNavItems = listOf(NavScreen.Library, NavScreen.Settings)
    val showBottomNav = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Movie, contentDescription = "Library") },
                        label = { Text("Library") },
                        selected = currentRoute == NavScreen.Library.route,
                        onClick = {
                            navController.navigate(NavScreen.Library.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = currentRoute == NavScreen.Settings.route,
                        onClick = {
                            navController.navigate(NavScreen.Settings.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { scaffoldPadding ->
        NavHost(
            navController = navController,
            startDestination = NavScreen.Library.route,
            modifier = Modifier.padding(scaffoldPadding)
        ) {
            composable(NavScreen.Library.route) {
                LibraryScreen(
                    onMovieClick = { itemId -> navController.navigate("player/$itemId") },
                    onSeriesClick = { seriesId -> navController.navigate("series/$seriesId") }
                )
            }
            composable(NavScreen.Settings.route) {
                SettingsScreen()
            }
            composable(
                NavScreen.Player.route,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType })
            ) { back ->
                val itemId = back.arguments?.getString("itemId") ?: return@composable
                val pvm: PlayerViewModel = viewModel()
                LaunchedEffect(Unit) { onPlayerViewModelCreated(pvm) }
                PlayerScreen(
                    itemId = itemId,
                    onBack = { navController.popBackStack() },
                    viewModel = pvm
                )
            }
            composable(
                NavScreen.Series.route,
                arguments = listOf(navArgument("seriesId") { type = NavType.StringType })
            ) { back ->
                val seriesId = back.arguments?.getString("seriesId") ?: return@composable
                SeriesDetailScreen(
                    seriesId = seriesId,
                    onBack = { navController.popBackStack() },
                    onEpisodeClick = { itemId -> navController.navigate("player/$itemId") }
                )
            }
        }
    }
}
