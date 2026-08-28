package com.example.ytdownloader

import android.app.Application
import android.util.Log
import com.example.ytdownloader.data.Store
import com.example.ytdownloader.download.DownloadManager
import com.yausername.youtubedl_android.YoutubeDL

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        DownloadManager.init(this)
        // Extract + init the bundled yt-dlp binary and ffmpeg. If this fails
        // (e.g. ABI/asset issue) the app still boots; downloads surface the error.
        try {
            YoutubeDL.getInstance().init(this)
            YoutubeDL.getInstance().initFFmpeg(this)
        } catch (e: Exception) {
            Log.e("YTDownloader", "Failed to init YoutubeDL/ffmpeg", e)
        }
        Store.refreshLibraries()
    }
}
