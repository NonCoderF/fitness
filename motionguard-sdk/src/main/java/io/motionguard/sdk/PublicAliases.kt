package motionguardsdk

import io.motionguard.core.ActivityType
import io.motionguard.core.TrackingStatus
import io.motionguard.exercise.ExerciseEngine
import io.motionguard.exercise.ExerciseResult
import io.motionguard.exercise.ExerciseType
import io.motionguard.exercise.PosePipelineConfig

/** Public activity classification alias used by the SDK facade. */
public typealias ActivityClass = ActivityType

/** Public tracking-quality alias used by the SDK facade. */
public typealias TrackingQuality = TrackingStatus

public typealias FitnessExerciseEngine = ExerciseEngine
public typealias FitnessExerciseResult = ExerciseResult
public typealias FitnessExerciseType = ExerciseType
public typealias FitnessPosePipelineConfig = PosePipelineConfig
