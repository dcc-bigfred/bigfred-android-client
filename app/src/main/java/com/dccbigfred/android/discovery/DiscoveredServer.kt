package com.dccbigfred.android.discovery

enum class DiscoverySource {
    MDNS,
    SUBNET,
    MANUAL,
}

data class DiscoveredServer(
    val baseUrl: String,
    val label: String,
    val source: DiscoverySource,
)
