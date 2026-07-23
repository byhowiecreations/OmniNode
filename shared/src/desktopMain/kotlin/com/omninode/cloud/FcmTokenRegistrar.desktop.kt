package com.omninode.cloud

/** Desktop has no FCM client — wake dispatch only (Mac → Android). */
actual object FcmTokenRegistrar {
    actual fun start() = Unit
    actual fun stop() = Unit
}
