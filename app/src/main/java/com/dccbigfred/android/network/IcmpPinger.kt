package com.dccbigfred.android.network

import android.os.Process
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileDescriptor
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Measures round-trip latency to a host.
 *
 * Prefers real ICMP echo via an unprivileged [SOCK_DGRAM]/[IPPROTO_ICMP]
 * socket (same approach as third-party Ping apps; no root, no shell).
 * Falls back to a TCP connect timed against the HTTP port when ICMP is
 * unavailable on the device (SELinux / OEM).
 *
 * The system `/system/bin/ping` binary is intentionally not used — most
 * Android builds deny apps from exec'ing it, which showed up as permanent
 * "timeout" in the connection screen.
 */
class IcmpPinger {

    @Volatile
    private var cachedHost: String? = null

    @Volatile
    private var cachedAddress: InetAddress? = null

    @Volatile
    private var preferTcp: Boolean = false

    /**
     * One RTT sample in milliseconds, or null on timeout / unreachable.
     * [host] may be a hostname or IP. [port] is used only for the TCP fallback
     * (BigFred HTTP port, typically 8080).
     */
    suspend fun measureRttMs(
        host: String,
        port: Int = DEFAULT_HTTP_PORT,
        timeoutMs: Int = 2_000,
    ): Long? = withContext(Dispatchers.IO) {
        val address = resolveTarget(host) ?: return@withContext null
        if (!preferTcp) {
            val icmp = runCatching { measureIcmpRttMs(address, timeoutMs) }
            when {
                icmp.isFailure -> {
                    preferTcp = true
                    Log.i(
                        TAG,
                        "ICMP unavailable (${icmp.exceptionOrNull()?.message}); using TCP connect on port $port",
                    )
                }
                icmp.getOrNull() != null -> return@withContext icmp.getOrNull()
                else -> {
                    // ICMP timed out — try TCP; if it works, ICMP is likely filtered.
                    val tcp = runCatching { measureTcpConnectMs(address, port, timeoutMs) }.getOrNull()
                    if (tcp != null) {
                        preferTcp = true
                        Log.i(TAG, "ICMP timed out; switching to TCP connect on port $port")
                        return@withContext tcp
                    }
                    return@withContext null
                }
            }
        }
        runCatching { measureTcpConnectMs(address, port, timeoutMs) }.getOrNull()
    }

    private fun resolveTarget(host: String): InetAddress? {
        cachedAddress?.let { if (cachedHost == host) return it }
        return runCatching { InetAddress.getByName(host) }
            .onFailure { Log.w(TAG, "DNS resolve failed for $host", it) }
            .getOrNull()
            ?.also {
                cachedHost = host
                cachedAddress = it
            }
    }

