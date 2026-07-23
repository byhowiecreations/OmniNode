package com.omninode.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.omninode.platform.androidApplicationContextOrNull

actual object OmniNodeMdnsAdvertiser {
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    actual fun start(port: Int, deviceId: String) {
        stop()
        val context = androidApplicationContextOrNull() ?: return
        val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsdManager = manager
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = OmniNodeMdns.serviceNameFor(deviceId)
            serviceType = OmniNodeMdns.SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                println("OmniNodeMdnsAdvertiser: registered ${info.serviceName}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                println("OmniNodeMdnsAdvertiser: registration failed code=$errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        registrationListener = listener
        runCatching {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure { error ->
            println("OmniNodeMdnsAdvertiser: registerService failed — ${error.message}")
        }
    }

    actual fun stop() {
        val manager = nsdManager
        val listener = registrationListener
        if (manager != null && listener != null) {
            runCatching { manager.unregisterService(listener) }
        }
        nsdManager = null
        registrationListener = null
    }
}
