package com.example.ytdownloader.data

/** What kind of download a request targets. */
enum class DownloadKind { VIDEO, MP3 }

data class VideoEntry(
    val videoId: String,
    val title: String,
    val filePath: String,
    val height: Int,
    val downloadedAt: String,
)

data class Mp3Entry(
    val videoId: String,
    val title: String,
    val artist: String,
    val filePath: String,
    val bitrateKbps: Int,
    val durationSec: Int,
    val downloadedAt: String,
)

data class DownloadProgress(
    val videoId: String,
    val percent: Double,
    val kind: DownloadKind = DownloadKind.VIDEO,
)

data class DoneEvent(
    val videoId: String,
    val filePath: String,
    val title: String,
    val height: Int? = null,
    val cached: Boolean = false,
    val kind: DownloadKind = DownloadKind.VIDEO,
    val artist: String? = null,
    val bitrateKbps: Int? = null,
    val durationSec: Int? = null,
)

data class ErrorEvent(
    val videoId: String,
    val message: String,
)

data class FallbackAsk(
    val videoId: String,
    val preferred: String,
    val options: List<FallbackOption>,
)

data class FallbackOption(
    val height: Int,
    val distance: Int,
    val label: String,
)

data class Settings(
    val preferredHeight: String = "2160", // "best" or "144".."4320"
    val downloadDir: String = "",
    val mp3Dir: String = "",
    val autoDeleteAfterWatch: Boolean = false,
    val pictureInPicture: Boolean = true,
    val mp3BitrateKbps: Int = 192,        // 320 | 192 | 128
    val defaultAction: String = "ask",    // "video" | "mp3" | "ask"
)
