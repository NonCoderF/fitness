package motionguardsdk

import io.motionguard.core.ActivityType
import io.motionguard.core.CameraView
import io.motionguard.core.MotionCalibration
import io.motionguard.core.MotionDebugMetrics
import io.motionguard.core.MotionResult
import io.motionguard.core.MotionState
import io.motionguard.core.TrackingStatus
import io.motionguard.core.StepEvent
import io.motionguard.core.PoseFrame

/** Stable public result emitted by MotionGuard after each processed pose frame. */
public data class MotionGuardResult(
    /** MOVING, NOT_MOVING, or UNCERTAIN. */
    public val motionState: MotionState,
    /** STATIONARY, WALKING, RUNNING, or UNKNOWN. */
    public val activity: ActivityType,
    /** Normalized confidence in the current motion state, from 0.0 to 1.0. */
    public val confidence: Float,
    /** Estimated walking/running cadence in steps per minute, or null when unavailable. */
    public val cadenceSpm: Int?,
    /** Normalized cadence-derived gait speed signal, not treadmill belt speed. */
    public val gaitSpeed: Float = 0f,
    /** Normalized lower-body signal strength, from 0.0 to 1.0. */
    public val movementIntensity: Float,
    /** Normalized pose tracking quality, from 0.0 to 1.0. */
    public val poseQuality: Float,
    /** Current high-level pose tracking status. */
    public val trackingQuality: TrackingStatus,
    /** Estimated camera viewpoint from pose geometry. */
    public val cameraView: CameraView,
    /** Current calibration, when available. */
    public val calibration: MotionCalibration?,
    /** Optional tuning metrics, present only when debug metrics are enabled. */
    public val debug: MotionDebugMetrics?,
    /** Newly confirmed gait events produced while processing this result. */
    public val stepEvents: List<StepEvent> = emptyList(),
    /** Latest confidence-smoothed pose, when supplied by a camera or pose pipeline. */
    public val poseFrame: PoseFrame? = null,
) {
    public companion object {
        internal fun fromCore(result: MotionResult, includeDebug: Boolean): MotionGuardResult =
            MotionGuardResult(
                motionState = result.state,
                activity = result.activityType,
                confidence = result.confidence,
                cadenceSpm = result.cadenceSpm,
                gaitSpeed = result.gaitSpeed,
                movementIntensity = result.movementIntensity,
                poseQuality = result.poseQuality,
                trackingQuality = result.trackingStatus,
                cameraView = result.cameraView,
                calibration = result.calibration,
                debug = result.debugMetrics.takeIf { includeDebug },
                stepEvents = result.stepEvents,
            )
    }
}
