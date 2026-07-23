package com.omninode.domain.presence

import com.omninode.di.OmniNodeServices

/** App lifecycle hook — debounced foreground peer refresh (no idle background polling). */
object PresenceForegroundRefresh {
    fun onAppForegrounded() {
        if (!OmniNodeServices.isDatabaseReady()) return
        OmniNodeServices.presenceMonitor.refreshPeersOnForeground()
    }
}
