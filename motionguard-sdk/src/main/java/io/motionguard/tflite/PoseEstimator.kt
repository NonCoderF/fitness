package io.motionguard.tflite

import io.motionguard.exercise.PoseFrame

public data class PoseEstimatorInput(
    val timestamp: Long,
    val width: Int,
    val height: Int,
    val argbPixels: IntArray,
)

public data class PoseEstimate(
    val frame: PoseFrame,
    val inferenceMs: Long,
)

public interface PoseEstimator : AutoCloseable {
    public suspend fun estimate(input: PoseEstimatorInput): PoseEstimate
    override fun close()
}

public data class MoveNetModelConfig(
    val assetName: String = MoveNetModelConfig.MOVENET_LIGHTNING_ASSET,
    val inputSize: Int = 192,
    val outputKeypoints: Int = 17,
    val numThreads: Int = 2,
) {
    public companion object {
        public const val MOVENET_LIGHTNING_ASSET: String = "movenet_lightning.tflite"
        public const val MOVENET_THUNDER_ASSET: String = "movenet_thunder.tflite"
    }
}
