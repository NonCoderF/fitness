package io.motionguard.exercise

import android.content.Context
import kotlin.system.measureTimeMillis

public data class BuiltInExerciseDetectorConfig(
    val posePipelineConfig: PosePipelineConfig = PosePipelineConfig(smaWindow = 3),
    val sequenceLength: Int = ExerciseFeatureEncoder.SEQUENCE_LENGTH,
    val classifyEveryFrames: Int = 3,
)

public data class ExerciseDetectionResult(
    val phase: ExercisePhase,
    val confidence: Float,
    val repCount: Int,
    val activityState: String,
    val poseInferenceMs: Long = 0L,
    val classifierInferenceMs: Long = 0L,
    val totalDetectionMs: Long = 0L,
)

public class BuiltInExerciseDetector(
    context: Context,
    private val config: BuiltInExerciseDetectorConfig = BuiltInExerciseDetectorConfig(),
    private val classifier: ExerciseClassifier = TfliteExerciseClassifier(context),
) {
    private val ema = EmaPoseSmoother(config.posePipelineConfig.emaAlpha)
    private val sma = config.posePipelineConfig.smaWindow.takeIf { it > 1 }?.let { SmaPoseSmoother(it) }
    private val normalizer = PoseNormalizer(config.posePipelineConfig.normalizerOrigin)
    private val extractor = PoseFeatureExtractor(config.posePipelineConfig.minimumConfidence)
    private val sequenceBuffer = PoseSequenceBuffer(config.sequenceLength)
    private val stabilizer = ClassificationStabilizer()
    private val squatMachine = SquatRepStateMachine()
    private val pushUpMachine = PushUpRepStateMachine()
    private val jumpingJackMachine = JumpingJackRepStateMachine()
    private val fallbackSquatDetector = SquatDetector()
    private val fallbackPushUpDetector = PushUpDetector()
    private val fallbackJumpingJackDetector = JumpingJackDetector()
    private val fallbackGaitDetector = GaitDetector()
    private var framesSinceClassification = 0
    private var lastClassification = ClassificationResult(ExercisePhase.UNKNOWN, 0f, emptyMap())

    public suspend fun process(rawFrame: PoseFrame, selectedExercise: ExerciseType): ExerciseDetectionResult {
        var result: ExerciseDetectionResult
        val totalMs = measureTimeMillis {
            val emaFrame = ema.filter(rawFrame)
            val smoothed = sma?.filter(emaFrame) ?: emaFrame
            val normalized = normalizer.normalize(smoothed)
            val features = extractor.extract(normalized)
            sequenceBuffer.append(features)
            framesSinceClassification++
            if (sequenceBuffer.isReady && framesSinceClassification >= config.classifyEveryFrames) {
                lastClassification = classifier.classify(sequenceBuffer.sequence())
                framesSinceClassification = 0
            }
            val allowed = selectedExercise.allowedPhases()
            val stablePhase = stabilizer.update(lastClassification, allowed)
            result = if (!classifier.isAvailable()) {
                selectedExercise.fallbackResult(normalized, features)
            } else {
                selectedExercise.toDetectionResult(stablePhase, lastClassification)
            }
        }
        return result.copy(totalDetectionMs = totalMs)
    }

    public fun reset() {
        ema.reset()
        sma?.reset()
        extractor.reset()
        sequenceBuffer.reset()
        stabilizer.reset()
        classifier.reset()
        squatMachine.reset()
        pushUpMachine.reset()
        jumpingJackMachine.reset()
        fallbackSquatDetector.reset()
        fallbackPushUpDetector.reset()
        fallbackJumpingJackDetector.reset()
        fallbackGaitDetector.reset()
        framesSinceClassification = 0
        lastClassification = ClassificationResult(ExercisePhase.UNKNOWN, 0f, emptyMap())
    }

    public fun close() {
        classifier.close()
    }

    private fun ExerciseType.toDetectionResult(
        phase: ExercisePhase,
        classification: ClassificationResult,
    ): ExerciseDetectionResult = when (this) {
        ExerciseType.SQUAT -> {
            val reps = squatMachine.process(phase)
            ExerciseDetectionResult(phase, classification.confidence, reps, squatMachine.stateLabel, classifierInferenceMs = classification.inferenceMs)
        }
        ExerciseType.PUSH_UP -> {
            val reps = pushUpMachine.process(phase)
            ExerciseDetectionResult(phase, classification.confidence, reps, pushUpMachine.stateLabel, classifierInferenceMs = classification.inferenceMs)
        }
        ExerciseType.JUMPING_JACK -> {
            val reps = jumpingJackMachine.process(phase)
            ExerciseDetectionResult(phase, classification.confidence, reps, jumpingJackMachine.stateLabel, classifierInferenceMs = classification.inferenceMs)
        }
        ExerciseType.WALKING, ExerciseType.RUNNING -> {
            val state = when (phase) {
                ExercisePhase.RUNNING -> "Running"
                ExercisePhase.WALKING -> "Walking"
                ExercisePhase.IDLE -> "Stopped"
                else -> "Finding stride"
            }
            ExerciseDetectionResult(phase, classification.confidence, 0, state, classifierInferenceMs = classification.inferenceMs)
        }
        ExerciseType.UNKNOWN -> ExerciseDetectionResult(phase, classification.confidence, 0, "Finding movement", classifierInferenceMs = classification.inferenceMs)
    }

    private fun ExerciseType.fallbackResult(frame: PoseFrame, features: PoseFeatures): ExerciseDetectionResult {
        val detection = when (this) {
            ExerciseType.SQUAT -> fallbackSquatDetector.process(frame, features)
            ExerciseType.PUSH_UP -> fallbackPushUpDetector.process(frame, features)
            ExerciseType.JUMPING_JACK -> fallbackJumpingJackDetector.process(frame, features)
            ExerciseType.WALKING, ExerciseType.RUNNING -> fallbackGaitDetector.process(frame, features)
            ExerciseType.UNKNOWN -> null
        }
        if (detection == null) {
            return ExerciseDetectionResult(ExercisePhase.UNKNOWN, 0f, 0, "Finding movement")
        }
        val phase = when (this) {
            ExerciseType.SQUAT -> when (detection.state) {
                ExerciseState.DESCENDING, ExerciseState.BOTTOM -> ExercisePhase.SQUAT_DOWN
                ExerciseState.STANDING, ExerciseState.ASCENDING -> ExercisePhase.SQUAT_UP
                else -> ExercisePhase.UNKNOWN
            }
            ExerciseType.PUSH_UP -> when (detection.state) {
                ExerciseState.DESCENDING, ExerciseState.BOTTOM -> ExercisePhase.PUSHUP_DOWN
                ExerciseState.TOP, ExerciseState.ASCENDING -> ExercisePhase.PUSHUP_UP
                else -> ExercisePhase.UNKNOWN
            }
            ExerciseType.JUMPING_JACK -> when (detection.state) {
                ExerciseState.OPENING, ExerciseState.OPEN -> ExercisePhase.JUMPING_JACK_OPEN
                ExerciseState.CLOSED, ExerciseState.CLOSING -> ExercisePhase.JUMPING_JACK_CLOSED
                else -> ExercisePhase.UNKNOWN
            }
            ExerciseType.WALKING -> if (detection.state == ExerciseState.WALKING) ExercisePhase.WALKING else ExercisePhase.IDLE
            ExerciseType.RUNNING -> if (detection.state == ExerciseState.RUNNING) ExercisePhase.RUNNING else ExercisePhase.IDLE
            ExerciseType.UNKNOWN -> ExercisePhase.UNKNOWN
        }
        return ExerciseDetectionResult(
            phase = phase,
            confidence = detection.confidence,
            repCount = detection.repCount,
            activityState = detection.state.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
            classifierInferenceMs = 0L,
        )
    }

    private fun ExerciseType.allowedPhases(): Set<ExercisePhase> = when (this) {
        ExerciseType.SQUAT -> setOf(ExercisePhase.SQUAT_UP, ExercisePhase.SQUAT_DOWN, ExercisePhase.IDLE, ExercisePhase.UNKNOWN)
        ExerciseType.PUSH_UP -> setOf(ExercisePhase.PUSHUP_UP, ExercisePhase.PUSHUP_DOWN, ExercisePhase.IDLE, ExercisePhase.UNKNOWN)
        ExerciseType.JUMPING_JACK -> setOf(ExercisePhase.JUMPING_JACK_OPEN, ExercisePhase.JUMPING_JACK_CLOSED, ExercisePhase.IDLE, ExercisePhase.UNKNOWN)
        ExerciseType.WALKING, ExerciseType.RUNNING -> setOf(ExercisePhase.WALKING, ExercisePhase.RUNNING, ExercisePhase.IDLE, ExercisePhase.UNKNOWN)
        ExerciseType.UNKNOWN -> ExercisePhase.entries.toSet()
    }
}
