package com.example.ytdownloader.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Settings live in SharedPreferences; the two libraries live in JSON files in
 * filesDir (videoLibrary.json, mp3Library.json). Writes are atomic (temp + move),
 * mirroring ytproject/src/main/store.js.
 */
object Store {
    private const val PREFS = "settings"
    private const val VIDEO_LIBRARY = "videoLibrary.json"
    private const val MP3_LIBRARY = "mp3Library.json"

    val ALLOWED_HEIGHTS = intArrayOf(144, 240, 360, 480, 720, 1080, 1440, 2160, 4320)
    val ALLOWED_BITRATES = intArrayOf(128, 192, 320)
    val ALLOWED_ACTIONS = setOf("video", "mp3", "ask")

    @Volatile
    private var appContext: Context? = null
    private val ctx: Context get() = appContext!!
    private val prefs: SharedPreferences get() = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val videoLibFile: File get() = File(ctx.filesDir, VIDEO_LIBRARY)
    private val mp3LibFile: File get() = File(ctx.filesDir, MP3_LIBRARY)

    fun init(context: Context) {
        appContext = context.applicationContext
        if (!videoLibFile.exists()) writeAtomic(videoLibFile, JSONArray())
        if (!mp3LibFile.exists()) writeAtomic(mp3LibFile, JSONArray())
    }

    fun getContext(): Context = appContext!!

