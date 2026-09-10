package com.sparkstudios.myapplication.ui

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.motionguard.core.ActivityType
import io.motionguard.core.MotionState
import io.motionguard.core.PoseFrame
import io.motionguard.exercise.CorePoseFrameAdapter
import io.motionguard.exercise.DtwMovementMatcher
import io.motionguard.exercise.DtwMovementMatcherConfig
import io.motionguard.exercise.ExerciseEngine
import io.motionguard.exercise.ExerciseState
import io.motionguard.exercise.MotionFrame
import io.motionguard.exercise.MotionTemplate
import io.motionguard.exercise.MovementSimilarity
import io.motionguard.exercise.ExerciseType as DetectionExerciseType
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import motionguardsdk.MotionGuard

enum class ExerciseType {
    PUSH_UP,
    SQUAT,
    JUMPING_JACK,
    TREADMILL_RUNNING,
    RECORD_REPEAT,
}

sealed class WorkoutMode {
    data class BuiltIn(val exercise: ExerciseType) : WorkoutMode()
    data class Record(val name: String) : WorkoutMode()
    data class Repeat(val templateId: String) : WorkoutMode()
}

enum class WorkoutDisplayMode {
    BUILT_IN,
    RECORD,
    REPEAT,
}

data class WorkoutUiState(
    val workoutMode: WorkoutMode,
    val displayMode: WorkoutDisplayMode,
    val exerciseType: ExerciseType? = null,
    val title: String,
    val repCount: Int = 0,
    val exerciseState: String = "",
    val isPaused: Boolean = false,
    val isRecording: Boolean = false,
    val poseData: PoseFrame? = null,
    val cameraError: String? = null,
    val countdownText: String? = null,
    val elapsedRecordingMs: Long = 0L,
    val similarity: MovementSimilarity? = null,
    val isCompleted: Boolean = false,
    val templateName: String? = null,
    val savedTemplateId: String? = null,
)

