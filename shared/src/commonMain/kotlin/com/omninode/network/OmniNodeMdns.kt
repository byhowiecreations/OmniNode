package com.omninode.network

/** mDNS/Bonjour service contract — SSOT for offline QR-paired LAN discovery. */
object OmniNodeMdns {
    /** NSD/jmDNS type (trailing dot required on Android NsdManager). */
    const val SERVICE_TYPE = "_omninode._tcp."

    /** Human-readable prefix; full service name is `$SERVICE_NAME_PREFIX$deviceId`. */
    const val SERVICE_NAME_PREFIX = "OmniNode-"

    fun serviceNameFor(deviceId: String): String =
        SERVICE_NAME_PREFIX + deviceId.trim()

    fun deviceIdFromServiceName(serviceName: String?): String? {
        val trimmed = serviceName?.trim().orEmpty()
        if (!trimmed.startsWith(SERVICE_NAME_PREFIX)) return null
        val id = trimmed.removePrefix(SERVICE_NAME_PREFIX).trim()
        return id.takeIf { it.isNotEmpty() }
    }
}
