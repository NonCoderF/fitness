package io.motionguard.exercise

import kotlin.math.max

public class PoseNormalizer(
    private val origin: Origin = Origin.HIP_CENTER,
    private val minimumScale: Float = 0.05f,
) {
    public enum class Origin { HIP_CENTER, TORSO_CENTER }

    public fun normalize(frame: PoseFrame): PoseFrame {
        val leftHip = frame.point(BodyPart.LEFT_HIP) ?: return frame
        val rightHip = frame.point(BodyPart.RIGHT_HIP) ?: return frame
        val hipCenter = midpoint(leftHip, rightHip)
        val torsoCenter = torsoCenter(frame) ?: hipCenter
        val originPoint = if (origin == Origin.TORSO_CENTER) torsoCenter else hipCenter
        val scale = stableScale(frame, hipCenter).coerceAtLeast(minimumScale)
        return frame.copy(
            keypoints = frame.keypoints.map {
                it.copy(x = (it.x - originPoint.x) / scale, y = (it.y - originPoint.y) / scale)
            },
        )
    }

    public fun toMotionFrame(frame: PoseFrame, features: PoseFeatures): MotionFrame {
        val normalized = FloatArray(BodyPart.entries.size * 3)
        BodyPart.entries.forEachIndexed { index, bodyPart ->
            val point = frame[bodyPart]
            normalized[index * 3] = point?.x ?: 0f
            normalized[index * 3 + 1] = point?.y ?: 0f
            normalized[index * 3 + 2] = point?.confidence ?: 0f
        }
        return MotionFrame(
            timestamp = frame.timestamp,
            normalizedKeypoints = normalized,
            jointAngles = floatArrayOf(
                features.leftElbowAngle,
                features.rightElbowAngle,
                features.leftShoulderAngle,
                features.rightShoulderAngle,
                features.leftHipAngle,
                features.rightHipAngle,
                features.leftKneeAngle,
                features.rightKneeAngle,
                features.torsoAngle,
            ),
        )
    }

    private fun stableScale(frame: PoseFrame, hipCenter: Point): Float {
        val leftShoulder = frame.point(BodyPart.LEFT_SHOULDER)
        val rightShoulder = frame.point(BodyPart.RIGHT_SHOULDER)
        val shoulderWidth = if (leftShoulder != null && rightShoulder != null) distance(leftShoulder, rightShoulder) else 0f
        val torso = torsoCenter(frame)?.let { distance(hipCenter, it) } ?: 0f
        val leg = listOfNotNull(frame.point(BodyPart.LEFT_KNEE), frame.point(BodyPart.RIGHT_KNEE), frame.point(BodyPart.LEFT_ANKLE), frame.point(BodyPart.RIGHT_ANKLE))
            .map { distance(hipCenter, it) }
            .maxOrNull() ?: 0f
        return max(max(shoulderWidth, torso), leg)
    }

    private fun torsoCenter(frame: PoseFrame): Point? {
        val leftShoulder = frame.point(BodyPart.LEFT_SHOULDER) ?: return null
        val rightShoulder = frame.point(BodyPart.RIGHT_SHOULDER) ?: return null
        val leftHip = frame.point(BodyPart.LEFT_HIP) ?: return null
        val rightHip = frame.point(BodyPart.RIGHT_HIP) ?: return null
        return midpoint(midpoint(leftShoulder, rightShoulder), midpoint(leftHip, rightHip))
    }

    private fun midpoint(a: Point, b: Point): Point = Point((a.x + b.x) / 2f, (a.y + b.y) / 2f)
}
