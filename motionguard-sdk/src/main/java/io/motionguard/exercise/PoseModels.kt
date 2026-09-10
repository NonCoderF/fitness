package io.motionguard.exercise

public enum class BodyPart {
    NOSE,
    LEFT_EYE,
    RIGHT_EYE,
    LEFT_EAR,
    RIGHT_EAR,
    LEFT_SHOULDER,
    RIGHT_SHOULDER,
    LEFT_ELBOW,
    RIGHT_ELBOW,
    LEFT_WRIST,
    RIGHT_WRIST,
    LEFT_HIP,
    RIGHT_HIP,
    LEFT_KNEE,
    RIGHT_KNEE,
    LEFT_ANKLE,
    RIGHT_ANKLE,
}

public data class Point(
    val x: Float,
    val y: Float,
)

public data class KeyPoint(
    val type: BodyPart,
    val x: Float,
    val y: Float,
    val confidence: Float,
) {
    public val point: Point get() = Point(x, y)
}

public data class PoseFrame(
    val timestamp: Long,
    val keypoints: List<KeyPoint>,
) {
    private val byType: Map<BodyPart, KeyPoint> = keypoints.associateBy { it.type }

    public operator fun get(type: BodyPart): KeyPoint? = byType[type]

    public fun point(type: BodyPart, minimumConfidence: Float = 0f): Point? =
        byType[type]?.takeIf { it.confidence >= minimumConfidence }?.point
}

public data class MotionFrame(
    val timestamp: Long,
    val normalizedKeypoints: FloatArray,
    val jointAngles: FloatArray,
)

public data class MotionTemplate(
    val id: String,
    val name: String,
    val durationMs: Long,
    val frames: List<MotionFrame>,
)

public data class MovementSimilarity(
    val overall: Float,
    val arms: Float,
    val legs: Float,
    val torso: Float,
)

public enum class ExerciseType {
    UNKNOWN,
    SQUAT,
    PUSH_UP,
    JUMPING_JACK,
    WALKING,
    RUNNING,
}

public enum class ExerciseState {
    UNKNOWN,
    STANDING,
    DESCENDING,
    BOTTOM,
    ASCENDING,
    TOP,
    CLOSED,
    OPENING,
    OPEN,
    CLOSING,
    STOPPED,
    WALKING,
    RUNNING,
}

public enum class GaitState {
    STOPPED,
    WALKING,
    RUNNING,
}

public data class ExerciseResult(
    val exercise: ExerciseType,
    val state: ExerciseState,
    val repCount: Int,
    val confidence: Float,
    val formScore: Float,
    val feedback: String? = null,
    val stepCount: Int = 0,
    val cadenceSpm: Int? = null,
    val motionFrame: MotionFrame? = null,
    val inferenceMs: Long = 0L,
    val processingMs: Long = 0L,
    val fps: Float = 0f,
)

public data class PosePipelineConfig(
    val minimumConfidence: Float = 0.2f,
    val emaAlpha: Float = 0.45f,
    val smaWindow: Int = 0,
    val normalizerOrigin: PoseNormalizer.Origin = PoseNormalizer.Origin.HIP_CENTER,
)
