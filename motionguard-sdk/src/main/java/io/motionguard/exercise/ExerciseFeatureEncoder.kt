package io.motionguard.exercise

/**
 * Fixed feature order for the built-in exercise classifier training and inference pipeline.
 *
 * Per-joint position block, in MoveNet BodyPart enum order:
 *   NOSE_X, NOSE_Y, LEFT_EYE_X, LEFT_EYE_Y, RIGHT_EYE_X, RIGHT_EYE_Y,
 *   LEFT_EAR_X, LEFT_EAR_Y, RIGHT_EAR_X, RIGHT_EAR_Y,
 *   LEFT_SHOULDER_X, LEFT_SHOULDER_Y, RIGHT_SHOULDER_X, RIGHT_SHOULDER_Y,
 *   LEFT_ELBOW_X, LEFT_ELBOW_Y, RIGHT_ELBOW_X, RIGHT_ELBOW_Y,
 *   LEFT_WRIST_X, LEFT_WRIST_Y, RIGHT_WRIST_X, RIGHT_WRIST_Y,
 *   LEFT_HIP_X, LEFT_HIP_Y, RIGHT_HIP_X, RIGHT_HIP_Y,
 *   LEFT_KNEE_X, LEFT_KNEE_Y, RIGHT_KNEE_X, RIGHT_KNEE_Y,
 *   LEFT_ANKLE_X, LEFT_ANKLE_Y, RIGHT_ANKLE_X, RIGHT_ANKLE_Y
 *
 * Scalar block:
 *   LEFT_ELBOW_ANGLE, RIGHT_ELBOW_ANGLE, LEFT_SHOULDER_ANGLE, RIGHT_SHOULDER_ANGLE,
 *   LEFT_HIP_ANGLE, RIGHT_HIP_ANGLE, LEFT_KNEE_ANGLE, RIGHT_KNEE_ANGLE, TORSO_ANGLE,
 *   SHOULDER_WIDTH, ANKLE_DISTANCE, WRIST_DISTANCE, WRIST_RELATIVE_TO_HEAD,
 *   HIP_VERTICAL_POSITION, KNEE_VERTICAL_POSITION, LEFT_RIGHT_SYMMETRY, AVERAGE_CONFIDENCE
 *
 * Velocity/direction block, in MoveNet BodyPart enum order:
 *   <PART>_VELOCITY, <PART>_DIRECTION_X, <PART>_DIRECTION_Y
 */
public class ExerciseFeatureEncoder {
    public fun encode(features: PoseFeatures): FloatArray =
        FloatArray(FEATURE_COUNT).also { encodeInto(features, it) }

    public fun encodeInto(features: PoseFeatures, output: FloatArray) {
        require(output.size >= FEATURE_COUNT) { "Expected at least $FEATURE_COUNT features." }
        var index = 0
        BodyPart.entries.forEach { part ->
            val point = features.normalizedPoints[part]
            output[index++] = point?.x ?: 0f
            output[index++] = point?.y ?: 0f
        }
        output[index++] = features.leftElbowAngle / 180f
        output[index++] = features.rightElbowAngle / 180f
        output[index++] = features.leftShoulderAngle / 180f
        output[index++] = features.rightShoulderAngle / 180f
        output[index++] = features.leftHipAngle / 180f
        output[index++] = features.rightHipAngle / 180f
        output[index++] = features.leftKneeAngle / 180f
        output[index++] = features.rightKneeAngle / 180f
        output[index++] = features.torsoAngle / 180f
        output[index++] = features.shoulderWidth
        output[index++] = features.ankleDistance
        output[index++] = features.wristDistance
        output[index++] = features.wristRelativeToHeadPosition
        output[index++] = features.hipVerticalPosition
        output[index++] = features.kneeVerticalPosition
        output[index++] = features.leftRightSymmetry
        output[index++] = features.averageConfidence
        BodyPart.entries.forEach { part ->
            val direction = features.jointDirections[part]
            output[index++] = features.jointVelocities[part] ?: 0f
            output[index++] = direction?.directionX ?: 0f
            output[index++] = direction?.directionY ?: 0f
        }
        while (index < FEATURE_COUNT) output[index++] = 0f
    }

    public companion object {
        public const val SEQUENCE_LENGTH: Int = 30
        public const val FEATURE_COUNT: Int = 102
    }
}
