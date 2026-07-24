package com.fileapex.network

import com.fileapex.di.FileApexServices
import com.fileapex.domain.presence.PresenceBackgroundWake
import com.fileapex.domain.presence.PresenceForegroundRefresh

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
        shutdownBlocking(fast = false)
    }

    /** Non-blocking quit path — do not wait on the UI thread for Ktor/mDNS teardown. */
    fun shutdownForQuit() {
        wakeReceiver?.stop()
        wakeReceiver = null
        Thread(
            {
                ServerLifecycleManager.stop(desktopLog, fast = true)
                println("DesktopShareServer: shutdown complete")
            },
            "FileApex-Shutdown"
        ).apply {
            isDaemon = true
            start()
        }
    }

    fun shutdownBlocking(fast: Boolean = false) {
        wakeReceiver?.stop()
        wakeReceiver = null
        ServerLifecycleManager.stop(desktopLog, fast = fast)
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
