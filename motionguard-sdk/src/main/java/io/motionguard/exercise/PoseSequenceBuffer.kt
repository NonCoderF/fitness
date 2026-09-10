package io.motionguard.exercise

public class PoseSequenceBuffer(
    private val sequenceLength: Int = ExerciseFeatureEncoder.SEQUENCE_LENGTH,
    private val encoder: ExerciseFeatureEncoder = ExerciseFeatureEncoder(),
) {
    private val frames = ArrayDeque<PoseFeatures>()
    private val tensor = FloatArray(sequenceLength * ExerciseFeatureEncoder.FEATURE_COUNT)
    private val encodedFrame = FloatArray(ExerciseFeatureEncoder.FEATURE_COUNT)

    public val isReady: Boolean get() = frames.size >= sequenceLength

    public fun append(features: PoseFeatures) {
        frames += features
        while (frames.size > sequenceLength) frames.removeFirst()
    }

    public fun sequence(): List<PoseFeatures> = frames.toList()

    public fun temporalTensor(): FloatArray {
        tensor.fill(0f)
        frames.forEachIndexed { frameIndex, features ->
            val offset = frameIndex * ExerciseFeatureEncoder.FEATURE_COUNT
            encoder.encodeInto(features, encodedFrame)
            encodedFrame.copyInto(tensor, destinationOffset = offset)
        }
        return tensor
    }

    public fun reset() {
        frames.clear()
        tensor.fill(0f)
    }
}
