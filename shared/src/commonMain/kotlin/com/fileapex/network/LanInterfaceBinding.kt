package com.fileapex.network

import com.fileapex.util.NetworkUtils

/**
 * SSOT for binding LAN UDP/TCP to the primary routable interface instead of wildcard/loopback.
 */
object LanInterfaceBinding {
    fun primaryLanIpv4OrNull(): String? =
        NetworkUtils.preferredLanIpv4().takeIf { NetworkUtils.isUsableLanIpv4(it) }

    /** Ordered local IPs for outbound peer sockets — active LAN first. */
    fun lanBindCandidates(): List<String> = NetworkUtils.lanBindCandidates()

    /** HTTP share-server bind address — primary LAN IP when available. */
    fun shareServerBindHost(): String = primaryLanIpv4OrNull() ?: "0.0.0.0"
}

data class PeerBoundHttpResponse(
    val statusCode: Int,
    val body: String
)

/** GET over TCP bound to the primary LAN interface (force-route for cross-platform peers). */
expect suspend fun peerHttpGet(
    host: String,
    port: Int,
    path: String,
    timeoutMs: Long
): PeerBoundHttpResponse?

/** POST over TCP bound to the primary LAN interface (force-route cluster merge). */
expect suspend fun peerHttpPost(
    host: String,
    port: Int,
    path: String,
    body: String,
    contentType: String,
    timeoutMs: Long
): PeerBoundHttpResponse?

/** Sends wake UDP from the primary LAN interface (broadcast + directed subnet + multicast). */
expect fun sendWakeBroadcastOnPrimaryInterface()
