package io.motionguard.core

enum class MotionConfigProfile {
    DEFAULT,
    CONSERVATIVE,
    RESPONSIVE,
}

fun MotionConfigProfile.toMotionConfig(): MotionConfig {
    val base = MotionConfig()
    return when (this) {
        MotionConfigProfile.DEFAULT -> base
        MotionConfigProfile.CONSERVATIVE -> base.copy(
            scoring = base.scoring.copy(
                movingThreshold = 0.64f,
                stationaryThreshold = 0.24f,
                minimumStepSignalAmplitude = 0.12f,
            ),
            transitions = base.transitions.copy(
                movingConfirmationMs = 1_000L,
                stationaryConfirmationMs = 2_000L,
                movingGraceMs = 1_600L,
            ),
        )
        MotionConfigProfile.RESPONSIVE -> base.copy(
            window = base.window.copy(
                minWindowDurationMs = 600L,
            ),
            scoring = base.scoring.copy(
                movingThreshold = 0.48f,
                stationaryThreshold = 0.34f,
                minimumStepSignalAmplitude = 0.060f,
                minimumBilateralConsistency = 0.28f,
            ),
            transitions = base.transitions.copy(
                movingConfirmationMs = 450L,
                stationaryConfirmationMs = 1_000L,
                movingGraceMs = 900L,
            ),
            cadence = base.cadence.copy(
                minimumPeakCount = 3,
                peakProminence = 0.035f,
            ),
        )
    }
}
