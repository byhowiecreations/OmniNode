package com.omninode.domain.presence

import com.omninode.cloud.GoogleLinkCoordinator
import com.omninode.di.OmniNodeServices
import com.omninode.network.OmniNodeMdnsBrowser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Event-driven LAN revalidation — single-shot mDNS probe + peer sweep after network transitions.
 * Avoids idle timers; complements FCM for cloud-linked devices.
 */
object PresenceNetworkRevalidator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun onLanNetworkTransition() {
        if (!OmniNodeServices.isDatabaseReady()) return
        scope.launch {
            runCatching {
                GoogleLinkCoordinator.invalidatePublishedPresenceCache()
                OmniNodeMdnsBrowser.requestProbe()
                OmniNodeServices.presenceMonitor.runSingleShotRevalidation()
            }.onFailure { error ->
                println("PresenceNetworkRevalidator: transition revalidation failed — ${error.message}")
            }
        }
    }

    fun ensureRegistered() {
        registerLanNetworkTransitionListener()
    }
}

/** Platform registers ConnectivityManager / interface watchers → [PresenceNetworkRevalidator]. */
expect fun registerLanNetworkTransitionListener()
