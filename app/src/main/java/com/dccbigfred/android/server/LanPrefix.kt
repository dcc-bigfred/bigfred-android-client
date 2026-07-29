package com.dccbigfred.android.server

import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Resolves a LAN scan prefix (e.g. "192.168.0") via Java NetworkInterface.
 * Used because Go's net.InterfaceAddrs uses netlink, which Android denies to apps.
 */
object LanPrefix {
    private const val TAG = "LanPrefix"

    /**
     * Returns a.b.c for the first non-loopback, non-link-local IPv4, preferring /24.
     */
    fun resolve(): String? {
        return try {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return null
            var fallback: String? = null
            while (ifaces.hasMoreElements()) {
                val nif = ifaces.nextElement()
                if (!nif.isUp || nif.isLoopback) continue
                val addrs = nif.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr !is Inet4Address || addr.isLoopbackAddress || addr.isLinkLocalAddress) {
                        continue
                    }
                    val bytes = addr.address ?: continue
                    if (bytes.size != 4) continue
                    val prefix = "${bytes[0].toUByte()}.${bytes[1].toUByte()}.${bytes[2].toUByte()}"
                    // NetworkInterface does not expose prefix length portably on all APIs;
                    // prefer site-local (RFC1918) as the usual LAN case.
                    if (addr.isSiteLocalAddress) {
                        Log.i(TAG, "LAN prefix $prefix from ${nif.name} (${addr.hostAddress})")
                        return prefix
                    }
                    if (fallback == null) fallback = prefix
                }
            }
            fallback?.also { Log.i(TAG, "LAN prefix $it (non-site-local fallback)") }
        } catch (e: Exception) {
            Log.w(TAG, "failed to resolve LAN prefix", e)
            null
        }
    }
}
