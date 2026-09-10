package io.motionguard.exercise

import kotlin.math.abs

public data class MovementVector(
    val velocity: Float = 0f,
    val directionX: Float = 0f,
    val directionY: Float = 0f,
)

public data class PoseFeatures(
    val timestamp: Long,
    val leftElbowAngle: Float = 0f,
    val rightElbowAngle: Float = 0f,
    val leftShoulderAngle: Float = 0f,
    val rightShoulderAngle: Float = 0f,
    val leftHipAngle: Float = 0f,
    val rightHipAngle: Float = 0f,
    val leftKneeAngle: Float = 0f,
    val rightKneeAngle: Float = 0f,
    val torsoAngle: Float = 0f,
    val shoulderWidth: Float = 0f,
    val ankleDistance: Float = 0f,
    val wristDistance: Float = 0f,
    val hipVerticalPosition: Float = 0f,
    val wristRelativeToHeadPosition: Float = 0f,
    val kneeVerticalPosition: Float = 0f,
    val jointVelocities: Map<BodyPart, Float> = emptyMap(),
    val jointDirections: Map<BodyPart, MovementVector> = emptyMap(),
    val leftRightSymmetry: Float = 0f,
    val averageConfidence: Float = 0f,
    val normalizedPoints: Map<BodyPart, Point> = emptyMap(),
)

