package io.motionguard.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseLayerTest {
    @Test
    fun emaSmoothingBlendsJointCoordinates() {
        val smoother = EmaPoseSmoother(alpha = 0.5f)
        smoother.filter(frame(0, key(BodyPart.LEFT_WRIST, 0f, 0f)))
        val smoothed = smoother.filter(frame(33, key(BodyPart.LEFT_WRIST, 1f, 1f)))
        val wrist = smoothed[BodyPart.LEFT_WRIST]!!
        assertEquals(0.5f, wrist.x, 0.001f)
        assertEquals(0.5f, wrist.y, 0.001f)
    }

    @Test
    fun angleCalculationReturnsNinetyDegrees() {
        val value = angle(Point(0f, 1f), Point(0f, 0f), Point(1f, 0f))
        assertEquals(90f, value, 0.001f)
    }

    @Test
    fun squatRepCountsOnlyCompleteCycle() {
        val detector = SquatDetector()
        val sequence = listOf(
            squatFeatures(0, knee = 170f, hip = 160f, hipY = 0f),
            squatFeatures(100, knee = 130f, hip = 135f, hipY = 0.08f),
            squatFeatures(200, knee = 90f, hip = 95f, hipY = 0.2f),
            squatFeatures(300, knee = 128f, hip = 132f, hipY = 0.12f),
            squatFeatures(400, knee = 166f, hip = 155f, hipY = 0.01f),
        )
        val result = lastResult(sequence) { features -> detector.process(emptyFrame(features.timestamp), features) }
        assertEquals(1, result.repCount)
        assertEquals(ExerciseState.STANDING, result.state)
    }

    @Test
    fun incompleteSquatDoesNotCount() {
        val detector = SquatDetector()
        val sequence = listOf(
            squatFeatures(0, knee = 170f, hip = 160f, hipY = 0f),
            squatFeatures(100, knee = 132f, hip = 135f, hipY = 0.06f),
            squatFeatures(200, knee = 160f, hip = 152f, hipY = 0.01f),
        )
        val result = lastResult(sequence) { features -> detector.process(emptyFrame(features.timestamp), features) }
        assertEquals(0, result.repCount)
    }

    @Test
    fun pushUpRepCountsFullTopBottomTop() {
        val detector = PushUpDetector()
        val sequence = listOf(165f, 125f, 80f, 118f, 160f).mapIndexed { index, elbow ->
            pushUpFeatures(index * 100L, elbow)
        }
        val result = lastResult(sequence) { features -> detector.process(emptyFrame(features.timestamp), features) }
        assertEquals(1, result.repCount)
        assertEquals(ExerciseState.TOP, result.state)
    }

    @Test
    fun jumpingJackRepRequiresOpenHandsAndFeet() {
        val detector = JumpingJackDetector()
        val sequence = listOf(
            jackFeatures(0, ankle = 0.4f, wrist = 0.4f, wristToHead = 0.2f),
            jackFeatures(100, ankle = 0.8f, wrist = 0.8f, wristToHead = 0f),
            jackFeatures(200, ankle = 1.3f, wrist = 1.2f, wristToHead = -0.25f),
            jackFeatures(300, ankle = 0.8f, wrist = 0.8f, wristToHead = 0f),
            jackFeatures(400, ankle = 0.4f, wrist = 0.4f, wristToHead = 0.2f),
        )
        val result = lastResult(sequence) { features -> detector.process(emptyFrame(features.timestamp), features) }
        assertEquals(1, result.repCount)
        assertEquals(ExerciseState.CLOSED, result.state)
    }

    @Test
    fun walkingGaitUsesAlternatingLegMotion() {
        val detector = GaitDetector(GaitDetectorConfig(stateHoldFrames = 2))
        val result = lastResult(gaitSequence(periodMs = 500L, velocity = 0.35f)) { features ->
            detector.process(emptyFrame(features.timestamp), features)
        }
        assertEquals(ExerciseType.WALKING, result.exercise)
        assertTrue(result.stepCount > 2)
    }

    @Test
    fun runningGaitUsesHigherCadence() {
        val detector = GaitDetector(GaitDetectorConfig(stateHoldFrames = 2))
        val result = lastResult(gaitSequence(periodMs = 180L, velocity = 1.0f)) { features ->
            detector.process(emptyFrame(features.timestamp), features)
        }
        assertEquals(ExerciseType.RUNNING, result.exercise)
        assertTrue(result.cadenceSpm!! >= 145)
    }

    @Test
    fun noisyIncompleteSquatDoesNotRapidlySwitchToRep() {
        val detector = SquatDetector()
        val sequence = listOf(170f, 142f, 151f, 138f, 156f, 148f, 165f).mapIndexed { index, knee ->
            squatFeatures(index * 50L, knee = knee, hip = 150f, hipY = 0.03f)
        }
        val result = lastResult(sequence) { features -> detector.process(emptyFrame(features.timestamp), features) }
        assertEquals(0, result.repCount)
    }

    @Test
    fun lowConfidenceJointsDoNotCountRep() {
        val detector = PushUpDetector()
        val result = listOf(165f, 80f, 165f).mapIndexed { index, elbow ->
            pushUpFeatures(index * 100L, elbow).copy(averageConfidence = 0.1f)
        }.let { sequence -> lastResult(sequence) { features -> detector.process(emptyFrame(features.timestamp), features) } }
        assertEquals(0, result.repCount)
        assertTrue(result.confidence < 0.35f)
    }

    private fun squatFeatures(timestamp: Long, knee: Float, hip: Float, hipY: Float): PoseFeatures =
        PoseFeatures(
            timestamp = timestamp,
            leftKneeAngle = knee,
            rightKneeAngle = knee,
            leftHipAngle = hip,
            rightHipAngle = hip,
            hipVerticalPosition = hipY,
            torsoAngle = 8f,
            averageConfidence = 0.9f,
        )

    private fun pushUpFeatures(timestamp: Long, elbow: Float): PoseFeatures =
        PoseFeatures(
            timestamp = timestamp,
            leftElbowAngle = elbow,
            rightElbowAngle = elbow,
            torsoAngle = 5f,
            averageConfidence = 0.9f,
        )

    private fun jackFeatures(timestamp: Long, ankle: Float, wrist: Float, wristToHead: Float): PoseFeatures =
        PoseFeatures(
            timestamp = timestamp,
            ankleDistance = ankle,
            wristDistance = wrist,
            wristRelativeToHeadPosition = wristToHead,
            averageConfidence = 0.9f,
        )

    private fun gaitSequence(periodMs: Long, velocity: Float): List<PoseFeatures> =
        (0 until 28).map { index ->
            val sign = if (index % 2 == 0) 1f else -1f
            PoseFeatures(
                timestamp = index * periodMs,
                jointVelocities = mapOf(
                    BodyPart.LEFT_KNEE to velocity,
                    BodyPart.RIGHT_KNEE to velocity,
                    BodyPart.LEFT_ANKLE to velocity,
                    BodyPart.RIGHT_ANKLE to velocity,
                ),
                jointDirections = mapOf(
                    BodyPart.LEFT_ANKLE to MovementVector(velocity, 0f, 0.03f * sign),
                    BodyPart.RIGHT_ANKLE to MovementVector(velocity, 0f, -0.03f * sign),
                ),
                averageConfidence = 0.9f,
            )
        }

    private fun frame(timestamp: Long, vararg keypoints: KeyPoint): PoseFrame =
        PoseFrame(timestamp, keypoints.toList())

    private fun emptyFrame(timestamp: Long = 0L): PoseFrame = PoseFrame(timestamp, emptyList())

    private fun key(type: BodyPart, x: Float, y: Float, confidence: Float = 1f): KeyPoint =
        KeyPoint(type, x, y, confidence)

    private fun lastResult(sequence: List<PoseFeatures>, block: (PoseFeatures) -> ExerciseResult): ExerciseResult {
        var result = ExerciseResult(ExerciseType.UNKNOWN, ExerciseState.UNKNOWN, 0, 0f, 0f)
        sequence.forEach { result = block(it) }
        return result
    }
}
