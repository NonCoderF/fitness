package io.motionguard.core

/** High-level treadmill movement state inferred from temporal lower-body pose dynamics. */
public enum class MotionState {
    MOVING,
    NOT_MOVING,
    UNCERTAIN,
}