class WorkoutViewModel(
    context: Context,
    private val workoutMode: WorkoutMode,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val motionGuard = MotionGuard.create(appContext)
    private val exerciseEngine = ExerciseEngine()
    private val templateStore = MotionTemplateStore(appContext)
    private val matcher = DtwMovementMatcher()
    private val matcherConfig = DtwMovementMatcherConfig()
    private val referenceTemplate = (workoutMode as? WorkoutMode.Repeat)?.let { templateStore.find(it.templateId) }
    private val _uiState = MutableStateFlow(initialUiState())
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    private val recordedFrames = mutableListOf<MotionFrame>()
    private val liveFrames = ArrayDeque<MotionFrame>()
    private var recordingStartTimestamp: Long? = null
    private var matchingStartTimestamp: Long? = null
    private var treadmillSteps = 0
    private var lastRepCount = 0
    private var recordStarted = workoutMode !is WorkoutMode.Record
    private var repeatStarted = false
    private var countdownInProgress = false
    private val sessionStartedAt = SystemClock.elapsedRealtime()

    init {
        motionGuard.start()
    }

    fun onPoseFrame(frame: PoseFrame?) {
        if (frame == null) return
        if (_uiState.value.isPaused || _uiState.value.isCompleted) {
            _uiState.update { it.copy(poseData = frame) }
            return
        }

        when (workoutMode) {
            is WorkoutMode.BuiltIn -> processBuiltIn(frame, workoutMode.exercise)
            is WorkoutMode.Record -> processRecord(frame)
            is WorkoutMode.Repeat -> processRepeat(frame)
        }
    }

    fun togglePause() {
        _uiState.update { it.copy(isPaused = !it.isPaused) }
    }

    fun startRecording() {
        if (workoutMode !is WorkoutMode.Record || recordStarted || countdownInProgress) return
        recordedFrames.clear()
        recordingStartTimestamp = null
        startCountdown {
            recordStarted = true
            _uiState.update {
                it.copy(isRecording = true, exerciseState = "Recording")
            }
        }
    }

    fun resetForControls() {
        treadmillSteps = 0
        lastRepCount = 0
        exerciseEngine.reset()
        liveFrames.clear()
        matchingStartTimestamp = null
        repeatStarted = false
        countdownInProgress = false
        _uiState.update {
            it.copy(
                repCount = 0,
                exerciseState = if (referenceTemplate == null) "Ready" else "GET INTO POSITION",
                similarity = null,
                isCompleted = false,
                isPaused = false,
                isRecording = false,
                countdownText = null,
            )
        }
    }

    fun stopRecording() {
        if (workoutMode !is WorkoutMode.Record || recordedFrames.isEmpty()) return
        val id = UUID.randomUUID().toString()
        val duration = recordedFrames.last().timestamp - recordedFrames.first().timestamp
        templateStore.save(
            MotionTemplate(
                id = id,
                name = workoutMode.name,
                durationMs = duration.coerceAtLeast(0L),
                frames = recordedFrames.toList(),
            ),
        )
        _uiState.update {
            it.copy(
                isCompleted = true,
                isRecording = false,
                exerciseState = "Saved",
                countdownText = null,
                savedTemplateId = id,
            )
        }
    }

    fun repeatAgain() {
        liveFrames.clear()
        matchingStartTimestamp = null
        repeatStarted = false
        countdownInProgress = false
        exerciseEngine.reset()
        _uiState.update {
            it.copy(
                similarity = null,
                isCompleted = false,
                isPaused = true,
                exerciseState = if (referenceTemplate == null) "No saved movement" else "GET INTO POSITION",
                countdownText = null,
            )
        }
    }

    fun setCameraError(message: String) {
        _uiState.update { it.copy(cameraError = message) }
    }

    fun resetWorkout() {
        treadmillSteps = 0
        lastRepCount = 0
        recordedFrames.clear()
        liveFrames.clear()
        recordingStartTimestamp = null
        matchingStartTimestamp = null
        recordStarted = workoutMode !is WorkoutMode.Record
        repeatStarted = false
        countdownInProgress = false
        motionGuard.reset()
        exerciseEngine.reset()
        _uiState.value = initialUiState()
    }

    fun buildBuiltInSummary(): WorkoutSessionSummary? {
        val builtIn = workoutMode as? WorkoutMode.BuiltIn ?: return null
        val durationMs = (SystemClock.elapsedRealtime() - sessionStartedAt).coerceAtLeast(0L)
        return WorkoutSessionSummary(
            exerciseType = builtIn.exercise,
            repCount = _uiState.value.repCount,
            durationMs = durationMs,
            caloriesBurned = CaloriesBurnedEstimator.estimate(builtIn.exercise, durationMs),
        )
    }

    override fun onCleared() {
        motionGuard.release()
    }

    private fun processBuiltIn(frame: PoseFrame, exercise: ExerciseType) {
        if (exercise == ExerciseType.TREADMILL_RUNNING) {
            val result = motionGuard.process(frame)
            treadmillSteps += result.stepEvents.size
            _uiState.update {
                it.copy(
                    repCount = treadmillSteps,
                    exerciseState = treadmillState(result.motionState, result.activity),
                    poseData = result.poseFrame ?: frame,
                )
            }
            runBuiltInClassifier(frame, exercise, updateCounter = false)
            return
        }

        _uiState.update { it.copy(poseData = frame) }
        runBuiltInClassifier(frame, exercise, updateCounter = true)
    }

    private fun runBuiltInClassifier(frame: PoseFrame, exercise: ExerciseType, updateCounter: Boolean) {
        val detection = exerciseEngine.process(
            rawFrame = CorePoseFrameAdapter.fromCore(frame),
            selectedExercise = exercise.toDetectionType(),
        )
        val stateText = if (updateCounter && detection.repCount > lastRepCount) {
            "Good rep"
        } else {
            detection.state.toDisplayText()
        }
        if (updateCounter) lastRepCount = detection.repCount
        _uiState.update {
            it.copy(
                repCount = if (updateCounter) detection.repCount else it.repCount,
                exerciseState = stateText,
                poseData = frame,
            )
        }
    }

    private fun processRecord(frame: PoseFrame) {
        val result = exerciseEngine.process(CorePoseFrameAdapter.fromCore(frame))
        if (!recordStarted) {
            _uiState.update { it.copy(poseData = frame) }
            return
        }
        val motionFrame = result.motionFrame ?: return
        val start = recordingStartTimestamp ?: motionFrame.timestamp.also { recordingStartTimestamp = it }
        recordedFrames += motionFrame.copy(timestamp = motionFrame.timestamp - start)
        _uiState.update {
            it.copy(
                poseData = frame,
                elapsedRecordingMs = motionFrame.timestamp - start,
                exerciseState = "Recording",
                countdownText = null,
            )
        }
    }

    private fun processRepeat(frame: PoseFrame) {
        val template = referenceTemplate
        if (template == null) {
            _uiState.update { it.copy(poseData = frame, exerciseState = "No saved movement") }
            return
        }
        val result = exerciseEngine.process(CorePoseFrameAdapter.fromCore(frame))
        val motionFrame = result.motionFrame ?: return

        if (!repeatStarted) {
            val startSimilarity = matcher.startingPoseSimilarity(template.frames, motionFrame)
            _uiState.update {
                it.copy(
                    poseData = frame,
                    similarity = MovementSimilarity(startSimilarity, startSimilarity, startSimilarity, startSimilarity),
                    exerciseState = if (startSimilarity >= matcherConfig.startingPoseThreshold) "GET READY" else "GET INTO POSITION",
                )
            }
            if (startSimilarity >= matcherConfig.startingPoseThreshold && !countdownInProgress) {
                startCountdown {
                    repeatStarted = true
                    matchingStartTimestamp = null
                    liveFrames.clear()
                    _uiState.update { it.copy(exerciseState = "MATCHING", countdownText = null) }
                }
            }
            return
        }

        val start = matchingStartTimestamp ?: motionFrame.timestamp.also { matchingStartTimestamp = it }
        liveFrames += motionFrame.copy(timestamp = motionFrame.timestamp - start)
        while (liveFrames.size > template.frames.size.coerceAtLeast(30) * 2) liveFrames.removeFirst()
        val similarity = matcher.compare(template.frames, liveFrames.toList())
        val state = similarityState(similarity.overall)
        val elapsed = motionFrame.timestamp - start
        val completed = elapsed >= template.durationMs.coerceAtLeast(1L)
        _uiState.update {
            it.copy(
                poseData = frame,
                similarity = similarity,
                exerciseState = if (completed) state else if (state == "TRY AGAIN") "MATCHING" else state,
                isCompleted = completed,
                isPaused = completed,
            )
        }
    }

    private fun startCountdown(onGo: () -> Unit) {
        viewModelScope.launch {
            countdownInProgress = true
            listOf("3", "2", "1", "GO").forEach { value ->
                _uiState.update { it.copy(countdownText = value) }
                delay(if (value == "GO") 550L else 1_000L)
            }
            _uiState.update { it.copy(countdownText = null) }
            countdownInProgress = false
            onGo()
        }
    }

    private fun initialUiState(): WorkoutUiState = when (workoutMode) {
        is WorkoutMode.BuiltIn -> WorkoutUiState(
            workoutMode = workoutMode,
            displayMode = WorkoutDisplayMode.BUILT_IN,
            exerciseType = workoutMode.exercise,
            title = workoutMode.exercise.workoutTitle,
            exerciseState = initialState(workoutMode.exercise),
        )
        is WorkoutMode.Record -> WorkoutUiState(
            workoutMode = workoutMode,
            displayMode = WorkoutDisplayMode.RECORD,
            title = workoutMode.name,
            exerciseState = "Get ready",
            templateName = workoutMode.name,
        )
        is WorkoutMode.Repeat -> WorkoutUiState(
            workoutMode = workoutMode,
            displayMode = WorkoutDisplayMode.REPEAT,
            title = referenceTemplate?.name ?: "Movement",
            isPaused = true,
            exerciseState = if (referenceTemplate == null) "No saved movement" else "GET INTO POSITION",
            templateName = referenceTemplate?.name,
        )
    }

    private fun ExerciseType.toDetectionType(): DetectionExerciseType = when (this) {
        ExerciseType.PUSH_UP -> DetectionExerciseType.PUSH_UP
        ExerciseType.SQUAT -> DetectionExerciseType.SQUAT
        ExerciseType.JUMPING_JACK -> DetectionExerciseType.JUMPING_JACK
        ExerciseType.TREADMILL_RUNNING -> DetectionExerciseType.RUNNING
        ExerciseType.RECORD_REPEAT -> DetectionExerciseType.UNKNOWN
    }

    private fun treadmillState(motionState: MotionState, activity: ActivityType): String = when {
        activity == ActivityType.RUNNING -> "Running"
        activity == ActivityType.WALKING -> "Walking"
        motionState == MotionState.NOT_MOVING -> "Stopped"
        else -> "Finding stride"
    }

    private fun initialState(type: ExerciseType): String = when (type) {
        ExerciseType.PUSH_UP -> "Top"
        ExerciseType.SQUAT -> "Standing"
        ExerciseType.JUMPING_JACK -> "Closed"
        ExerciseType.TREADMILL_RUNNING -> "Finding stride"
        ExerciseType.RECORD_REPEAT -> "Ready"
    }

    private fun similarityState(value: Float): String = when {
        value >= matcherConfig.excellentThreshold -> "EXCELLENT"
        value >= matcherConfig.goodThreshold -> "GOOD"
        value >= matcherConfig.fairThreshold -> "FAIR"
        else -> "TRY AGAIN"
    }

    class Factory(
        private val context: Context,
        private val workoutMode: WorkoutMode,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WorkoutViewModel(context, workoutMode) as T
    }
}

val ExerciseType.workoutTitle: String
    get() = when (this) {
        ExerciseType.PUSH_UP -> "PUSH-UPS"
        ExerciseType.SQUAT -> "SQUATS"
        ExerciseType.JUMPING_JACK -> "JUMPING JACKS"
        ExerciseType.TREADMILL_RUNNING -> "RUNNING"
        ExerciseType.RECORD_REPEAT -> "RECORD & REPEAT"
    }

private fun ExerciseState.toDisplayText(): String = when (this) {
    ExerciseState.STANDING -> "Standing"
    ExerciseState.DESCENDING -> "Going down"
    ExerciseState.BOTTOM -> "Bottom"
    ExerciseState.ASCENDING -> "Coming up"
    ExerciseState.TOP -> "Top"
    ExerciseState.CLOSED -> "Closed"
    ExerciseState.OPENING -> "Opening"
    ExerciseState.OPEN -> "Open"
    ExerciseState.CLOSING -> "Closing"
    ExerciseState.WALKING -> "Walking"
    ExerciseState.RUNNING -> "Running"
    ExerciseState.STOPPED -> "Stopped"
    ExerciseState.UNKNOWN -> "Ready"
}
