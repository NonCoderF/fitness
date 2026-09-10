package com.sparkstudios.myapplication.ui

import kotlin.math.roundToInt

data class WorkoutSessionSummary(
    val exerciseType: ExerciseType,
    val repCount: Int,
    val durationMs: Long,
    val caloriesBurned: Int,
)

object CaloriesBurnedEstimator {
    const val DEFAULT_BODY_WEIGHT_KG = 70f

    // MET x 3.5 x body weight (kg) / 200 = kcal per minute.
    fun estimate(
        exerciseType: ExerciseType,
        durationMs: Long,
        bodyWeightKg: Float = DEFAULT_BODY_WEIGHT_KG,
    ): Int {
        val met = when (exerciseType) {
            ExerciseType.PUSH_UP -> 8.0f
            ExerciseType.SQUAT -> 5.0f
            ExerciseType.JUMPING_JACK -> 8.0f
            ExerciseType.TREADMILL_RUNNING -> 8.3f
            ExerciseType.RECORD_REPEAT -> 5.0f
        }
        val minutes = durationMs.coerceAtLeast(0L) / 60_000f
        return (met * 3.5f * bodyWeightKg.coerceAtLeast(1f) / 200f * minutes).roundToInt()
    }
}
