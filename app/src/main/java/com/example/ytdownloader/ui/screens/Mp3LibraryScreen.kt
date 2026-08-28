package com.example.ytdownloader.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.ytdownloader.data.AppState
import com.example.ytdownloader.data.DownloadKind
import com.example.ytdownloader.data.Mp3Entry
import com.example.ytdownloader.download.DownloadManager
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun Mp3LibraryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by AppState.mp3Lib.collectAsState()
    val nowPlaying by AppState.nowPlayingMp3.collectAsState()

    val sorted = entries.sortedByDescending { it.downloadedAt }

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    LaunchedEffect(nowPlaying?.filePath) {
        if (nowPlaying != null) {
            exoPlayer.setMediaItem(MediaItem.fromUri(nowPlaying!!.filePath))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Music", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        nowPlaying?.let { np ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Now playing", style = MaterialTheme.typography.labelMedium)
                    Text(np.title.ifBlank { np.videoId }, style = MaterialTheme.typography.titleMedium)
                    Text(np.artist, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        IconButton(onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play/Pause")
                        }
                        IconButton(onClick = {
                            exoPlayer.stop()
                            exoPlayer.clearMediaItems()
                            AppState.nowPlayingMp3.value = null
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Stop")
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (sorted.isEmpty()) {
            Text(
                "No MP3s yet. Choose \"Download as MP3\" when tapping a video.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sorted, key = { it.videoId }) { entry ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                entry.title.ifBlank { entry.videoId },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                buildString {
                                    append(entry.artist)
                                    if (entry.bitrateKbps > 0) append(" · ${entry.bitrateKbps} kbps")
                                    append(" · ${formatDate(entry.downloadedAt)}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row {
                                Button(onClick = {
                                    // Dedup → cached play (or re-download if the file is gone).
                                    DownloadManager.request(entry.videoId, DownloadKind.MP3)
                                }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                    Spacer(Modifier.width(4.dp))
                                    Text("Play")
                                }
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = { shareMp3(context, entry) }) {
                                    Icon(Icons.Default.Share, contentDescription = "Share")
                                }
                                IconButton(onClick = { scope.launch { DownloadManager.deleteMp3(entry.videoId) } }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun shareMp3(context: Context, entry: Mp3Entry) {
    val file = File(entry.filePath)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/mpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Share MP3")) }
}
