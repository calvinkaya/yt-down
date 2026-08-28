package com.example.ytdownloader.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ytdownloader.bridge.InterceptorJs
import com.example.ytdownloader.bridge.VideoId
import com.example.ytdownloader.bridge.YouTubeBridge
import com.example.ytdownloader.data.AppState
import kotlinx.coroutines.launch

private const val YOUTUBE_URL = "https://m.youtube.com/"
private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 14; 14T Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

private class WebViewHolder {
    var webView: WebView? = null
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeScreen() {
    val holder = remember { WebViewHolder() }
    val bridge = remember { YouTubeBridge() }

    Column(Modifier.fillMaxSize()) {
        // Toolbar (port of the Electron YouTube toolbar: Home / Back / Reload).
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { holder.webView?.loadUrl(YOUTUBE_URL) }) {
                    Icon(Icons.Default.Home, contentDescription = "Home")
                }
                IconButton(onClick = { if (holder.webView?.canGoBack() == true) holder.webView?.goBack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                IconButton(onClick = { holder.webView?.reload() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload")
                }
            }
        }

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    holder.webView = this
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.userAgentString = MOBILE_UA
                    settings.setSupportMultipleWindows(false)

                    addJavascriptInterface(bridge, "AndroidBridge")

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url.toString()
                            val id = VideoId.extract(url)
                            if (id != null) {
                                emitTap(id)
                                return false // let the watch page load (registers the view)
                            }
                            // Non-YouTube link → open in the external browser.
                            if (!VideoId.isYoutubeHost(url)) {
                                runCatching {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                                return true
                            }
                            return false
                        }

                        // SPA / History-API fallback (port of did-navigate-in-page).
                        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            val id = VideoId.extract(url)
                            if (id != null) emitTap(id)
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            super.onPageFinished(view, url)
                            view.evaluateJavascript(InterceptorJs.SCRIPT, null)
                        }
                    }

                    loadUrl(YOUTUBE_URL)
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

private fun emitTap(videoId: String) {
    AppState.scope.launch { AppState.videoTap.emit(videoId) }
}
