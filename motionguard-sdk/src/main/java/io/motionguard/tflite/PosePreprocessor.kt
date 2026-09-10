package io.motionguard.tflite

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

public class PosePreprocessor(
    private val inputSize: Int,
) {
    private val inputBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(1 * inputSize * inputSize * 3)
        .order(ByteOrder.nativeOrder())

    public fun toUInt8Rgb(input: PoseEstimatorInput): ByteBuffer {
        inputBuffer.rewind()
        val width = input.width.coerceAtLeast(1)
        val height = input.height.coerceAtLeast(1)
        for (y in 0 until inputSize) {
            val sourceY = ((y + 0.5f) * height / inputSize - 0.5f).roundToInt().coerceIn(0, height - 1)
            for (x in 0 until inputSize) {
                val sourceX = ((x + 0.5f) * width / inputSize - 0.5f).roundToInt().coerceIn(0, width - 1)
                val pixel = input.argbPixels[sourceY * width + sourceX]
                inputBuffer.put(((pixel shr 16) and 0xFF).toByte())
                inputBuffer.put(((pixel shr 8) and 0xFF).toByte())
                inputBuffer.put((pixel and 0xFF).toByte())
            }
        }
        inputBuffer.rewind()
        return inputBuffer
    }
}
