package com.dccbigfred.android.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

/**
 * Measures ICMP round-trip time the same way third-party "Ping" apps do on
 * non-rooted Android: it execs the system `ping` binary and parses the RTT.
 *
 * Raw ICMP sockets require root, and [InetAddress.isReachable] silently falls
 * back to a TCP echo probe (and only returns a boolean), so neither is used.
 */
class IcmpPinger {

    /** Resolved IP cache so repeated pings don't trigger mDNS `.local` lookups. */
    @Volatile
    private var cachedHost: String? = null

    @Volatile
    private var cachedAddress: String? = null

    /**
     * Pings [host] once. [host] may be a hostname or IP; it is resolved to an
     * IP address once and cached. Returns the RTT in milliseconds, or null on
     * timeout / unreachable / parse failure.
     */
    suspend fun measureRttMs(host: String, timeoutSeconds: Int = 2): Long? =
        withContext(Dispatchers.IO) {
            val target = resolveTarget(host) ?: host
            runCatching { runPing(target, timeoutSeconds) }.getOrNull()
        }

    private fun resolveTarget(host: String): String? {
        cachedAddress?.let { if (cachedHost == host) return it }
        return runCatching { InetAddress.getByName(host).hostAddress }
            .getOrNull()
            ?.also {
                cachedHost = host
                cachedAddress = it
            }
    }

    private fun runPing(target: String, timeoutSeconds: Int): Long? {
        val process = ProcessBuilder(
            "/system/bin/ping",
            "-c", "1",
            "-w", timeoutSeconds.toString(),
            target,
        ).redirectErrorStream(true).start()

        val output = try {
            process.inputStream.bufferedReader().use(BufferedReader::readText)
        } finally {
            if (!process.waitFor(timeoutSeconds + 1L, TimeUnit.SECONDS)) {
                process.destroy()
            }
        }

        // ping prints RTT with a `.` decimal in the C locale, e.g. "time=81.3 ms".
        val match = RTT_REGEX.find(output) ?: return null
        return match.groupValues[1].toDoubleOrNull()?.roundToLong()
    }

    companion object {
        private val RTT_REGEX = Regex("""time[=<]\s*([0-9.]+)""")

        /** Extracts the bare host (no scheme/port) from a base URL. */
        fun hostFromBaseUrl(baseUrl: String): String {
            val withScheme = if (baseUrl.contains("://")) baseUrl else "http://$baseUrl"
            return runCatching { URI(withScheme).host }.getOrNull()
                ?: baseUrl.substringAfter("://").substringBefore('/').substringBefore(':')
        }
    }
}
