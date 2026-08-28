package com.example.ytdownloader

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ytdownloader.data.AppState
import com.example.ytdownloader.data.DownloadKind
import com.example.ytdownloader.data.FallbackAsk
import com.example.ytdownloader.data.Mp3Entry
import com.example.ytdownloader.data.Store
import com.example.ytdownloader.data.VideoEntry
import com.example.ytdownloader.download.DownloadManager
import com.example.ytdownloader.ui.navigation.AppDestination
import com.example.ytdownloader.ui.navigation.AppNavHost
import com.example.ytdownloader.ui.screens.DownloadActionSheet
import com.example.ytdownloader.ui.theme.YTDownloaderTheme
import java.time.Instant

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()
        setContent {
            YTDownloaderTheme {
                AppRoot()
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @Composable
    private fun AppRoot() {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }

        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route

        var sheetVideoId by remember { mutableStateOf<String?>(null) }
        var fallbackAsk by remember { mutableStateOf<FallbackAsk?>(null) }

        // Tap handling (from the WebView bridge).
        LaunchedEffect(Unit) {
            AppState.videoTap.collect { id ->
                when (Store.getSettings().defaultAction) {
                    "video" -> {
                        DownloadManager.request(id, DownloadKind.VIDEO)
                        navigateTo(navController, AppDestination.Player.route)
                    }
                    "mp3" -> {
                        DownloadManager.request(id, DownloadKind.MP3)
                        navigateTo(navController, AppDestination.Music.route)
                    }
                    else -> sheetVideoId = id
                }
            }
        }

        // Download completion → navigate + play + (native) PiP.
        LaunchedEffect(Unit) {
            AppState.done.collect { event ->
                when (event.kind) {
                    DownloadKind.VIDEO -> {
                        AppState.nowPlayingVideo.value = VideoEntry(
                            videoId = event.videoId,
                            title = event.title,
                            filePath = event.filePath,
                            height = event.height ?: 0,
                            downloadedAt = Instant.now().toString(),
                        )
                        navigateTo(navController, AppDestination.Player.route)
                        if (!event.cached && Store.getSettings().pictureInPicture) {
                            enterPip()
                        }
                    }
                    DownloadKind.MP3 -> {
                        AppState.nowPlayingMp3.value = Mp3Entry(
                            videoId = event.videoId,
                            title = event.title,
                            artist = event.artist ?: "",
                            filePath = event.filePath,
                            bitrateKbps = event.bitrateKbps ?: 0,
                            durationSec = event.durationSec ?: 0,
                            downloadedAt = Instant.now().toString(),
                        )
                        navigateTo(navController, AppDestination.Music.route)
                    }
                }
            }
        }

        // Errors → snackbar.
        LaunchedEffect(Unit) {
            AppState.error.collect { e -> snackbarHostState.showSnackbar(e.message) }
        }

        // Fallback ask → global dialog.
        LaunchedEffect(Unit) {
            AppState.fallback.collect { ask -> fallbackAsk = ask }
        }

        val progress by AppState.progress.collectAsState()

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar {
                    AppDestination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = { navigateTo(navController, dest.route) },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { (progress!!.percent / 100.0).toFloat() },
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    )
                }
                AppNavHost(navController)
            }
        }

        // Bottom sheet for tap (default action "ask").
        sheetVideoId?.let { id ->
            DownloadActionSheet(
                onDismiss = { sheetVideoId = null },
                onVideo = {
                    DownloadManager.request(id, DownloadKind.VIDEO)
                    navigateTo(navController, AppDestination.Player.route)
                    sheetVideoId = null
                },
                onMp3 = {
                    DownloadManager.request(id, DownloadKind.MP3)
                    navigateTo(navController, AppDestination.Music.route)
                    sheetVideoId = null
                },
            )
        }

        // Global quality-fallback dialog.
        fallbackAsk?.let { ask ->
            val preferred = if (ask.preferred == "best") "Best" else "${ask.preferred}p"
            AlertDialog(
                onDismissRequest = {
                    DownloadManager.cancelFallback(ask.videoId)
                    fallbackAsk = null
                },
                title = { Text("Quality not available") },
                text = {
                    Column {
                        Text("$preferred isn't available for this video. Download another quality?")
                        Spacer(Modifier.height(8.dp))
                        ask.options.forEach { opt ->
                            TextButton(
                                onClick = {
                                    DownloadManager.answerFallback(ask.videoId, opt.height)
                                    fallbackAsk = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Download ${opt.label}")
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = {
                        DownloadManager.cancelFallback(ask.videoId)
                        fallbackAsk = null
                    }) { Text("Cancel") }
                },
            )
        }
    }

    private fun navigateTo(navController: NavHostController, route: String) {
        if (navController.currentDestination?.route != route) {
            navController.navigate(route) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                )
            }
        }
    }
}
