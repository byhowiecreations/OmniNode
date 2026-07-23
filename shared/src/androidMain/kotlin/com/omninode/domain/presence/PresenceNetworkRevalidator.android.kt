package com.omninode.domain.presence

/** Android: ConnectivityManager NetworkCallback. */
actual fun registerLanNetworkTransitionListener() {
    LanNetworkTransitionMonitor.ensureRegistered()
}
