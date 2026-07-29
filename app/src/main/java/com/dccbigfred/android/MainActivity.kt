package com.dccbigfred.android

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dccbigfred.android.server.LocoServerService
import com.dccbigfred.android.ui.navigation.BigFredApp
import com.dccbigfred.android.ui.theme.BigFredTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : AppCompatActivity() {
    /**
     * When non-null and returns true, VOLUME_UP/DOWN are consumed (no system volume).
     * Set from Compose when the WebView session is live and the settings toggle is on.
     */
    var volumeKeyInterceptor: ((KeyEvent) -> Boolean)? = null

    private val openLocalWebViewChannel = Channel<Unit>(Channel.BUFFERED)
    val openLocalWebViewRequests = openLocalWebViewChannel.receiveAsFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        handleNotificationIntent(intent)
        setContent {
            BigFredTheme {
                BigFredApp(openLocalWebViewRequests = openLocalWebViewRequests)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(LocoServerService.EXTRA_OPEN_LOCAL_WEBVIEW, false) != true) {
            return
        }
        intent.removeExtra(LocoServerService.EXTRA_OPEN_LOCAL_WEBVIEW)
        openLocalWebViewChannel.trySend(Unit)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val interceptor = volumeKeyInterceptor
            if (interceptor != null && interceptor(event)) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