public class PoseFeatureExtractor(
    private val minimumConfidence: Float = 0.2f,
) {
    private var previousFrame: PoseFrame? = null

    public fun extract(frame: PoseFrame): PoseFeatures {
        fun p(part: BodyPart): Point? = frame.point(part, minimumConfidence)
        fun jointAngle(a: BodyPart, b: BodyPart, c: BodyPart): Float {
            val pa = p(a)
            val pb = p(b)
            val pc = p(c)
            return if (pa != null && pb != null && pc != null) angle(pa, pb, pc) else 0f
        }

        val leftShoulder = p(BodyPart.LEFT_SHOULDER)
        val rightShoulder = p(BodyPart.RIGHT_SHOULDER)
        val leftHip = p(BodyPart.LEFT_HIP)
        val rightHip = p(BodyPart.RIGHT_HIP)
        val leftAnkle = p(BodyPart.LEFT_ANKLE)
        val rightAnkle = p(BodyPart.RIGHT_ANKLE)
        val leftWrist = p(BodyPart.LEFT_WRIST)
        val rightWrist = p(BodyPart.RIGHT_WRIST)
        val leftKnee = p(BodyPart.LEFT_KNEE)
        val rightKnee = p(BodyPart.RIGHT_KNEE)
        val head = p(BodyPart.NOSE)

        val shoulderCenter = center(leftShoulder, rightShoulder)
        val hipCenter = center(leftHip, rightHip)
        val kneeCenter = center(leftKnee, rightKnee)
        val wristCenter = center(leftWrist, rightWrist)
        val velocities = velocities(frame)
        val directions = directions(frame, velocities)

        val features = PoseFeatures(
            timestamp = frame.timestamp,
            leftElbowAngle = jointAngle(BodyPart.LEFT_SHOULDER, BodyPart.LEFT_ELBOW, BodyPart.LEFT_WRIST),
            rightElbowAngle = jointAngle(BodyPart.RIGHT_SHOULDER, BodyPart.RIGHT_ELBOW, BodyPart.RIGHT_WRIST),
            leftShoulderAngle = jointAngle(BodyPart.LEFT_ELBOW, BodyPart.LEFT_SHOULDER, BodyPart.LEFT_HIP),
            rightShoulderAngle = jointAngle(BodyPart.RIGHT_ELBOW, BodyPart.RIGHT_SHOULDER, BodyPart.RIGHT_HIP),
            leftHipAngle = jointAngle(BodyPart.LEFT_SHOULDER, BodyPart.LEFT_HIP, BodyPart.LEFT_KNEE),
            rightHipAngle = jointAngle(BodyPart.RIGHT_SHOULDER, BodyPart.RIGHT_HIP, BodyPart.RIGHT_KNEE),
            leftKneeAngle = jointAngle(BodyPart.LEFT_HIP, BodyPart.LEFT_KNEE, BodyPart.LEFT_ANKLE),
            rightKneeAngle = jointAngle(BodyPart.RIGHT_HIP, BodyPart.RIGHT_KNEE, BodyPart.RIGHT_ANKLE),
            torsoAngle = torsoAngle(shoulderCenter, hipCenter),
            shoulderWidth = if (leftShoulder != null && rightShoulder != null) distance(leftShoulder, rightShoulder) else 0f,
            ankleDistance = if (leftAnkle != null && rightAnkle != null) distance(leftAnkle, rightAnkle) else 0f,
            wristDistance = if (leftWrist != null && rightWrist != null) distance(leftWrist, rightWrist) else 0f,
            hipVerticalPosition = hipCenter?.y ?: 0f,
            wristRelativeToHeadPosition = if (wristCenter != null && head != null) wristCenter.y - head.y else 0f,
            kneeVerticalPosition = kneeCenter?.y ?: 0f,
            jointVelocities = velocities,
            jointDirections = directions,
            leftRightSymmetry = symmetry(frame),
            averageConfidence = frame.keypoints.map { it.confidence }.average().toFloat().coerceIn(0f, 1f),
            normalizedPoints = frame.keypoints.associate { it.type to Point(it.x, it.y) },
        )
        previousFrame = frame
        return features
    }

    public fun reset() {
        previousFrame = null
    }

    private fun velocities(frame: PoseFrame): Map<BodyPart, Float> {
        val previous = previousFrame ?: return emptyMap()
        val dt = ((frame.timestamp - previous.timestamp).coerceAtLeast(1L) / 1000f)
        return BodyPart.entries.mapNotNull { part ->
            val a = previous.point(part, minimumConfidence)
            val b = frame.point(part, minimumConfidence)
            if (a != null && b != null) part to velocity(a, b, dt) else null
        }.toMap()
    }

    private fun directions(frame: PoseFrame, velocities: Map<BodyPart, Float>): Map<BodyPart, MovementVector> {
        val previous = previousFrame ?: return emptyMap()
        return BodyPart.entries.mapNotNull { part ->
            val a = previous.point(part, minimumConfidence)
            val b = frame.point(part, minimumConfidence)
            val speed = velocities[part]
            if (a != null && b != null && speed != null) {
                part to MovementVector(speed, b.x - a.x, b.y - a.y)
            } else {
                null
            }
        }.toMap()
    }

    private fun symmetry(frame: PoseFrame): Float {
        val pairs = listOf(
            BodyPart.LEFT_SHOULDER to BodyPart.RIGHT_SHOULDER,
            BodyPart.LEFT_ELBOW to BodyPart.RIGHT_ELBOW,
            BodyPart.LEFT_WRIST to BodyPart.RIGHT_WRIST,
            BodyPart.LEFT_HIP to BodyPart.RIGHT_HIP,
            BodyPart.LEFT_KNEE to BodyPart.RIGHT_KNEE,
            BodyPart.LEFT_ANKLE to BodyPart.RIGHT_ANKLE,
        )
        val deltas = pairs.mapNotNull { (left, right) ->
            val l = frame.point(left, minimumConfidence)
            val r = frame.point(right, minimumConfidence)
            if (l != null && r != null) abs(abs(l.x) - abs(r.x)) + abs(l.y - r.y) else null
        }
        if (deltas.isEmpty()) return 0f
        return (1f - deltas.average().toFloat()).coerceIn(0f, 1f)
    }

    private fun center(a: Point?, b: Point?): Point? = when {
        a != null && b != null -> Point((a.x + b.x) / 2f, (a.y + b.y) / 2f)
        a != null -> a
        b != null -> b
        else -> null
    }

    private fun torsoAngle(shoulderCenter: Point?, hipCenter: Point?): Float {
        if (shoulderCenter == null || hipCenter == null) return 0f
        return Math.toDegrees(kotlin.math.atan2((shoulderCenter.x - hipCenter.x).toDouble(), (hipCenter.y - shoulderCenter.y).toDouble())).toFloat()
    }
}
