package com.omninode.network

/** Registers this node on LAN via mDNS/Bonjour when the share server is running. */
expect object OmniNodeMdnsAdvertiser {
    fun start(port: Int, deviceId: String)
    fun stop()
}
