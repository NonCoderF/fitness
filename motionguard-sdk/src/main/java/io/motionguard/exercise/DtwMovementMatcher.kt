package io.motionguard.exercise

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

public data class DtwMovementMatcherConfig(
    val positionWeight: Float = 0.55f,
    val angleWeight: Float = 0.45f,
    val maxCostForZeroSimilarity: Float = 2.25f,
    val startingPoseThreshold: Float = 0.72f,
    val excellentThreshold: Float = 0.90f,
    val goodThreshold: Float = 0.80f,
    val fairThreshold: Float = 0.65f,
)

public class DtwMovementMatcher(
    private val config: DtwMovementMatcherConfig = DtwMovementMatcherConfig(),
) {
    public fun compare(
        reference: List<MotionFrame>,
        live: List<MotionFrame>,
    ): MovementSimilarity {
        if (reference.isEmpty() || live.isEmpty()) return MovementSimilarity(0f, 0f, 0f, 0f)
        val arms = regionSimilarity(reference, live, ArmParts, ArmAngles)
        val legs = regionSimilarity(reference, live, LegParts, LegAngles)
        val torso = regionSimilarity(reference, live, TorsoParts, TorsoAngles)
        val overall = (arms * 0.34f + legs * 0.38f + torso * 0.28f).coerceIn(0f, 1f)
        return MovementSimilarity(overall, arms, legs, torso)
    }

    public fun startingPoseSimilarity(reference: List<MotionFrame>, live: MotionFrame): Float {
        if (reference.isEmpty()) return 0f
        val sampleCount = min(6, reference.size)
        val cost = reference.take(sampleCount).minOf { frameCost(it, live, BodyPart.entries.toSet(), AllAngleIndices) }
        return costToSimilarity(cost)
    }

    private fun regionSimilarity(
        reference: List<MotionFrame>,
        live: List<MotionFrame>,
        parts: Set<BodyPart>,
        angleIndices: Set<Int>,
    ): Float = costToSimilarity(dtwCost(reference, live, parts, angleIndices))

    private fun dtwCost(
        reference: List<MotionFrame>,
        live: List<MotionFrame>,
        parts: Set<BodyPart>,
        angleIndices: Set<Int>,
    ): Float {
        val previous = FloatArray(live.size + 1) { Float.POSITIVE_INFINITY }
        val current = FloatArray(live.size + 1) { Float.POSITIVE_INFINITY }
        previous[0] = 0f
        for (i in reference.indices) {
            current[0] = Float.POSITIVE_INFINITY
            for (j in live.indices) {
                val cost = frameCost(reference[i], live[j], parts, angleIndices)
                current[j + 1] = cost + minOf(previous[j + 1], current[j], previous[j])
            }
            for (j in current.indices) {
                previous[j] = current[j]
                current[j] = Float.POSITIVE_INFINITY
            }
        }
        return previous[live.size] / (reference.size + live.size).coerceAtLeast(1)
    }

    private fun frameCost(
        reference: MotionFrame,
        live: MotionFrame,
        parts: Set<BodyPart>,
        angleIndices: Set<Int>,
    ): Float {
        var positionCost = 0f
        var positionWeight = 0f
        parts.forEach { part ->
            val index = part.ordinal * 3
            if (index + 2 < reference.normalizedKeypoints.size && index + 2 < live.normalizedKeypoints.size) {
                val confidence = min(reference.normalizedKeypoints[index + 2], live.normalizedKeypoints[index + 2])
                if (confidence > 0f) {
                    val dx = reference.normalizedKeypoints[index] - live.normalizedKeypoints[index]
                    val dy = reference.normalizedKeypoints[index + 1] - live.normalizedKeypoints[index + 1]
                    positionCost += sqrt(dx * dx + dy * dy) * confidence
                    positionWeight += confidence
                }
            }
        }

        var angleCost = 0f
        var angleWeight = 0
        angleIndices.forEach { index ->
            if (index < reference.jointAngles.size && index < live.jointAngles.size) {
                angleCost += (abs(reference.jointAngles[index] - live.jointAngles[index]) / 180f).coerceIn(0f, 1f)
                angleWeight++
            }
        }

        val normalizedPosition = if (positionWeight > 0f) positionCost / positionWeight else 1f
        val normalizedAngle = if (angleWeight > 0) angleCost / angleWeight else 1f
        return normalizedPosition * config.positionWeight + normalizedAngle * config.angleWeight
    }

    private fun costToSimilarity(cost: Float): Float =
        (1f - cost / config.maxCostForZeroSimilarity).coerceIn(0f, 1f)

    private companion object {
        val ArmParts = setOf(
            BodyPart.LEFT_SHOULDER,
            BodyPart.RIGHT_SHOULDER,
            BodyPart.LEFT_ELBOW,
            BodyPart.RIGHT_ELBOW,
            BodyPart.LEFT_WRIST,
            BodyPart.RIGHT_WRIST,
        )
        val LegParts = setOf(
            BodyPart.LEFT_HIP,
            BodyPart.RIGHT_HIP,
            BodyPart.LEFT_KNEE,
            BodyPart.RIGHT_KNEE,
            BodyPart.LEFT_ANKLE,
            BodyPart.RIGHT_ANKLE,
        )
        val TorsoParts = setOf(
            BodyPart.LEFT_SHOULDER,
            BodyPart.RIGHT_SHOULDER,
            BodyPart.LEFT_HIP,
            BodyPart.RIGHT_HIP,
        )
        val ArmAngles = setOf(0, 1, 2, 3)
        val LegAngles = setOf(4, 5, 6, 7)
        val TorsoAngles = setOf(8)
        val AllAngleIndices = ArmAngles + LegAngles + TorsoAngles
    }
}
