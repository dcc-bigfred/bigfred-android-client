package com.dccbigfred.android.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.content.getSystemService
import com.dccbigfred.android.data.ServerPreferences
import com.dccbigfred.android.network.ServerProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetAddress

class ServerDiscovery(
    context: Context,
    private val probe: ServerProbe = ServerProbe(),
) {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService<NsdManager>()
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _servers = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val servers: StateFlow<List<DiscoveredServer>> = _servers.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var discoveryJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun start() {
        if (discoveryJob?.isActive == true) return
        discoveryJob = scope.launch {
            _scanning.value = true
            _servers.value = emptyList()
            acquireMulticastLock()
            try {
                coroutineScope {
                    launch { probeMdnsHostname() }
                    launch { probeSubnetFallback() }
                    launch { collectNsdDiscoveries() }
                }
            } finally {
                releaseMulticastLock()
                _scanning.value = false
            }
        }
    }

    fun stop() {
        discoveryJob?.cancel()
        discoveryJob = null
        releaseMulticastLock()
        _scanning.value = false
    }

    suspend fun probeManual(hostOrUrl: String, port: Int = DEFAULT_PORT): DiscoveredServer? {
        val base = when {
            hostOrUrl.contains("://") -> ServerPreferences.normalizeBaseUrl(hostOrUrl)
            hostOrUrl.contains(":") && !hostOrUrl.startsWith("[") ->
                ServerPreferences.normalizeBaseUrl("http://$hostOrUrl")
            else -> ServerPreferences.normalizeBaseUrl("http://$hostOrUrl:$port")
        }
        return if (probe.isReachable(base)) {
            val server = DiscoveredServer(
                baseUrl = base,
                label = base.removePrefix("http://").removePrefix("https://"),
                source = DiscoverySource.MANUAL,
            )
            upsert(server)
            server
        } else {
            null
        }
    }

    private suspend fun probeMdnsHostname() {
        val base = "http://$MDNS_HOST:$DEFAULT_PORT"
        // Resolve .local via system DNS (may work when NSD does not).
        withTimeoutOrNull(2_500) {
            runCatching { InetAddress.getByName(MDNS_HOST) }
        }
        if (probe.isReachable(base)) {
            upsert(
                DiscoveredServer(
                    baseUrl = base,
                    label = "$MDNS_HOST:$DEFAULT_PORT",
                    source = DiscoverySource.MDNS,
                ),
            )
        }
    }

    private suspend fun probeSubnetFallback() {
        val prefix = localIpv4Prefix() ?: return
        val base = "http://$prefix.$HUB_OCTET:$DEFAULT_PORT"
        if (probe.isReachable(base)) {
            upsert(
                DiscoveredServer(
                    baseUrl = base,
                    label = "$prefix.$HUB_OCTET:$DEFAULT_PORT",
                    source = DiscoverySource.SUBNET,
                ),
            )
        }
    }

    private suspend fun collectNsdDiscoveries() {
        val manager = nsdManager ?: return
        withTimeoutOrNull(DISCOVERY_WINDOW_MS) {
            nsdServiceFlow(manager).collect { info ->
                @Suppress("DEPRECATION")
                val host = info.host?.hostAddress ?: return@collect
                val port = if (info.port > 0) info.port else DEFAULT_PORT
                val name = info.serviceName.orEmpty()
                val looksLikeBigFred =
                    name.contains("bigfred", ignoreCase = true) ||
                        host.contains("bigfred", ignoreCase = true)
                if (!looksLikeBigFred && port != DEFAULT_PORT) return@collect

                val base = ServerPreferences.normalizeBaseUrl("http://$host:$port")
                if (probe.isReachable(base)) {
                    upsert(
                        DiscoveredServer(
                            baseUrl = base,
                            label = if (name.isNotBlank()) "$name ($host:$port)" else "$host:$port",
                            source = DiscoverySource.MDNS,
                        ),
                    )
                }
            }
        }
    }

    private fun nsdServiceFlow(manager: NsdManager): Flow<NsdServiceInfo> = callbackFlow {
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "NSD discovery started: $serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "NSD discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "NSD start failed: $errorCode")
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "NSD stop failed: $errorCode")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                manager.resolveService(
                    serviceInfo,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.w(TAG, "NSD resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            trySend(resolved)
                        }
                    },
                )
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                // no-op: list is probe-based
            }
        }

        manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        awaitClose {
            runCatching {
                manager.stopServiceDiscovery(discoveryListener)
            }
        }
    }

    private fun upsert(server: DiscoveredServer) {
        _servers.update { current ->
            val without = current.filterNot { it.baseUrl == server.baseUrl }
            (without + server).sortedWith(
                compareBy<DiscoveredServer> { it.source.ordinal }
                    .thenBy { it.label },
            )
        }
    }

    private fun localIpv4Prefix(): String? {
        val cm = appContext.getSystemService<ConnectivityManager>() ?: return null
        val network = cm.activeNetwork ?: return wifiManagerIpv4Fallback()
        val props: LinkProperties = cm.getLinkProperties(network) ?: return wifiManagerIpv4Fallback()
        val ipv4 = props.linkAddresses
            .mapNotNull { it.address as? Inet4Address }
            .firstOrNull { !it.isLoopbackAddress }
            ?: return wifiManagerIpv4Fallback()
        val parts = ipv4.hostAddress?.split('.') ?: return null
        if (parts.size != 4) return null
        return "${parts[0]}.${parts[1]}.${parts[2]}"
    }

    @Suppress("DEPRECATION")
    private fun wifiManagerIpv4Fallback(): String? {
        val wifi = appContext.getSystemService<WifiManager>() ?: return null
        val ip = wifi.connectionInfo?.ipAddress ?: return null
        if (ip == 0) return null
        val a = ip and 0xff
        val b = ip shr 8 and 0xff
        val c = ip shr 16 and 0xff
        return "$a.$b.$c"
    }

    private fun acquireMulticastLock() {
        val wifi = appContext.getSystemService<WifiManager>() ?: return
        multicastLock = wifi.createMulticastLock("bigfred-mdns").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.let {
            if (it.isHeld) it.release()
        }
        multicastLock = null
    }

    companion object {
        private const val TAG = "ServerDiscovery"
        private const val SERVICE_TYPE = "_http._tcp."
        private const val MDNS_HOST = "bigfred.local"
        private const val DEFAULT_PORT = 8080
        private const val HUB_OCTET = 120
        private const val DISCOVERY_WINDOW_MS = 6_000L
    }
}
