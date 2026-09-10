package motionguardsdk

/** Preset tuning profiles for common MotionGuard integrations. */
public enum class MotionProfile {
    /** Balanced defaults for general treadmill use. */
    DEFAULT,

    /** Slower confirmation and stronger evidence requirements to reduce false positives. */
    CONSERVATIVE,

    /** Faster feedback for demos and threshold tuning. */
    RESPONSIVE,
}
