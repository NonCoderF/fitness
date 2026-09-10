package io.motionguard.core

import java.util.ArrayDeque

/** Short, bounded temporal average for stable pose coordinates. */
public class PoseFrameSmaSmoother(private val windowSize: Int = 3) {
    private val history = ArrayDeque<PoseFrame>()
    private var lastTimestampMs: Long? = null

    public fun reset() { history.clear(); lastTimestampMs = null }

    public fun filter(frame: PoseFrame): PoseFrame {
        if (lastTimestampMs != null && frame.timestampMs - lastTimestampMs!! > 2_500L) reset()
        lastTimestampMs = frame.timestampMs
        history.addLast(frame)
        while (history.size > windowSize) history.removeFirst()
        fun average(select: (PoseFrame) -> Point2D?): Point2D? {
            if (select(frame) == null) return null
            val points = history.mapNotNull(select)
            return Point2D(
                points.map { it.x }.average().toFloat(),
                points.map { it.y }.average().toFloat(),
                points.map { it.confidence }.average().toFloat(),
            )
        }
        return frame.copy(
            leftShoulder = average { it.leftShoulder }, rightShoulder = average { it.rightShoulder },
            leftElbow = average { it.leftElbow }, rightElbow = average { it.rightElbow },
            leftWrist = average { it.leftWrist }, rightWrist = average { it.rightWrist },
            leftHip = average { it.leftHip }, rightHip = average { it.rightHip },
            leftKnee = average { it.leftKnee }, rightKnee = average { it.rightKnee },
            leftAnkle = average { it.leftAnkle }, rightAnkle = average { it.rightAnkle },
        )
    }
}
