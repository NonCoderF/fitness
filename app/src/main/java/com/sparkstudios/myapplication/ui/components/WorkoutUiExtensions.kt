package com.sparkstudios.myapplication.ui

internal val WorkoutMode.key: String
    get() = when (this) {
        is WorkoutMode.BuiltIn -> "builtin_${exercise.name}"
        is WorkoutMode.Record -> "record_$name"
        is WorkoutMode.Repeat -> "repeat_$templateId"
    }

internal val WorkoutUiState.primaryValue: String
    get() = when (displayMode) {
        WorkoutDisplayMode.BUILT_IN -> repCount.toString()
        WorkoutDisplayMode.RECORD -> elapsedRecordingMs.toTimerText()
        WorkoutDisplayMode.REPEAT -> "${similarity?.overall?.toIntPercent() ?: 0}%"
    }

internal val WorkoutUiState.primaryLabel: String
    get() = when (displayMode) {
        WorkoutDisplayMode.BUILT_IN -> if (exerciseType == ExerciseType.TREADMILL_RUNNING) "STEPS" else "REPS"
        WorkoutDisplayMode.RECORD -> templateName ?: "CAPTURING"
        WorkoutDisplayMode.REPEAT -> "MATCH"
    }

internal val WorkoutUiState.counterAccessibilityText: String
    get() = when (displayMode) {
        WorkoutDisplayMode.BUILT_IN -> {
            val unit = if (exerciseType == ExerciseType.TREADMILL_RUNNING) "steps" else "reps"
            "$title, ${repCount.toSpokenNumber()} $unit, ${if (isPaused) "Paused" else exerciseState}"
        }
        WorkoutDisplayMode.RECORD -> "Recording ${templateName ?: "movement"}"
        WorkoutDisplayMode.REPEAT -> "${similarity?.overall?.toIntPercent() ?: 0} percent match"
    }

internal val WorkoutUiState.counterAnnouncementText: String?
    get() = when (displayMode) {
        WorkoutDisplayMode.RECORD -> {
            val seconds = elapsedRecordingMs.coerceAtLeast(0L) / 1000L
            if (seconds > 0L && seconds % 10L == 0L) "$seconds seconds recorded" else null
        }
        WorkoutDisplayMode.REPEAT -> "${((similarity?.overall ?: 0f) * 100f).toInt()} percent match"
        WorkoutDisplayMode.BUILT_IN -> null
    }

internal fun Long.toTimerText(): String {
    val totalSeconds = this.coerceAtLeast(0L) / 1000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

internal fun Float.toIntPercent(): Int = (coerceIn(0f, 1f) * 100f).toInt()

internal fun Int.toSpokenNumber(): String {
    val small = arrayOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen",
        "eighteen", "nineteen",
    )
    val tens = arrayOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
    return when {
        this in 0..19 -> small[this]
        this in 20..99 -> tens[this / 10] + if (this % 10 == 0) "" else "-" + small[this % 10]
        this in 100..999 -> {
            val remainder = this % 100
            "${small[this / 100]} hundred" + if (remainder == 0) "" else " ${remainder.toSpokenNumber()}"
        }
        else -> toString()
    }
}
