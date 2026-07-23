package com.omninode.network

import com.omninode.di.OmniNodeServices
import com.omninode.domain.presence.PresenceBackgroundWake
import com.omninode.domain.presence.PresenceForegroundRefresh

/**
 * Desktop/Mac process-owned share server lifecycle around [ServerLifecycleManager].
 * Includes a bound UDP wake listener so Android peers can trigger foreground refresh.
 */
object DesktopShareServerController {
    private var wakeReceiver: UdpWakeReceiver? = null

    fun start() {
        ServerLifecycleManager.ensureRunning(desktopLog)
        ensureWakeListener()
    }

    fun stop() {
        wakeReceiver?.stop()
        wakeReceiver = null
        shutdownBlocking()
    }

    fun shutdownBlocking() {
        wakeReceiver?.stop()
        wakeReceiver = null
        ServerLifecycleManager.stop(desktopLog)
        println("DesktopShareServer: shutdown complete")
    }

    private fun ensureWakeListener() {
        if (wakeReceiver != null) return
        wakeReceiver = UdpWakeReceiver(
            onWakeAccepted = {
                PresenceBackgroundWake.onRemoteWakeSignal(sourceDeviceId = null)
                PresenceForegroundRefresh.onAppForegrounded()
            },
            onLog = { message -> println("DesktopShareServer: $message") }
        ).also { it.start() }
    }

    private val desktopLog: (String, Throwable?) -> Unit = { message, error ->
        if (error != null) {
            println("DesktopShareServer: $message :: ${error.message}")
            error.printStackTrace()
        } else {
            println("DesktopShareServer: $message")
        }
    }
}
