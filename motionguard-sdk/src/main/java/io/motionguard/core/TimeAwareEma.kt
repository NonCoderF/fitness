package io.motionguard.core

import kotlin.math.exp
import kotlin.math.ln

/**
 * Converts the configured alpha at the reference frame interval into an alpha for
 * the actual timestamp gap. This keeps smoothing consistent when FPS varies.
 */
internal fun timeAwareAlpha(baseAlpha: Float, deltaMs: Long, referenceFrameIntervalMs: Float = 33.333f): Float {
    val base = baseAlpha.coerceIn(.01f, .99f)
    val interval = referenceFrameIntervalMs.coerceAtLeast(1f)
    val timeConstant = -interval / ln(1f - base)
    return (1f - exp(-deltaMs.coerceAtLeast(1L).toFloat() / timeConstant)).coerceIn(.01f, 1f)
}
