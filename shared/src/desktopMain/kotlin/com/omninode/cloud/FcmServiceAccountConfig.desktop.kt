package com.omninode.cloud

actual fun fcmServiceAccountConfig(): FcmServiceAccountConfig? =
    GeneratedFcmCredentials.toConfig()
