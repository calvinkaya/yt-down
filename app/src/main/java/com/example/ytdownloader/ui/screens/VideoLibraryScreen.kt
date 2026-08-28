package com.example.ytdownloader.ui.screens

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ytdownloader.data.AppState
import com.example.ytdownloader.data.DownloadKind
import com.example.ytdownloader.download.DownloadManager
import kotlinx.coroutines.launch

@Composable
fun VideoLibraryScreen() {
    val entries by AppState.videoLib.collectAsState()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }

    val sorted = entries.sortedByDescending { it.downloadedAt }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Videos", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        if (sorted.isEmpty()) {
            Text(
                "No videos downloaded yet. Tap a video on the YouTube tab.",
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
                                "${entry.height}p · ${formatDate(entry.downloadedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row {
                                Button(onClick = {
                                    // Dedup → cached play (or re-download if the file is gone).
                                    DownloadManager.request(entry.videoId, DownloadKind.VIDEO)
                                }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                    Spacer(Modifier.width(4.dp))
                                    Text("Play")
                                }
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        scope.launch { error = DownloadManager.deleteVideo(entry.videoId) }
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    Spacer(Modifier.width(4.dp))
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
