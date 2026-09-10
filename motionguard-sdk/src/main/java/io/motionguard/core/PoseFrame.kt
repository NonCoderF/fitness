package io.motionguard.core

/** One timestamped internal pose sample. Coordinates are normalized to the camera frame, 0.0 to 1.0. */
public data class PoseFrame(
    val timestampMs: Long,
    val leftShoulder: Point2D? = null,
    val rightShoulder: Point2D? = null,
    val leftElbow: Point2D? = null,
    val rightElbow: Point2D? = null,
    val leftWrist: Point2D? = null,
    val rightWrist: Point2D? = null,
    val leftHip: Point2D?,
    val rightHip: Point2D?,
    val leftKnee: Point2D?,
    val rightKnee: Point2D?,
    val leftAnkle: Point2D?,
    val rightAnkle: Point2D?,
    /** Dimensions of the normalized image after CameraX rotation, when known. */
    val imageWidth: Int = 1,
    val imageHeight: Int = 1,
) {
    val hasLowerBodyPose: Boolean
        get() = listOf(leftHip, rightHip, leftKnee, rightKnee, leftAnkle, rightAnkle)
            .count { it != null && it.confidence >= 0.45f } >= 4
}
