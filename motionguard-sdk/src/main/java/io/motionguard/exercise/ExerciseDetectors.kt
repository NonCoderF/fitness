package io.motionguard.exercise

import kotlin.math.abs

public interface ExerciseDetector {
    public fun process(frame: PoseFrame, features: PoseFeatures): ExerciseResult
    public fun reset()
}

public data class SquatDetectorConfig(
    val standingKneeAngle: Float = 130f,
    // A completed squat must return to the standing range after reaching bottom.
    // Use the standing threshold here so normal camera noise does not leave the
    // detector stuck in ASCENDING after the user has visibly stood up.
    val repCompletionKneeAngle: Float = 130f,
    val descendKneeAngle: Float = 145f,
    val bottomKneeAngle: Float = 125f,
    val standingHipAngle: Float = 140f,
    val bottomHipAngle: Float = 125f,
    val minimumHipDrop: Float = 0.08f,
    val maxTorsoLeanDegrees: Float = 45f,
    val minimumConfidence: Float = 0.2f,
)

public class SquatDetector(
    private val config: SquatDetectorConfig = SquatDetectorConfig(),
) : ExerciseDetector {
    private var state = ExerciseState.STANDING
    private var reps = 0
    private var standingHipY: Float? = null
    private var bottomSeen = false
    private var descentSeen = false
    private var lowestKneeAngle = Float.MAX_VALUE

    override fun process(frame: PoseFrame, features: PoseFeatures): ExerciseResult {
        val knee = averagePositive(features.leftKneeAngle, features.rightKneeAngle)
        val hip = averagePositive(features.leftHipAngle, features.rightHipAngle)
        val confidence = features.averageConfidence
        val hipDrop = standingHipY?.let { features.hipVerticalPosition - it } ?: 0f
        val torsoOk = abs(features.torsoAngle) <= config.maxTorsoLeanDegrees

        if (confidence < config.minimumConfidence || knee == 0f || hip == 0f) {
            return result(features, confidence * 0.5f, "Low confidence joints")
        }

        when (state) {
            ExerciseState.STANDING -> {
                standingHipY = features.hipVerticalPosition
                bottomSeen = false
                descentSeen = false
                lowestKneeAngle = Float.MAX_VALUE
                if (knee < config.descendKneeAngle && hip < config.standingHipAngle) {
                    descentSeen = true
                    lowestKneeAngle = knee
                    state = ExerciseState.DESCENDING
                }
            }
            ExerciseState.DESCENDING -> {
                descentSeen = true
                lowestKneeAngle = minOf(lowestKneeAngle, knee)
                if (knee <= config.bottomKneeAngle && hip <= config.bottomHipAngle && hipDrop >= config.minimumHipDrop) {
                    bottomSeen = true
                    state = ExerciseState.BOTTOM
                } else if (knee >= config.standingKneeAngle) {
                    if (descentSeen && config.descendKneeAngle - lowestKneeAngle >= 15f) reps++
                    state = ExerciseState.STANDING
                    bottomSeen = false
                    descentSeen = false
                }
            }
            ExerciseState.BOTTOM -> {
                if (knee > config.bottomKneeAngle - 10f) state = ExerciseState.ASCENDING
            }
            ExerciseState.ASCENDING -> {
                if ((knee >= config.repCompletionKneeAngle || hip >= config.standingHipAngle - 10f) && (bottomSeen || descentSeen)) {
                    reps++
                    state = ExerciseState.STANDING
                    standingHipY = features.hipVerticalPosition
                    bottomSeen = false
                    descentSeen = false
                    lowestKneeAngle = Float.MAX_VALUE
                } else if (knee <= config.bottomKneeAngle) {
                    state = ExerciseState.BOTTOM
                }
            }
            else -> state = ExerciseState.STANDING
        }

        val form = listOf(confidence, if (torsoOk) 1f else 0.55f, (hipDrop / config.minimumHipDrop).coerceIn(0f, 1f)).average().toFloat()
        return result(features, confidence, if (torsoOk) null else "Keep torso more upright", form)
    }

    override fun reset() {
        state = ExerciseState.STANDING
        reps = 0
        standingHipY = null
        bottomSeen = false
        descentSeen = false
        lowestKneeAngle = Float.MAX_VALUE
    }

    private fun result(features: PoseFeatures, confidence: Float, feedback: String?, formScore: Float = confidence): ExerciseResult =
        ExerciseResult(ExerciseType.SQUAT, state, reps, confidence.coerceIn(0f, 1f), formScore.coerceIn(0f, 1f), feedback)
}

public data class PushUpDetectorConfig(
    val topElbowAngle: Float = 120f,
    // Count only after returning to the top range, not merely after leaving bottom.
    val repCompletionElbowAngle: Float = 120f,
    val descendElbowAngle: Float = 135f,
    val bottomElbowAngle: Float = 110f,
    val maxBodyAlignmentError: Float = 0.28f,
    val minimumElbowRange: Float = 30f,
    val minimumConfidence: Float = 0.2f,
)

