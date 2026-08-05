package com.dccbigfred.android.server

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Cleans up orphaned loco-server / microinit processes left after force-stop.
 * Verifies /proc/<pid>/cmdline and starttime before signaling to avoid PID reuse kills.
 */
object ProcessOrphanReaper {

    data class PidIdentity(val pid: Int, val starttime: Long?)

    fun reap(pidFile: File, cmdlineNeedle: String) {
        stopGracefully(pidFile, cmdlineNeedle, graceMs = 3_000)
    }

    /**
     * SIGTERM the process backing [pidFile] (after verifying /proc/<pid>/cmdline
     * contains [cmdlineNeedle] and optional starttime to guard against PID reuse),
     * wait up to [graceMs] for it to exit, then SIGKILL. The pidfile is deleted
     * once the process is gone.
     */
    fun stopGracefully(pidFile: File, cmdlineNeedle: String, graceMs: Long) {
        val identity = readPidIdentity(pidFile) ?: return
        if (!matchesIdentity(identity, cmdlineNeedle)) {
            pidFile.delete()
            return
        }
        signal(identity.pid, OsConstants.SIGTERM)
        val deadline = System.currentTimeMillis() + graceMs
        while (System.currentTimeMillis() < deadline) {
            if (!matchesIdentity(identity, cmdlineNeedle)) break
            Thread.sleep(100)
        }
        if (matchesIdentity(identity, cmdlineNeedle)) {
            signal(identity.pid, OsConstants.SIGKILL)
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

    fun readPid(pidFile: File): Int? = readPidIdentity(pidFile)?.pid

    /**
     * Pidfile format:
     * - line 1: pid
     * - line 2 (optional): /proc/<pid>/stat starttime (field 22)
     */
    fun readPidIdentity(pidFile: File): PidIdentity? {
        if (!pidFile.isFile) return null
        val lines = pidFile.readText().trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        val pid = lines[0].toIntOrNull()?.takeIf { it > 0 } ?: return null
        val starttime = lines.getOrNull(1)?.toLongOrNull()
        return PidIdentity(pid, starttime)
    }

    fun writePid(pidFile: File, pid: Long) {
        writePidIdentity(pidFile, pid.toInt(), readStarttime(pid.toInt()))
    }

    fun writePidIdentity(pidFile: File, pid: Int, starttime: Long?) {
        pidFile.parentFile?.mkdirs()
        val body = if (starttime != null) "$pid\n$starttime\n" else "$pid\n"
        pidFile.writeText(body)
    }

    fun cmdlineMatches(pid: Int, needle: String): Boolean {
        val cmdline = File("/proc/$pid/cmdline")
        if (!cmdline.isFile) return false
        val text = cmdline.readBytes().toString(Charsets.UTF_8).replace('\u0000', ' ')
        return text.contains(needle)
    }

    fun isAlive(pid: Int): Boolean =
        File("/proc/$pid").isDirectory

    fun readStarttime(pid: Int): Long? {
        val stat = File("/proc/$pid/stat")
        if (!stat.isFile) return null
        val text = stat.readText()
        val close = text.lastIndexOf(") ")
        if (close < 0) return null
        val fields = text.substring(close + 2).trim().split(Regex("\\s+"))
        // After ") ": index 0 = state (stat field 3). starttime is field 22 → index 19.
        return fields.getOrNull(19)?.toLongOrNull()
    }

    fun matchesIdentity(identity: PidIdentity, cmdlineNeedle: String): Boolean {
        if (!isAlive(identity.pid)) return false
        if (!cmdlineMatches(identity.pid, cmdlineNeedle)) return false
        val expected = identity.starttime ?: return true
        val actual = readStarttime(identity.pid) ?: return false
        return actual == expected
    }

    private fun signal(pid: Int, sig: Int) {
        try {
            Os.kill(pid, sig)
        } catch (_: Exception) {
            // already gone
        }
    }
}
