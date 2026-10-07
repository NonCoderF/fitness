package com.sparkstudios.myapplication.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.motionguard.camera.PoseAnalyzer
import io.motionguard.core.PoseFrame
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    onPoseFrame: (PoseFrame?) -> Unit,
    onError: (Throwable) -> Unit,
    lensFacing: Int = CameraSelector.LENS_FACING_FRONT,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Lens changes recreate the analyzer lifecycle, so each camera binding needs a live executor.
    val cameraExecutor = remember(lensFacing) { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val analyzer = remember(lensFacing) {
        PoseAnalyzer(
            mirrorHorizontally = lensFacing == CameraSelector.LENS_FACING_FRONT,
            onPoseFrame = onPoseFrame,
            onAnalyzerError = onError,
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { previewView },
    )

    DisposableEffect(lifecycleOwner, lensFacing, analyzer) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var disposed = false
        var cameraProvider: ProcessCameraProvider? = null
        var imageAnalysis: ImageAnalysis? = null

        cameraProviderFuture.addListener(
            {
                if (disposed) return@addListener
                try {
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(cameraExecutor, analyzer) }
                    imageAnalysis = analysis

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                        preview,
                        analysis,
                    )
                } catch (throwable: Throwable) {
                    onError(throwable)
                }
            },
            mainExecutor,
        )

        onDispose {
            disposed = true
            imageAnalysis?.clearAnalyzer()
            cameraProvider?.unbindAll()
            if (cameraProviderFuture.isDone) {
                runCatching { cameraProviderFuture.get().unbindAll() }
            }
            analyzer.close()
            cameraExecutor.shutdown()
        }
    }
}
