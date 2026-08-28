package com.example.ytdownloader

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.ytdownloader.data.AppState
import com.example.ytdownloader.data.Store
import com.example.ytdownloader.download.DownloadManager
import com.yausername.youtubedl_android.YoutubeDL

class App : Application() {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        DownloadManager.init(this)
        Store.refreshLibraries()
        // Init the bundled yt-dlp + ffmpeg binaries, then update yt-dlp in the
        // background. Binary extraction can take a few seconds, so keep it off the
        // main thread. Downloads await `downloaderReady`; a failure here is
        // surfaced (instead of the generic "instance not initialised") on download.
        Thread {
            try {
                YoutubeDL.getInstance().init(this)
                // REQUIRED: unpacks ffmpeg and tells yt-dlp where it is. Without
                // this, bestvideo+bestaudio merges and MP3 encode (`-x`) both fail.
                YoutubeDL.getInstance().initFFmpeg(this)
                AppState.initError.value = null
            } catch (e: Exception) {
                val msg = "Downloader failed to start: ${e.message ?: e.javaClass.simpleName}"
                Log.e("YTDownloader", msg, e)
                AppState.initError.value = msg
                mainHandler.post { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
                AppState.downloaderReady.complete(Unit) // don't hang downloads
                return@Thread
            }
            // The bundled yt-dlp is too old (lacks --js-runtimes), so update it in
            // the background to a version the library's launcher actually supports.
            try {
                YoutubeDL.updateYoutubeDL(this)
                Log.i("YTDownloader", "yt-dlp updated")
            } catch (e: Exception) {
                Log.e("YTDownloader", "yt-dlp update failed", e)
            } finally {
                AppState.downloaderReady.complete(Unit)
            }
        }.start()
    }
}
