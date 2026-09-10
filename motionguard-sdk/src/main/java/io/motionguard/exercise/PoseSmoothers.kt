package io.motionguard.exercise

public interface PoseSmoother {
    public fun filter(frame: PoseFrame): PoseFrame
    public fun reset()
}

public class EmaPoseSmoother(
    private val alpha: Float = 0.45f,
) : PoseSmoother {
    private val previous = mutableMapOf<BodyPart, KeyPoint>()

    override fun filter(frame: PoseFrame): PoseFrame {
        val a = alpha.coerceIn(0f, 1f)
        val smoothed = frame.keypoints.map { current ->
            val prior = previous[current.type]
            val value = if (prior == null) {
                current
            } else {
                current.copy(
                    x = a * current.x + (1f - a) * prior.x,
                    y = a * current.y + (1f - a) * prior.y,
                    confidence = a * current.confidence + (1f - a) * prior.confidence,
                )
            }
            previous[current.type] = value
            value
        }
        return frame.copy(keypoints = smoothed)
    }

    override fun reset() {
        previous.clear()
    }
}

public class SmaPoseSmoother(
    private val windowSize: Int = 3,
) : PoseSmoother {
    private val history = ArrayDeque<PoseFrame>()

    override fun filter(frame: PoseFrame): PoseFrame {
        val size = windowSize.coerceAtLeast(1)
        history += frame
        while (history.size > size) history.removeFirst()

        val smoothed = frame.keypoints.map { current ->
            val samples = history.mapNotNull { it[current.type] }
            if (samples.isEmpty()) {
                current
            } else {
                current.copy(
                    x = samples.map { it.x }.average().toFloat(),
                    y = samples.map { it.y }.average().toFloat(),
                    confidence = samples.map { it.confidence }.average().toFloat(),
                )
            }
        }
        return frame.copy(keypoints = smoothed)
    }

    override fun reset() {
        history.clear()
    }
}
