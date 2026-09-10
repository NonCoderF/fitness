package io.motionguard.core

import java.util.ArrayDeque
import java.util.EnumMap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Per-joint SMA + EMA whose strength is derived from that joint's confidence. */
public class ConfidenceAdaptivePoseSmoother(
    private val minimumWindow: Int = 3,
    private val maximumWindow: Int = 7,
    private val minimumAlpha: Float = .20f,
    private val maximumAlpha: Float = .55f,
) {
    private val history = ArrayDeque<PoseFrame>()
    private val previous = ConcurrentHashMap<BodyJoint, Point2D>()
    private val sampleX = EnumMap<BodyJoint, FloatArray>(BodyJoint::class.java).apply { BodyJoint.values().forEach { put(it, FloatArray(maximumWindow)) } }
    private val sampleY = EnumMap<BodyJoint, FloatArray>(BodyJoint::class.java).apply { BodyJoint.values().forEach { put(it, FloatArray(maximumWindow)) } }
    private var lastTimestampMs: Long? = null

    public fun reset() { history.clear(); previous.clear(); lastTimestampMs = null }

    public fun filter(frame: PoseFrame): PoseFrame {
        if (lastTimestampMs != null && frame.timestampMs - lastTimestampMs!! > 2_500L) reset()
        lastTimestampMs = frame.timestampMs
        history.addLast(frame)
        while (history.size > maximumWindow) history.removeFirst()

        return frame.copy(
            leftShoulder = smoothPoint(BodyJoint.LEFT_SHOULDER, frame.leftShoulder), rightShoulder = smoothPoint(BodyJoint.RIGHT_SHOULDER, frame.rightShoulder),
            leftElbow = smoothPoint(BodyJoint.LEFT_ELBOW, frame.leftElbow), rightElbow = smoothPoint(BodyJoint.RIGHT_ELBOW, frame.rightElbow),
            leftWrist = smoothPoint(BodyJoint.LEFT_WRIST, frame.leftWrist), rightWrist = smoothPoint(BodyJoint.RIGHT_WRIST, frame.rightWrist),
            leftHip = smoothPoint(BodyJoint.LEFT_HIP, frame.leftHip), rightHip = smoothPoint(BodyJoint.RIGHT_HIP, frame.rightHip),
            leftKnee = smoothPoint(BodyJoint.LEFT_KNEE, frame.leftKnee), rightKnee = smoothPoint(BodyJoint.RIGHT_KNEE, frame.rightKnee),
            leftAnkle = smoothPoint(BodyJoint.LEFT_ANKLE, frame.leftAnkle), rightAnkle = smoothPoint(BodyJoint.RIGHT_ANKLE, frame.rightAnkle),
        )
    }

    /** Experimental parallel path: joints run concurrently, while frames remain ordered. */
    public suspend fun filterParallel(frame: PoseFrame): PoseFrame = coroutineScope {
        if (lastTimestampMs != null && frame.timestampMs - lastTimestampMs!! > 2_500L) reset()
        lastTimestampMs = frame.timestampMs
        history.addLast(frame)
        while (history.size > maximumWindow) history.removeFirst()
        val results = BodyJoint.values().map { joint -> async(Dispatchers.Default) { joint to smoothPoint(joint, frame.point(joint)) } }.awaitAll().toMap()
        frame.copy(
            leftShoulder = results[BodyJoint.LEFT_SHOULDER], rightShoulder = results[BodyJoint.RIGHT_SHOULDER],
            leftElbow = results[BodyJoint.LEFT_ELBOW], rightElbow = results[BodyJoint.RIGHT_ELBOW],
            leftWrist = results[BodyJoint.LEFT_WRIST], rightWrist = results[BodyJoint.RIGHT_WRIST],
            leftHip = results[BodyJoint.LEFT_HIP], rightHip = results[BodyJoint.RIGHT_HIP],
            leftKnee = results[BodyJoint.LEFT_KNEE], rightKnee = results[BodyJoint.RIGHT_KNEE],
            leftAnkle = results[BodyJoint.LEFT_ANKLE], rightAnkle = results[BodyJoint.RIGHT_ANKLE],
        )
    }

    private fun smoothPoint(joint: BodyJoint, current: Point2D?): Point2D? {
        if (current == null) return null
        val confidence = current.confidence.coerceIn(0f, 1f)
        val window = (maximumWindow - confidence * (maximumWindow - minimumWindow)).toInt().coerceIn(minimumWindow, maximumWindow)
        var count = 0
        val iterator = history.descendingIterator()
        val xSamples = sampleX[joint]!!
        val ySamples = sampleY[joint]!!
        while (iterator.hasNext() && count < window) {
            iterator.next().point(joint)?.let {
                xSamples[count] = it.x
                ySamples[count] = it.y
                count++
            }
        }
        if (count == 0) return null
        var sumX = 0f
        var sumY = 0f
        for (index in 0 until count) {
            sumX += xSamples[index]
            sumY += ySamples[index]
        }
        val average = Point2D(sumX / count, sumY / count, confidence)
        val alpha = minimumAlpha + (maximumAlpha - minimumAlpha) * confidence
        val old = previous[joint]
        val output = if (old == null) average else Point2D(alpha * average.x + (1f - alpha) * old.x, alpha * average.y + (1f - alpha) * old.y, current.confidence)
        previous[joint] = output
        return output
    }

    private fun PoseFrame.point(joint: BodyJoint): Point2D? = when (joint) {
        BodyJoint.LEFT_SHOULDER -> leftShoulder; BodyJoint.RIGHT_SHOULDER -> rightShoulder
        BodyJoint.LEFT_ELBOW -> leftElbow; BodyJoint.RIGHT_ELBOW -> rightElbow
        BodyJoint.LEFT_WRIST -> leftWrist; BodyJoint.RIGHT_WRIST -> rightWrist
        BodyJoint.LEFT_HIP -> leftHip; BodyJoint.RIGHT_HIP -> rightHip
        BodyJoint.LEFT_KNEE -> leftKnee; BodyJoint.RIGHT_KNEE -> rightKnee
        BodyJoint.LEFT_ANKLE -> leftAnkle; BodyJoint.RIGHT_ANKLE -> rightAnkle
    }
}
