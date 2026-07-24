package com.fileapex.domain.presence

/**
 * Single source of truth for intent-driven presence probe timing.
 *
 * No idle background polling — reachability refreshes on cold launch, app foreground,
 * user taps/transfers, and inbound merge payloads.
 */
object LanPresenceTiming {
    /** Offline badge grace after the last successful probe or passive merge. */
    const val OFFLINE_GRACE_MS = 360_000L

    /** Local-only UI re-evaluation when grace windows expire (no network I/O). */
    const val ONLINE_SNAPSHOT_REFRESH_MS = 60_000L

    /** Minimum spacing between foreground peer refresh sweeps. */
    const val FOREGROUND_REFRESH_DEBOUNCE_MS = 30_000L

    /** Fast on-demand health probe timeout (tap-to-browse / wake). */
    const val ON_DEMAND_HEALTH_TIMEOUT_MS = 1_500L

    /** Brief wait after on-demand UDP wake for inbound merge payloads. */
    const val PASSIVE_ENDPOINT_WAIT_MS = 1_500L

    /** Upper bound for LAN identity sweep when stored endpoint is stale. */
    const val LAN_DISCOVERY_BUDGET_MS = 4_000L

    /** Extended sweep for peers that appear offline (stale IP / cross-platform). */
    const val STALE_PEER_LAN_DISCOVERY_BUDGET_MS = 12_000L

    /** Active LAN poll interval while the app process is running (desktop + Android). */
    const val ACTIVE_LAN_POLL_MS = 30_000L

    @Deprecated("Use ACTIVE_LAN_POLL_MS", ReplaceWith("ACTIVE_LAN_POLL_MS"))
    const val DESKTOP_LAN_POLL_MS = ACTIVE_LAN_POLL_MS

    /** Stored-endpoint probe attempts before LAN discovery. */
    const val ON_DEMAND_PRIME_ATTEMPTS = 2

    const val ON_DEMAND_PRIME_RETRY_MS = 400L
}
