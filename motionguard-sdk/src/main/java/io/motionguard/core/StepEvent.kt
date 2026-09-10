package io.motionguard.core

/** A confirmed gait step detected from a temporally consistent pose sequence. */
public data class StepEvent(
    val timestampMs: Long,
    val side: LegSide? = null,
    val confidence: Float,
)

/** Which leg contributed most clearly to a detected gait transition. */
public enum class LegSide {
    LEFT,
    RIGHT,
}
