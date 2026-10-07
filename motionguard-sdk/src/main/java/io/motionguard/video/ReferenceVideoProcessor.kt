package io.motionguard.video

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.os.Build
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import io.motionguard.core.MotionSequence
import io.motionguard.core.MotionSequenceBuilder
import io.motionguard.mlkit.MlKitPoseAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

public sealed interface VideoProcessingState {
    public data object Idle : VideoProcessingState
    public data class Processing(val progress: Float) : VideoProcessingState
    public data class Completed(val sequence: MotionSequence) : VideoProcessingState
    public data class Failed(val message: String, val cause: Throwable? = null) : VideoProcessingState
}

public data class VideoProcessingConfig(
    val sampleRateHz: Float = 12f,
    val emaAlpha: Float = .35f,
    val minimumMotionChange: Float = .012f,
    val maxSamples: Int = 4_000,
)

/** Decodes only sampled bitmaps, converts them to compact pose keyframes, then releases them. */
public class ReferenceVideoProcessor(private val context: Context) {
    public suspend fun process(uri: Uri, config: VideoProcessingConfig = VideoProcessingConfig(), onState: (VideoProcessingState) -> Unit = {}): MotionSequence = withContext(Dispatchers.Default) {
        require(config.sampleRateHz in 1f..30f) { "sampleRateHz must be between 1 and 30." }
        val retriever = MediaMetadataRetriever()
        val detector = PoseDetection.getClient(PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE).build())
        val adapter = MlKitPoseAdapter(false)
        val builder = MotionSequenceBuilder(config.emaAlpha, config.minimumMotionChange, config.maxSamples)
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(1L)
                ?: error("The selected video has no readable duration.")
            val interval = (1000f / config.sampleRateHz).toLong().coerceAtLeast(1L)
            var timestamp = 0L
            var detectedPoses = 0
            var retainedFrames = 0
            var qualitySum = 0f
            val (sourceWidth, sourceHeight) = videoDimensions(retriever)
            while (timestamp <= duration) {
                coroutineContext.ensureActive()
                onState(VideoProcessingState.Processing((timestamp.toFloat() / duration).coerceIn(0f, 1f)))
                val bitmap = retriever.decodeSampledFrame(
                    timestampUs = timestamp * 1000L,
                    sourceWidth = sourceWidth,
                    sourceHeight = sourceHeight,
                    maxDimension = 720,
                )
                if (bitmap != null) {
                    try {
                        val pose = Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)))
                        val frame = adapter.toPoseFrame(pose, bitmap.width, bitmap.height, timestamp)
                        val points = listOfNotNull(frame.leftShoulder, frame.rightShoulder, frame.leftHip, frame.rightHip, frame.leftKnee, frame.rightKnee, frame.leftAnkle, frame.rightAnkle)
                        if (points.size >= 3) {
                            detectedPoses++
                            if (builder.add(frame)) retainedFrames++
                            qualitySum += points.map { it.confidence }.average().toFloat()
                        }
                    } finally { bitmap.recycle() }
                }
                timestamp += interval
            }
            if (detectedPoses < 2 || retainedFrames < 2) error("No usable human pose was found in the video.")
            val sequence = builder.build(uri.toString(), duration, config.sampleRateHz, (qualitySum / detectedPoses.coerceAtLeast(1)).coerceIn(0f, 1f))
            onState(VideoProcessingState.Completed(sequence))
            sequence
        } catch (t: Throwable) {
            onState(VideoProcessingState.Failed(t.message ?: "Reference video processing failed.", t))
            throw t
        } finally {
            runCatching { detector.close() }
            runCatching { retriever.release() }
        }
    }

    private fun videoDimensions(retriever: MediaMetadataRetriever): Pair<Int, Int> =
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?.let { width ->
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                    ?.takeIf { it > 0 }
                    ?.let { height -> width to height }
            }
            ?: (720 to 720)

    private fun MediaMetadataRetriever.decodeSampledFrame(
        timestampUs: Long,
        sourceWidth: Int,
        sourceHeight: Int,
        maxDimension: Int,
    ): Bitmap? {
        val scale = (maxOf(sourceWidth, sourceHeight).toFloat() / maxDimension).coerceAtLeast(1f)
        val targetWidth = (sourceWidth / scale).toInt().coerceAtLeast(1)
        val targetHeight = (sourceHeight / scale).toInt().coerceAtLeast(1)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getScaledFrameAtTime(timestampUs, MediaMetadataRetriever.OPTION_CLOSEST, targetWidth, targetHeight)
        } else {
            getFrameAtTime(timestampUs, MediaMetadataRetriever.OPTION_CLOSEST)?.let { bitmap ->
                if (bitmap.width <= maxDimension && bitmap.height <= maxDimension) bitmap
                else Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true).also { bitmap.recycle() }
            }
        }
    }
}
