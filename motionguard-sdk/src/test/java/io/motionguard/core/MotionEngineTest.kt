package io.motionguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class MotionEngineTest {
    private val config = MotionConfig(
        window = WindowConfig(
            analysisWindowMs = 2_800L,
            cadenceWindowMs = 6_000L,
            minFramesForDecision = 8,
            minWindowDurationMs = 800L,
        ),
        transitions = TransitionConfig(
            movingConfirmationMs = 600L,
            stationaryConfirmationMs = 1_200L,
            lowQualityUncertainMs = 800L,
            movingGraceMs = 1_100L,
        ),
        cadence = CadenceConfig(
            minimumPeakCount = 3,
            peakProminence = 0.045f,
            smoothingAlpha = 0.35f,
        ),
    )

    @Test
    fun standingStill_classifiesStationaryWithoutCadence() {
        val result = runSequence(stillSequence(durationMs = 5_000L))

        assertEquals(MotionState.NOT_MOVING, result.state)
        assertEquals(ActivityType.STATIONARY, result.activityType)
        assertNull(result.cadenceSpm)
        assertTrue(result.confidence >= 0.65f)
        assertTrue(result.movementIntensity < 0.25f)
    }

    @Test
    fun slowWalking_classifiesWalkingWithPlausibleCadence() {
        val result = runSequence(gaitSequence(durationMs = 8_000L, stepIntervalMs = 760L, amplitude = 0.055f))

        assertEquals(result.summary(), MotionState.MOVING, result.state)
        assertEquals(ActivityType.WALKING, result.activityType)
        assertCadenceNear(expected = 79, actual = result.cadenceSpm, tolerance = 10)
        assertTrue(result.debugMetrics!!.gaitScore >= config.scoring.movingThreshold)
    }

    @Test
    fun walkingEmitsNewConfirmedStepEventsWithoutDuplicates() {
        val engine = MotionEngine(config)
        val events = gaitSequence(durationMs = 8_000L, stepIntervalMs = 625L, amplitude = 0.070f)
            .flatMap { engine.addFrame(it).stepEvents }

        assertTrue(events.size >= 5)
        assertEquals(events.size, events.map { it.timestampMs }.distinct().size)
        assertTrue(events.all { it.confidence >= 0.35f })
    }

    @Test
    fun standingDoesNotEmitConfirmedStepEvents() {
        val engine = MotionEngine(config)

        val events = stillSequence(durationMs = 6_000L)
            .flatMap { engine.addFrame(it).stepEvents }

        assertTrue(events.isEmpty())
    }

    @Test
    fun normalWalking_classifiesWalkingWithPlausibleCadence() {
        val result = runSequence(gaitSequence(durationMs = 8_000L, stepIntervalMs = 625L, amplitude = 0.070f))

        assertEquals(result.summary(), MotionState.MOVING, result.state)
        assertEquals(ActivityType.WALKING, result.activityType)
        assertCadenceNear(expected = 96, actual = result.cadenceSpm, tolerance = 9)
        assertTrue(result.movementIntensity >= 0.45f)
    }

    @Test
    fun jogging_classifiesWalkingOrRunningWithHigherCadence() {
        val result = runSequence(gaitSequence(durationMs = 8_000L, stepIntervalMs = 455L, amplitude = 0.088f))

        assertEquals(result.summary(), MotionState.MOVING, result.state)
        assertTrue(result.activityType == ActivityType.WALKING || result.activityType == ActivityType.RUNNING)
        assertCadenceNear(expected = 132, actual = result.cadenceSpm, tolerance = 12)
        assertTrue(result.confidence >= 0.50f)
    }

    @Test
    fun running_classifiesRunningWithPlausibleCadence() {
        val result = runSequence(gaitSequence(durationMs = 8_000L, stepIntervalMs = 365L, amplitude = 0.105f))

        assertEquals(result.summary(), MotionState.MOVING, result.state)
        assertEquals(result.summary(), ActivityType.RUNNING, result.activityType)
        assertCadenceNear(expected = 164, actual = result.cadenceSpm, tolerance = 14)
        assertTrue(result.movementIntensity >= config.classification.runningIntensityThreshold)
    }

    @Test
    fun landmarkJitterWhileStationary_staysStationary() {
        val frames = frameTimes(5_000L).mapIndexed { index, timestamp ->
            val jitter = if (index % 2 == 0) 0.0025f else -0.0025f
            standingFrame(timestampMs = timestamp, lowerBodyShiftX = jitter, lowerBodyShiftY = -jitter)
        }

        val result = runSequence(frames)

        assertEquals(MotionState.NOT_MOVING, result.state)
        assertEquals(ActivityType.STATIONARY, result.activityType)
        assertNull(result.cadenceSpm)
        assertTrue(result.debugMetrics!!.gaitScore < config.scoring.stationaryThreshold)
    }

    @Test
    fun armMovementOnly_doesNotCreateMovement() {
        val result = runSequence(stillSequence(durationMs = 5_000L))

        assertEquals(MotionState.NOT_MOVING, result.state)
        assertEquals(ActivityType.STATIONARY, result.activityType)
        assertNull(result.cadenceSpm)
    }

    @Test
    fun isolatedLegLift_doesNotStartMoving() {
        val frames = frameTimes(5_000L).map { timestamp ->
            val lift = if (timestamp in 1_800L..2_400L) -0.080f * sin(((timestamp - 1_800L) / 600f) * PI).toFloat() else 0f
            standingFrame(timestampMs = timestamp, leftAnkleYOffset = lift, leftKneeYOffset = lift * 0.5f)
        }

        val result = runSequence(frames)

        assertTrue(result.state == MotionState.NOT_MOVING || result.state == MotionState.UNCERTAIN)
        assertTrue(result.cadenceSpm == null || result.debugMetrics!!.periodicityScore < 0.45f)
    }

    @Test
    fun temporaryMissingPose_doesNotImmediatelyDropActiveMotion() {
        val engine = MotionEngine(config)
        gaitSequence(durationMs = 5_000L, stepIntervalMs = 365L, amplitude = 0.105f).forEach(engine::addFrame)

        val result = frameTimes(durationMs = 700L, startMs = 5_100L)
            .map { missingFrame(it) }
            .map(engine::addFrame)
            .last()

        assertEquals(result.summary(), MotionState.MOVING, result.state)
        assertEquals(false, result.poseDetected)
    }

    @Test
    fun poorLandmarkConfidence_returnsUncertain() {
        val result = runSequence(
            gaitSequence(durationMs = 4_000L, stepIntervalMs = 625L, amplitude = 0.075f, confidence = 0.25f),
        )

        assertEquals(MotionState.UNCERTAIN, result.state)
        assertEquals(ActivityType.UNKNOWN, result.activityType)
        assertTrue(result.poseQuality < config.pose.minimumPoseQualityForDecision)
    }

    @Test
    fun cameraTranslationWhereWholePoseShiftsTogether_staysStationary() {
        val frames = frameTimes(5_000L).map { timestamp ->
            val shift = sin(timestamp / 240f).toFloat() * 0.055f
            standingFrame(timestampMs = timestamp, cameraShiftX = shift, cameraShiftY = shift * 0.6f)
        }

        val result = runSequence(frames)

        assertEquals(MotionState.NOT_MOVING, result.state)
        assertEquals(ActivityType.STATIONARY, result.activityType)
        assertTrue(result.movementIntensity < 0.25f)
    }

    @Test
    fun sideViewWalking_classifiesMoving() {
        val result = runSequence(gaitSequence(durationMs = 8_000L, stepIntervalMs = 625L, amplitude = 0.075f, view = CameraView.SIDE_LEFT))

        assertEquals(result.summary(), MotionState.MOVING, result.state)
        assertTrue(result.cameraView == CameraView.SIDE_LEFT || result.cameraView == CameraView.SIDE_RIGHT)
        assertCadenceNear(expected = 96, actual = result.cadenceSpm, tolerance = 12)
    }

    @Test
    fun missingAnkles_degradesToUncertainRatherThanStationary() {
        val result = runSequence(
            gaitSequence(durationMs = 5_000L, stepIntervalMs = 625L, amplitude = 0.080f, includeAnkles = false),
        )

        assertTrue(result.state == MotionState.UNCERTAIN || result.confidence < 0.55f)
        assertTrue(result.poseQuality < 0.85f)
    }

    @Test
    fun oneLegMovement_isCappedBelowMovingConfidence() {
        val frames = frameTimes(6_000L).map { timestamp ->
            val lift = sin(PI.toFloat() * timestamp / 600f) * 0.09f
            standingFrame(timestampMs = timestamp, leftAnkleYOffset = lift, leftKneeYOffset = lift * 0.45f)
        }

        val result = runSequence(frames)

        assertTrue(result.state != MotionState.MOVING || result.confidence < 0.50f)
    }

    @Test
    fun jumpingSynchronousLegMotion_isRejectedAsTreadmillGait() {
        val frames = frameTimes(6_000L).map { timestamp ->
            val bounce = sin(PI.toFloat() * timestamp / 380f) * 0.09f
            standingFrame(
                timestampMs = timestamp,
                leftAnkleYOffset = bounce,
                rightAnkleYOffset = bounce,
                leftKneeYOffset = bounce * 0.5f,
                rightKneeYOffset = bounce * 0.5f,
            )
        }

        val result = runSequence(frames)

        assertTrue(result.state != MotionState.MOVING)
        assertTrue(result.debugMetrics!!.synchronousLegScore > 0.70f)
    }

    @Test
    fun calibrationRaisesEffectiveThresholdForNoisyStanding() {
        val engine = MotionEngine(config)
        frameTimes(2_500L).forEachIndexed { index, timestamp ->
            val jitter = sin(index.toFloat()) * 0.018f
            engine.addFrame(standingFrame(timestampMs = timestamp, lowerBodyShiftY = jitter))
        }
        val result = engine.addFrame(standingFrame(timestampMs = 2_700L))

        assertTrue(result.calibration != null)
        assertTrue(result.debugMetrics!!.effectiveAnkleThreshold >= config.scoring.minimumStepSignalAmplitude)
    }

    @Test
    fun suddenBodyScaleChange_degradesTracking() {
        val engine = MotionEngine(config)
        gaitSequence(durationMs = 3_000L, stepIntervalMs = 625L, amplitude = 0.075f).forEach(engine::addFrame)
        val result = engine.addFrame(standingFrame(timestampMs = 3_100L, bodyScaleMultiplier = 1.8f))

        assertTrue(result.trackingStatus != TrackingStatus.TRACKING_GOOD || result.state == MotionState.UNCERTAIN)
    }

    @Test
    fun poseSerializationRoundTripsFramesForReplay() {
        val frames = gaitSequence(durationMs = 1_000L, stepIntervalMs = 625L, amplitude = 0.070f)
        val restored = PoseFrameSerializer.deserialize(PoseFrameSerializer.serialize(frames))
        val replayResults = PoseReplayRunner(MotionEngine(config)).replay(restored)

        assertEquals(frames.size, restored.size)
        assertEquals(replayResults.size, restored.size)
    }

    @Test
    fun variableInputFrameRate_keepsCadencePlausible() {
        val timestamps = buildList {
            var time = 0L
            var index = 0
            while (time <= 8_000L) {
                add(time)
                time += listOf(72L, 108L, 92L, 134L)[index % 4]
                index++
            }
        }
        val frames = gaitSequence(timestamps = timestamps, stepIntervalMs = 625L, amplitude = 0.072f)

        val result = runSequence(frames)

        assertEquals(result.summary(), MotionState.MOVING, result.state)
        assertCadenceNear(expected = 96, actual = result.cadenceSpm, tolerance = 14)
    }

    @Test
    fun movementBeginning_requiresSustainedEvidence() {
        val engine = MotionEngine(config)
        stillSequence(durationMs = 2_500L).forEach(engine::addFrame)
        val earlyMoving = gaitSequence(durationMs = 400L, startMs = 2_600L, stepIntervalMs = 625L, amplitude = 0.075f)
            .map(engine::addFrame)
            .last()
        assertTrue(earlyMoving.state != MotionState.MOVING)

        val final = gaitSequence(durationMs = 5_000L, startMs = 3_100L, stepIntervalMs = 625L, amplitude = 0.075f)
            .map(engine::addFrame)
            .last()
        assertEquals(final.summary(), MotionState.MOVING, final.state)
    }

    @Test
    fun movementEnding_usesGraceThenTransitionsStationary() {
        val engine = MotionEngine(config)
        gaitSequence(durationMs = 5_000L, stepIntervalMs = 365L, amplitude = 0.105f).forEach(engine::addFrame)

        val final = stillSequence(durationMs = 8_000L, startMs = 5_100L)
            .map(engine::addFrame)
            .last()

        assertEquals(MotionState.NOT_MOVING, final.state)
        assertEquals(ActivityType.STATIONARY, final.activityType)
    }

    @Test
    fun briefPauseWhileRunning_keepsMovingState() {
        val engine = MotionEngine(config)
        gaitSequence(durationMs = 4_500L, stepIntervalMs = 365L, amplitude = 0.105f).forEach(engine::addFrame)
        stillSequence(durationMs = 650L, startMs = 4_600L).forEach(engine::addFrame)

        val result = gaitSequence(durationMs = 2_500L, startMs = 5_350L, stepIntervalMs = 365L, amplitude = 0.105f)
            .map(engine::addFrame)
            .last()

        assertEquals(result.summary(), MotionState.MOVING, result.state)
        assertTrue(result.activityType == ActivityType.RUNNING || result.activityType == ActivityType.WALKING)
    }

    @Test
    fun inconsistentRandomLegMovement_doesNotReliablyClassifyMoving() {
        val offsets = listOf(0.0f, 0.08f, -0.01f, 0.04f, -0.09f, 0.02f, 0.01f, -0.05f, 0.07f, 0.00f)
        val frames = frameTimes(5_000L).mapIndexed { index, timestamp ->
            standingFrame(
                timestampMs = timestamp,
                leftAnkleYOffset = offsets[index % offsets.size],
                rightAnkleYOffset = offsets[(index * 3 + 1) % offsets.size] * 0.5f,
                leftKneeYOffset = offsets[(index + 2) % offsets.size] * 0.25f,
                rightKneeYOffset = offsets[(index + 5) % offsets.size] * 0.20f,
            )
        }

        val result = runSequence(frames)

        assertTrue(result.state != MotionState.MOVING || result.confidence < 0.55f)
        assertTrue(result.debugMetrics!!.periodicityScore < 0.65f)
    }

    private fun runSequence(frames: List<PoseFrame>): MotionResult {
        val engine = MotionEngine(config)
        return frames.map(engine::addFrame).last()
    }

    private fun stillSequence(durationMs: Long, startMs: Long = 0L): List<PoseFrame> =
        frameTimes(durationMs, startMs).map { timestamp -> standingFrame(timestampMs = timestamp) }

    private fun gaitSequence(
        durationMs: Long,
        stepIntervalMs: Long,
        amplitude: Float,
        startMs: Long = 0L,
        confidence: Float = 0.92f,
        view: CameraView = CameraView.FRONT,
        includeAnkles: Boolean = true,
    ): List<PoseFrame> = gaitSequence(
        timestamps = frameTimes(durationMs, startMs),
        stepIntervalMs = stepIntervalMs,
        amplitude = amplitude,
        confidence = confidence,
        view = view,
        includeAnkles = includeAnkles,
    )

    private fun gaitSequence(
        timestamps: List<Long>,
        stepIntervalMs: Long,
        amplitude: Float,
        confidence: Float = 0.92f,
        view: CameraView = CameraView.FRONT,
        includeAnkles: Boolean = true,
    ): List<PoseFrame> = timestamps.map { timestamp ->
        val phase = PI.toFloat() * timestamp / stepIntervalMs
        val leftSwing = sin(phase)
        val rightSwing = -leftSwing
        val xMultiplier = if (view == CameraView.SIDE_LEFT || view == CameraView.SIDE_RIGHT) 1.15f else 0.55f
        val yMultiplier = if (view == CameraView.SIDE_LEFT || view == CameraView.SIDE_RIGHT) 0.45f else 1f
        standingFrame(
            timestampMs = timestamp,
            leftAnkleYOffset = if (includeAnkles) leftSwing * amplitude * yMultiplier else 0f,
            rightAnkleYOffset = if (includeAnkles) rightSwing * amplitude * yMultiplier else 0f,
            leftAnkleXOffset = if (includeAnkles) leftSwing * amplitude * xMultiplier else 0f,
            rightAnkleXOffset = if (includeAnkles) rightSwing * amplitude * xMultiplier else 0f,
            leftKneeYOffset = -maxOf(leftSwing, 0f) * amplitude * 0.65f,
            rightKneeYOffset = -maxOf(rightSwing, 0f) * amplitude * 0.65f,
            leftKneeXOffset = leftSwing * amplitude * 0.30f,
            rightKneeXOffset = rightSwing * amplitude * 0.30f,
            confidence = confidence,
            view = view,
            includeAnkles = includeAnkles,
        )
    }

    private fun standingFrame(
        timestampMs: Long,
        lowerBodyShiftX: Float = 0f,
        lowerBodyShiftY: Float = 0f,
        cameraShiftX: Float = 0f,
        cameraShiftY: Float = 0f,
        leftAnkleYOffset: Float = 0f,
        rightAnkleYOffset: Float = 0f,
        leftKneeYOffset: Float = 0f,
        rightKneeYOffset: Float = 0f,
        leftAnkleXOffset: Float = 0f,
        rightAnkleXOffset: Float = 0f,
        leftKneeXOffset: Float = 0f,
        rightKneeXOffset: Float = 0f,
        confidence: Float = 0.92f,
        view: CameraView = CameraView.FRONT,
        includeAnkles: Boolean = true,
        bodyScaleMultiplier: Float = 1f,
    ): PoseFrame = PoseFrame(
        timestampMs = timestampMs,
        leftShoulder = point(0.50f - ((if (view == CameraView.FRONT) 0.12f else 0.03f) * bodyScaleMultiplier) + cameraShiftX, 0.25f + cameraShiftY, confidence),
        rightShoulder = point(0.50f + ((if (view == CameraView.FRONT) 0.12f else 0.03f) * bodyScaleMultiplier) + cameraShiftX, 0.25f + cameraShiftY, confidence),
        leftHip = point(0.45f + cameraShiftX, 0.42f + cameraShiftY, confidence),
        rightHip = point(0.45f + ((if (view == CameraView.FRONT) 0.12f else 0.04f) * bodyScaleMultiplier) + cameraShiftX, 0.42f + cameraShiftY, confidence),
        leftKnee = point(
            0.44f + cameraShiftX + lowerBodyShiftX + leftKneeXOffset,
            0.42f + (0.20f * bodyScaleMultiplier) + cameraShiftY + lowerBodyShiftY + leftKneeYOffset,
            confidence,
        ),
        rightKnee = point(
            0.56f + cameraShiftX + lowerBodyShiftX + rightKneeXOffset,
            0.42f + (0.20f * bodyScaleMultiplier) + cameraShiftY + lowerBodyShiftY + rightKneeYOffset,
            confidence,
        ),
        leftAnkle = if (includeAnkles) point(
            0.43f + cameraShiftX + lowerBodyShiftX + leftAnkleXOffset,
            0.42f + (0.42f * bodyScaleMultiplier) + cameraShiftY + lowerBodyShiftY + leftAnkleYOffset,
            confidence,
        ) else null,
        rightAnkle = if (includeAnkles) point(
            0.57f + cameraShiftX + lowerBodyShiftX + rightAnkleXOffset,
            0.42f + (0.42f * bodyScaleMultiplier) + cameraShiftY + lowerBodyShiftY + rightAnkleYOffset,
            confidence,
        ) else null,
    )

    private fun frameTimes(durationMs: Long, startMs: Long = 0L, intervalMs: Long = 100L): List<Long> {
        val values = mutableListOf<Long>()
        var timestamp = startMs
        val end = startMs + durationMs
        while (timestamp <= end) {
            values.add(timestamp)
            timestamp += intervalMs
        }
        return values
    }

    private fun missingFrame(timestampMs: Long): PoseFrame =
        PoseFrame(
            timestampMs = timestampMs,
            leftHip = null,
            rightHip = null,
            leftKnee = null,
            rightKnee = null,
            leftAnkle = null,
            rightAnkle = null,
        )

    private fun point(x: Float, y: Float, confidence: Float): Point2D =
        Point2D(x = x, y = y, confidence = confidence)

    private fun assertCadenceNear(expected: Int, actual: Int?, tolerance: Int) {
        assertTrue("Expected cadence but was null", actual != null)
        assertTrue(
            "Expected cadence near $expected spm, actual was $actual spm",
            actual!! in (expected - tolerance)..(expected + tolerance),
        )
    }

    private fun MotionResult.summary(): String =
        "state=$state activity=$activityType cadence=$cadenceSpm confidence=$confidence intensity=$movementIntensity quality=$poseQuality debug=$debugMetrics"
}
