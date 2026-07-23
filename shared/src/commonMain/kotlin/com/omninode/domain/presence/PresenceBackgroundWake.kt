package com.omninode.domain.presence

import com.omninode.di.OmniNodeServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** SSOT entry for background wake signals (FCM data, future push channels). */
object PresenceBackgroundWake {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun onRemoteWakeSignal(sourceDeviceId: String?) {
        if (!OmniNodeServices.isDatabaseReady()) return
        scope.launch {
            runCatching {
                OmniNodeServices.presenceMonitor.onBackgroundWakeSignal(sourceDeviceId)
            }.onFailure { error ->
                println("PresenceBackgroundWake: wake handling failed — ${error.message}")
            }
        }
    }
}
