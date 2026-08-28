package com.example.ytdownloader.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.ytdownloader.data.AppState
import com.example.ytdownloader.data.DownloadKind
import com.example.ytdownloader.data.Store
import com.example.ytdownloader.download.DownloadManager
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nowPlaying by AppState.nowPlayingVideo.collectAsState()
    val progress by AppState.progress.collectAsState()

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    // Play the selected video; also keep the file open for deletion bookkeeping.
    LaunchedEffect(nowPlaying?.filePath) {
        nowPlaying?.let { entry ->
            exoPlayer.setMediaItem(MediaItem.fromUri(entry.filePath))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    // Auto-delete-after-watch + cleanup.
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val entry = AppState.nowPlayingVideo.value ?: return
                    if (Store.getSettings().autoDeleteAfterWatch) {
                        exoPlayer.stop()
                        exoPlayer.clearMediaItems()
                        AppState.nowPlayingVideo.value = null
                        scope.launch { DownloadManager.deleteVideo(entry.videoId) }
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (nowPlaying == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (progress != null && progress!!.kind == DownloadKind.VIDEO) {
                        Text("Downloading… ${progress!!.percent.toInt()}%")
                    } else {
                        Text("No video playing", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "Tap a video on the YouTube tab.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            val entry = nowPlaying!!
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer } },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text(entry.title.ifBlank { entry.videoId }, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${entry.height}p",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row {
                    Button(
                        onClick = {
                            exoPlayer.stop()
                            exoPlayer.clearMediaItems()
                            AppState.nowPlayingVideo.value = null
                            scope.launch { DownloadManager.deleteVideo(entry.videoId) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                        Spacer(Modifier.width(8.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }
}
