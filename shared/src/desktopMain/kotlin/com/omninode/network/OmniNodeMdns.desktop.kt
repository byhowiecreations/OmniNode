package com.omninode.network

import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

actual object OmniNodeMdnsAdvertiser {
    private var jmdns: JmDNS? = null
    private var registeredName: String? = null

    actual fun start(port: Int, deviceId: String) {
        stop()
        val bindAddress = selectBindAddress() ?: return
        runCatching {
            val instance = JmDNS.create(bindAddress)
            val name = OmniNodeMdns.serviceNameFor(deviceId)
            instance.registerService(
                ServiceInfo.create(
                    OmniNodeMdns.SERVICE_TYPE,
                    name,
                    port,
                    0,
                    0,
                    emptyMap<String, String>()
                )
            )
            jmdns = instance
            registeredName = name
            println("OmniNodeMdnsAdvertiser: registered $name on ${bindAddress.hostAddress}:$port")
        }.onFailure { error ->
            println("OmniNodeMdnsAdvertiser: register failed — ${error.message}")
        }
    }

    actual fun stop() {
        val instance = jmdns ?: return
        runCatching {
            instance.unregisterAllServices()
            instance.close()
        }
        jmdns = null
        registeredName = null
    }

    private fun selectBindAddress(): InetAddress? {
        val host = LanInterfaceBinding.primaryLanIpv4OrNull()
            ?: LanInterfaceBinding.lanBindCandidates().firstOrNull()
        if (host.isNullOrBlank()) return null
        return runCatching { Inet4Address.getByName(host) }.getOrNull()
    }
}

actual object OmniNodeMdnsBrowser {
    private var jmdns: JmDNS? = null
    private var callback: ((String, Int, String?) -> Unit)? = null
    private val listener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            jmdns?.requestServiceInfo(event.type, event.name, true)
        }

        override fun serviceRemoved(event: ServiceEvent) = Unit

        override fun serviceResolved(event: ServiceEvent) {
            val info = event.info ?: return
            if (!info.name.startsWith(OmniNodeMdns.SERVICE_NAME_PREFIX)) return
            val addresses = info.inet4Addresses
            val host = addresses.firstOrNull()?.hostAddress?.trim().orEmpty()
            if (host.isEmpty() || info.port <= 0) return
            callback?.invoke(host, info.port, OmniNodeMdns.deviceIdFromServiceName(info.name))
        }
    }

    actual fun start(onPeerDiscovered: (host: String, port: Int, hintedDeviceId: String?) -> Unit) {
        stop()
        callback = onPeerDiscovered
        runCatching {
            val instance = JmDNS.create()
            instance.addServiceListener(OmniNodeMdns.SERVICE_TYPE, listener)
            jmdns = instance
            println("OmniNodeMdnsBrowser: listening for ${OmniNodeMdns.SERVICE_TYPE}")
        }.onFailure { error ->
            println("OmniNodeMdnsBrowser: start failed — ${error.message}")
        }
    }

    actual fun stop() {
        val instance = jmdns
        if (instance != null) {
            runCatching {
                instance.removeServiceListener(OmniNodeMdns.SERVICE_TYPE, listener)
                instance.close()
            }
        }
        jmdns = null
        callback = null
    }

    actual fun requestProbe() {
        val instance = jmdns ?: return
        runCatching {
            instance.requestServiceInfo(OmniNodeMdns.SERVICE_TYPE, null, 1_500L)
        }.onFailure { error ->
            if (error !is IOException) {
                println("OmniNodeMdnsBrowser: requestProbe failed — ${error.message}")
            }
        }
    }
}
