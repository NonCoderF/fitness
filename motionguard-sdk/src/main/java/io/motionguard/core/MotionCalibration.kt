package io.motionguard.core

/** Optional non-biometric geometric calibration used for adaptive thresholds. */
public data class MotionCalibration(
    val baselineJitter: Float,
    val bodyScale: Float,
    val hipWidth: Float?,
    val legLengthEstimate: Float?,
    val cameraView: CameraView,
    val quality: Float,
)