    private fun measureIcmpRttMs(address: InetAddress, timeoutMs: Int): Long? {
        val isV6 = address is Inet6Address
        val family = if (isV6) OsConstants.AF_INET6 else OsConstants.AF_INET
        val protocol = if (isV6) OsConstants.IPPROTO_ICMPV6 else OsConstants.IPPROTO_ICMP
        val echoType = if (isV6) ICMPV6_ECHO_REQUEST else ICMP_ECHO_REQUEST
        val replyType = if (isV6) ICMPV6_ECHO_REPLY else ICMP_ECHO_REPLY

        val socketFd: FileDescriptor = Os.socket(family, OsConstants.SOCK_DGRAM, protocol)

        try {
            val id = (Process.myPid() and 0xffff).toShort()
            val seq = ((System.nanoTime() ushr 10) and 0xffffL).toShort()
            val payload = ByteArray(PAYLOAD_LEN) { i -> i.toByte() }
            val packet = ByteBuffer.allocate(ICMP_HEADER_LEN + PAYLOAD_LEN).order(ByteOrder.BIG_ENDIAN)
            packet.put(echoType)
            packet.put(0) // code
            packet.putShort(0) // checksum placeholder
            packet.putShort(id)
            packet.putShort(seq)
            packet.put(payload)
            val bytes = packet.array()
            // ICMPv6 checksum is offloaded by the kernel for SOCK_DGRAM; IPv4 needs it.
            if (!isV6) {
                val cs = icmpChecksum(bytes)
                bytes[2] = (cs.toInt() ushr 8).toByte()
                bytes[3] = (cs.toInt() and 0xff).toByte()
            }

            val started = System.nanoTime()
            Os.sendto(socketFd, bytes, 0, bytes.size, 0, address, 0)

            val pollFd = StructPollfd().apply {
                this.fd = socketFd
                events = OsConstants.POLLIN.toShort()
            }
            val ready = Os.poll(arrayOf(pollFd), timeoutMs)
            if (ready <= 0) return null

            val buf = ByteArray(256)
            val n = Os.read(socketFd, buf, 0, buf.size)
            if (n < ICMP_HEADER_LEN) return null

            // SOCK_DGRAM delivers ICMP payload only (no IP header) on Linux/Android.
            if (buf[0] != replyType) return null
            val replyId = ((buf[4].toInt() and 0xff) shl 8) or (buf[5].toInt() and 0xff)
            val replySeq = ((buf[6].toInt() and 0xff) shl 8) or (buf[7].toInt() and 0xff)
            if (replyId != (id.toInt() and 0xffff) || replySeq != (seq.toInt() and 0xffff)) {
                return null
            }

            return ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(0L)
        } finally {
            runCatching { Os.close(socketFd) }
        }
    }

    private fun measureTcpConnectMs(address: InetAddress, port: Int, timeoutMs: Int): Long? {
        Socket().use { socket ->
            val started = System.nanoTime()
            return try {
                socket.connect(InetSocketAddress(address, port), timeoutMs)
                ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(0L)
            } catch (_: Exception) {
                null
            }
        }
    }

    companion object {
        private const val TAG = "IcmpPinger"
        private const val DEFAULT_HTTP_PORT = 8080
        private const val ICMP_HEADER_LEN = 8
        private const val PAYLOAD_LEN = 32
        private const val ICMP_ECHO_REQUEST: Byte = 8
        private const val ICMP_ECHO_REPLY: Byte = 0
        private const val ICMPV6_ECHO_REQUEST: Byte = 128.toByte()
        private const val ICMPV6_ECHO_REPLY: Byte = 129.toByte()

        /** Host + TCP fallback port extracted from a BigFred base URL. */
        fun endpointFromBaseUrl(baseUrl: String): Endpoint {
            val withScheme = if (baseUrl.contains("://")) baseUrl else "http://$baseUrl"
            val uri = runCatching { URI(withScheme) }.getOrNull()
            val host = uri?.host
                ?: baseUrl.substringAfter("://").substringBefore('/').substringBefore(':')
            val port = when {
                uri != null && uri.port != -1 -> uri.port
                uri?.scheme.equals("https", ignoreCase = true) -> 443
                else -> DEFAULT_HTTP_PORT
            }
            return Endpoint(host = host, port = port)
        }

        @Deprecated("Use endpointFromBaseUrl", ReplaceWith("endpointFromBaseUrl(baseUrl).host"))
        fun hostFromBaseUrl(baseUrl: String): String = endpointFromBaseUrl(baseUrl).host

        /** Internet checksum over [data] (checksum field must be zero). */
        private fun icmpChecksum(data: ByteArray): Short {
            var sum = 0L
            var i = 0
            while (i < data.size - 1) {
                sum += (((data[i].toInt() and 0xff) shl 8) or (data[i + 1].toInt() and 0xff)).toLong()
                i += 2
            }
            if (data.size % 2 != 0) {
                sum += ((data[data.size - 1].toInt() and 0xff) shl 8).toLong()
            }
            while (sum ushr 16 != 0L) {
                sum = (sum and 0xffff) + (sum ushr 16)
            }
            return (sum.inv() and 0xffff).toShort()
        }
    }

    data class Endpoint(val host: String, val port: Int)
}
