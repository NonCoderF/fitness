package io.motionguard.exercise

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TfliteExerciseClassifierLayerTest {
    @Test
    fun featureEncoderUsesDocumentedOrder() {
        val features = PoseFeatures(
            timestamp = 0,
            leftElbowAngle = 90f,
            rightElbowAngle = 45f,
            leftShoulderAngle = 30f,
            rightShoulderAngle = 60f,
            leftHipAngle = 120f,
            rightHipAngle = 150f,
            leftKneeAngle = 100f,
            rightKneeAngle = 140f,
            torsoAngle = 18f,
            shoulderWidth = 0.4f,
            ankleDistance = 0.8f,
            wristDistance = 0.7f,
            wristRelativeToHeadPosition = -0.2f,
            hipVerticalPosition = 0.1f,
            kneeVerticalPosition = 0.5f,
            leftRightSymmetry = 0.9f,
            averageConfidence = 0.85f,
            jointVelocities = mapOf(BodyPart.NOSE to 0.3f),
            jointDirections = mapOf(BodyPart.NOSE to MovementVector(0.3f, 0.1f, -0.1f)),
            normalizedPoints = mapOf(BodyPart.NOSE to Point(0.11f, -0.22f)),
        )

        val encoded = ExerciseFeatureEncoder().encode(features)

        assertEquals(ExerciseFeatureEncoder.FEATURE_COUNT, encoded.size)
        assertEquals(0.11f, encoded[0], 0.001f)
        assertEquals(-0.22f, encoded[1], 0.001f)
        assertEquals(90f / 180f, encoded[34], 0.001f)
        assertEquals(45f / 180f, encoded[35], 0.001f)
        assertEquals(0.4f, encoded[43], 0.001f)
        assertEquals(0.85f, encoded[50], 0.001f)
        assertEquals(0.3f, encoded[51], 0.001f)
        assertEquals(0.1f, encoded[52], 0.001f)
        assertEquals(-0.1f, encoded[53], 0.001f)
    }

    @Test
    fun sequenceBufferWaitsForConfiguredLengthAndFlattensTensor() {
        val buffer = PoseSequenceBuffer(sequenceLength = 3)
        assertFalse(buffer.isReady)
        buffer.append(features(0, noseX = 0.1f))
        buffer.append(features(1, noseX = 0.2f))
        assertFalse(buffer.isReady)
        buffer.append(features(2, noseX = 0.3f))
        assertTrue(buffer.isReady)
        assertEquals(3, buffer.sequence().size)
        assertEquals(0.1f, buffer.temporalTensor()[0], 0.001f)
        assertEquals(0.2f, buffer.temporalTensor()[ExerciseFeatureEncoder.FEATURE_COUNT], 0.001f)
        buffer.append(features(3, noseX = 0.4f))
        assertEquals(0.2f, buffer.temporalTensor()[0], 0.001f)
    }

    @Test
    fun classificationStabilizerRequiresConsecutiveHighConfidencePredictions() {
        val stabilizer = ClassificationStabilizer(
            ClassificationStabilizerConfig(minConfidence = 0.7f, requiredConsecutivePredictions = 2),
        )
        assertEquals(ExercisePhase.UNKNOWN, stabilizer.update(result(ExercisePhase.SQUAT_UP, 0.9f)))
        assertEquals(ExercisePhase.SQUAT_UP, stabilizer.update(result(ExercisePhase.SQUAT_UP, 0.9f)))
        assertEquals(ExercisePhase.SQUAT_UP, stabilizer.update(result(ExercisePhase.PUSHUP_DOWN, 0.9f), allowedPhases = setOf(ExercisePhase.SQUAT_UP)))
    }

    @Test
    fun lowConfidencePredictionsDoNotBecomeActive() {
        val stabilizer = ClassificationStabilizer(
            ClassificationStabilizerConfig(minConfidence = 0.7f, requiredConsecutivePredictions = 1),
        )
        assertEquals(ExercisePhase.UNKNOWN, stabilizer.update(result(ExercisePhase.SQUAT_DOWN, 0.4f)))
    }

    @Test
    fun unknownPredictionsDoNotDestabilizeActiveState() {
        val stabilizer = ClassificationStabilizer(
            ClassificationStabilizerConfig(minConfidence = 0.7f, requiredConsecutivePredictions = 1),
        )
        assertEquals(ExercisePhase.SQUAT_UP, stabilizer.update(result(ExercisePhase.SQUAT_UP, 0.9f)))
        assertEquals(ExercisePhase.SQUAT_UP, stabilizer.update(result(ExercisePhase.UNKNOWN, 0.1f)))
    }

    @Test
    fun squatRepCountsUpDownUpOnlyOnce() {
        val machine = SquatRepStateMachine()
        assertEquals(0, machine.process(ExercisePhase.SQUAT_DOWN))
        assertEquals(0, machine.process(ExercisePhase.SQUAT_UP))
        assertEquals(0, machine.process(ExercisePhase.SQUAT_DOWN))
        assertEquals(1, machine.process(ExercisePhase.SQUAT_UP))
        assertEquals(1, machine.process(ExercisePhase.SQUAT_UP))
    }

    @Test
    fun pushUpRepCountsUpDownUpOnlyOnce() {
        val machine = PushUpRepStateMachine()
        assertEquals(0, machine.process(ExercisePhase.PUSHUP_UP))
        assertEquals(0, machine.process(ExercisePhase.PUSHUP_DOWN))
        assertEquals(1, machine.process(ExercisePhase.PUSHUP_UP))
        assertEquals(1, machine.process(ExercisePhase.PUSHUP_UP))
    }

    @Test
    fun jumpingJackRepCountsClosedOpenClosedOnlyOnce() {
        val machine = JumpingJackRepStateMachine()
        assertEquals(0, machine.process(ExercisePhase.JUMPING_JACK_CLOSED))
        assertEquals(0, machine.process(ExercisePhase.JUMPING_JACK_OPEN))
        assertEquals(1, machine.process(ExercisePhase.JUMPING_JACK_CLOSED))
        assertEquals(1, machine.process(ExercisePhase.JUMPING_JACK_CLOSED))
    }

    @Test
    fun fakeClassifierCanResetForTests() = runTest {
        val classifier = FakeExerciseClassifier(
            result(ExercisePhase.SQUAT_UP, 0.9f),
            result(ExercisePhase.SQUAT_DOWN, 0.9f),
        )
        assertEquals(ExercisePhase.SQUAT_UP, classifier.classify(listOf(features(0))).label)
        assertEquals(ExercisePhase.SQUAT_DOWN, classifier.classify(listOf(features(1))).label)
        classifier.reset()
        assertEquals(ExercisePhase.SQUAT_UP, classifier.classify(listOf(features(2))).label)
    }

    private fun features(timestamp: Long, noseX: Float = 0f): PoseFeatures =
        PoseFeatures(
            timestamp = timestamp,
            normalizedPoints = mapOf(BodyPart.NOSE to Point(noseX, 0f)),
            averageConfidence = 0.9f,
        )

    private fun result(phase: ExercisePhase, confidence: Float): ClassificationResult =
        ClassificationResult(phase, confidence, mapOf(phase to confidence))
}

private class FakeExerciseClassifier(
    private vararg val results: ClassificationResult,
) : ExerciseClassifier {
    private var index = 0

    override suspend fun classify(sequence: List<PoseFeatures>): ClassificationResult =
        results[index.coerceAtMost(results.lastIndex)].also { index++ }

    override fun reset() {
        index = 0
    }
}
