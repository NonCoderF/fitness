package io.motionguard.exercise

import kotlin.system.measureTimeMillis

public class ExerciseEngine(
    config: PosePipelineConfig = PosePipelineConfig(),
    private val squatDetector: ExerciseDetector = SquatDetector(),
    private val pushUpDetector: ExerciseDetector = PushUpDetector(),
    private val jumpingJackDetector: ExerciseDetector = JumpingJackDetector(),
    private val gaitDetector: ExerciseDetector = GaitDetector(),
) {
    private val ema = EmaPoseSmoother(config.emaAlpha)
    private val sma = config.smaWindow.takeIf { it > 1 }?.let { SmaPoseSmoother(it) }
    private val normalizer = PoseNormalizer(config.normalizerOrigin)
    private val extractor = PoseFeatureExtractor(config.minimumConfidence)
    private var lastResultTimestamp = Long.MIN_VALUE

    public fun process(rawFrame: PoseFrame, inferenceMs: Long = 0L, selectedExercise: ExerciseType? = null): ExerciseResult {
        lateinit var normalizedFrame: PoseFrame
        lateinit var features: PoseFeatures
        lateinit var motionFrame: MotionFrame
        val processingMs = measureTimeMillis {
            val emaFrame = ema.filter(rawFrame)
            val smoothed = sma?.filter(emaFrame) ?: emaFrame
            normalizedFrame = normalizer.normalize(smoothed)
            features = extractor.extract(normalizedFrame)
            motionFrame = normalizer.toMotionFrame(normalizedFrame, features)
        }
        val exerciseType = selectedExercise ?: ExerciseType.UNKNOWN
        val detector = when (exerciseType) {
            ExerciseType.SQUAT -> squatDetector
            ExerciseType.PUSH_UP -> pushUpDetector
            ExerciseType.JUMPING_JACK -> jumpingJackDetector
            ExerciseType.WALKING, ExerciseType.RUNNING -> gaitDetector
            ExerciseType.UNKNOWN -> gaitDetector
        }
        val result = detector.process(normalizedFrame, features)
        val fps = if (lastResultTimestamp == Long.MIN_VALUE) {
            0f
        } else {
            1000f / (rawFrame.timestamp - lastResultTimestamp).coerceAtLeast(1L)
        }
        lastResultTimestamp = rawFrame.timestamp
        val finalExercise = if (exerciseType == ExerciseType.UNKNOWN) result.exercise else exerciseType
        return result.copy(
            exercise = finalExercise,
            motionFrame = motionFrame,
            inferenceMs = inferenceMs,
            processingMs = processingMs,
            fps = fps,
        )
    }

    public fun reset() {
        ema.reset()
        sma?.reset()
        extractor.reset()
        squatDetector.reset()
        pushUpDetector.reset()
        jumpingJackDetector.reset()
        gaitDetector.reset()
        lastResultTimestamp = Long.MIN_VALUE
    }
}
