package io.motionguard.core

import java.util.ArrayDeque

/** Rejects isolated landmark spikes while retaining the latest real movement. */
public class PoseFrameMedianFilter(private val windowSize: Int = 3) {
    private val history = ArrayDeque<PoseFrame>()
    private var lastTimestamp: Long? = null

    public fun reset() { history.clear(); lastTimestamp = null }

    public fun filter(frame: PoseFrame): PoseFrame {
        if (lastTimestamp != null && (frame.timestampMs - lastTimestamp!!) > 2_500L) reset()
        lastTimestamp = frame.timestampMs
        history.addLast(frame)
        while (history.size > windowSize) history.removeFirst()
        fun median(select: (PoseFrame) -> Point2D?): Point2D? {
            // Do not keep a stale joint alive when ML Kit lost it in the current frame.
            if (select(frame) == null) return null
            val points = history.mapNotNull(select)
            if (points.isEmpty()) return null
            val xs = points.map { it.x }.sorted()
            val ys = points.map { it.y }.sorted()
            val cs = points.map { it.confidence }.sorted()
            val middle = points.size / 2
            return Point2D(xs[middle], ys[middle], cs[middle])
        }
        return frame.copy(
            leftShoulder = median { it.leftShoulder }, rightShoulder = median { it.rightShoulder },
            leftElbow = median { it.leftElbow }, rightElbow = median { it.rightElbow },
            leftWrist = median { it.leftWrist }, rightWrist = median { it.rightWrist },
            leftHip = median { it.leftHip }, rightHip = median { it.rightHip },
            leftKnee = median { it.leftKnee }, rightKnee = median { it.rightKnee },
            leftAnkle = median { it.leftAnkle }, rightAnkle = median { it.rightAnkle },
        )
    }
}
