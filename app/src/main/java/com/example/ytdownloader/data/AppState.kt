package com.example.ytdownloader.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The "IPC" equivalent — a single event bus replacing Electron's
 * webContents.send / ipcRenderer. Screens collect these flows.
 */
object AppState {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val progress = MutableStateFlow<DownloadProgress?>(null)
    val done = MutableSharedFlow<DoneEvent>(extraBufferCapacity = 16)
    val error = MutableSharedFlow<ErrorEvent>(extraBufferCapacity = 16)
    val fallback = MutableSharedFlow<FallbackAsk>(extraBufferCapacity = 4)

    val videoLib = MutableStateFlow<List<VideoEntry>>(emptyList())
    val mp3Lib = MutableStateFlow<List<Mp3Entry>>(emptyList())

    // Video taps from the WebView bridge (videoId). Collected by MainActivity.
    val videoTap = MutableSharedFlow<String>(extraBufferCapacity = 16)

    val nowPlayingVideo = MutableStateFlow<VideoEntry?>(null)
    val nowPlayingMp3 = MutableStateFlow<Mp3Entry?>(null)
}
