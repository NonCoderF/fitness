package io.motionguard.tflite

import android.content.Context
import io.motionguard.exercise.BodyPart
import io.motionguard.exercise.KeyPoint
import io.motionguard.exercise.PoseFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.system.measureTimeMillis

public class MoveNetPoseEstimator(
    context: Context,
    private val config: MoveNetModelConfig = MoveNetModelConfig(),
) : PoseEstimator {
    private val appContext = context.applicationContext
    private val preprocessor = PosePreprocessor(config.inputSize)
    private val output = Array(1) { Array(1) { Array(config.outputKeypoints) { FloatArray(3) } } }
    private val interpreter: Interpreter = Interpreter(
        loadModel(appContext, config.assetName),
        Interpreter.Options().setNumThreads(config.numThreads),
    )

    override suspend fun estimate(input: PoseEstimatorInput): PoseEstimate = withContext(Dispatchers.Default) {
        val tensorInput = preprocessor.toUInt8Rgb(input)
        val inferenceMs = measureTimeMillis {
            interpreter.run(tensorInput, output)
        }
        PoseEstimate(
            frame = PoseFrame(
                timestamp = input.timestamp,
                keypoints = BodyPart.entries.mapIndexed { index, bodyPart ->
                    val row = output[0][0][index]
                    KeyPoint(
                        type = bodyPart,
                        x = row[1].coerceIn(0f, 1f),
                        y = row[0].coerceIn(0f, 1f),
                        confidence = row[2].coerceIn(0f, 1f),
                    )
                },
            ),
            inferenceMs = inferenceMs,
        )
    }

    override fun close() {
        interpreter.close()
    }

    private fun loadModel(context: Context, assetName: String): MappedByteBuffer {
        val descriptor = context.assets.openFd(assetName)
        FileInputStream(descriptor.fileDescriptor).use { input ->
            return input.channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
        }
    }
}

public typealias TflitePoseEstimator = MoveNetPoseEstimator
