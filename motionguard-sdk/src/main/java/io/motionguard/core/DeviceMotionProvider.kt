package io.motionguard.core

import kotlinx.coroutines.flow.Flow

/** Optional source of compact accelerometer/gyroscope motion samples for camera-motion down-weighting. */
public fun interface DeviceMotionProvider {
    /** Observe device-motion samples. Implementations should not emit at unnecessarily high frequency. */
    public fun observe(): Flow<DeviceMotionSample>
}