    // ---- defaults (app-specific external storage: zero permissions) ----
    fun defaultDownloadDir(context: Context): String =
        File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir, "YTDownloader").absolutePath

    fun defaultMp3Dir(context: Context): String =
        File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir, "YTDownloader").absolutePath

    // ---- settings ----
    fun getSettings(): Settings {
        val c = appContext ?: return Settings()
        val defDir = defaultDownloadDir(c)
        val defMp3 = defaultMp3Dir(c)

        val h = prefs.getString("preferredHeight", "2160") ?: "2160"
        val hInt = h.toIntOrNull()
        val normalizedH = if (h == "best" || (hInt != null && hInt in ALLOWED_HEIGHTS)) h else "2160"
        val bitrate = prefs.getInt("mp3BitrateKbps", 192).let { if (it in ALLOWED_BITRATES) it else 192 }
        val action = prefs.getString("defaultAction", "ask").orEmpty().let { if (it in ALLOWED_ACTIONS) it else "ask" }

        return Settings(
            preferredHeight = normalizedH,
            downloadDir = prefs.getString("downloadDir", null)?.takeIf { it.isNotBlank() } ?: defDir,
            mp3Dir = prefs.getString("mp3Dir", null)?.takeIf { it.isNotBlank() } ?: defMp3,
            autoDeleteAfterWatch = prefs.getBoolean("autoDeleteAfterWatch", false),
            pictureInPicture = prefs.getBoolean("pictureInPicture", true),
            mp3BitrateKbps = bitrate,
            defaultAction = action,
        )
    }

    fun setSettings(s: Settings): Settings {
        val c = appContext ?: return s
        val preferredInt = s.preferredHeight.toIntOrNull()
        require(s.preferredHeight == "best" || (preferredInt != null && preferredInt in ALLOWED_HEIGHTS)) {
            "Invalid preferred height: ${s.preferredHeight}"
        }
        require(s.downloadDir.isNotBlank()) { "Download directory must be a non-empty path" }
        require(s.mp3Dir.isNotBlank()) { "MP3 directory must be a non-empty path" }
        require(s.mp3BitrateKbps in ALLOWED_BITRATES) { "Invalid MP3 bitrate" }
        require(s.defaultAction in ALLOWED_ACTIONS) { "Invalid default action" }

        prefs.edit()
            .putString("preferredHeight", s.preferredHeight)
            .putString("downloadDir", s.downloadDir)
            .putString("mp3Dir", s.mp3Dir)
            .putBoolean("autoDeleteAfterWatch", s.autoDeleteAfterWatch)
            .putBoolean("pictureInPicture", s.pictureInPicture)
            .putInt("mp3BitrateKbps", s.mp3BitrateKbps)
            .putString("defaultAction", s.defaultAction)
            .apply()

        runCatching { File(s.downloadDir).mkdirs() }
        runCatching { File(s.mp3Dir).mkdirs() }
        return getSettings()
    }

    // ---- video library ----
    fun getVideoLibrary(): List<VideoEntry> = readVideoLibrary()

    fun findVideo(videoId: String): VideoEntry? =
        readVideoLibrary().firstOrNull { it.videoId == videoId }

    fun upsertVideo(entry: VideoEntry) {
        val list = readVideoLibrary().toMutableList()
        val i = list.indexOfFirst { it.videoId == entry.videoId }
        if (i >= 0) list[i] = entry else list.add(entry)
        writeVideoLibrary(list)
    }

    fun removeVideo(videoId: String) {
        writeVideoLibrary(readVideoLibrary().filter { it.videoId != videoId })
    }

    // ---- mp3 library ----
    fun getMp3Library(): List<Mp3Entry> = readMp3Library()

    fun findMp3(videoId: String): Mp3Entry? =
        readMp3Library().firstOrNull { it.videoId == videoId }

    fun upsertMp3(entry: Mp3Entry) {
        val list = readMp3Library().toMutableList()
        val i = list.indexOfFirst { it.videoId == entry.videoId }
        if (i >= 0) list[i] = entry else list.add(entry)
        writeMp3Library(list)
    }

    fun removeMp3(videoId: String) {
        writeMp3Library(readMp3Library().filter { it.videoId != videoId })
    }

    fun refreshLibraries() {
        AppState.videoLib.value = getVideoLibrary()
        AppState.mp3Lib.value = getMp3Library()
    }

    // ---- JSON I/O ----
    private fun readVideoLibrary(): List<VideoEntry> {
        val arr = readJsonArray(videoLibFile) ?: return emptyList()
        val out = mutableListOf<VideoEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                VideoEntry(
                    videoId = o.optString("videoId", ""),
                    title = o.optString("title", ""),
                    filePath = o.optString("filePath", ""),
                    height = o.optInt("height", 0),
                    downloadedAt = o.optString("downloadedAt", ""),
                )
            )
        }
        return out
    }

    private fun readMp3Library(): List<Mp3Entry> {
        val arr = readJsonArray(mp3LibFile) ?: return emptyList()
        val out = mutableListOf<Mp3Entry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                Mp3Entry(
                    videoId = o.optString("videoId", ""),
                    title = o.optString("title", ""),
                    artist = o.optString("artist", ""),
                    filePath = o.optString("filePath", ""),
                    bitrateKbps = o.optInt("bitrateKbps", 192),
                    durationSec = o.optInt("durationSec", 0),
                    downloadedAt = o.optString("downloadedAt", ""),
                )
            )
        }
        return out
    }

    private fun writeVideoLibrary(list: List<VideoEntry>) {
        val arr = JSONArray()
        for (e in list) {
            arr.put(
                JSONObject()
                    .put("videoId", e.videoId)
                    .put("title", e.title)
                    .put("filePath", e.filePath)
                    .put("height", e.height)
                    .put("downloadedAt", e.downloadedAt)
            )
        }
        writeAtomic(videoLibFile, arr)
    }

    private fun writeMp3Library(list: List<Mp3Entry>) {
        val arr = JSONArray()
        for (e in list) {
            arr.put(
                JSONObject()
                    .put("videoId", e.videoId)
                    .put("title", e.title)
                    .put("artist", e.artist)
                    .put("filePath", e.filePath)
                    .put("bitrateKbps", e.bitrateKbps)
                    .put("durationSec", e.durationSec)
                    .put("downloadedAt", e.downloadedAt)
            )
        }
        writeAtomic(mp3LibFile, arr)
    }

    private fun readJsonArray(file: File): JSONArray? = runCatching {
        JSONArray(file.readText())
    }.getOrNull()

    private fun writeAtomic(file: File, value: JSONArray) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(value.toString())
        if (file.exists()) file.delete()
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }
}
