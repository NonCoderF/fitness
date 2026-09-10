package io.motionguard.core

/** Advanced engine configuration. Most SDK consumers should use MotionGuardConfig profiles first. */
public data class MotionConfig(
    val pose: PoseQualityConfig = PoseQualityConfig(),
    val window: WindowConfig = WindowConfig(),
    val scoring: ScoringConfig = ScoringConfig(),
    val transitions: TransitionConfig = TransitionConfig(),
    val cadence: CadenceConfig = CadenceConfig(),
    val classification: ClassificationConfig = ClassificationConfig(),
    val calibration: CalibrationConfig = CalibrationConfig(),
    val recovery: RecoveryConfig = RecoveryConfig(),
    val ema: EmaConfig = EmaConfig(),
)

data class EmaConfig(
    val positionAlpha: Float = 0.35f,
    /** Frame interval at which positionAlpha is defined. 33.333 ms represents 30 FPS. */
    val referenceFrameIntervalMs: Float = 33.333f,
    val resetAfterMissingMs: Long = 2_500L,
)

data class PoseQualityConfig(
    val minimumLandmarkConfidence: Float = 0.45f,
    val minimumPoseQualityForDecision: Float = 0.48f,
    val minimumNormalizedLegScale: Float = 0.08f,
    val missingFramePenalty: Float = 0.11f,
    val geometryJumpTolerance: Float = 0.18f,
)

data class WindowConfig(
    val analysisWindowMs: Long = 2_800L,
    val cadenceWindowMs: Long = 6_000L,
    val minFramesForDecision: Int = 10,
    val minWindowDurationMs: Long = 900L,
)

data class ScoringConfig(
    val ankleMovementThreshold: Float = 0.13f,
    val kneeAngleCycleThresholdDegrees: Float = 16f,
    val kneeAngleMovementThresholdDegrees: Float = 22f,
    val minimumStepSignalAmplitude: Float = 0.10f,
    val cameraShakeSimilarityPenaltyThreshold: Float = 0.82f,
    val movingThreshold: Float = 0.50f,
    val stationaryThreshold: Float = 0.30f,
    val alternationWeight: Float = 0.24f,
    val ankleMovementWeight: Float = 0.22f,
    val kneeCycleWeight: Float = 0.20f,
    val periodicityWeight: Float = 0.22f,
    val kneeAmplitudeWeight: Float = 0.12f,
    val bilateralConsistencyWeight: Float = 0.16f,
    val minimumBilateralConsistency: Float = 0.36f,
    val synchronousLegRejectionThreshold: Float = 0.72f,
    val oneLegConfidenceCap: Float = 0.46f,
    val phoneMotionPenaltyScale: Float = 0.35f,
)

data class TransitionConfig(
    val movingConfirmationMs: Long = 700L,
    val stationaryConfirmationMs: Long = 1_500L,
    val lowQualityUncertainMs: Long = 1_000L,
    val movingGraceMs: Long = 1_200L,
)

data class CadenceConfig(
    val minCadenceSpm: Int = 55,
    val maxCadenceSpm: Int = 220,
    val minimumPeakCount: Int = 4,
    val peakProminence: Float = 0.055f,
    val smoothingAlpha: Float = 0.28f,
)

data class ClassificationConfig(
    val runningCadenceThresholdSpm: Int = 145,
    val runningIntensityThreshold: Float = 0.45f,
    val walkingMinimumCadenceSpm: Int = 55,
    val jumpingSynchronousThreshold: Float = 0.72f,
    val bouncingHipMotionThreshold: Float = 0.08f,
)

data class CalibrationConfig(
    val enabled: Boolean = true,
    val durationMs: Long = 2_000L,
    val minQuality: Float = 0.65f,
    val jitterMultiplier: Float = 3.5f,
    val minAdaptiveAnkleThreshold: Float = 0.07f,
    val maxAdaptiveAnkleThreshold: Float = 0.18f,
    val maxStandingJitterForCalibration: Float = 0.045f,
)

data class RecoveryConfig(
    val identityScaleJumpThreshold: Float = 0.45f,
    val longOcclusionResetMs: Long = 2_500L,
    val degradedTrackingResetMs: Long = 3_500L,
)

typealias MotionEngineConfig = MotionConfig
