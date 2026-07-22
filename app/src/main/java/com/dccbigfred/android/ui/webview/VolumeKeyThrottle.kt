package com.dccbigfred.android.ui.webview

import android.view.KeyEvent

/** Pure volume key handling; testable without a device KeyEvent instance. */
fun handleVolumeKey(
    keyCode: Int,
    action: Int,
    onHardwareKey: (direction: Int) -> Unit,
): Boolean {
    if (keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
        keyCode != KeyEvent.KEYCODE_VOLUME_DOWN
    ) {
        return false
    }
    if (action == KeyEvent.ACTION_DOWN) {
        val direction = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1
        onHardwareKey(direction)
    }
    return true
}

/** Activity/WebView entry point for real [KeyEvent] instances. */
fun handleVolumeKeyEvent(
    event: KeyEvent,
    onHardwareKey: (direction: Int) -> Unit,
): Boolean = handleVolumeKey(event.keyCode, event.action, onHardwareKey)

fun throttleHardwareKeysJavascript(direction: Int): String =
    "(function(d){var n=window.__bigfredThrottleHardwareKeys;" +
        "if(typeof n==='function'){n(d);}})($direction);"
