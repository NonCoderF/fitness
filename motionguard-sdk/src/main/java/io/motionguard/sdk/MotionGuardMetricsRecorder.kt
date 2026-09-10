package motionguardsdk

/** Records public MotionGuard results as CSV rows for local developer tuning. */
public class MotionGuardMetricsRecorder {
    private val rows = mutableListOf<String>()

    /** Add one metrics row. This records motion metrics only, never camera frames or video. */
    public fun record(timestampMs: Long, result: MotionGuardResult) {
        val debug = result.debug
        rows += listOf(
            timestampMs,
            result.motionState,
            result.activity,
            result.cadenceSpm ?: "",
            result.poseQuality,
            debug?.gaitScore ?: "",
            debug?.periodicityScore ?: "",
            debug?.alternationScore ?: "",
            result.cameraView,
            result.trackingQuality,
        ).joinToString(",")
    }

    /** Export all rows as CSV text. */
    public fun toCsv(): String = buildString {
        appendLine("timestampMs,motionState,activityClass,cadence,poseQuality,gaitScore,periodicity,alternation,cameraView,trackingStatus")
        rows.forEach(::appendLine)
    }

    /** Clear recorded metrics. */
    public fun clear() {
        rows.clear()
    }
}
