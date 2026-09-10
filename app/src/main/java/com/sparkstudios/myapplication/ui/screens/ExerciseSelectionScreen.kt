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
fun ExerciseSelectionScreen(
    onWorkoutModeSelected: (WorkoutMode) -> Unit,
) {
    var showNameDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SelectionDesign.Background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SelectionDesign.ScreenPadding, vertical = 22.dp),
        ) {
            Text(
                text = "FORMFLOW",
                color = SelectionDesign.Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Train smarter.\nMove better.",
                color = SelectionDesign.PrimaryText,
                fontSize = 34.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Move. Measure. Improve.",
                color = SelectionDesign.SecondaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Choose your workout",
                color = SelectionDesign.PrimaryText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))
            ExerciseFeatureCard(
                title = "PUSH-UPS",
                subtitle = "Upper body | Form tracking",
                exercise = ExerciseType.PUSH_UP,
                onClick = { onWorkoutModeSelected(WorkoutMode.BuiltIn(ExerciseType.PUSH_UP)) },
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ExerciseCompactCard(
                    title = "SQUATS",
                    subtitle = "Depth | Control",
                    exercise = ExerciseType.SQUAT,
                    modifier = Modifier.weight(1f),
                    onClick = { onWorkoutModeSelected(WorkoutMode.BuiltIn(ExerciseType.SQUAT)) },
                )
                ExerciseCompactCard(
                    title = "JUMPING\nJACKS",
                    subtitle = "Range | Rhythm",
                    exercise = ExerciseType.JUMPING_JACK,
                    modifier = Modifier.weight(1f),
                    onClick = { onWorkoutModeSelected(WorkoutMode.BuiltIn(ExerciseType.JUMPING_JACK)) },
                )
            }
            Spacer(Modifier.height(14.dp))
            ExerciseFeatureCard(
                title = "TREADMILL RUNNING",
                subtitle = "Steps | Cadence | Movement",
                exercise = ExerciseType.TREADMILL_RUNNING,
                onClick = { onWorkoutModeSelected(WorkoutMode.BuiltIn(ExerciseType.TREADMILL_RUNNING)) },
            )
            Spacer(Modifier.height(26.dp))
            Text(
                text = "YOUR MOVEMENT. YOUR RULES.",
                color = SelectionDesign.Accent.copy(alpha = 0.86f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
            )
            Spacer(Modifier.height(10.dp))
            RecordRepeatHeroCard(onClick = { showNameDialog = true })
            Spacer(Modifier.height(10.dp))
        }
    }

    if (showNameDialog) {
        MovementNameDialog(
            onDismiss = { showNameDialog = false },
            onStart = { name ->
                showNameDialog = false
                onWorkoutModeSelected(WorkoutMode.Record(name))
            },
        )
    }
}

@Composable
private fun ExerciseFeatureCard(
    title: String,
    subtitle: String,
    exercise: ExerciseType,
    onClick: () -> Unit,
) {
    SelectionCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(154.dp),
        accentStrength = 0.18f,
    ) {
        PoseSkeletonArt(
            exercise = exercise,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(130.dp)
                .padding(end = 10.dp),
            alpha = 0.26f,
        )
        ExerciseCardText(
            title = title,
            subtitle = subtitle,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp, end = 92.dp),
        )
        ArrowIndicator(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp),
        )
    }
}

@Composable
private fun ExerciseCompactCard(
    title: String,
    subtitle: String,
    exercise: ExerciseType,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    SelectionCard(
        onClick = onClick,
        modifier = modifier.height(166.dp),
        accentStrength = 0.12f,
    ) {
        PoseSkeletonArt(
            exercise = exercise,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(92.dp)
                .padding(end = 6.dp, bottom = 4.dp),
            alpha = 0.22f,
        )
        ExerciseCardText(
            title = title,
            subtitle = subtitle,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            compact = true,
        )
        ArrowIndicator(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        )
    }
}

@Composable
private fun RecordRepeatHeroCard(
    onClick: () -> Unit,
) {
    SelectionCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(172.dp),
        accentStrength = 0.36f,
        borderColor = SelectionDesign.Accent.copy(alpha = 0.45f),
    ) {
        PoseSkeletonArt(
            exercise = ExerciseType.RECORD_REPEAT,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(150.dp)
                .padding(end = 6.dp),
            alpha = 0.32f,
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp, end = 104.dp),
        ) {
            Text(
                text = "RECORD & REPEAT",
                color = SelectionDesign.PrimaryText,
                fontSize = 23.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Teach FormFlow any movement\nand practice it again.",
                color = SelectionDesign.SecondaryText,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        ArrowIndicator(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp),
            prominent = true,
        )
    }
}

@Composable
private fun SelectionCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentStrength: Float,
    borderColor: Color = SelectionDesign.Border,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(140),
        label = "selectionCardScale",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(SelectionDesign.CardRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        SelectionDesign.CardTop,
                        SelectionDesign.CardBottom,
                        SelectionDesign.Accent.copy(alpha = accentStrength),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                color = if (pressed) SelectionDesign.Accent.copy(alpha = 0.72f) else borderColor,
                shape = RoundedCornerShape(SelectionDesign.CardRadius),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        content = content,
    )
}

