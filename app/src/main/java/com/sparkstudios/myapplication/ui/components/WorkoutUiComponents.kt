package com.sparkstudios.myapplication.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.motionguard.core.Point2D
import io.motionguard.core.PoseFrame
import io.motionguard.exercise.MovementSimilarity
import kotlinx.coroutines.delay

@Composable
internal fun PermissionScreen(onRequestPermission: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF111816),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Choose Exercise",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Camera permission is required for live pose tracking.",
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRequestPermission) {
                Text("Allow camera")
            }
        }
    }
}

@Composable
internal fun KeepScreenAwake(context: Context) {
    DisposableEffect(context) {
        val activity = context.findActivity()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}


@Composable
internal fun PoseOverlay(
    poseFrame: PoseFrame?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (poseFrame == null) return@Canvas

        val sourceWidth = poseFrame.imageWidth.coerceAtLeast(1).toFloat()
        val sourceHeight = poseFrame.imageHeight.coerceAtLeast(1).toFloat()
        val scale = maxOf(size.width / sourceWidth, size.height / sourceHeight)
        val renderedWidth = sourceWidth * scale
        val renderedHeight = sourceHeight * scale
        val cropX = (size.width - renderedWidth) / 2f
        val cropY = (size.height - renderedHeight) / 2f
        fun Point2D.offset() = Offset(cropX + x * renderedWidth, cropY + y * renderedHeight)
        fun drawBone(start: Point2D?, end: Point2D?) {
            if (start == null || end == null) return
            drawLine(
                color = Color(0xFF57D68D),
                start = start.offset(),
                end = end.offset(),
                strokeWidth = 7f,
                cap = StrokeCap.Round,
            )
        }
        fun drawJoint(point: Point2D?) {
            if (point == null) return
            drawCircle(
                color = Color(0xFF57D68D),
                radius = 11f,
                center = point.offset(),
            )
            drawCircle(color = Color.White, radius = 4f, center = point.offset())
        }

        drawBone(poseFrame.leftHip, poseFrame.rightHip)
        drawBone(poseFrame.leftShoulder, poseFrame.rightShoulder)
        drawBone(poseFrame.leftShoulder, poseFrame.leftElbow)
        drawBone(poseFrame.leftElbow, poseFrame.leftWrist)
        drawBone(poseFrame.rightShoulder, poseFrame.rightElbow)
        drawBone(poseFrame.rightElbow, poseFrame.rightWrist)
        drawBone(poseFrame.leftShoulder, poseFrame.leftHip)
        drawBone(poseFrame.rightShoulder, poseFrame.rightHip)
        drawBone(poseFrame.leftHip, poseFrame.leftKnee)
        drawBone(poseFrame.leftKnee, poseFrame.leftAnkle)
        drawBone(poseFrame.rightHip, poseFrame.rightKnee)
        drawBone(poseFrame.rightKnee, poseFrame.rightAnkle)

        listOf(
            poseFrame.leftShoulder,
            poseFrame.rightShoulder,
            poseFrame.leftElbow,
            poseFrame.rightElbow,
            poseFrame.leftWrist,
            poseFrame.rightWrist,
            poseFrame.leftHip,
            poseFrame.rightHip,
            poseFrame.leftKnee,
            poseFrame.rightKnee,
            poseFrame.leftAnkle,
            poseFrame.rightAnkle,
        ).forEach(::drawJoint)
    }
}

