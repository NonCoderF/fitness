package io.motionguard.core

import java.util.ArrayDeque
import java.util.EnumMap
import kotlin.math.max

/**
 * Causal Whittaker-Eilers smoother for pose landmarks.
 *
 * Each joint is fitted independently over a bounded trailing window. The
 * second-difference penalty removes frame-to-frame shake while preserving
 * gradual movement. Missing current joints are not held in place.
 */
public class WhittakerEilersPoseSmoother(
    private val windowSize: Int = 9,
    private val lambda: Double = 24.0,
    private val resetAfterGapMs: Long = 2_500L,
) {
    private val history = ArrayDeque<PoseFrame>()
    private var lastTimestampMs: Long? = null

    public fun reset() {
        history.clear()
        lastTimestampMs = null
    }

    public fun filter(frame: PoseFrame): PoseFrame {
        if (lastTimestampMs != null && frame.timestampMs - lastTimestampMs!! > resetAfterGapMs) reset()
        lastTimestampMs = frame.timestampMs
        history.addLast(frame)
        while (history.size > windowSize) history.removeFirst()

        fun smooth(joint: BodyJoint, current: Point2D?): Point2D? {
            if (current == null) return null
            val samples = history.mapNotNull { it.point(joint) }
            if (samples.size < 2) return current
            val weights = DoubleArray(samples.size) { index ->
                val confidence = samples[index].confidence.coerceIn(0f, 1f).toDouble()
                max(0.08, confidence * confidence)
            }
            val x = smoothSeries(samples.map { it.x.toDouble() }.toDoubleArray(), weights)
            val y = smoothSeries(samples.map { it.y.toDouble() }.toDoubleArray(), weights)
            return Point2D(x.toFloat(), y.toFloat(), current.confidence)
        }

        return frame.copy(
            leftShoulder = smooth(BodyJoint.LEFT_SHOULDER, frame.leftShoulder), rightShoulder = smooth(BodyJoint.RIGHT_SHOULDER, frame.rightShoulder),
            leftElbow = smooth(BodyJoint.LEFT_ELBOW, frame.leftElbow), rightElbow = smooth(BodyJoint.RIGHT_ELBOW, frame.rightElbow),
            leftWrist = smooth(BodyJoint.LEFT_WRIST, frame.leftWrist), rightWrist = smooth(BodyJoint.RIGHT_WRIST, frame.rightWrist),
            leftHip = smooth(BodyJoint.LEFT_HIP, frame.leftHip), rightHip = smooth(BodyJoint.RIGHT_HIP, frame.rightHip),
            leftKnee = smooth(BodyJoint.LEFT_KNEE, frame.leftKnee), rightKnee = smooth(BodyJoint.RIGHT_KNEE, frame.rightKnee),
            leftAnkle = smooth(BodyJoint.LEFT_ANKLE, frame.leftAnkle), rightAnkle = smooth(BodyJoint.RIGHT_ANKLE, frame.rightAnkle),
        )
    }

    private fun smoothSeries(values: DoubleArray, weights: DoubleArray): Double {
        val n = values.size
        val matrix = Array(n) { DoubleArray(n) }
        val rhs = DoubleArray(n)
        for (i in 0 until n) {
            matrix[i][i] += weights[i]
            rhs[i] = weights[i] * values[i]
            if (i >= 2) {
                val indices = intArrayOf(i - 2, i - 1, i)
                val coefficients = doubleArrayOf(1.0, -2.0, 1.0)
                for (a in indices.indices) for (b in indices.indices) {
                    matrix[indices[a]][indices[b]] += lambda * coefficients[a] * coefficients[b]
                }
            }
        }
        return solve(matrix, rhs).last()
    }

    // Small bounded systems are intentional here; this keeps the filter deterministic and dependency-free.
    private fun solve(matrix: Array<DoubleArray>, rhs: DoubleArray): DoubleArray {
        val n = rhs.size
        for (column in 0 until n) {
            var pivot = column
            for (row in column + 1 until n) if (kotlin.math.abs(matrix[row][column]) > kotlin.math.abs(matrix[pivot][column])) pivot = row
            val pivotRow = matrix[pivot]
            matrix[pivot] = matrix[column]
            matrix[column] = pivotRow
            val pivotValue = matrix[column][column].coerceAtLeast(1e-9)
            for (row in column + 1 until n) {
                val factor = matrix[row][column] / pivotValue
                if (factor == 0.0) continue
                for (j in column until n) matrix[row][j] -= factor * matrix[column][j]
                rhs[row] -= factor * rhs[column]
            }
        }
        val result = DoubleArray(n)
        for (row in n - 1 downTo 0) {
            var value = rhs[row]
            for (j in row + 1 until n) value -= matrix[row][j] * result[j]
            result[row] = value / matrix[row][row].coerceAtLeast(1e-9)
        }
        return result
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
