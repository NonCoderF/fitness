package io.motionguard.core

class MotionMetricsRecorder {
    private val rows = mutableListOf<String>()

    fun record(timestampMs: Long, result: MotionResult) {
        val debug = result.debugMetrics ?: MotionDebugMetrics()
        rows += listOf(
            timestampMs,
            result.state,
            result.activityType,
            result.cadenceSpm ?: "",
            result.poseQuality,
            debug.gaitScore,
            debug.periodicityScore,
            debug.alternationScore,
            result.cameraView,
            result.trackingStatus,
        ).joinToString(",")
    }

    fun toCsv(): String = buildString {
        appendLine("timestampMs,motionState,activityClass,cadence,poseQuality,gaitScore,periodicity,alternation,cameraView,trackingStatus")
        rows.forEach(::appendLine)
    }

    fun clear() {
        rows.clear()
    }
}
