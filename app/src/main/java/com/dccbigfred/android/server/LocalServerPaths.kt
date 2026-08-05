package com.dccbigfred.android.server

import android.content.Context
import java.io.File

/** Writable BigFred data tree under the app files directory. */
data class LocalServerPaths(
    val dataDir: File,
) {
    val etcDir: File get() = File(dataDir, "etc")
    val runDir: File get() = File(dataDir, "run")
    val logsDir: File get() = File(dataDir, "logs")
    val redisDir: File get() = File(dataDir, "var/lib/redis")

    val locoServerPid: File get() = File(runDir, "loco-server.pid")
    val microinitSocket: File get() = File(runDir, "microinit.sock")
    val microinitPid: File get() = File(runDir, "microinit.pid")
    val dbFile: File get() = File(dataDir, "bigfred.db")

    fun ensureDirs() {
        listOf(dataDir, etcDir, runDir, logsDir, redisDir).forEach { it.mkdirs() }
    }

    companion object {
        fun from(context: Context): LocalServerPaths =
            LocalServerPaths(File(context.filesDir, "bigfred-data"))
    }
}
