package motionguardsdk

import android.content.Context
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.core.Camera
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import io.motionguard.camera.PoseAnalyzer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** CameraX bridge owned by the SDK; the host only supplies a lifecycle and a View. */
public class MotionGuardCameraController internal constructor(
    private val motionGuard: MotionGuard,
    context: Context,
) {
    private val appContext = context.applicationContext
    private var executor: ExecutorService? = null
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var analyzer: PoseAnalyzer? = null
    private var analysis: ImageAnalysis? = null

    /** Creates the PreviewView without requiring the host to depend on CameraX types. */
    public fun createPreviewView(): View = PreviewView(appContext).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }

    /** Binds preview and pose analysis. Rebinding safely replaces the previous camera. */
    public fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: View,
        lens: MotionGuardLens = MotionGuardLens.FRONT,
        onError: (Throwable) -> Unit,
        onResult: (MotionGuardResult) -> Unit = {},
    ) {
        val target = previewView as? PreviewView ?: run {
            onError(IllegalArgumentException("previewView must be created by createPreviewView()"))
            return
        }
        unbind()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(appContext)
        val mainExecutor = ContextCompat.getMainExecutor(appContext)
        cameraProviderFuture.addListener({
            runCatching {
                val cameraProvider = cameraProviderFuture.get()
                provider = cameraProvider
                val poseAnalyzer = PoseAnalyzer(
                    mirrorHorizontally = lens == MotionGuardLens.FRONT,
                    onPoseFrame = { frame ->
                        frame?.let { onResult(motionGuard.process(it)) }
                    },
                    onAnalyzerError = onError,
                )
                analyzer = poseAnalyzer
                executor = Executors.newSingleThreadExecutor()
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor!!, poseAnalyzer) }
                analysis = imageAnalysis
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(target.surfaceProvider) }
                cameraProvider.unbindAll()
                val lensFacing = if (lens == MotionGuardLens.FRONT) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.Builder().requireLensFacing(lensFacing).build(), preview, imageAnalysis)
            }.onFailure(onError)
        }, mainExecutor)
    }

    /** Stops analysis and releases CameraX resources. */
    public fun unbind() {
        analysis?.clearAnalyzer()
        provider?.unbindAll()
        camera = null
        analyzer?.close()
        executor?.shutdown()
        analysis = null
        provider = null
        analyzer = null
        executor = null
    }

    public fun release() { unbind() }

    /** Adjusts optical/digital camera zoom by a gesture scale factor. */
    public fun adjustZoom(scaleFactor: Float) {
        if (scaleFactor <= 0f) return
        val activeCamera = camera ?: return
        val state = activeCamera.cameraInfo.zoomState.value ?: return
        activeCamera.cameraControl.setZoomRatio(
            (state.zoomRatio * scaleFactor).coerceIn(state.minZoomRatio, state.maxZoomRatio),
        )
    }
}
