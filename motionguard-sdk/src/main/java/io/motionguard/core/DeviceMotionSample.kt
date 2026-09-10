package io.motionguard.core

data class DeviceMotionSample(
    val timestampMs: Long,
    val accelerationMagnitude: Float,
    val rotationMagnitude: Float,
) {
    val motionScore: Float
        get() = ((accelerationMagnitude / 18f) + (rotationMagnitude / 3.5f)).coerceIn(0f, 1f)
}
