package com.example.ytdownloader.bridge

import android.webkit.JavascriptInterface
import com.example.ytdownloader.data.AppState
import kotlinx.coroutines.launch

/**
 * @JavascriptInterface bridge exposed to the YouTube WebView as `AndroidBridge`.
 * The injected JS (InterceptorJs) calls `AndroidBridge.onVideoTap(videoId)`.
 *
 * NOTE: @JavascriptInterface methods run on a non-UI thread, so we post to the
 * main dispatcher before touching StateFlow/UI (ANDROID_PORT_PROMPT §7.1).
 */
class YouTubeBridge {

    @JavascriptInterface
    fun onVideoTap(videoId: String?) {
        val id = videoId?.trim().takeIf { !it.isNullOrEmpty() } ?: return
        AppState.scope.launch { AppState.videoTap.emit(id) }
    }
}
