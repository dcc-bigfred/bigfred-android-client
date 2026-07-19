package com.dccbigfred.android.wifi

import android.content.Context
import android.net.wifi.WifiManager
import androidx.core.content.getSystemService

/**
 * Holds a [WifiManager.WIFI_MODE_FULL_LOW_LATENCY] lock while the app is
 * showing BigFred WebView or the connection status screen (foreground + screen on).
 */
class LowLatencyWifiLock(context: Context) {
    private val appContext = context.applicationContext
    private var lock: WifiManager.WifiLock? = null

    fun acquire() {
        if (lock?.isHeld == true) return
        val wifi = appContext.getSystemService<WifiManager>() ?: return
        val wifiLock = wifi.createWifiLock(
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
            TAG,
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
        lock = wifiLock
    }

    fun release() {
        lock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        lock = null
    }

    companion object {
        private const val TAG = "bigfred-webview"
    }
}
