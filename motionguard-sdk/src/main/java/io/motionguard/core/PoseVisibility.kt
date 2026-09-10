package io.motionguard.core

data class PoseVisibility(
    val leftLegScore: Float,
    val rightLegScore: Float,
    val lowerBodyScore: Float,
    val fullBodyScore: Float,
)
