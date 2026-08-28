package com.example.ytdownloader

import android.app.Application
import android.util.Log
import android.widget.Toast
import com.example.ytdownloader.data.AppState
import com.example.ytdownloader.data.Store
import com.example.ytdownloader.download.DownloadManager
import com.yausername.youtubedl_android.YoutubeDL

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        DownloadManager.init(this)
        // Extract + init the bundled yt-dlp binary (and native libs). If this
        // fails, surface the real error so it's visible instead of the generic
        // "instance not initialised" that downloads would otherwise show.
        try {
            YoutubeDL.getInstance().init(this)
            AppState.initError.value = null
        } catch (e: Exception) {
            val msg = "Downloader failed to start: ${e.message ?: e.javaClass.simpleName}"
            Log.e("YTDownloader", msg, e)
            AppState.initError.value = msg
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            AppState.downloaderReady.complete(Unit) // don't hang downloads
            Store.refreshLibraries()
            return
        }
        Store.refreshLibraries()
        // The bundled yt-dlp is too old (lacks --js-runtimes), so update it in the
        // background to a version the library's launcher actually supports.
        Thread {
            try {
                YoutubeDL.getInstance().updateYoutubeDL(this)
                Log.i("YTDownloader", "yt-dlp updated")
            } catch (e: Exception) {
                Log.e("YTDownloader", "yt-dlp update failed", e)
            } finally {
                AppState.downloaderReady.complete(Unit)
            }
        }.start()
    }
}
