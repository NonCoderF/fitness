package io.motionguard.core

import kotlin.math.abs

/** O(1) EMA preprocessing for the six lower-body landmarks used by gait analysis. */
public class PoseEmaSmoother(
    private val config: EmaConfig = EmaConfig(),
    private val minimumConfidence: Float = 0.45f,
) {
    private var previous: PoseFrame? = null

    public fun reset() {
        previous = null
    }

    public fun filter(frame: PoseFrame): PoseFrame {
        val old = previous
        val scaleChanged = old != null && bodyScaleRatio(old, frame) !in 0.625f..1.6f
        if (scaleChanged || (old != null && frame.timestampMs - old.timestampMs > config.resetAfterMissingMs)) {
            previous = null
        }
        val base = previous
        fun smooth(current: Point2D?, prior: Point2D?): Point2D? {
            if (current == null || current.confidence < minimumConfidence) return current
            if (prior == null) return current
            val alpha = config.positionAlpha.coerceIn(0.05f, 1f)
            return Point2D(
                x = alpha * current.x + (1f - alpha) * prior.x,
                y = alpha * current.y + (1f - alpha) * prior.y,
                confidence = current.confidence,
            )
        }
        val result = frame.copy(
            leftHip = smooth(frame.leftHip, base?.leftHip),
            rightHip = smooth(frame.rightHip, base?.rightHip),
            leftKnee = smooth(frame.leftKnee, base?.leftKnee),
            rightKnee = smooth(frame.rightKnee, base?.rightKnee),
            leftAnkle = smooth(frame.leftAnkle, base?.leftAnkle),
            rightAnkle = smooth(frame.rightAnkle, base?.rightAnkle),
        )
        previous = result
        return result
    }

    private fun bodyScaleRatio(a: PoseFrame, b: PoseFrame): Float {
        fun width(frame: PoseFrame): Float = abs(frame.leftHip!!.x - frame.rightHip!!.x)
        val first = runCatching { width(a) }.getOrDefault(0f)
        val second = runCatching { width(b) }.getOrDefault(0f)
        return if (first <= 0f || second <= 0f) 1f else second / first
    }
}
