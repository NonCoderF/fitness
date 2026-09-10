package io.motionguard.core

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.PI

/** One Euro filter for one 2D landmark. It is adaptive: smooth when still, responsive when moving. */
internal class OneEuroPointFilter(
    baseAlpha: Float,
    referenceFrameIntervalMs: Float,
    private val beta: Float = 0.045f,
    private val derivativeCutoff: Float = 1.0f,
) {
    private val referenceIntervalSeconds = referenceFrameIntervalMs.coerceAtLeast(1f) / 1_000f
    private val timeConstantSeconds = -referenceIntervalSeconds / ln(1f - baseAlpha.coerceIn(.01f, .99f))
    private val minCutoff = 1f / (2f * PI.toFloat() * timeConstantSeconds.coerceAtLeast(.001f))
    private var previousTimestampMs: Long? = null
    private var x: Float? = null
    private var y: Float? = null
    private var dx = 0f
    private var dy = 0f

    fun filter(point: Point2D, timestampMs: Long, lowConfidenceCutoffScale: Float = .35f): Point2D {
        val oldTimestamp = previousTimestampMs
        if (oldTimestamp == null || timestampMs <= oldTimestamp || timestampMs - oldTimestamp > 2_500L) {
            previousTimestampMs = timestampMs
            x = point.x; y = point.y; dx = 0f; dy = 0f
            return point
        }
        val dt = max(.001f, (timestampMs - oldTimestamp).toFloat() / 1_000f)
        val currentX = x ?: point.x
        val currentY = y ?: point.y
        dx = lowPass((point.x - currentX) / dt, dx, alpha(derivativeCutoff, dt))
        dy = lowPass((point.y - currentY) / dt, dy, alpha(derivativeCutoff, dt))
        val confidenceRatio = point.confidence.coerceIn(0f, 1f)
        val cutoffScale = (lowConfidenceCutoffScale + (1f - lowConfidenceCutoffScale) * confidenceRatio).coerceIn(.1f, 1f)
        val ax = alpha((minCutoff + beta * abs(dx)) * cutoffScale, dt)
        val ay = alpha((minCutoff + beta * abs(dy)) * cutoffScale, dt)
        x = lowPass(point.x, currentX, ax)
        y = lowPass(point.y, currentY, ay)
        previousTimestampMs = timestampMs
        return Point2D(x!!, y!!, point.confidence)
    }

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoff.coerceAtLeast(.01f))
        return (1f / (1f + tau / dt)).coerceIn(.01f, 1f)
    }

    private fun lowPass(value: Float, previous: Float, a: Float): Float = a * value + (1f - a) * previous
}
