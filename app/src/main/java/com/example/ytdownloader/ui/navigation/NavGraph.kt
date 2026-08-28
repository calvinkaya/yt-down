package com.example.ytdownloader.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.ytdownloader.ui.screens.Mp3LibraryScreen
import com.example.ytdownloader.ui.screens.PlayerScreen
import com.example.ytdownloader.ui.screens.SettingsScreen
import com.example.ytdownloader.ui.screens.VideoLibraryScreen
import com.example.ytdownloader.ui.screens.YouTubeScreen

enum class AppDestination(val route: String, val label: String, val icon: ImageVector) {
    YouTube("youtube", "YouTube", Icons.Default.Home),
    Player("player", "Player", Icons.Default.PlayArrow),
    Videos("videos", "Videos", Icons.Default.List),
    Music("music", "Music", Icons.Default.Star),
    Settings("settings", "Settings", Icons.Default.Settings),
}

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = AppDestination.YouTube.route) {
        composable(AppDestination.YouTube.route) { YouTubeScreen() }
        composable(AppDestination.Player.route) { PlayerScreen() }
        composable(AppDestination.Videos.route) { VideoLibraryScreen() }
        composable(AppDestination.Music.route) { Mp3LibraryScreen() }
        composable(AppDestination.Settings.route) { SettingsScreen() }
    }
}
