package com.dccbigfred.android.ui.webview

import android.webkit.JavascriptInterface
import com.dccbigfred.android.locale.LocalePrefs

/**
 * JS bridge exposed as `window.BigFredNativeApp`. Methods run on the
 * binder thread — callers must post to the main thread before touching UI.
 */
class BigFredJsBridge(
    private val onOpenModelPicker: () -> Unit,
    private val onThrottleHardwareKeysActive: (Boolean) -> Unit = {},
) {
    @JavascriptInterface
    fun openModelPicker() {
        onOpenModelPicker()
    }

    @JavascriptInterface
    fun getPreferredLocale(): String {
        return LocalePrefs.resolvedWebLocale()
    }

    /**
     * Called by the SPA when a throttle surface mounts/unmounts so the shell
     * can claim volume keys only on Throttle / takeover (not other routes).
     */
    @JavascriptInterface
    fun setThrottleHardwareKeysActive(active: Boolean) {
        onThrottleHardwareKeysActive(active)
    }
}
