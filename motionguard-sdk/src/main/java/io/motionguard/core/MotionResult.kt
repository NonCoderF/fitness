package io.motionguard.core

/** Internal/core analysis result produced by [MotionEngine]. */
public data class MotionResult(
    val state: MotionState,
    val confidence: Float,
    val activityType: ActivityType,
    val cadenceSpm: Int?,
    /** Normalized cadence-derived gait speed signal, not treadmill belt speed. */
    val gaitSpeed: Float = 0f,
    val movementIntensity: Float,
    val poseQuality: Float,
    val poseDetected: Boolean,
    val cameraView: CameraView = CameraView.UNKNOWN,
    val trackingStatus: TrackingStatus = TrackingStatus.TRACKING_LOST,
    val calibration: MotionCalibration? = null,
    val debugMetrics: MotionDebugMetrics? = null,
    /** Newly confirmed gait events associated with this processed pose frame. */
    val stepEvents: List<StepEvent> = emptyList(),
)