@Composable
private fun ExerciseCardText(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            color = SelectionDesign.PrimaryText,
            fontSize = if (compact) 21.sp else 24.sp,
            lineHeight = if (compact) 24.sp else 28.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = subtitle,
            color = SelectionDesign.SecondaryText,
            fontSize = if (compact) 12.sp else 14.sp,
            lineHeight = if (compact) 15.sp else 18.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ArrowIndicator(
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(if (prominent) 42.dp else 34.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(SelectionDesign.Accent.copy(alpha = if (prominent) 0.18f else 0.10f))
            .border(
                1.dp,
                SelectionDesign.Accent.copy(alpha = if (prominent) 0.54f else 0.30f),
                RoundedCornerShape(18.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = ">",
            color = SelectionDesign.Accent,
            fontSize = if (prominent) 22.sp else 18.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

private object SelectionDesign {
    val Background = Color(0xFF080A0C)
    val CardTop = Color(0xFF171B1F)
    val CardBottom = Color(0xFF101316)
    val Border = Color.White.copy(alpha = 0.09f)
    val PrimaryText = Color(0xFFF4F7F2)
    val SecondaryText = Color(0xFF9AA4A0)
    val Accent = Color(0xFFB7FF4A)
    val ScreenPadding = 20.dp
    val CardRadius = 24.dp
}

@Composable
private fun PoseSkeletonArt(
    exercise: ExerciseType,
    modifier: Modifier = Modifier,
    alpha: Float,
) {
    Canvas(modifier = modifier) {
        val accent = SelectionDesign.Accent.copy(alpha = alpha)
        val muted = Color.White.copy(alpha = alpha * 0.45f)
        val stroke = 4.5f
        fun joint(x: Float, y: Float, r: Float = 5.5f) {
            drawCircle(accent, r, Offset(size.width * x, size.height * y))
        }
        fun bone(ax: Float, ay: Float, bx: Float, by: Float) {
            drawLine(
                color = muted,
                start = Offset(size.width * ax, size.height * ay),
                end = Offset(size.width * bx, size.height * by),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }

        when (exercise) {
            ExerciseType.PUSH_UP -> {
                bone(.14f, .66f, .42f, .55f); bone(.42f, .55f, .70f, .45f); bone(.70f, .45f, .88f, .52f)
                bone(.42f, .55f, .36f, .82f); bone(.66f, .46f, .84f, .76f)
                listOf(.14f to .66f, .42f to .55f, .70f to .45f, .88f to .52f, .36f to .82f, .84f to .76f).forEach { joint(it.first, it.second) }
            }
            ExerciseType.SQUAT -> {
                bone(.48f, .18f, .43f, .42f); bone(.43f, .42f, .36f, .58f); bone(.36f, .58f, .62f, .68f); bone(.62f, .68f, .46f, .88f)
                bone(.43f, .42f, .22f, .48f); bone(.43f, .42f, .66f, .38f)
                listOf(.48f to .18f, .43f to .42f, .36f to .58f, .62f to .68f, .46f to .88f, .22f to .48f, .66f to .38f).forEach { joint(it.first, it.second) }
            }
            ExerciseType.JUMPING_JACK -> {
                bone(.50f, .18f, .50f, .48f); bone(.50f, .34f, .20f, .12f); bone(.50f, .34f, .80f, .12f)
                bone(.50f, .48f, .24f, .88f); bone(.50f, .48f, .76f, .88f)
                listOf(.50f to .18f, .50f to .34f, .20f to .12f, .80f to .12f, .50f to .48f, .24f to .88f, .76f to .88f).forEach { joint(it.first, it.second) }
            }
            ExerciseType.TREADMILL_RUNNING -> {
                bone(.46f, .16f, .54f, .40f); bone(.54f, .40f, .42f, .58f); bone(.42f, .58f, .20f, .74f)
                bone(.54f, .40f, .70f, .62f); bone(.70f, .62f, .86f, .84f); bone(.52f, .28f, .76f, .20f)
                listOf(.46f to .16f, .54f to .40f, .42f to .58f, .20f to .74f, .70f to .62f, .86f to .84f, .76f to .20f).forEach { joint(it.first, it.second) }
            }
            ExerciseType.RECORD_REPEAT -> {
                drawCircle(accent.copy(alpha = alpha * 0.46f), size.minDimension * .38f, Offset(size.width * .58f, size.height * .48f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))
                drawCircle(accent.copy(alpha = alpha * 0.76f), size.minDimension * .23f, Offset(size.width * .58f, size.height * .48f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f))
                bone(.35f, .52f, .58f, .34f); bone(.58f, .34f, .78f, .52f); bone(.58f, .34f, .58f, .70f); bone(.58f, .70f, .38f, .86f); bone(.58f, .70f, .78f, .86f)
                listOf(.35f to .52f, .58f to .34f, .78f to .52f, .58f to .70f, .38f to .86f, .78f to .86f).forEach { joint(it.first, it.second) }
            }
        }
    }
}


