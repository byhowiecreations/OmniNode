package com.omninode.network

/**
 * Listens for peer mDNS announcements (no polling). [onPeerDiscovered] is invoked on a background thread;
 * implementations must hop to the presence layer which serializes Room writes.
 */
expect object OmniNodeMdnsBrowser {
    fun start(onPeerDiscovered: (host: String, port: Int, hintedDeviceId: String?) -> Unit)
    fun stop()
    /** One-shot browse nudge after Wi‑Fi/LAN transitions. */
    fun requestProbe()
}
