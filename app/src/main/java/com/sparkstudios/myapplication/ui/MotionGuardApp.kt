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
fun MotionGuardApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    var finalSimilarity by remember { mutableStateOf<MovementSimilarity?>(null) }
    var workoutSummary by remember { mutableStateOf<WorkoutSessionSummary?>(null) }
    var workoutLensFacing by rememberSaveable { mutableStateOf(CameraSelector.LENS_FACING_FRONT) }
    var skeletonVisible by rememberSaveable { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    if (!hasCameraPermission) {
        PermissionScreen(onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) })
        return
    }

    NavHost(
        navController = navController,
        startDestination = FitnessRoute.Selection,
    ) {
        composable(FitnessRoute.Selection) {
            ExerciseSelectionScreen(
                onWorkoutModeSelected = { workoutMode ->
                    navController.navigate(FitnessRoute.workout(workoutMode))
                },
            )
        }
        composable(
            route = FitnessRoute.RecordSummary,
            arguments = listOf(navArgument(FitnessRoute.TemplateArg) { type = NavType.StringType }),
        ) { backStackEntry ->
            val templateId = backStackEntry.arguments?.getString(FitnessRoute.TemplateArg).orEmpty()
            RecordSummaryScreen(
                templateId = templateId,
                onStartDetection = {
                    navController.navigate(FitnessRoute.workout(WorkoutMode.Repeat(templateId))) {
                        popUpTo(FitnessRoute.RecordSummary) { inclusive = true }
                    }
                },
                onFinish = {
                    navController.popBackStack(FitnessRoute.Selection, false)
                },
            )
        }
        composable(
            route = FitnessRoute.DetectionSummary,
            arguments = listOf(navArgument(FitnessRoute.TemplateArg) { type = NavType.StringType }),
        ) { backStackEntry ->
            val templateId = backStackEntry.arguments?.getString(FitnessRoute.TemplateArg).orEmpty()
            DetectionSummaryScreen(
                similarity = finalSimilarity,
                onRepeatAgain = {
                    navController.navigate(FitnessRoute.workout(WorkoutMode.Repeat(templateId))) {
                        popUpTo(FitnessRoute.DetectionSummary) { inclusive = true }
                    }
                },
                onFinish = {
                    finalSimilarity = null
                    navController.popBackStack(FitnessRoute.Selection, false)
                },
            )
        }
        composable(FitnessRoute.WorkoutSummary) {
            workoutSummary?.let { summary ->
                BuiltInWorkoutSummaryScreen(
                    summary = summary,
                    onFinish = {
                        workoutSummary = null
                        navController.popBackStack(FitnessRoute.Selection, false)
                    },
                )
            }
        }
        composable(
            route = FitnessRoute.Workout,
            arguments = listOf(
                navArgument(FitnessRoute.ModeArg) { type = NavType.StringType },
                navArgument(FitnessRoute.ValueArg) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val workoutMode = FitnessRoute.toWorkoutMode(
                mode = backStackEntry.arguments?.getString(FitnessRoute.ModeArg),
                value = backStackEntry.arguments?.getString(FitnessRoute.ValueArg),
            )
            WorkoutCameraScreen(
                workoutMode = workoutMode,
                lensFacing = workoutLensFacing,
                onLensFacingChanged = { workoutLensFacing = it },
                showSkeleton = skeletonVisible,
                onSkeletonVisibilityChanged = { skeletonVisible = it },
                onStop = {
                    if (!navController.popBackStack()) {
                        navController.navigate(FitnessRoute.Selection)
                    }
                },
                onBuiltInSummary = { summary ->
                    workoutSummary = summary
                    navController.navigate(FitnessRoute.WorkoutSummary) {
                        popUpTo(FitnessRoute.Workout) { inclusive = true }
                    }
                },
                onRecordedTemplate = { templateId ->
                    navController.navigate(FitnessRoute.recordSummary(templateId)) {
                        popUpTo(FitnessRoute.Workout) { inclusive = true }
                    }
                },
                onDetectionCompleted = { templateId, similarity ->
                    finalSimilarity = similarity
                    navController.navigate(FitnessRoute.detectionSummary(templateId)) {
                        popUpTo(FitnessRoute.Workout) { inclusive = true }
                    }
                },
            )
        }
    }
}

private object FitnessRoute {
    const val Selection = "exercise_selection"
    const val ModeArg = "mode"
    const val ValueArg = "value"
    const val TemplateArg = "templateId"
    const val Workout = "workout/{$ModeArg}/{$ValueArg}"
    const val RecordSummary = "record_summary/{$TemplateArg}"
    const val DetectionSummary = "detection_summary/{$TemplateArg}"
    const val WorkoutSummary = "workout_summary"

    fun workout(workoutMode: WorkoutMode): String {
        val mode = when (workoutMode) {
            is WorkoutMode.BuiltIn -> "builtin"
            is WorkoutMode.Record -> "record"
            is WorkoutMode.Repeat -> "repeat"
        }
        val value = when (workoutMode) {
            is WorkoutMode.BuiltIn -> workoutMode.exercise.name
            is WorkoutMode.Record -> workoutMode.name
            is WorkoutMode.Repeat -> workoutMode.templateId
        }
        return "workout/$mode/${Uri.encode(value)}"
    }

    fun recordSummary(templateId: String): String = "record_summary/${Uri.encode(templateId)}"

    fun detectionSummary(templateId: String): String = "detection_summary/${Uri.encode(templateId)}"

    fun toWorkoutMode(mode: String?, value: String?): WorkoutMode {
        val decodedValue = Uri.decode(value.orEmpty())
        return when (mode) {
            "builtin" -> WorkoutMode.BuiltIn(
                enumValues<ExerciseType>().firstOrNull { it.name == decodedValue } ?: ExerciseType.SQUAT,
            )
            "record" -> WorkoutMode.Record(decodedValue.ifBlank { "Morning Stretch" })
            "repeat" -> WorkoutMode.Repeat(decodedValue)
            else -> WorkoutMode.BuiltIn(ExerciseType.SQUAT)
        }
    }
}

internal fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
