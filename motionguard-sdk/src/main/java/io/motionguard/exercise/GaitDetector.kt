package io.motionguard.exercise

import kotlin.math.abs

public data class GaitDetectorConfig(
    val windowMs: Long = 4_000L,
    val stoppedVelocity: Float = 0.08f,
    val walkingCadenceMin: Float = 65f,
    val runningCadenceMin: Float = 145f,
    val minimumAlternation: Float = 0.35f,
    val stateHoldFrames: Int = 5,
    val minimumConfidence: Float = 0.2f,
)

public class GaitDetector(
    private val config: GaitDetectorConfig = GaitDetectorConfig(),
) : ExerciseDetector {
    private val window = ArrayDeque<PoseFeatures>()
    private var state = GaitState.STOPPED
    private var candidateState = GaitState.STOPPED
    private var candidateFrames = 0
    private var stepCount = 0
    private var lastStepTimestamp = Long.MIN_VALUE
    private var lastLegSign = 0

    override fun process(frame: PoseFrame, features: PoseFeatures): ExerciseResult {
        push(features)
        updateSteps(features)
        val cadence = cadence()
        val alternation = alternation()
        val legVelocity = legVelocity(features)
        val target = when {
            features.averageConfidence < config.minimumConfidence -> GaitState.STOPPED
            legVelocity < config.stoppedVelocity && cadence < config.walkingCadenceMin -> GaitState.STOPPED
            cadence >= config.runningCadenceMin && alternation >= config.minimumAlternation -> GaitState.RUNNING
            cadence >= config.walkingCadenceMin && alternation >= config.minimumAlternation -> GaitState.WALKING
            else -> GaitState.STOPPED
        }
        holdState(target)
        val exercise = when (state) {
            GaitState.WALKING -> ExerciseType.WALKING
            GaitState.RUNNING -> ExerciseType.RUNNING
            GaitState.STOPPED -> ExerciseType.UNKNOWN
        }
        val confidence = listOf(features.averageConfidence, alternation, (legVelocity / 0.8f).coerceIn(0f, 1f)).average().toFloat()
        return ExerciseResult(
            exercise = exercise,
            state = when (state) {
                GaitState.STOPPED -> ExerciseState.STOPPED
                GaitState.WALKING -> ExerciseState.WALKING
                GaitState.RUNNING -> ExerciseState.RUNNING
            },
            repCount = 0,
            confidence = confidence.coerceIn(0f, 1f),
            formScore = confidence.coerceIn(0f, 1f),
            stepCount = stepCount,
            cadenceSpm = cadence.toInt().takeIf { it > 0 },
        )
    }

    override fun reset() {
        window.clear()
        state = GaitState.STOPPED
        candidateState = GaitState.STOPPED
        candidateFrames = 0
        stepCount = 0
        lastStepTimestamp = Long.MIN_VALUE
        lastLegSign = 0
    }

    private fun push(features: PoseFeatures) {
        window += features
        val minTimestamp = features.timestamp - config.windowMs
        while (window.firstOrNull()?.timestamp?.let { it < minTimestamp } == true) window.removeFirst()
    }

    private fun updateSteps(features: PoseFeatures) {
        val left = features.jointDirections[BodyPart.LEFT_ANKLE]?.directionY ?: 0f
        val right = features.jointDirections[BodyPart.RIGHT_ANKLE]?.directionY ?: 0f
        val signal = left - right
        val sign = when {
            signal > 0.015f -> 1
            signal < -0.015f -> -1
            else -> 0
        }
        val enoughTimeSinceLastStep = lastStepTimestamp == Long.MIN_VALUE || features.timestamp - lastStepTimestamp > 220L
        if (sign != 0 && lastLegSign != 0 && sign != lastLegSign && enoughTimeSinceLastStep) {
            stepCount++
            lastStepTimestamp = features.timestamp
        }
        if (sign != 0) lastLegSign = sign
    }

    private fun cadence(): Float {
        if (window.size < 2) return 0f
        val durationMinutes = ((window.last().timestamp - window.first().timestamp).coerceAtLeast(1L) / 60_000f)
        val localChanges = window.zipWithNext().count { (a, b) ->
            val av = (a.jointDirections[BodyPart.LEFT_ANKLE]?.directionY ?: 0f) - (a.jointDirections[BodyPart.RIGHT_ANKLE]?.directionY ?: 0f)
            val bv = (b.jointDirections[BodyPart.LEFT_ANKLE]?.directionY ?: 0f) - (b.jointDirections[BodyPart.RIGHT_ANKLE]?.directionY ?: 0f)
            av * bv < 0f && abs(av - bv) > 0.02f
        }
        return localChanges / durationMinutes
    }

    private fun alternation(): Float {
        if (window.size < 3) return 0f
        val opposite = window.count {
            val left = it.jointDirections[BodyPart.LEFT_ANKLE]?.directionY ?: 0f
            val right = it.jointDirections[BodyPart.RIGHT_ANKLE]?.directionY ?: 0f
            left * right < 0f
        }
        return (opposite.toFloat() / window.size).coerceIn(0f, 1f)
    }

    private fun legVelocity(features: PoseFeatures): Float {
        val parts = listOf(BodyPart.LEFT_KNEE, BodyPart.RIGHT_KNEE, BodyPart.LEFT_ANKLE, BodyPart.RIGHT_ANKLE)
        return parts.map { features.jointVelocities[it] ?: 0f }.average().toFloat()
    }

    private fun holdState(target: GaitState) {
        if (target == state) {
            candidateState = target
            candidateFrames = 0
            return
        }
        if (target == candidateState) {
            candidateFrames++
        } else {
            candidateState = target
            candidateFrames = 1
        }
        if (candidateFrames >= config.stateHoldFrames) {
            state = target
            candidateFrames = 0
        }
    }
}
