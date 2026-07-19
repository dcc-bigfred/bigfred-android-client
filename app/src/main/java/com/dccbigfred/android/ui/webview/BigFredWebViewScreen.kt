package com.dccbigfred.android.ui.webview

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Hosts the BigFred SPA. Timers and the WebSocket keepalive (2s ping) must keep
 * running while this screen is shown, otherwise the dcc-bus dead-man (~6s)
 * emergency-stops moving locos.
 *
 * We wire [WebView.onResume]/[WebView.resumeTimers] to [Lifecycle.Event.ON_RESUME]
 * and only pause on [Lifecycle.Event.ON_STOP], so a brief loss of window focus
 * (notification shade, drawer) does not freeze JS timers.
 *
 * Chromium throttles `setInterval` on page *visibility*, not input focus. A
 * Compose overlay / drawer scrim can flip window visibility to INVISIBLE and
 * stall keepalive — [KeepAliveWebView] reports VISIBLE while the Activity is
 * resumed so the page stays `document.visibilityState === "visible"`.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BigFredWebViewScreen(
    baseUrl: String,
) {
    // Freeze the URL for this WebView session so DataStore / nav recompositions
    // with the same address do not trigger a reload (which would churn the WS).
    val sessionUrl = remember(baseUrl) { baseUrl.trimEnd('/') + "/" }
    var webView by remember { mutableStateOf<KeepAliveWebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    // Pair WebView with Activity lifecycle so Chromium timers stay alive in
    // the foreground and resume after backgrounding.
    DisposableEffect(lifecycleOwner, webView) {
        val view = webView ?: return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    view.keepWindowVisible = true
                    view.onResume()
                    // Affects all WebViews in the process; required after pauseTimers.
                    view.resumeTimers()
                    view.requestFocus()
                }
                Lifecycle.Event.ON_STOP -> {
                    // Pause only when the Activity is fully stopped — not on
                    // ON_PAUSE (shade / dialogs) so keepalive pings keep flowing.
                    view.keepWindowVisible = false
                    view.onPause()
                    view.pauseTimers()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // If we attach while already RESUMED, kick timers immediately.
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            view.keepWindowVisible = true
            view.onResume()
            view.resumeTimers()
            view.requestFocus()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                keepWindowVisible = false
                stopLoading()
                // Ensure timers are not left paused for other WebViews after destroy.
                resumeTimers()
                destroy()
            }
            webView = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                KeepAliveWebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    keepScreenOn = true
                    // Stay classified as a visible, focusable surface so Chromium
                    // does not intensive-throttle setInterval while the page is up.
                    visibility = View.VISIBLE
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        cacheMode = WebSettings.LOAD_DEFAULT
                        setSupportMultipleWindows(false)
                        javaScriptCanOpenWindowsAutomatically = false
                        // Keep rasterizing when briefly covered (drawer scrim /
                        // Compose overlays) so timers are less likely to throttle.
                        setOffscreenPreRaster(true)
                    }
                    isFocusable = true
                    isFocusableInTouchMode = true
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            // Keep navigation inside the WebView (local BigFred SPA).
                            return false
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            loading = false
                            canGoBack = view.canGoBack()
                            view.requestFocus()
                        }

                        override fun onPageStarted(
                            view: WebView,
                            url: String?,
                            favicon: android.graphics.Bitmap?,
                        ) {
                            loading = true
                        }

                        override fun doUpdateVisitedHistory(
                            view: WebView,
                            url: String?,
                            isReload: Boolean,
                        ) {
                            canGoBack = view.canGoBack()
                        }
                    }
                    // Tag before load so the first update{} pass does not reload.
                    tag = sessionUrl
                    loadUrl(sessionUrl)
                    webView = this
                    requestFocus()
                }
            },
            update = { view ->
                // Reload only when the user actually picks a different hub URL.
                if (view.tag != sessionUrl) {
                    view.tag = sessionUrl
                    view.loadUrl(sessionUrl)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

/**
 * WebView that reports [View.VISIBLE] window visibility while [keepWindowVisible]
 * is true, so Chromium does not mark the page hidden when a drawer scrim or
 * other overlay briefly covers it. Real GONE (Activity stopped) is still
 * forwarded once [keepWindowVisible] is cleared.
 */
class KeepAliveWebView(context: Context) : WebView(context) {
    var keepWindowVisible: Boolean = true

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (keepWindowVisible && visibility != View.VISIBLE) {
            super.onWindowVisibilityChanged(View.VISIBLE)
            return
        }
        super.onWindowVisibilityChanged(visibility)
    }
}
