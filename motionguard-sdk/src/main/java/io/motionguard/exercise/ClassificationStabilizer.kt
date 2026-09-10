package io.motionguard.exercise

public data class ClassificationStabilizerConfig(
    val minConfidence: Float = 0.65f,
    val requiredConsecutivePredictions: Int = 3,
)

public class ClassificationStabilizer(
    private val config: ClassificationStabilizerConfig = ClassificationStabilizerConfig(),
) {
    private var active = ExercisePhase.UNKNOWN
    private var candidate = ExercisePhase.UNKNOWN
    private var candidateCount = 0

    public fun update(result: ClassificationResult, allowedPhases: Set<ExercisePhase>? = null): ExercisePhase {
        val label = result.label.takeIf { allowedPhases == null || it in allowedPhases } ?: ExercisePhase.UNKNOWN
        if (result.confidence < config.minConfidence) return active
        if (label == active) {
            candidate = label
            candidateCount = 0
            return active
        }
        if (label == candidate) {
            candidateCount++
        } else {
            candidate = label
            candidateCount = 1
        }
        if (candidateCount >= config.requiredConsecutivePredictions) {
            active = candidate
            candidateCount = 0
        }
        return active
    }

    public fun reset() {
        active = ExercisePhase.UNKNOWN
        candidate = ExercisePhase.UNKNOWN
        candidateCount = 0
    }
}
