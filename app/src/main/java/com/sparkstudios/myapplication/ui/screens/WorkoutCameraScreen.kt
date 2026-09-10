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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
fun WorkoutCameraScreen(
    workoutMode: WorkoutMode,
    lensFacing: Int,
    onLensFacingChanged: (Int) -> Unit,
    showSkeleton: Boolean,
    onSkeletonVisibilityChanged: (Boolean) -> Unit,
    onStop: () -> Unit,
    onBuiltInSummary: (WorkoutSessionSummary) -> Unit,
    onRecordedTemplate: (String) -> Unit,
    onDetectionCompleted: (String, MovementSimilarity) -> Unit,
    viewModel: WorkoutViewModel = viewModel(
        key = workoutMode.key,
        factory = WorkoutViewModel.Factory(LocalContext.current, workoutMode),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    KeepScreenAwake(LocalView.current.context)

    val exitWorkout = {
        val summary = viewModel.buildBuiltInSummary()
        viewModel.resetWorkout()
        if (summary != null) onBuiltInSummary(summary) else onStop()
    }
    BackHandler(onBack = exitWorkout)

    LaunchedEffect(uiState.savedTemplateId) {
        val id = uiState.savedTemplateId
        if (id != null && workoutMode is WorkoutMode.Record) {
            onRecordedTemplate(id)
        }
    }

    LaunchedEffect(uiState.isCompleted, uiState.similarity) {
        if (uiState.isCompleted && workoutMode is WorkoutMode.Repeat) {
            uiState.similarity?.let { onDetectionCompleted(workoutMode.templateId, it) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        CameraPreview(
            onPoseFrame = viewModel::onPoseFrame,
            onError = { throwable -> viewModel.setCameraError(throwable.message ?: "Camera initialization failed") },
            lensFacing = lensFacing,
            modifier = Modifier.fillMaxSize(),
        )
        if (showSkeleton) {
            PoseOverlay(
                poseFrame = uiState.poseData,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.62f),
                        0.36f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.66f),
                    ),
                ),
        )
        WorkoutBackButton(
            onClick = exitWorkout,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 4.dp, top = 4.dp),
        )
        WorkoutPrimaryOverlay(
            uiState = uiState,
        )
        WorkoutUtilityControls(
            onSwitchCamera = {
                onLensFacingChanged(if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    CameraSelector.LENS_FACING_BACK
                } else {
                    CameraSelector.LENS_FACING_FRONT
                })
            },
            showSkeleton = showSkeleton,
            onToggleSkeleton = { onSkeletonVisibilityChanged(!showSkeleton) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 10.dp),
        )
        WorkoutControls(
            displayMode = uiState.displayMode,
            isCompleted = uiState.isCompleted,
            isPaused = uiState.isPaused,
            isRecording = uiState.isRecording,
            onPauseResume = viewModel::togglePause,
            onReset = viewModel::resetForControls,
            onStartRecording = viewModel::startRecording,
            onStopRecording = viewModel::stopRecording,
            onRepeatAgain = viewModel::repeatAgain,
            onStop = exitWorkout,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(18.dp),
        )
        uiState.countdownText?.let {
            CountdownOverlay(text = it)
        }
        uiState.cameraError?.let {
            Text(
                text = it,
                color = Color(0xFFFFB4AB),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun WorkoutBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Go back" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun WorkoutPrimaryOverlay(uiState: WorkoutUiState) {
    val view = LocalView.current
    val counterAccessibilityText = uiState.counterAccessibilityText
    val counterAnnouncementText = uiState.counterAnnouncementText
    var lastAnnouncement by remember { mutableStateOf<String?>(null) }
    var bump by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.repCount, uiState.similarity?.overall?.toIntPercent()) {
        bump = true
        delay(120)
        bump = false
    }
    LaunchedEffect(uiState.repCount) {
        if (uiState.displayMode != WorkoutDisplayMode.BUILT_IN || uiState.repCount <= 0) return@LaunchedEffect
        val unit = if (uiState.exerciseType == ExerciseType.TREADMILL_RUNNING) "steps" else "reps"
        view.announceForAccessibility("${uiState.repCount.toSpokenNumber()} $unit")
    }
    LaunchedEffect(uiState.similarity?.overall?.toIntPercent(), uiState.elapsedRecordingMs / 10_000L) {
        if (uiState.displayMode == WorkoutDisplayMode.BUILT_IN) return@LaunchedEffect
        val announcement = counterAnnouncementText ?: return@LaunchedEffect
        if (lastAnnouncement != announcement) view.announceForAccessibility(announcement)
        lastAnnouncement = announcement
    }
    val counterScale by animateFloatAsState(
        targetValue = if (bump) 1.05f else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "counterScale",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .clearAndSetSemantics {
                contentDescription = counterAccessibilityText
                liveRegion = LiveRegionMode.Polite
            }
            .padding(horizontal = 18.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = uiState.primaryValue,
            color = Color.White,
            fontSize = 112.sp,
            lineHeight = 112.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.scale(counterScale),
            textAlign = TextAlign.Center,
        )
        Text(
            text = uiState.primaryLabel,
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (uiState.isPaused) "Paused" else uiState.exerciseState,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        if (uiState.displayMode == WorkoutDisplayMode.REPEAT && uiState.similarity != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Arms ${uiState.similarity.arms.toIntPercent()}%  Legs ${uiState.similarity.legs.toIntPercent()}%  Torso ${uiState.similarity.torso.toIntPercent()}%",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CountdownOverlay(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.24f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = if (text == "GO") 82.sp else 112.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WorkoutUtilityControls(
    onSwitchCamera: () -> Unit,
    showSkeleton: Boolean,
    onToggleSkeleton: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UtilityActionButton(
            contentDescription = "Switch camera",
            onClick = onSwitchCamera,
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(25.dp),
            )
        }
        UtilityActionButton(
            contentDescription = if (showSkeleton) "Hide skeleton" else "Show skeleton",
            onClick = onToggleSkeleton,
        ) {
            Icon(
                imageVector = if (showSkeleton) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(25.dp),
            )
        }
    }
}

@Composable
private fun UtilityActionButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.24f))
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

