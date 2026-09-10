package io.motionguard.core

/** Normalized 2D landmark coordinate with detector confidence from 0.0 to 1.0. */
public data class Point2D(
    val x: Float,
    val y: Float,
    val confidence: Float,
)