public class PushUpDetector(
    private val config: PushUpDetectorConfig = PushUpDetectorConfig(),
) : ExerciseDetector {
    private var state = ExerciseState.TOP
    private var reps = 0
    private var minElbow = Float.MAX_VALUE
    private var maxElbow = 0f

    override fun process(frame: PoseFrame, features: PoseFeatures): ExerciseResult {
        val elbow = averagePositive(features.leftElbowAngle, features.rightElbowAngle)
        val confidence = features.averageConfidence
        val alignmentError = abs(features.torsoAngle)
        if (confidence < config.minimumConfidence || elbow == 0f) {
            return result(confidence * 0.5f, "Low confidence upper-body joints")
        }
        minElbow = minOf(minElbow, elbow)
        maxElbow = maxOf(maxElbow, elbow)

        when (state) {
            ExerciseState.TOP -> if (elbow < config.descendElbowAngle) state = ExerciseState.DESCENDING
            ExerciseState.DESCENDING -> {
                if (elbow <= config.bottomElbowAngle) state = ExerciseState.BOTTOM
                else if (elbow >= config.topElbowAngle) {
                    if (maxElbow - minElbow >= config.minimumElbowRange) reps++
                    state = ExerciseState.TOP
                    resetRangeOnly()
                }
            }
            ExerciseState.BOTTOM -> if (elbow > config.bottomElbowAngle - 5f) {
                state = ExerciseState.ASCENDING
            }
            ExerciseState.ASCENDING -> {
                if (elbow >= config.repCompletionElbowAngle || elbow >= config.topElbowAngle) {
                    if (maxElbow - minElbow >= config.minimumElbowRange) reps++
                    state = ExerciseState.TOP
                    resetRangeOnly()
                } else if (elbow <= config.bottomElbowAngle) {
                    state = ExerciseState.BOTTOM
                }
            }
            else -> state = ExerciseState.TOP
        }

        val alignmentScore = (1f - alignmentError / 70f).coerceIn(0f, 1f)
        return result(confidence, if (alignmentScore < 0.55f) "Keep shoulders and hips aligned" else null, minOf(confidence, alignmentScore))
    }

    override fun reset() {
        state = ExerciseState.TOP
        reps = 0
        resetRangeOnly()
    }

    private fun resetRangeOnly() {
        minElbow = Float.MAX_VALUE
        maxElbow = 0f
    }

    private fun result(confidence: Float, feedback: String?, formScore: Float = confidence): ExerciseResult =
        ExerciseResult(ExerciseType.PUSH_UP, state, reps, confidence.coerceIn(0f, 1f), formScore.coerceIn(0f, 1f), feedback)
}

public data class JumpingJackDetectorConfig(
    val closedAnkleDistance: Float = 0.70f,
    val openAnkleDistance: Float = 0.80f,
    val openWristDistance: Float = 0.65f,
    val wristAboveHeadY: Float = -0.05f,
    val minimumConfidence: Float = 0.2f,
)

public class JumpingJackDetector(
    private val config: JumpingJackDetectorConfig = JumpingJackDetectorConfig(),
) : ExerciseDetector {
    private var state = ExerciseState.CLOSED
    private var reps = 0
    private var openSeen = false

    override fun process(frame: PoseFrame, features: PoseFeatures): ExerciseResult {
        val confidence = features.averageConfidence
        if (confidence < config.minimumConfidence) return result(confidence * 0.5f, "Low confidence joints")
        val closed = features.ankleDistance <= config.closedAnkleDistance
        val leftWristRaised = raisedAboveShoulder(features, BodyPart.LEFT_WRIST, BodyPart.LEFT_SHOULDER)
        val rightWristRaised = raisedAboveShoulder(features, BodyPart.RIGHT_WRIST, BodyPart.RIGHT_SHOULDER)
        val handsRaised = (leftWristRaised && rightWristRaised) ||
            (features.wristRelativeToHeadPosition <= config.wristAboveHeadY)
        val open = features.ankleDistance >= config.openAnkleDistance &&
            features.wristDistance >= config.openWristDistance &&
            handsRaised

        when (state) {
            ExerciseState.CLOSED -> {
                openSeen = false
                if (!closed && features.ankleDistance > config.closedAnkleDistance) state = ExerciseState.OPENING
            }
            ExerciseState.OPENING -> {
                if (open) {
                    openSeen = true
                    state = ExerciseState.OPEN
                } else if (closed) {
                    state = ExerciseState.CLOSED
                }
            }
            ExerciseState.OPEN -> if (!open) state = ExerciseState.CLOSING
            ExerciseState.CLOSING -> {
                if (closed && openSeen) {
                    reps++
                    state = ExerciseState.CLOSED
                    openSeen = false
                } else if (open) {
                    state = ExerciseState.OPEN
                }
            }
            else -> state = ExerciseState.CLOSED
        }
        return result(confidence, null)
    }

    override fun reset() {
        state = ExerciseState.CLOSED
        reps = 0
        openSeen = false
    }

    private fun result(confidence: Float, feedback: String?): ExerciseResult =
        ExerciseResult(ExerciseType.JUMPING_JACK, state, reps, confidence.coerceIn(0f, 1f), confidence.coerceIn(0f, 1f), feedback)

    private fun raisedAboveShoulder(features: PoseFeatures, wrist: BodyPart, shoulder: BodyPart): Boolean {
        val wristPoint = features.normalizedPoints[wrist] ?: return false
        val shoulderPoint = features.normalizedPoints[shoulder] ?: return false
        return wristPoint.y < shoulderPoint.y - 0.03f
    }
}

private fun averagePositive(a: Float, b: Float): Float {
    val values = listOf(a, b).filter { it > 0f }
    return if (values.isEmpty()) 0f else values.average().toFloat()
}
