package com.omninode.domain.presence

import com.omninode.cloud.FcmTokenRegistrar
import com.omninode.cloud.GoogleLinkCoordinator
import com.omninode.data.identity.loadLocalIdentity
import com.omninode.di.OmniNodeServices
import com.omninode.network.OmniNodeMdnsAdvertiser
import com.omninode.network.OmniNodeMdnsBrowser
import com.omninode.network.ServerLifecycleManager

/**
 * Dual-path background presence bootstrap — mDNS listener, network revalidation, FCM token sync.
 * Started with the share server (FGS on Android, desktop controller on Mac).
 */
object BackgroundPresenceServices {
    @Volatile
    private var started = false

    fun start() {
        if (started) return
        started = true
        if (ServerLifecycleManager.isRunning) {
            val identity = loadLocalIdentity()
            OmniNodeMdnsAdvertiser.start(identity.sharePort, identity.deviceId)
        }
        OmniNodeMdnsBrowser.start { host, port, hintedDeviceId ->
            if (OmniNodeServices.isDatabaseReady()) {
                OmniNodeServices.presenceMonitor.onMdnsPeerDiscovered(host, port, hintedDeviceId)
            }
        }
        PresenceNetworkRevalidator.ensureRegistered()
        FcmTokenRegistrar.start()
        runCatching { GoogleLinkCoordinator.invalidatePublishedPresenceCache() }
    }

    fun stop() {
        if (!started) return
        started = false
        OmniNodeMdnsAdvertiser.stop()
        OmniNodeMdnsBrowser.stop()
        FcmTokenRegistrar.stop()
    }

    fun onShareServerStarted(port: Int, deviceId: String) {
        if (!started) return
        OmniNodeMdnsAdvertiser.start(port, deviceId)
    }

    fun onShareServerStopped() {
        OmniNodeMdnsAdvertiser.stop()
    }
}
