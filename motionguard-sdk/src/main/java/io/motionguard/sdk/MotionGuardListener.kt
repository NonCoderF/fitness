package motionguardsdk

/** Java-friendly callback for receiving MotionGuard results. */
public fun interface MotionGuardListener {
    /** Called after each processed pose frame and after reset. */
    public fun onResult(result: MotionGuardResult)
}
