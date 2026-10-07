package io.motionguard.camera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import io.motionguard.core.PoseFrame
import io.motionguard.mlkit.MlKitPoseAdapter
import java.util.concurrent.atomic.AtomicBoolean

class PoseAnalyzer(
    mirrorHorizontally: Boolean,
    private val onPoseFrame: (PoseFrame?) -> Unit,
    private val onAnalyzerError: (Throwable) -> Unit = {},
) : ImageAnalysis.Analyzer {
    private val adapter = MlKitPoseAdapter(mirrorHorizontally)
    private val isProcessing = AtomicBoolean(false)
    private val isClosed = AtomicBoolean(false)
    // ML Kit pose detection is still beta and can fail during construction on
    // some devices. Keep that failure out of the Compose/main-thread crash path.
    private val detector: PoseDetector? = runCatching {
        PoseDetection.getClient(
            PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build(),
        )
    }.onFailure(onAnalyzerError).getOrNull()

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val activeDetector = detector
        if (activeDetector == null) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (isClosed.get() || mediaImage == null || !isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        val timestampMs = imageProxy.imageInfo.timestamp / 1_000_000L
        val rotatedWidth = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.height else imageProxy.width
        val rotatedHeight = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.width else imageProxy.height

        activeDetector.process(inputImage)
            .addOnSuccessListener { pose ->
                if (isClosed.get()) return@addOnSuccessListener
                val frame = adapter.toPoseFrame(
                    pose = pose,
                    imageWidth = rotatedWidth,
                    imageHeight = rotatedHeight,
                    timestampMs = timestampMs,
                )
                onPoseFrame(frame)
            }
            .addOnFailureListener {
                if (isClosed.get()) return@addOnFailureListener
                val missingFrame = PoseFrame(
                    timestampMs = timestampMs,
                    leftHip = null,
                    rightHip = null,
                    leftKnee = null,
                    rightKnee = null,
                    leftAnkle = null,
                    rightAnkle = null,
                )
                onPoseFrame(missingFrame)
            }
            .addOnCompleteListener {
                isProcessing.set(false)
                imageProxy.close()
            }
    }

    fun close() {
        if (isClosed.compareAndSet(false, true)) {
            detector?.close()
        }
    }
}
