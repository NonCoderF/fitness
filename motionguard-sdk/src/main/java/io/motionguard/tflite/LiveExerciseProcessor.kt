package io.motionguard.tflite

import io.motionguard.exercise.ExerciseEngine
import io.motionguard.exercise.ExerciseResult
import java.util.concurrent.atomic.AtomicBoolean

public class LiveExerciseProcessor(
    private val estimator: PoseEstimator,
    private val engine: ExerciseEngine = ExerciseEngine(),
) {
    private val running = AtomicBoolean(false)

    public suspend fun processLatest(input: PoseEstimatorInput): ExerciseResult? {
        if (!running.compareAndSet(false, true)) return null
        return try {
            val estimate = estimator.estimate(input)
            engine.process(estimate.frame, estimate.inferenceMs)
        } finally {
            running.set(false)
        }
    }

    public fun reset() {
        engine.reset()
    }

    public fun close() {
        estimator.close()
    }
}
