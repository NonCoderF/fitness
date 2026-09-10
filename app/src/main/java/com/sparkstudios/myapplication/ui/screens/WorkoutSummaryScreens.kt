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
internal fun RecordSummaryScreen(
    templateId: String,
    onStartDetection: () -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val template = remember(templateId) { MotionTemplateStore(context).find(templateId) }
    val frameCount = template?.frames?.size ?: 0
    val durationMs = template?.durationMs ?: 0L
    val captureSpeed = if (durationMs > 0L) {
        "${(frameCount * 1_000L / durationMs).toInt()} FPS"
    } else {
        "--"
    }
    val quality = when {
        frameCount >= 30 -> "Excellent"
        frameCount >= 15 -> "Good"
        frameCount > 0 -> "Fair"
        else -> "Unavailable"
    }

    BackHandler(onBack = onFinish)
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF101513)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "RECORDING COMPLETE",
                color = Color(0xFF8CF14B),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Text(
                text = template?.name ?: "Movement",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Your movement is ready for detection.",
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RecordSummaryMetricCard(
                    title = "QUALITY",
                    value = quality,
                    modifier = Modifier.weight(1f),
                )
                RecordSummaryMetricCard(
                    title = "CAPTURE SPEED",
                    value = captureSpeed,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onStartDetection,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8CF14B),
                    contentColor = Color(0xFF10200E),
                ),
            ) {
                Text("START DETECTION", fontWeight = FontWeight.Black)
            }
            OutlinedButton(
                onClick = onFinish,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Text("FINISH")
            }
        }
    }
}

@Composable
internal fun RecordSummaryMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = value,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun DetectionSummaryScreen(
    similarity: MovementSimilarity?,
    onRepeatAgain: () -> Unit,
    onFinish: () -> Unit,
) {
    val result = similarity ?: MovementSimilarity(0f, 0f, 0f, 0f)
    val overall = result.overall.toIntPercent()
    val rating = when {
        overall >= 90 -> "EXCELLENT"
        overall >= 80 -> "GOOD"
        overall >= 65 -> "FAIR"
        else -> "TRY AGAIN"
    }

    BackHandler(onBack = onFinish)
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF101513)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "DETECTION COMPLETE",
                color = Color(0xFF8CF14B),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Text(
                text = "Movement accuracy",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = rating,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF8CF14B).copy(alpha = 0.14f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8CF14B).copy(alpha = 0.62f)),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "$overall%",
                        color = Color.White,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "OVERALL ACCURACY",
                        color = Color.White.copy(alpha = 0.68f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RecordSummaryMetricCard("ARMS", "${result.arms.toIntPercent()}%", Modifier.weight(1f))
                RecordSummaryMetricCard("LEGS", "${result.legs.toIntPercent()}%", Modifier.weight(1f))
                RecordSummaryMetricCard("TORSO", "${result.torso.toIntPercent()}%", Modifier.weight(1f))
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onRepeatAgain,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8CF14B),
                    contentColor = Color(0xFF10200E),
                ),
            ) {
                Text("REPEAT AGAIN", fontWeight = FontWeight.Black)
            }
            OutlinedButton(
                onClick = onFinish,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Text("FINISH")
            }
        }
    }
}

@Composable
internal fun BuiltInWorkoutSummaryScreen(
    summary: WorkoutSessionSummary,
    onFinish: () -> Unit,
) {
    BackHandler(onBack = onFinish)
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF101513)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "WORKOUT COMPLETE",
                color = Color(0xFF8CF14B),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Text(
                text = summary.exerciseType.summaryTitle,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Nice work. Here is your session result.",
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 16.sp,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF8CF14B).copy(alpha = 0.14f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8CF14B).copy(alpha = 0.62f)),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = summary.repCount.toString(),
                        color = Color.White,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = if (summary.exerciseType == ExerciseType.TREADMILL_RUNNING) "STEPS" else "REPS",
                        color = Color.White.copy(alpha = 0.68f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RecordSummaryMetricCard("TIME", summary.durationMs.toSummaryTime(), Modifier.weight(1f))
                RecordSummaryMetricCard("CALORIES", "${summary.caloriesBurned} kcal", Modifier.weight(1f))
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onFinish,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8CF14B),
                    contentColor = Color(0xFF10200E),
                ),
            ) {
                Text("DONE", fontWeight = FontWeight.Black)
            }
        }
    }
}

private val ExerciseType.summaryTitle: String
    get() = when (this) {
        ExerciseType.PUSH_UP -> "PUSH-UPS"
        ExerciseType.SQUAT -> "SQUATS"
        ExerciseType.JUMPING_JACK -> "JUMPING JACKS"
        ExerciseType.TREADMILL_RUNNING -> "TREADMILL RUNNING"
        ExerciseType.RECORD_REPEAT -> "WORKOUT"
    }

private fun Long.toSummaryTime(): String {
    val seconds = coerceAtLeast(0L) / 1000L
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}
