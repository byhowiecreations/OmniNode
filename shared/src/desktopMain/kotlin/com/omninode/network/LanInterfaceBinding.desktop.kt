package com.omninode.network

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun directedBroadcastOrNull(localIp: String, networkInterface: NetworkInterface): String? {
    val localAddress = InetAddress.getByName(localIp) as? Inet4Address ?: return null
    val ifaceAddress = networkInterface.interfaceAddresses.firstOrNull { address ->
        address.address is Inet4Address && address.address.hostAddress == localIp
    } ?: return null
    val prefixLength = ifaceAddress.networkPrefixLength.toInt()
    if (prefixLength !in 1..32) return null
    val ip = localAddress.address
    val mask = ByteArray(4)
    for (i in 0 until 4) {
        val bits = (prefixLength - i * 8).coerceIn(0, 8)
        mask[i] = ((0xFF shl (8 - bits)) and 0xFF).toByte()
    }
    val broadcast = ByteArray(4) { index ->
        (ip[index].toInt() and mask[index].toInt() or (mask[index].toInt().inv() and 0xFF)).toByte()
    }
    return InetAddress.getByAddress(broadcast).hostAddress
}

actual fun sendWakeBroadcastOnPrimaryInterface() {
    val candidates = LanInterfaceBinding.lanBindCandidates()
    if (candidates.isEmpty()) {
        return
    }
    val payload = WakeProtocol.PAYLOAD.toByteArray(Charsets.UTF_8)
    for (localIp in candidates) {
        val networkInterface = networkInterfaceForIp(localIp) ?: continue
        DatagramSocket(null).use { socket ->
            socket.reuseAddress = true
            socket.broadcast = true
            socket.bind(InetSocketAddress(localIp, 0))
            val targets = linkedSetOf(
                WakeProtocol.BROADCAST_ADDRESS,
                WakeProtocol.MULTICAST_ADDRESS
            )
            directedBroadcastOrNull(localIp, networkInterface)?.let { targets.add(it) }
            for (target in targets) {
                runCatching {
                    val address = InetAddress.getByName(target)
                    val packet = DatagramPacket(payload, payload.size, address, WakeProtocol.PORT)
                    socket.send(packet)
                }
            }
        }
    }
}

private fun networkInterfaceForIp(localIp: String): NetworkInterface? =
    NetworkInterface.getNetworkInterfaces().toList().firstOrNull { iface ->
        iface.isUp &&
            !iface.isLoopback &&
            iface.inetAddresses.toList().any { address ->
                address is Inet4Address && address.hostAddress == localIp
            }
    }

internal fun openWakeListenerOnPrimaryInterface(onLog: (String) -> Unit): DatagramSocket? {
    val localIp = LanInterfaceBinding.lanBindCandidates().firstOrNull()
    val networkInterface = localIp?.let { networkInterfaceForIp(it) }
    if (localIp == null || networkInterface == null) {
        onLog("UDP wake bind skipped — no primary LAN interface")
        return null
    }
    return runCatching {
        MulticastSocket(null).apply {
            reuseAddress = true
            broadcast = true
            bind(InetSocketAddress(localIp, WakeProtocol.PORT))
            val groupAddress = InetAddress.getByName(WakeProtocol.MULTICAST_ADDRESS)
            joinGroup(InetSocketAddress(groupAddress, WakeProtocol.PORT), networkInterface)
            onLog("UDP wake bound to $localIp:${WakeProtocol.PORT}")
        }
    }.onFailure { error ->
        onLog("UDP wake bind failed on $localIp: ${error.message}")
    }.getOrNull()
}

actual suspend fun peerHttpGet(
    host: String,
    port: Int,
    path: String,
    timeoutMs: Long
): PeerBoundHttpResponse? = withContext(Dispatchers.IO) {
    executeBoundHttp(
        host = host,
        port = port,
        method = "GET",
        path = path,
        body = null,
        contentType = null,
        timeoutMs = timeoutMs
    )
}

actual suspend fun peerHttpPost(
    host: String,
    port: Int,
    path: String,
    body: String,
    contentType: String,
    timeoutMs: Long
): PeerBoundHttpResponse? = withContext(Dispatchers.IO) {
    executeBoundHttp(
        host = host,
        port = port,
        method = "POST",
        path = path,
        body = body,
        contentType = contentType,
        timeoutMs = timeoutMs
    )
}

private fun executeBoundHttp(
    host: String,
    port: Int,
    method: String,
    path: String,
    body: String?,
    contentType: String?,
    timeoutMs: Long
): PeerBoundHttpResponse? {
    val candidates = LanInterfaceBinding.lanBindCandidates()
    if (candidates.isEmpty()) {
        return null
    }
    for (localIp in candidates) {
        val response = runCatching {
            executeBoundHttpOnLocalIp(
                localIp = localIp,
                host = host,
                port = port,
                method = method,
                path = path,
                body = body,
                contentType = contentType,
                timeoutMs = timeoutMs
            )
        }.getOrNull()
        if (response != null && response.statusCode > 0) {
            return response
        }
    }
    return null
}

private fun executeBoundHttpOnLocalIp(
    localIp: String,
    host: String,
    port: Int,
    method: String,
    path: String,
    body: String?,
    contentType: String?,
    timeoutMs: Long
): PeerBoundHttpResponse {
    val timeout = timeoutMs.coerceIn(250L, 60_000L).toInt()
    Socket().use { socket ->
        socket.bind(InetSocketAddress(localIp, 0))
        socket.connect(InetSocketAddress(host, port), timeout)
        socket.soTimeout = timeout
        val payload = body.orEmpty()
        val request = buildString {
            append(method)
            append(' ')
            append(path)
            append(" HTTP/1.1\r\n")
            append("Host: ")
            append(host)
            append(':')
            append(port)
            append("\r\n")
            append("Connection: close\r\n")
            append("Accept: application/json\r\n")
            if (contentType != null) {
                append("Content-Type: ")
                append(contentType)
                append("\r\n")
                append("Content-Length: ")
                append(payload.toByteArray(Charsets.UTF_8).size)
                append("\r\n")
            }
            append("\r\n")
            if (contentType != null) {
                append(payload)
            }
        }
        val output = socket.getOutputStream()
        output.write(request.toByteArray(Charsets.UTF_8))
        output.flush()
        val raw = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
        return parseHttpResponse(raw)
    }
}

private fun parseHttpResponse(raw: String): PeerBoundHttpResponse {
    val headerEnd = raw.indexOf("\r\n\r\n")
    val header = if (headerEnd >= 0) raw.substring(0, headerEnd) else raw
    val body = if (headerEnd >= 0) raw.substring(headerEnd + 4) else ""
    val statusLine = header.lineSequence().firstOrNull().orEmpty()
    val statusCode = Regex("HTTP/\\d\\.\\d (\\d+)").find(statusLine)?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: 0
    return PeerBoundHttpResponse(statusCode = statusCode, body = body.trim())
}
