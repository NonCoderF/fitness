package io.motionguard.mlkit

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import io.motionguard.core.Point2D
import io.motionguard.core.PoseFrame

class MlKitPoseAdapter(
    private val mirrorHorizontally: Boolean,
) {
    fun toPoseFrame(
        pose: Pose,
        imageWidth: Int,
        imageHeight: Int,
        timestampMs: Long,
    ): PoseFrame {
        fun landmark(type: Int): Point2D? {
            val poseLandmark = pose.getPoseLandmark(type) ?: return null
            val rawX = (poseLandmark.position.x / imageWidth.toFloat()).coerceIn(0f, 1f)
            val x = if (mirrorHorizontally) 1f - rawX else rawX
            val y = (poseLandmark.position.y / imageHeight.toFloat()).coerceIn(0f, 1f)
            return Point2D(
                x = x,
                y = y,
                confidence = poseLandmark.inFrameLikelihood,
            )
        }

        return PoseFrame(
            timestampMs = timestampMs,
            leftShoulder = landmark(PoseLandmark.LEFT_SHOULDER),
            rightShoulder = landmark(PoseLandmark.RIGHT_SHOULDER),
            leftElbow = landmark(PoseLandmark.LEFT_ELBOW),
            rightElbow = landmark(PoseLandmark.RIGHT_ELBOW),
            leftWrist = landmark(PoseLandmark.LEFT_WRIST),
            rightWrist = landmark(PoseLandmark.RIGHT_WRIST),
            leftHip = landmark(PoseLandmark.LEFT_HIP),
            rightHip = landmark(PoseLandmark.RIGHT_HIP),
            leftKnee = landmark(PoseLandmark.LEFT_KNEE),
            rightKnee = landmark(PoseLandmark.RIGHT_KNEE),
            leftAnkle = landmark(PoseLandmark.LEFT_ANKLE),
            rightAnkle = landmark(PoseLandmark.RIGHT_ANKLE),
            imageWidth = imageWidth,
            imageHeight = imageHeight,
        )
    }
}
