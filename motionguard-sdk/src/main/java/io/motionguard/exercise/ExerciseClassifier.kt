package io.motionguard.exercise

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.channels.FileChannel
import kotlin.system.measureTimeMillis

public enum class ExercisePhase {
    UNKNOWN,
    IDLE,
    SQUAT_UP,
    SQUAT_DOWN,
    PUSHUP_UP,
    PUSHUP_DOWN,
    JUMPING_JACK_OPEN,
    JUMPING_JACK_CLOSED,
    WALKING,
    RUNNING,
}

public data class ClassificationResult(
    val label: ExercisePhase,
    val confidence: Float,
    val probabilities: Map<ExercisePhase, Float>,
    val inferenceMs: Long = 0L,
)

public interface ExerciseClassifier {
    public suspend fun classify(sequence: List<PoseFeatures>): ClassificationResult
    public fun isAvailable(): Boolean = true
    public fun reset() {}
    public fun close() {}
}

public data class TfliteExerciseClassifierConfig(
    val modelAssetName: String = "exercise_classifier.tflite",
    val labelAssetName: String = "exercise_labels.txt",
    val sequenceLength: Int = ExerciseFeatureEncoder.SEQUENCE_LENGTH,
    val numThreads: Int = 2,
)

public class TfliteExerciseClassifier(
    context: Context,
    private val config: TfliteExerciseClassifierConfig = TfliteExerciseClassifierConfig(),
    private val encoder: ExerciseFeatureEncoder = ExerciseFeatureEncoder(),
) : ExerciseClassifier {
    private companion object {
        const val TAG = "TfliteExerciseClassifier"
    }

    private val appContext = context.applicationContext
    private val labels = loadLabels(appContext, config.labelAssetName)
    private val input = Array(1) { Array(config.sequenceLength) { FloatArray(ExerciseFeatureEncoder.FEATURE_COUNT) } }
    private val output = Array(1) { FloatArray(labels.size.coerceAtLeast(1)) }
    private val interpreter = try {
        Interpreter(
            loadModel(appContext, config.modelAssetName),
            Interpreter.Options().setNumThreads(config.numThreads),
        ).also { model ->
            Log.i(TAG, "Loaded ${config.modelAssetName}: input=${model.getInputTensor(0).shape().contentToString()}, output=${model.getOutputTensor(0).shape().contentToString()}")
        }
    } catch (error: Throwable) {
        Log.e(TAG, "Unable to load ${config.modelAssetName}. Add the trained model to motionguard-sdk/src/main/assets.", error)
        null
    }

    override fun isAvailable(): Boolean = interpreter != null

    override suspend fun classify(sequence: List<PoseFeatures>): ClassificationResult = withContext(Dispatchers.Default) {
        val model = interpreter ?: return@withContext unavailableResult()
        if (sequence.size < config.sequenceLength) return@withContext unavailableResult()
        sequence.takeLast(config.sequenceLength).forEachIndexed { frameIndex, features ->
            encoder.encodeInto(features, input[0][frameIndex])
        }
        val inferenceMs = measureTimeMillis {
            model.run(input, output)
        }
        val probabilities = labels.mapIndexed { index, phase ->
            phase to (output[0].getOrNull(index) ?: 0f).coerceIn(0f, 1f)
        }.toMap()
        val best = probabilities.maxByOrNull { it.value }
        ClassificationResult(
            label = best?.key ?: ExercisePhase.UNKNOWN,
            confidence = best?.value ?: 0f,
            probabilities = probabilities,
            inferenceMs = inferenceMs,
        )
    }

    override fun close() {
        interpreter?.close()
    }

    private fun unavailableResult(): ClassificationResult =
        ClassificationResult(ExercisePhase.UNKNOWN, 0f, labels.associateWith { 0f })

    private fun loadModel(context: Context, assetName: String) =
        context.assets.openFd(assetName).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { input ->
                input.channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
            }
        }

    private fun loadLabels(context: Context, assetName: String): List<ExercisePhase> =
        try {
            context.assets.open(assetName).bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    line.trim().takeIf { it.isNotEmpty() }?.let { runCatching { ExercisePhase.valueOf(it) }.getOrNull() }
                }.toList()
            }.ifEmpty { ExercisePhase.entries }
        } catch (_: IOException) {
            ExercisePhase.entries
        }
}
