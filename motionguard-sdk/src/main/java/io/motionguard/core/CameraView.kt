package io.motionguard.core

/** Approximate camera viewpoint inferred from pose geometry. */
public enum class CameraView {
    FRONT,
    SIDE_LEFT,
    SIDE_RIGHT,
    OBLIQUE,
    UNKNOWN,
}
