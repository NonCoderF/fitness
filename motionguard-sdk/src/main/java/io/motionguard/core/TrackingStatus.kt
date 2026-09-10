package io.motionguard.core

/** Current pose tracking quality used to prefer uncertainty over false classifications. */
public enum class TrackingStatus {
    TRACKING_GOOD,
    TRACKING_DEGRADED,
    TRACKING_LOST,
}
