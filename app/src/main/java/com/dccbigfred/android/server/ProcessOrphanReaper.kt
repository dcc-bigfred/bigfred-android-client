package com.dccbigfred.android.server

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Cleans up orphaned loco-server / valkey processes left after force-stop.
 * Verifies /proc/<pid>/cmdline before signaling to avoid PID reuse kills.
 */
object ProcessOrphanReaper {

    fun reap(pidFile: File, cmdlineNeedle: String) {
        val pid = readPid(pidFile) ?: return
        if (!cmdlineMatches(pid, cmdlineNeedle)) {
            pidFile.delete()
            return
        }
        signal(pid, OsConstants.SIGTERM)
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline) {
            if (!isAlive(pid)) break
            Thread.sleep(100)
        }
        if (isAlive(pid)) {
            signal(pid, OsConstants.SIGKILL)
        }
        pidFile.delete()
    }

    fun isPortOpen(host: String, port: Int, timeoutMs: Int = 300): Boolean =
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }

    fun readPid(pidFile: File): Int? {
        if (!pidFile.isFile) return null
        return pidFile.readText().trim().toIntOrNull()?.takeIf { it > 0 }
    }

    fun writePid(pidFile: File, pid: Long) {
        pidFile.parentFile?.mkdirs()
        pidFile.writeText(pid.toString())
    }

    fun cmdlineMatches(pid: Int, needle: String): Boolean {
        val cmdline = File("/proc/$pid/cmdline")
        if (!cmdline.isFile) return false
        val text = cmdline.readBytes().toString(Charsets.UTF_8).replace('\u0000', ' ')
        return text.contains(needle)
    }

    fun isAlive(pid: Int): Boolean =
        File("/proc/$pid").isDirectory

    private fun signal(pid: Int, sig: Int) {
        try {
            Os.kill(pid, sig)
        } catch (_: Exception) {
            // already gone
        }
    }
}
