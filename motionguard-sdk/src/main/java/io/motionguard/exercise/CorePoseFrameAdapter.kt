package io.motionguard.exercise

import io.motionguard.core.Point2D

public object CorePoseFrameAdapter {
    public fun fromCore(frame: io.motionguard.core.PoseFrame): PoseFrame {
        fun point(type: BodyPart, point: Point2D?): KeyPoint? =
            point?.let { KeyPoint(type, it.x, it.y, it.confidence) }

        val keypoints = listOfNotNull(
            point(BodyPart.LEFT_SHOULDER, frame.leftShoulder),
            point(BodyPart.RIGHT_SHOULDER, frame.rightShoulder),
            point(BodyPart.LEFT_ELBOW, frame.leftElbow),
            point(BodyPart.RIGHT_ELBOW, frame.rightElbow),
            point(BodyPart.LEFT_WRIST, frame.leftWrist),
            point(BodyPart.RIGHT_WRIST, frame.rightWrist),
            point(BodyPart.LEFT_HIP, frame.leftHip),
            point(BodyPart.RIGHT_HIP, frame.rightHip),
            point(BodyPart.LEFT_KNEE, frame.leftKnee),
            point(BodyPart.RIGHT_KNEE, frame.rightKnee),
            point(BodyPart.LEFT_ANKLE, frame.leftAnkle),
            point(BodyPart.RIGHT_ANKLE, frame.rightAnkle),
        )
        return PoseFrame(frame.timestampMs, keypoints)
    }
}
