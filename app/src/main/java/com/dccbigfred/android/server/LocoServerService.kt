package com.dccbigfred.android.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.dccbigfred.android.BigFredApplication
import com.dccbigfred.android.MainActivity
import com.dccbigfred.android.R
import com.dccbigfred.android.network.ServerProbe
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

/**
 * Foreground service hosting loco-server and its microinit-managed children for "BigFred on phone".
 */
class LocoServerService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var locoProcess: Process? = null
    private var watchdogThread: Thread? = null
    private var bootThread: Thread? = null
    /** True while a boot is in progress; gates concurrent ACTION_START deliveries. */
    private val booting = AtomicBoolean(false)
    /** True once boot() reached Running; cleared on stop or boot failure. */
    private val running = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopLocalServer(startId)
                return START_NOT_STICKY
            }
            ACTION_RESTART -> restartLocalServer()
            ACTION_START, null -> {
                if (!running.get()) {
                    startLocalServer()
                }
            }
        }
        return START_STICKY
    }

    private fun startLocalServer() {
        if (!booting.compareAndSet(false, true)) return
        _state.value = LocalServerState.Starting
        try {
            startForegroundNotification()
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            booting.set(false)
            _state.value = LocalServerState.Failed(e.message ?: e.toString())
            stopSelf()
            return
        }
        acquireWakeLock()
        launchBoot(restart = false)
    }

    private fun restartLocalServer() {
        if (!booting.compareAndSet(false, true)) return
        _state.value = LocalServerState.Starting
        try {
            startForegroundNotification()
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed during restart", e)
            booting.set(false)
            _state.value = LocalServerState.Failed(e.message ?: e.toString())
            stopSelf()
            return
        }
        acquireWakeLock()
        launchBoot(restart = true)
    }

    private fun launchBoot(restart: Boolean) {
        bootThread = thread(
            name = if (restart) "loco-server-restart" else "loco-server-boot",
            isDaemon = true,
        ) {
            try {
                if (restart) {
                    running.set(false)
                    watchdogThread?.interrupt()
                    watchdogThread = null
                    cleanupChildren()
                    ensureBootActive()
                }
                boot()
            } catch (e: InterruptedException) {
                Log.i(TAG, "boot interrupted, stopping")
                cleanupChildren()
            } catch (e: Exception) {
                Log.e(TAG, "local server start failed", e)
                cleanupChildren()
                releaseWakeLock()
                _state.value = LocalServerState.Failed(e.message ?: e.toString())
                stopSelf()
            } finally {
                if (bootThread === Thread.currentThread()) {
                    bootThread = null
                }
                booting.set(false)
            }
        }
    }

    override fun onDestroy() {
        cleanupChildren()
        releaseWakeLock()
        // Keep Failed visible so the UI can show the error after the service exits.
        if (_state.value !is LocalServerState.Failed) {
            _state.value = LocalServerState.Stopped
        }
        running.set(false)
        booting.set(false)
        bootThread?.interrupt()
        bootThread = null
        watchdogThread?.interrupt()
        watchdogThread = null
        super.onDestroy()
    }

    private fun boot() {
        _state.value = LocalServerState.Starting
        ensureBootActive()
        val paths = LocalServerPaths.from(this)
        paths.ensureDirs()

        ProcessOrphanReaper.reap(paths.locoServerPid, NativeBinaries.LOCO_SERVER)
        ProcessOrphanReaper.reap(paths.microinitPid, NativeBinaries.MICROINIT)
        ensureBootActive()

        if (ProcessOrphanReaper.isPortOpen("127.0.0.1", HTTP_PORT) &&
            ProcessOrphanReaper.readPid(paths.locoServerPid) == null
        ) {
            throw IllegalStateException(
                "Port $HTTP_PORT is already in use without a known loco-server pidfile",
            )
        }

        val locoBin = NativeBinaries.require(this, NativeBinaries.LOCO_SERVER)
        val valkeyBin = NativeBinaries.require(this, NativeBinaries.VALKEY)
        val microinitBin = NativeBinaries.require(this, NativeBinaries.MICROINIT)

        val prefs = (application as BigFredApplication).serverPreferences
        val jwt = runBlocking { prefs.getOrCreateLocalJwtSecret() }

        val env = HashMap(System.getenv())
        env["BIGFRED_DATA_DIR"] = paths.dataDir.absolutePath
        env["BIGFRED_JWT_SECRET"] = jwt
        LanPrefix.resolve()?.let { prefix ->
            env["BIGFRED_LAN_PREFIX"] = prefix
            Log.i(TAG, "BIGFRED_LAN_PREFIX=$prefix (for dcc-bus scan --lan-prefix)")
        }

        ensureBootActive()
        val locoLog = File(paths.logsDir, "loco-server.log")
        val locoArgs = mutableListOf(
            locoBin.absolutePath,
            "--http", "0.0.0.0:$HTTP_PORT",
            "--db", paths.dbFile.absolutePath,
            "--redis-bin", valkeyBin.absolutePath,
            "--redis-addr", "127.0.0.1:$REDIS_PORT",
            "--microinit-bin", microinitBin.absolutePath,
            "--microinit-socket", paths.microinitSocket.absolutePath,
            "--mdns=false",
        )

        locoProcess = ProcessBuilder(locoArgs)
            .directory(paths.dataDir)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(locoLog))
            .also { it.environment().clear(); it.environment().putAll(env) }
            .start()
        ensureBootActive()
        ProcessOrphanReaper.writePid(paths.locoServerPid, processPid(locoProcess!!))

        waitForHttpReady(45_000)
        // stop may have been requested while we were waiting for HTTP — bail
        // before flipping to Running / starting the watchdog, otherwise an
        // interrupted boot would resurrect the service after stop.
        if (Thread.currentThread().isInterrupted || !booting.get()) {
            throw InterruptedException("stopped during boot")
        }
        running.set(true)
        _state.value = LocalServerState.Running(LOCAL_BASE_URL)
        startWatchdog()
    }

    private fun ensureBootActive() {
        if (Thread.currentThread().isInterrupted || !booting.get()) {
            throw InterruptedException("local server boot cancelled")
        }
    }

    private fun waitForHttpReady(timeoutMs: Long) {
        val probe = ServerProbe()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val ok = runBlocking { probe.isReachable(LOCAL_BASE_URL) }
            if (ok) return
            Thread.sleep(300)
        }
        throw IllegalStateException("Timeout waiting for $LOCAL_BASE_URL")
    }

    private fun startWatchdog() {
        watchdogThread?.interrupt()
        watchdogThread = thread(name = "loco-server-watchdog", isDaemon = true) {
            while (running.get() && !Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(3_000)
                    val locoAlive = locoProcess?.isAlive == true
                    if (!locoAlive) {
                        Log.w(TAG, "loco-server died — restarting")
                        cleanupChildren()
                        running.set(false)
                        if (!booting.compareAndSet(false, true)) {
                            Log.w(TAG, "restart skipped — boot already in progress")
                            return@thread
                        }
                        try {
                            boot()
                        } finally {
                            booting.set(false)
                        }
                        return@thread
                    }
                } catch (_: InterruptedException) {
                    return@thread
                } catch (e: Exception) {
                    Log.e(TAG, "watchdog restart failed", e)
                    _state.value = LocalServerState.Failed(e.message ?: e.toString())
                    running.set(false)
                    booting.set(false)
                    stopSelf()
                    return@thread
                }
            }
        }
    }

    private fun stopLocalServer(startId: Int) {
        running.set(false)
        booting.set(false)
        bootThread?.interrupt()
        watchdogThread?.interrupt()
        watchdogThread = null
        _state.value = LocalServerState.Stopped
        thread(name = "loco-server-stop", isDaemon = true) {
            cleanupChildren()
            releaseWakeLock()
            stopSelf(startId)
        }
    }

    @Synchronized
    private fun cleanupChildren() {
        locoProcess?.let { proc ->
            try {
                proc.destroy()
                if (!proc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    proc.destroyForcibly()
                }
            } catch (_: Exception) {
            }
        }
        locoProcess = null
        try {
            val paths = LocalServerPaths.from(this)
            paths.locoServerPid.delete()
            paths.microinitPid.delete()
        } catch (_: Exception) {
        }
    }

    /** java.lang.Process.pid() is only in the public Android SDK from API 31+. */
    private fun processPid(process: Process): Long {
        return try {
            val method = Process::class.java.getMethod("pid")
            (method.invoke(process) as Long)
        } catch (_: Exception) {
            Regex("""pid[= ](\d+)""")
                .find(process.toString())
                ?.groupValues
                ?.getOrNull(1)
                ?.toLongOrNull()
                ?: -1L
        }
    }

    private fun startForegroundNotification() {
        ensureChannel()
        val openActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_LOCAL_WEBVIEW, true)
        }
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            openActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LocoServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.local_server_notification_title))
            .setContentText(getString(R.string.local_server_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.local_server_stop), stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.local_server_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        mgr.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bigfred:local-server").also {
            it.setReferenceCounted(false)
            it.acquire()
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    companion object {
        private const val TAG = "LocoServerService"
        private const val CHANNEL_ID = "bigfred_local_server"
        private const val NOTIFICATION_ID = 42
        const val HTTP_PORT = 8080
        const val REDIS_PORT = 6379
        const val LOCAL_BASE_URL = "http://127.0.0.1:$HTTP_PORT"
        const val EXTRA_OPEN_LOCAL_WEBVIEW = "com.dccbigfred.android.OPEN_LOCAL_WEBVIEW"
        const val ACTION_START = "com.dccbigfred.android.server.START"
        const val ACTION_STOP = "com.dccbigfred.android.server.STOP"
        const val ACTION_RESTART = "com.dccbigfred.android.server.RESTART"

        private val _state = MutableStateFlow<LocalServerState>(LocalServerState.Stopped)
        val state: StateFlow<LocalServerState> = _state.asStateFlow()

        fun start(context: Context) {
            if (_state.value !is LocalServerState.Running &&
                _state.value !is LocalServerState.Starting
            ) {
                _state.value = LocalServerState.Starting
            }
            val intent = Intent(context, LocoServerService::class.java).setAction(ACTION_START)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "startForegroundService failed", e)
                _state.value = LocalServerState.Failed(e.message ?: e.toString())
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocoServerService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        fun restart(context: Context) {
            val intent = Intent(context, LocoServerService::class.java).setAction(ACTION_RESTART)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "restart foreground service failed", e)
                _state.value = LocalServerState.Failed(e.message ?: e.toString())
            }
        }

        fun isLocalUrl(url: String?): Boolean =
            url != null && (url.contains("127.0.0.1") || url.contains("localhost"))
    }
}

sealed class LocalServerState {
    data object Stopped : LocalServerState()
    data object Starting : LocalServerState()
    data class Running(val baseUrl: String) : LocalServerState()
    data class Failed(val message: String) : LocalServerState()
}
