package com.dccbigfred.android.ui.webview

import android.webkit.JavascriptInterface

/**
 * JS bridge exposed as `window.BigFredAndroid`. Methods run on the
 * binder thread — callers must post to the main thread before touching UI.
 */
class BigFredJsBridge(
    private val onOpenModelPicker: () -> Unit,
) {
    @JavascriptInterface
    fun openModelPicker() {
        onOpenModelPicker()
    }
}