@Composable
private fun WorkoutControls(
    displayMode: WorkoutDisplayMode,
    isCompleted: Boolean,
    isPaused: Boolean,
    isRecording: Boolean,
    onPauseResume: () -> Unit,
    onReset: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onRepeatAgain: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (displayMode == WorkoutDisplayMode.RECORD) {
        WorkoutControlDock(modifier = modifier, label = "STOP") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                WorkoutControlCircle(
                    onClick = if (isRecording) onStopRecording else onStartRecording,
                    contentDescription = if (isRecording) "Stop recording" else "Start recording",
                    accent = if (isRecording) Color(0xFFE85D5D) else Color(0xFF8CF14B),
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isRecording) "Stop" else "Start",
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 14.sp,
                )
            }
        }
        return
    }

    WorkoutControlDock(modifier = modifier) {
        WorkoutControlItem(
            label = "Reset",
            contentDescription = "Reset workout",
            onClick = if (isCompleted) onRepeatAgain else onReset,
        ) {
            Icon(Icons.Default.RestartAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        WorkoutControlItem(
            label = if (isPaused) "Resume" else "Pause",
            contentDescription = if (isPaused) "Resume workout" else "Pause workout",
            onClick = onPauseResume,
            large = true,
            accent = Color(0xFF8CF14B),
        ) {
            Icon(
                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = null,
                tint = Color(0xFF8CF14B),
                modifier = Modifier.size(32.dp),
            )
        }
        WorkoutControlItem(
            label = if (isCompleted) "Finish" else "Finish",
            contentDescription = "Finish workout",
            onClick = onStop,
        ) {
            Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun WorkoutControlDock(
    modifier: Modifier,
    label: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun RowScope.WorkoutControlItem(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    large: Boolean = false,
    accent: Color = Color.White.copy(alpha = 0.7f),
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WorkoutControlCircle(
            onClick = onClick,
            contentDescription = contentDescription,
            accent = accent,
            size = if (large) 68.dp else 48.dp,
            icon = icon,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.86f),
            fontSize = if (large) 14.sp else 12.sp,
            fontWeight = if (large) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun WorkoutControlCircle(
    onClick: () -> Unit,
    contentDescription: String,
    accent: Color,
    size: androidx.compose.ui.unit.Dp = 68.dp,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(if (size == 68.dp) 2.dp else 1.dp, accent, CircleShape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

@Composable
internal fun MovementNameDialog(
    onDismiss: () -> Unit,
    onStart: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Movement name") },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Morning Stretch") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onStart(name.trim().ifBlank { "Morning Stretch" }) },
            ) {
                Text("Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
