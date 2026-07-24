package com.fileapex.platform

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log

/**
 * Holds a partial wake lock while the share-server foreground service is active so OEM
 * freezer daemons (notably Motorola Moto Signature) cannot treat the process as fully idle.
 */
internal object ShareServerWakeLock {
    private const val TAG = "ShareServerWakeLock"
    private const val LOCK_TAG = "FileApex:ShareServer"
    private const val WAKE_LOCK_TIMEOUT_MS = 6 * 60 * 60 * 1000L

    @Volatile
    private var wakeLock: PowerManager.WakeLock? = null

    fun acquire(context: Context) {
        synchronized(this) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val lock = wakeLock
            if (lock?.isHeld == true) {
                renewHeldLock(lock)
                return
            }
            val newLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                LOCK_TAG
            ).apply {
                setReferenceCounted(false)
            }
            runCatching {
                acquireLock(newLock)
                wakeLock = newLock
                Log.i(TAG, "Acquired partial wake lock")
            }.onFailure { error ->
                Log.w(TAG, "Wake lock acquire failed :: ${error.message}")
            }
        }
    }

    private fun acquireLock(lock: PowerManager.WakeLock) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            lock.acquire(WAKE_LOCK_TIMEOUT_MS)
        } else {
            @Suppress("DEPRECATION")
            lock.acquire()
        }
    }

    private fun renewHeldLock(lock: PowerManager.WakeLock) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && lock.isHeld) {
            runCatching { lock.acquire(WAKE_LOCK_TIMEOUT_MS) }
                .onFailure { error ->
                    Log.w(TAG, "Wake lock renew failed :: ${error.message}")
                }
        }
    }

    fun release() {
        synchronized(this) {
            val lock = wakeLock ?: return
            runCatching {
                if (lock.isHeld) {
                    lock.release()
                    Log.i(TAG, "Released partial wake lock")
                }
            }.onFailure { error ->
                Log.w(TAG, "Wake lock release failed :: ${error.message}")
            }
            wakeLock = null
        }
    }
}
