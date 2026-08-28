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
        }
        Store.refreshLibraries()
    }
}
