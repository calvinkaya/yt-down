package com.example.ytdownloader.download

import android.content.Context
import com.example.ytdownloader.data.AppState
import com.example.ytdownloader.data.DoneEvent
import com.example.ytdownloader.data.DownloadKind
import com.example.ytdownloader.data.DownloadProgress
import com.example.ytdownloader.data.ErrorEvent
import com.example.ytdownloader.data.FallbackAsk
import com.example.ytdownloader.data.FallbackOption
import com.example.ytdownloader.data.Mp3Entry
import com.example.ytdownloader.data.Store
import com.example.ytdownloader.data.VideoEntry
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Instant
import kotlin.math.abs

/**
 * Port of ytproject/src/main/downloader.js: serial queue, dedup, format
 * discovery, smart quality fallback, progress parse, delete-with-retry — plus
 * the new MP3 flow. Uses youtubedl-android (YoutubeDL) instead of spawning
 * yt-dlp/ffmpeg binaries.
 */
object DownloadManager {

    private data class Request(val videoId: String, val kind: DownloadKind)

    private val queue = Channel<Request>(Channel.UNLIMITED)
    private val inFlight = mutableSetOf<String>()
    private val pendingAsks = mutableMapOf<String, CompletableDeferred<Int?>>() // null = cancel

    @Volatile
    private var activeProcessId: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        scope.launch { processLoop() }
    }

    private fun watchUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"
    private fun processId(videoId: String, kind: DownloadKind) = "$kind:$videoId"
    private fun key(videoId: String, kind: DownloadKind) = "$kind:$videoId"

    fun request(videoId: String, kind: DownloadKind) {
        if (videoId.isBlank()) return
        val k = key(videoId, kind)
        synchronized(inFlight) {
            if (inFlight.contains(k)) return
            inFlight.add(k)
        }
        queue.trySend(Request(videoId, kind))
    }

    fun answerFallback(videoId: String, height: Int) {
        pendingAsks.remove(videoId)?.complete(height)
    }

    fun cancelFallback(videoId: String) {
        pendingAsks.remove(videoId)?.complete(null)
    }

    suspend fun deleteVideo(videoId: String): String? = withContext(Dispatchers.IO) {
        cancelIfActive(videoId, DownloadKind.VIDEO)
        val entry = Store.findVideo(videoId)
        if (entry != null) {
            Store.removeVideo(videoId)
            Store.refreshLibraries()
            val f = File(entry.filePath)
            if (f.exists()) {
                repeat(10) { i ->
                    if (f.delete()) return@withContext null
                    if (i == 9) return@withContext "Could not delete file (it may be in use)."
                    delay(300)
                }
            }
        }
        null
    }

    suspend fun deleteMp3(videoId: String): String? = withContext(Dispatchers.IO) {
        cancelIfActive(videoId, DownloadKind.MP3)
        val entry = Store.findMp3(videoId)
        if (entry != null) {
            Store.removeMp3(videoId)
            Store.refreshLibraries()
            val f = File(entry.filePath)
            if (f.exists()) {
                repeat(10) { i ->
                    if (f.delete()) return@withContext null
                    if (i == 9) return@withContext "Could not delete file (it may be in use)."
                    delay(300)
                }
            }
        }
        null
    }

    private fun cancelIfActive(videoId: String, kind: DownloadKind) {
        val pid = processId(videoId, kind)
        if (activeProcessId == pid) {
            runCatching { YoutubeDL.getInstance().destroyProcessById(pid) }
        }
    }

    // ---------------------------------------------------------------- queue

    private suspend fun processLoop() {
        for (req in queue) {
            try {
                when (req.kind) {
                    DownloadKind.VIDEO -> openVideoFlow(req.videoId)
                    DownloadKind.MP3 -> openMp3Flow(req.videoId)
                }
            } catch (e: Exception) {
                AppState.error.emit(ErrorEvent(req.videoId, e.message ?: "Download failed."))
            } finally {
                val empty: Boolean
                synchronized(inFlight) {
                    inFlight.remove(key(req.videoId, req.kind))
                    empty = inFlight.isEmpty()
                }
                AppState.progress.value = null
                if (empty) DownloadService.stop(Store.getContext())
            }
        }
    }

    // ------------------------------------------------------------ video flow

    private suspend fun openVideoFlow(videoId: String) {
        val settings = Store.getSettings()

        // §7.2 step 1 — dedup: play instantly if entry exists AND file is on disk.
        val entry = Store.findVideo(videoId)
        if (entry != null && File(entry.filePath).exists()) {
            AppState.done.emit(
                DoneEvent(
                    videoId = videoId,
                    filePath = entry.filePath,
                    title = entry.title,
                    height = entry.height,
                    cached = true,
                    kind = DownloadKind.VIDEO,
                )
            )
            return
        }

        // §7.2 step 2 — discover formats.
        val info = try {
            fetchInfo(videoId)
        } catch (e: Exception) {
            AppState.error.emit(ErrorEvent(videoId, e.message ?: "Could not fetch video info."))
            return
        }

        val available = buildHeights(info)
        if (available.isEmpty()) {
            AppState.error.emit(ErrorEvent(videoId, "No playable video formats found for this video."))
            return
        }

        // §7.2 step 3 — resolve target height.
        val H = settings.preferredHeight
        val decision = FallbackResolver.resolveTarget(H, available)
        val target: Int = if (decision.ask != null) {
            val answer = askUser(videoId, H, decision.ask)
            if (answer == null) {
                AppState.error.emit(ErrorEvent(videoId, "Download cancelled — quality not confirmed."))
                return
            }
            answer
        } else {
            decision.target ?: run {
                AppState.error.emit(ErrorEvent(videoId, "No matching video quality found."))
                return
            }
        }

        // §7.3 — download.
        val dir = File(settings.downloadDir).apply { mkdirs() }
        DownloadService.start(Store.getContext())
        try {
            runDownload(videoId, target, dir) { pct ->
                AppState.progress.value = DownloadProgress(videoId, pct, DownloadKind.VIDEO)
            }
        } catch (e: Exception) {
            cleanupPartials(dir, videoId)
            AppState.error.emit(ErrorEvent(videoId, e.message ?: "Download failed."))
            return
        }

        // Finalize: locate the produced file and record the entry.
        val finalFile = findOutput(dir, videoId)
        if (finalFile == null) {
            cleanupPartials(dir, videoId)
            AppState.error.emit(ErrorEvent(videoId, "Download finished but no output file was found."))
            return
        }
        val title = info.optString("title", "").ifBlank { videoId }
        val newEntry = VideoEntry(videoId, title, finalFile.absolutePath, target, Instant.now().toString())
        Store.upsertVideo(newEntry)
        Store.refreshLibraries()
        AppState.done.emit(
            DoneEvent(
                videoId = videoId,
                filePath = finalFile.absolutePath,
                title = title,
                height = target,
                cached = false,
                kind = DownloadKind.VIDEO,
            )
        )
    }

    // -------------------------------------------------------------- mp3 flow

    private suspend fun openMp3Flow(videoId: String) {
        val settings = Store.getSettings()

        // Dedup in mp3Library.json (independent of the video library).
        val entry = Store.findMp3(videoId)
        if (entry != null && File(entry.filePath).exists()) {
            AppState.done.emit(
                DoneEvent(
                    videoId = videoId,
                    filePath = entry.filePath,
                    title = entry.title,
                    cached = true,
                    kind = DownloadKind.MP3,
                    artist = entry.artist,
                    bitrateKbps = entry.bitrateKbps,
                    durationSec = entry.durationSec,
                )
            )
            return
        }

        val info = try {
            fetchInfo(videoId)
        } catch (e: Exception) {
            AppState.error.emit(ErrorEvent(videoId, e.message ?: "Could not fetch video info."))
            return
        }
        val title = info.optString("title", "").ifBlank { videoId }
        val artist = info.optString("uploader", "")
            .ifBlank { info.optString("channel", "") }
            .ifBlank { info.optString("artist", "") }
        val duration = info.optInt("duration", 0)

        val dir = File(settings.mp3Dir).apply { mkdirs() }
        DownloadService.start(Store.getContext())

        val request = Mp3Converter.buildRequest(watchUrl(videoId), dir.absolutePath, videoId, settings.mp3BitrateKbps)
        request.addOption("--progress-template", "download:PROGRESS:%(progress.downloaded_bytes)s:%(progress.total_bytes)s")

        val result = try {
            runRequest(request, processId(videoId, DownloadKind.MP3)) { pct ->
                AppState.progress.value = DownloadProgress(videoId, pct, DownloadKind.MP3)
            }
        } catch (e: Exception) {
            cleanupPartials(dir, videoId)
            AppState.error.emit(ErrorEvent(videoId, e.message ?: "Download failed."))
            return
        }
        if (result.exitCode != 0) {
            cleanupPartials(dir, videoId)
            AppState.error.emit(ErrorEvent(videoId, friendlyError("Download failed", result.err)))
            return
        }

        val finalFile = findOutput(dir, videoId)
        if (finalFile == null) {
            AppState.error.emit(ErrorEvent(videoId, "Download finished but no MP3 file was found."))
            return
        }

        val mp3Entry = Mp3Entry(
            videoId = videoId,
            title = title,
            artist = artist,
            filePath = finalFile.absolutePath,
            bitrateKbps = settings.mp3BitrateKbps,
            durationSec = duration,
            downloadedAt = Instant.now().toString(),
        )
        Store.upsertMp3(mp3Entry)
        Store.refreshLibraries()
        AppState.done.emit(
            DoneEvent(
                videoId = videoId,
                filePath = finalFile.absolutePath,
                title = title,
                cached = false,
                kind = DownloadKind.MP3,
                artist = artist,
                bitrateKbps = settings.mp3BitrateKbps,
                durationSec = duration,
            )
        )
    }

    // ------------------------------------------------------------- ask/answer

    private suspend fun askUser(videoId: String, preferred: String, heights: List<Int>): Int? {
        val deferred = CompletableDeferred<Int?>()
        pendingAsks[videoId] = deferred
        val preferredNum = preferred.toIntOrNull() ?: 0
        AppState.fallback.emit(
            FallbackAsk(
                videoId = videoId,
                preferred = preferred,
                options = heights.map { h ->
                    FallbackOption(h, abs(preferredNum - h), "${h}p")
                },
            )
        )
        return deferred.await()
    }

    // -------------------------------------------------------- yt-dlp helpers

    private fun fetchInfo(videoId: String): JSONObject {
        val request = YoutubeDLRequest(watchUrl(videoId))
        request.addOption("--no-playlist")
        request.addOption("--no-warnings")
        request.addOption("-J") // dump single JSON
        val response = YoutubeDL.getInstance().execute(request)
        if (response.exitCode != 0) {
            throw RuntimeException(friendlyError("Could not fetch video info", response.err))
        }
        return JSONObject(response.out)
    }

    private fun buildHeights(info: JSONObject): List<Int> {
        val out = linkedSetOf<Int>()
        val formats = info.optJSONArray("formats") ?: return emptyList()
        for (i in 0 until formats.length()) {
            val f = formats.optJSONObject(i) ?: continue
            val vcodec = f.optString("vcodec", "")
            val height = f.optInt("height", 0)
            if (vcodec.isNotEmpty() && vcodec != "none" && height > 0) out.add(height)
        }
        return out.toList()
    }

    private fun runDownload(videoId: String, target: Int, dir: File, onProgress: (Double) -> Unit) {
        // §7.3 command — exact format string from the plan. ExoPlayer plays the
        // produced mp4/webm/mkv natively, so no separate ffmpeg remux is needed.
        val format = "bestvideo[height<=$target]+bestaudio/best[height<=$target]/best"
        val request = YoutubeDLRequest(watchUrl(videoId))
        request.addOption("-f", format)
        request.addOption("-o", File(dir, "$videoId.%(ext)s").absolutePath)
        request.addOption("--no-playlist")
        request.addOption("--newline")
        request.addOption("--no-warnings")
        request.addOption("--progress-template", "download:PROGRESS:%(progress.downloaded_bytes)s:%(progress.total_bytes)s")

        val result = runRequest(request, processId(videoId, DownloadKind.VIDEO), onProgress)
        if (result.exitCode != 0) {
            throw RuntimeException(friendlyError("Download failed", result.err))
        }
    }

    private fun runRequest(
        request: YoutubeDLRequest,
        processId: String,
        onProgress: (Double) -> Unit,
    ): YoutubeDLResponse {
        activeProcessId = processId
        try {
            return YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, line ->
                val pct = ProgressParser.parse(line) ?: progress.toDouble()
                onProgress(pct)
            }
        } finally {
            activeProcessId = null
        }
    }

    private fun friendlyError(prefix: String, stderr: String): String {
        val lines = stderr
            .split(Regex("""\r?\n"""))
            .map { it.replace(Regex("""\u001b\[[0-9;]*m"""), "").trim() }
            .filter { it.isNotEmpty() }
        val tail = lines.takeLast(8).joinToString(" ").ifBlank { "yt-dlp exited with an error." }
        val msg = "$prefix: $tail"
        return if (msg.length > 600) msg.take(597) + "…" else msg
    }

    // --------------------------------------------------------- file helpers

    private fun cleanupPartials(dir: File, videoId: String) {
        runCatching {
            dir.listFiles()
                ?.filter { it.name.startsWith("$videoId.") }
                ?.forEach { it.delete() }
        }
    }

    private val PARTIAL_EXT = Regex("""\.(part|ytdl|temp|tmp|json|f\d+)$""", RegexOption.IGNORE_CASE)

    private fun findOutput(dir: File, videoId: String): File? =
        dir.listFiles()
            ?.filter { it.name.startsWith("$videoId.") }
            ?.filter { !PARTIAL_EXT.containsMatchIn(it.name) }
            ?.filter { it.isFile }
            ?.maxByOrNull { it.lastModified() }
}
