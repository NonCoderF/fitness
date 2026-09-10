package io.motionguard.core

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Deterministic, platform-independent treadmill motion analyzer that consumes normalized [PoseFrame] samples. */
public class MotionEngine(private val config: MotionConfig = MotionConfig()) {
    private val ema = PoseEmaSmoother(config.ema, config.pose.minimumLandmarkConfidence)
    private val frames = ArrayDeque<NormalizedFrame>()
    private val calibrationFrames = ArrayDeque<NormalizedFrame>()
    private var state = MotionState.UNCERTAIN
    private var candidateMovingSinceMs: Long? = null
    private var candidateStillSinceMs: Long? = null
    private var lowQualitySinceMs: Long? = null
    private var lastActiveMotionMs: Long? = null
    private var lastPoseSeenMs: Long? = null
    private var smoothedCadenceSpm: Float? = null
    private var calibration: MotionCalibration? = null
    private var latestDeviceMotion: DeviceMotionSample? = null
    private var lastEmittedStepTimestampMs: Long? = null
    private var previousCycleSignal: Float? = null
    private var cycleBaseline: Float? = null
    private var cycleSign: Int = 0
    private var lastCycleTransitionMs: Long? = null
    private var cycleExcursion: Float = 0f
    private val completedStepTimestamps = ArrayDeque<Long>()
    private var lastResult = emptyResult()

    fun reset() {
        frames.clear()
        ema.reset()
        calibrationFrames.clear()
        state = MotionState.UNCERTAIN
        candidateMovingSinceMs = null
        candidateStillSinceMs = null
        lowQualitySinceMs = null
        lastActiveMotionMs = null
        lastPoseSeenMs = null
        smoothedCadenceSpm = null
        calibration = null
        latestDeviceMotion = null
        lastEmittedStepTimestampMs = null
        previousCycleSignal = null
        cycleBaseline = null
        cycleSign = 0
        lastCycleTransitionMs = null
        cycleExcursion = 0f
        completedStepTimestamps.clear()
        lastResult = emptyResult()
    }

    fun updateDeviceMotion(sample: DeviceMotionSample?) {
        latestDeviceMotion = sample
    }

    fun addFrame(frame: PoseFrame): MotionResult {
        val smoothedFrame = ema.filter(frame)
        val visibility = smoothedFrame.visibility()
        val poseQuality = calculatePoseQuality(smoothedFrame, visibility)
        val cameraView = inferCameraView(smoothedFrame)
        val normalized = smoothedFrame.normalizedOrNull(poseQuality, visibility, cameraView)
        val tracking = trackingStatus(frame.timestampMs, poseQuality, normalized)

        if (tracking == TrackingStatus.TRACKING_LOST && shouldResetForLongLoss(frame.timestampMs)) {
            frames.clear()
            smoothedCadenceSpm = null
            lastEmittedStepTimestampMs = null
        }
        if (normalized != null) {
            handleContinuity(normalized)
            frames.addLast(normalized)
            lastPoseSeenMs = frame.timestampMs
            trimWindow(frame.timestampMs)
            updateCalibration(normalized)
        }
        val completedStep = normalized?.let(::detectCompletedStep)

        val analysis = analyzeWindow(frame.timestampMs, poseQuality, visibility, cameraView, tracking)
        val result = updateState(frame.timestampMs, poseQuality, normalized != null, cameraView, tracking, analysis, completedStep)
        lastResult = result
        return result
    }

    private fun updateState(
        timestampMs: Long,
        poseQuality: Float,
        poseDetected: Boolean,
        cameraView: CameraView,
        tracking: TrackingStatus,
        analysis: WindowAnalysis?,
        completedStep: StepEvent?,
    ): MotionResult {
        if (analysis == null) {
            if (tracking == TrackingStatus.TRACKING_DEGRADED) lowQualitySinceMs = lowQualitySinceMs ?: timestampMs
            if (tracking == TrackingStatus.TRACKING_LOST &&
                timestampMs - (lastActiveMotionMs ?: timestampMs) > config.recovery.longOcclusionResetMs
            ) {
                state = MotionState.UNCERTAIN
            }
            return MotionResult(
                state = state,
                confidence = lastResult.confidence * if (poseDetected) 0.82f else 0.72f,
                activityType = classify(state, lastResult.cadenceSpm, lastResult.movementIntensity, MotionDebugMetrics()),
                cadenceSpm = lastResult.cadenceSpm,
                gaitSpeed = lastResult.gaitSpeed * 0.75f,
                movementIntensity = lastResult.movementIntensity * 0.75f,
                poseQuality = poseQuality,
                poseDetected = poseDetected,
                cameraView = cameraView,
                trackingStatus = tracking,
                calibration = calibration,
                debugMetrics = MotionDebugMetrics(
                    poseQuality = poseQuality,
                    cameraView = cameraView,
                    trackingStatus = tracking,
                    calibration = calibration,
                ),
                stepEvents = emptyList(),
            )
        }

        if (tracking != TrackingStatus.TRACKING_GOOD) {
            lowQualitySinceMs = lowQualitySinceMs ?: timestampMs
            candidateMovingSinceMs = null
            if (timestampMs - (lowQualitySinceMs ?: timestampMs) >= config.transitions.lowQualityUncertainMs) {
                state = MotionState.UNCERTAIN
            }
        } else {
            lowQualitySinceMs = null
            val movingEvidence = analysis.gaitScore >= analysis.effectiveMovingThreshold &&
                analysis.cadenceSpm != null &&
                analysis.debugMetrics.bilateralConsistency >= config.scoring.minimumBilateralConsistency &&
                analysis.debugMetrics.periodicityScore >= 0.55f &&
                analysis.debugMetrics.alternationScore >= 0.50f &&
                analysis.debugMetrics.synchronousLegScore < config.scoring.synchronousLegRejectionThreshold
            val stillEvidence = analysis.gaitScore <= config.scoring.stationaryThreshold &&
                analysis.visibility.lowerBodyScore >= 0.55f &&
                frames.last().leftAnkle != null && frames.last().rightAnkle != null

            when {
                movingEvidence -> {
                    candidateMovingSinceMs = candidateMovingSinceMs ?: timestampMs
                    candidateStillSinceMs = null
                    if (timestampMs - (candidateMovingSinceMs ?: timestampMs) >= config.transitions.movingConfirmationMs) {
                        state = MotionState.MOVING
                        lastActiveMotionMs = timestampMs
                    }
                }
                stillEvidence -> {
                    candidateStillSinceMs = candidateStillSinceMs ?: timestampMs
                    candidateMovingSinceMs = null
                    val graceElapsed = lastActiveMotionMs?.let { timestampMs - it > config.transitions.movingGraceMs } ?: true
                    if ((state != MotionState.MOVING || graceElapsed) &&
                        timestampMs - (candidateStillSinceMs ?: timestampMs) >= config.transitions.stationaryConfirmationMs
                    ) {
                        state = MotionState.NOT_MOVING
                    }
                }
                else -> {
                    candidateMovingSinceMs = null
                    candidateStillSinceMs = null
                    if (analysis.visibility.lowerBodyScore < 0.42f) state = MotionState.UNCERTAIN
                }
            }
        }

        val confidence = when (state) {
            MotionState.MOVING -> analysis.gaitScore * poseQuality
            MotionState.NOT_MOVING -> (1f - analysis.gaitScore) * poseQuality
            MotionState.UNCERTAIN -> min(poseQuality, 1f - abs(analysis.gaitScore - 0.5f))
        }.coerceIn(0f, 1f)

        val stepEvents = if (state == MotionState.MOVING && completedStep != null) {
            emitStepEvents(listOf(completedStep))
        } else {
            completedStep?.timestampMs?.let { candidate ->
                lastEmittedStepTimestampMs = max(lastEmittedStepTimestampMs ?: Long.MIN_VALUE, candidate)
            }
            emptyList()
        }

        return MotionResult(
            state = state,
            confidence = confidence,
            activityType = classify(state, analysis.cadenceSpm, analysis.movementIntensity, analysis.debugMetrics),
            cadenceSpm = analysis.cadenceSpm,
            gaitSpeed = gaitSpeed(analysis.cadenceSpm),
            movementIntensity = analysis.movementIntensity,
            poseQuality = poseQuality,
            poseDetected = poseDetected,
            cameraView = cameraView,
            trackingStatus = tracking,
            calibration = calibration,
            debugMetrics = analysis.debugMetrics.copy(
                poseQuality = poseQuality,
                cameraView = cameraView,
                trackingStatus = tracking,
                visibility = analysis.visibility,
                calibration = calibration,
            ),
            stepEvents = stepEvents,
        )
    }

    private fun analyzeWindow(
        timestampMs: Long,
        poseQuality: Float,
        visibility: PoseVisibility,
        cameraView: CameraView,
        tracking: TrackingStatus,
    ): WindowAnalysis? {
        trimWindow(timestampMs)
        if (frames.size < config.window.minFramesForDecision) return null
        if (frames.last().timestampMs - frames.first().timestampMs < config.window.minWindowDurationMs) return null

        val effectiveAnkleThreshold = effectiveAnkleThreshold()
        val effectiveKneeThreshold = config.scoring.kneeAngleCycleThresholdDegrees
        val leftSignal = selectCadenceSignal(
            ankle = frames.mapNotNull { it.leftAnkle?.let { ankle -> if (cameraView == CameraView.SIDE_LEFT || cameraView == CameraView.SIDE_RIGHT) ankle.x else ankle.y } },
            knee = frames.mapNotNull { it.leftKneeAngleDegrees?.div(180f) },
        )
        val rightSignal = selectCadenceSignal(
            ankle = frames.mapNotNull { it.rightAnkle?.let { ankle -> if (cameraView == CameraView.SIDE_LEFT || cameraView == CameraView.SIDE_RIGHT) ankle.x else ankle.y } },
            knee = frames.mapNotNull { it.rightKneeAngleDegrees?.div(180f) },
        )
        val leftAnkleSignal = frames.mapNotNull { it.leftAnkle?.let { ankle -> if (cameraView == CameraView.SIDE_LEFT || cameraView == CameraView.SIDE_RIGHT) ankle.x else ankle.y } }
        val rightAnkleSignal = frames.mapNotNull { it.rightAnkle?.let { ankle -> if (cameraView == CameraView.SIDE_LEFT || cameraView == CameraView.SIDE_RIGHT) ankle.x else ankle.y } }
        val leftKneeAngles = frames.mapNotNull { it.leftKneeAngleDegrees }
        val rightKneeAngles = frames.mapNotNull { it.rightKneeAngleDegrees }
        val hipWidths = frames.map { it.hipWidth }
        val hipY = frames.map { it.hipCenterY }

        val cadenceEstimate = estimateCadenceFromSteps(timestampMs)
        val periodicityScore = cadenceEstimate?.periodicityScore ?: 0f
        val ankleAmplitude = listOf(leftAnkleSignal.range(), rightAnkleSignal.range()).averageFloat()
        val kneeRange = listOf(leftKneeAngles.range(), rightKneeAngles.range()).averageFloat()
        val alternationScore = alternatingScore(leftSignal, rightSignal)
        val bilateralConsistency = bilateralConsistency(leftSignal, rightSignal)
        val synchronousLegScore = synchronousScore(leftSignal, rightSignal)
        val hipStabilityScore = (1f - (hipWidths.range() / config.pose.geometryJumpTolerance)).coerceIn(0f, 1f)
        val bodyBounceScore = (hipY.range() / config.classification.bouncingHipMotionThreshold).coerceIn(0f, 1f)
        val ankleScore = (ankleAmplitude / effectiveAnkleThreshold).coerceIn(0f, 1f)
        val kneeCycleScore = (kneeRange / effectiveKneeThreshold).coerceIn(0f, 1f) * periodicityScore
        val kneeAmplitudeScore = (kneeRange / config.scoring.kneeAngleMovementThresholdDegrees).coerceIn(0f, 1f)
        val sensorPenalty = 1f - ((latestDeviceMotion?.motionScore ?: 0f) * config.scoring.phoneMotionPenaltyScale)

        val weights = viewpointWeights(cameraView, visibility)
        val weighted =
            alternationScore * weights.alternation +
                ankleScore * weights.ankle +
                kneeCycleScore * weights.kneeCycle +
                periodicityScore * weights.periodicity +
                kneeAmplitudeScore * weights.kneeAmplitude +
                bilateralConsistency * weights.bilateral
        val gaitScore = (weighted / weights.total * hipStabilityScore * sensorPenalty)
            .let { if (synchronousLegScore > config.scoring.synchronousLegRejectionThreshold) it * 0.42f else it }
            .let { if (visibility.leftLegScore < 0.55f || visibility.rightLegScore < 0.55f) min(it, config.scoring.oneLegConfidenceCap) else it }
            .let {
                if (frames.last().leftAnkle == null && frames.last().rightAnkle == null) min(it, 0.48f) else it
            }
            .coerceIn(0f, 1f)
        val intensity = (ankleScore * 0.34f + kneeAmplitudeScore * 0.30f + periodicityScore * 0.22f + bodyBounceScore * 0.14f)
            .coerceIn(0f, 1f) * poseQuality
        val cadence = cadenceEstimate?.cadenceSpm?.let { smoothCadence(it) }
        val debug = MotionDebugMetrics(
            alternationScore = alternationScore,
            kneeCycleScore = kneeCycleScore,
            ankleMovementScore = ankleScore,
            periodicityScore = periodicityScore,
            kneeAmplitudeScore = kneeAmplitudeScore,
            hipStabilityScore = hipStabilityScore,
            gaitScore = gaitScore,
            poseQuality = poseQuality,
            detectedStepCount = cadenceEstimate?.stepCount ?: 0,
            cameraView = cameraView,
            trackingStatus = tracking,
            visibility = visibility,
            bilateralConsistency = bilateralConsistency,
            cycleStability = periodicityScore,
            synchronousLegScore = synchronousLegScore,
            effectiveAnkleThreshold = effectiveAnkleThreshold,
            effectiveKneeAngleThresholdDegrees = effectiveKneeThreshold,
            sensorMotionScore = latestDeviceMotion?.motionScore ?: 0f,
            calibration = calibration,
            gaitPhase = cycleSign,
            rawCadenceSpm = cadenceEstimate?.cadenceSpm,
            smoothedCadenceSpm = cadence,
        )
        return WindowAnalysis(gaitScore, cadence, intensity, debug, visibility, effectiveMovingThreshold(cameraView, visibility))
    }

    private fun selectCadenceSignal(ankle: List<Float>, knee: List<Float>): List<Float> {
        if (ankle.size < config.window.minFramesForDecision) return knee
        if (knee.size < config.window.minFramesForDecision) return ankle
        return if (ankle.range() >= effectiveAnkleThreshold() * 0.75f) ankle else knee
    }

    /** Emits one step only after the bilateral lower-body phase crosses the deadband. */
    private fun detectCompletedStep(frame: NormalizedFrame): StepEvent? {
        val left = frame.leftAnkle?.let { if (frame.cameraView.isSide()) it.x else it.y }
            ?: frame.leftKneeAngleDegrees?.div(180f)
        val right = frame.rightAnkle?.let { if (frame.cameraView.isSide()) it.x else it.y }
            ?: frame.rightKneeAngleDegrees?.div(180f)
        if (left == null || right == null) return null

        val rawSignal = left - right
        val baseline = cycleBaseline ?: rawSignal.also { cycleBaseline = it }
        val signal = rawSignal - baseline
        val previous = previousCycleSignal
        previousCycleSignal = signal
        val deadband = 0.025f
        val sign = when {
            signal > deadband -> 1
            signal < -deadband -> -1
            else -> 0
        }
        if (sign == 0) {
            cycleExcursion = max(cycleExcursion, abs(signal))
            return null
        }
        if (cycleSign == 0) {
            cycleSign = sign
            cycleExcursion = abs(signal)
            return null
        }
        cycleExcursion = max(cycleExcursion, max(abs(signal), abs(previous ?: signal)))
        if (sign == cycleSign) return null

        val timestamp = frame.timestampMs
        val elapsed = lastCycleTransitionMs?.let { timestamp - it }
        val validInterval = elapsed == null || elapsed in 220L..1_500L
        val validExcursion = cycleExcursion >= 0.055f
        cycleSign = sign
        cycleExcursion = abs(signal)
        if (!validInterval || !validExcursion) return null

        lastCycleTransitionMs = timestamp
        completedStepTimestamps.addLast(timestamp)
        while (completedStepTimestamps.size > 24) completedStepTimestamps.removeFirst()
        return StepEvent(timestampMs = timestamp, confidence = 0.70f)
    }

    private fun CameraView.isSide(): Boolean = this == CameraView.SIDE_LEFT || this == CameraView.SIDE_RIGHT

    private fun emitStepEvents(candidates: List<StepEvent>): List<StepEvent> {
        val previous = lastEmittedStepTimestampMs
        val events = if (previous == null) {
            candidates.takeLast(1)
        } else {
            candidates.filter { it.timestampMs > previous }
        }
        if (events.isNotEmpty()) lastEmittedStepTimestampMs = events.maxOf { it.timestampMs }
        return events
    }

    private fun PoseFrame.normalizedOrNull(quality: Float, visibility: PoseVisibility, view: CameraView): NormalizedFrame? {
        val lh = leftHip ?: return null
        val rh = rightHip ?: return null
        if (quality < 0.30f || visibility.lowerBodyScore < 0.28f) return null
        val scale = stableBodyScale(this) ?: return null
        if (scale < config.pose.minimumNormalizedLegScale) return null
        val hipCenter = Point2D((lh.x + rh.x) / 2f, (lh.y + rh.y) / 2f, min(lh.confidence, rh.confidence))
        fun Point2D.n() = Point2D((x - hipCenter.x) / scale, (y - hipCenter.y) / scale, confidence)
        val nlh = lh.n()
        val nrh = rh.n()
        val nlk = leftKnee?.takeIfGood()?.n()
        val nrk = rightKnee?.takeIfGood()?.n()
        val nla = leftAnkle?.takeIfGood()?.n()
        val nra = rightAnkle?.takeIfGood()?.n()
        return NormalizedFrame(
            timestampMs = timestampMs,
            leftHip = nlh,
            rightHip = nrh,
            leftKnee = nlk,
            rightKnee = nrk,
            leftAnkle = nla,
            rightAnkle = nra,
            hipWidth = distance(nlh, nrh),
            hipCenterY = 0f,
            bodyScale = scale,
            cameraView = view,
            leftKneeAngleDegrees = angleDegrees(nlh, nlk, nla),
            rightKneeAngleDegrees = angleDegrees(nrh, nrk, nra),
        )
    }

    private fun Point2D.takeIfGood(): Point2D? = takeIf { confidence >= config.pose.minimumLandmarkConfidence }

    private fun calculatePoseQuality(frame: PoseFrame, visibility: PoseVisibility): Float {
        val joints = frame.lowerBodyJoints()
        val visible = joints.filter { it != null && it.confidence >= config.pose.minimumLandmarkConfidence }
        if (visible.isEmpty()) return 0f
        val confidenceScore = visible.map { it!!.confidence.coerceIn(0f, 1f) }.average().toFloat()
        val scaleScore = stableBodyScale(frame)?.let { (it / (config.pose.minimumNormalizedLegScale * 2.4f)).coerceIn(0f, 1f) } ?: 0f
        return (visibility.lowerBodyScore * 0.58f + confidenceScore * 0.30f + scaleScore * 0.12f).coerceIn(0f, 1f)
    }

    private fun PoseFrame.visibility(): PoseVisibility {
        fun Point2D?.score() = if (this != null) confidence.coerceIn(0f, 1f) else 0f
        val left = (leftHip.score() * 0.25f + leftKnee.score() * 0.42f + leftAnkle.score() * 0.33f)
        val right = (rightHip.score() * 0.25f + rightKnee.score() * 0.42f + rightAnkle.score() * 0.33f)
        return PoseVisibility(left, right, (left + right) / 2f, (left + right) / 2f)
    }

    private fun inferCameraView(frame: PoseFrame): CameraView {
        val hipWidth = width(frame.leftHip, frame.rightHip)
        val apparentWidth = hipWidth ?: 0f
        if (apparentWidth <= 0f) return calibration?.cameraView ?: CameraView.UNKNOWN
        if (apparentWidth > 0.12f) return CameraView.FRONT
        if (apparentWidth in 0.075f..0.12f) return CameraView.OBLIQUE
        val leftVisible = frame.leftKnee?.confidence ?: 0f
        val rightVisible = frame.rightKnee?.confidence ?: 0f
        return if (leftVisible >= rightVisible) CameraView.SIDE_LEFT else CameraView.SIDE_RIGHT
    }

    private fun updateCalibration(frame: NormalizedFrame) {
        if (!config.calibration.enabled || calibration != null) return
        calibrationFrames.addLast(frame)
        val duration = calibrationFrames.last().timestampMs - calibrationFrames.first().timestampMs
        if (duration < config.calibration.durationMs) return
        val jitter = calibrationFrames.zipWithNext { a, b ->
            listOfNotNull(
                a.leftKnee?.let { ak -> b.leftKnee?.let { bk -> distance(ak, bk) } },
                a.rightKnee?.let { ak -> b.rightKnee?.let { bk -> distance(ak, bk) } },
                a.leftAnkle?.let { aa -> b.leftAnkle?.let { ba -> distance(aa, ba) } },
                a.rightAnkle?.let { aa -> b.rightAnkle?.let { ba -> distance(aa, ba) } },
            ).averageFloat()
        }.averageFloat()
        if (jitter > config.calibration.maxStandingJitterForCalibration) {
            calibrationFrames.clear()
            return
        }
        calibration = MotionCalibration(
            baselineJitter = jitter,
            bodyScale = calibrationFrames.map { it.bodyScale }.average().toFloat(),
            hipWidth = calibrationFrames.map { it.hipWidth }.average().toFloat(),
            legLengthEstimate = null,
            cameraView = calibrationFrames.groupingBy { it.cameraView }.eachCount().maxByOrNull { it.value }?.key ?: CameraView.UNKNOWN,
            quality = if (jitter < 0.04f) 0.9f else 0.65f,
        )
        calibrationFrames.clear()
    }

    private fun effectiveAnkleThreshold(): Float {
        val adaptive = calibration?.baselineJitter?.let { it * config.calibration.jitterMultiplier }
        return max(config.scoring.minimumStepSignalAmplitude, adaptive ?: config.scoring.ankleMovementThreshold)
            .coerceIn(config.calibration.minAdaptiveAnkleThreshold, config.calibration.maxAdaptiveAnkleThreshold)
    }

    private fun effectiveMovingThreshold(view: CameraView, visibility: PoseVisibility): Float {
        val viewAdjustment = when (view) {
            CameraView.FRONT -> 0f
            CameraView.OBLIQUE -> 0.02f
            CameraView.SIDE_LEFT, CameraView.SIDE_RIGHT -> -0.02f
            CameraView.UNKNOWN -> 0.04f
        }
        val visibilityAdjustment = if (visibility.lowerBodyScore < 0.70f) 0.06f else 0f
        return (config.scoring.movingThreshold + viewAdjustment + visibilityAdjustment).coerceIn(0.44f, 0.72f)
    }

    private fun viewpointWeights(view: CameraView, visibility: PoseVisibility): Weights {
        val ankleAvailability = ((visibility.leftLegScore + visibility.rightLegScore) / 2f).coerceIn(0.15f, 1f)
        val side = view == CameraView.SIDE_LEFT || view == CameraView.SIDE_RIGHT
        return Weights(
            alternation = if (side) 0.16f else 0.26f,
            ankle = (if (side) 0.18f else 0.22f) * ankleAvailability,
            kneeCycle = if (side) 0.28f else 0.20f,
            periodicity = 0.22f,
            kneeAmplitude = if (side) 0.18f else 0.12f,
            bilateral = if (visibility.leftLegScore > 0.55f && visibility.rightLegScore > 0.55f) 0.16f else 0.06f,
        )
    }

    private fun trackingStatus(timestampMs: Long, quality: Float, frame: NormalizedFrame?): TrackingStatus {
        if (frame == null) return if (lastPoseSeenMs?.let { timestampMs - it < config.recovery.longOcclusionResetMs } == true) TrackingStatus.TRACKING_DEGRADED else TrackingStatus.TRACKING_LOST
        if (quality < config.pose.minimumPoseQualityForDecision) return TrackingStatus.TRACKING_DEGRADED
        val previous = frames.lastOrNull() ?: return TrackingStatus.TRACKING_GOOD
        val scaleJump = abs(previous.bodyScale - frame.bodyScale) / max(previous.bodyScale, 0.001f)
        return if (scaleJump > config.recovery.identityScaleJumpThreshold) TrackingStatus.TRACKING_DEGRADED else TrackingStatus.TRACKING_GOOD
    }

    private fun handleContinuity(frame: NormalizedFrame) {
        val previous = frames.lastOrNull() ?: return
        val scaleJump = abs(previous.bodyScale - frame.bodyScale) / max(previous.bodyScale, 0.001f)
        if (scaleJump > config.recovery.identityScaleJumpThreshold) {
            frames.clear()
            candidateMovingSinceMs = null
            smoothedCadenceSpm = null
            state = MotionState.UNCERTAIN
            lastEmittedStepTimestampMs = null
        }
    }

    private fun shouldResetForLongLoss(timestampMs: Long): Boolean =
        lastPoseSeenMs?.let { timestampMs - it > config.recovery.longOcclusionResetMs } == true

    private fun estimateCadenceFromSteps(timestampMs: Long): CadenceEstimate? {
        val oldest = timestampMs - config.cadenceWindowMs()
        while (completedStepTimestamps.isNotEmpty() && completedStepTimestamps.first() < oldest) {
            completedStepTimestamps.removeFirst()
        }
        if (completedStepTimestamps.size < config.cadence.minimumPeakCount) return null
        val intervals = completedStepTimestamps.zipWithNext { a, b -> (b - a).toFloat() }
        val median = intervals.median()
        if (median <= 0f) return null
        val cadence = 60_000f / median
        if (cadence !in config.cadence.minCadenceSpm.toFloat()..config.cadence.maxCadenceSpm.toFloat()) return null
        val average = intervals.average().toFloat()
        val deviation = sqrt(intervals.sumOf { ((it - average) * (it - average)).toDouble() }.toFloat() / intervals.size)
        return CadenceEstimate(
            cadenceSpm = cadence,
            periodicityScore = (1f - deviation / (average * 0.45f)).coerceIn(0f, 1f),
            stepCount = completedStepTimestamps.size,
            stepTimestamps = completedStepTimestamps.toList(),
        )
    }

    private fun MotionConfig.cadenceWindowMs(): Long = max(window.cadenceWindowMs, 2_000L)

    private fun classify(state: MotionState, cadenceSpm: Int?, intensity: Float, debug: MotionDebugMetrics): ActivityType = when {
        state == MotionState.NOT_MOVING -> ActivityType.STATIONARY
        state != MotionState.MOVING && debug.poseQuality >= config.pose.minimumPoseQualityForDecision -> ActivityType.STATIONARY
        state != MotionState.MOVING -> ActivityType.UNKNOWN
        debug.synchronousLegScore >= config.classification.jumpingSynchronousThreshold -> ActivityType.UNKNOWN
        cadenceSpm == null -> ActivityType.UNKNOWN
        cadenceSpm >= config.classification.runningCadenceThresholdSpm -> ActivityType.RUNNING
        cadenceSpm >= config.classification.walkingMinimumCadenceSpm -> ActivityType.WALKING
        else -> ActivityType.UNKNOWN
    }

    private fun gaitSpeed(cadenceSpm: Int?): Float = cadenceSpm
        ?.let { ((it - config.cadence.minCadenceSpm).toFloat() / (config.cadence.maxCadenceSpm - config.cadence.minCadenceSpm)) }
        ?.coerceIn(0f, 1f)
        ?: 0f

    private fun trimWindow(timestampMs: Long) {
        val oldestAllowed = timestampMs - max(config.window.analysisWindowMs, config.window.cadenceWindowMs)
        while (frames.isNotEmpty() && frames.first().timestampMs < oldestAllowed) frames.removeFirst()
    }

    private fun smoothCadence(cadenceSpm: Float): Int {
        val next = smoothedCadenceSpm?.let { it * (1f - config.cadence.smoothingAlpha) + cadenceSpm * config.cadence.smoothingAlpha } ?: cadenceSpm
        smoothedCadenceSpm = next
        return next.toInt()
    }

    private fun alternatingScore(left: List<Float>, right: List<Float>): Float = antiCorrelation(left, right)

    private fun bilateralConsistency(left: List<Float>, right: List<Float>): Float {
        if (left.size < 4 || right.size < 4) return 0.25f
        val ratio = min(left.range(), right.range()) / max(max(left.range(), right.range()), 0.001f)
        return ratio.coerceIn(0f, 1f)
    }

    private fun synchronousScore(left: List<Float>, right: List<Float>): Float {
        if (left.size < 4 || right.size < 4) return 0f
        return correlation(left, right).coerceIn(0f, 1f)
    }

    private fun antiCorrelation(left: List<Float>, right: List<Float>): Float = ((-correlation(left, right)) + 1f).coerceIn(0f, 2f) / 2f

    private fun correlation(left: List<Float>, right: List<Float>): Float {
        val count = min(left.size, right.size)
        if (count < config.window.minFramesForDecision) return 0f
        val l = left.takeLast(count)
        val r = right.takeLast(count)
        val lm = l.average().toFloat()
        val rm = r.average().toFloat()
        var n = 0f
        var le = 0f
        var re = 0f
        for (i in 0 until count) {
            val ld = l[i] - lm
            val rd = r[i] - rm
            n += ld * rd
            le += ld * ld
            re += rd * rd
        }
        val d = sqrt(le * re)
        return if (d <= 0.0001f) 0f else n / d
    }

    private fun stableBodyScale(frame: PoseFrame): Float? {
        val hipScale = width(frame.leftHip, frame.rightHip)?.times(3f)
        val legScale = legLength(frame)
        return hipScale?.takeIf { it >= config.pose.minimumNormalizedLegScale }
            ?: legScale?.takeIf { it >= config.pose.minimumNormalizedLegScale }
    }

    private fun legLength(frame: PoseFrame): Float? {
        val left = frame.leftHip?.let { h -> frame.leftKnee?.let { k -> distance(h, k) + (frame.leftAnkle?.let { distance(k, it) } ?: 0f) } }
        val right = frame.rightHip?.let { h -> frame.rightKnee?.let { k -> distance(h, k) + (frame.rightAnkle?.let { distance(k, it) } ?: 0f) } }
        return max(left ?: 0f, right ?: 0f).takeIf { it > 0f }
    }

    private fun width(a: Point2D?, b: Point2D?): Float? = if (a != null && b != null) abs(a.x - b.x) else null
    private fun PoseFrame.lowerBodyJoints() = listOf(leftHip, rightHip, leftKnee, rightKnee, leftAnkle, rightAnkle)
    private fun angleDegrees(a: Point2D?, b: Point2D?, c: Point2D?): Float? {
        if (a == null || b == null || c == null) return null
        val abX = a.x - b.x
        val abY = a.y - b.y
        val cbX = c.x - b.x
        val cbY = c.y - b.y
        val d = sqrt((abX * abX + abY * abY) * (cbX * cbX + cbY * cbY))
        if (d <= 0.0001f) return null
        return Math.toDegrees(acos(((abX * cbX + abY * cbY) / d).coerceIn(-1f, 1f)).toDouble()).toFloat()
    }
    private fun distance(a: Point2D, b: Point2D): Float = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
    private fun List<Float>.range(): Float = if (isEmpty()) 0f else (maxOrNull() ?: 0f) - (minOrNull() ?: 0f)
    private fun List<Float>.averageFloat(): Float = if (isEmpty()) 0f else average().toFloat()
    private fun List<Float>.smooth3(): List<Float> = indices.map { i -> subList(max(0, i - 1), min(size, i + 2)).average().toFloat() }
    private fun List<Float>.median(): Float = sorted().let { if (it.isEmpty()) 0f else it[it.size / 2] }

    private fun emptyResult() = MotionResult(
        state = MotionState.UNCERTAIN,
        confidence = 0f,
        activityType = ActivityType.UNKNOWN,
        cadenceSpm = null,
        gaitSpeed = 0f,
        movementIntensity = 0f,
        poseQuality = 0f,
        poseDetected = false,
        debugMetrics = MotionDebugMetrics(),
        stepEvents = emptyList(),
    )

    private data class NormalizedFrame(
        val timestampMs: Long,
        val leftHip: Point2D,
        val rightHip: Point2D,
        val leftKnee: Point2D?,
        val rightKnee: Point2D?,
        val leftAnkle: Point2D?,
        val rightAnkle: Point2D?,
        val hipWidth: Float,
        val hipCenterY: Float,
        val bodyScale: Float,
        val cameraView: CameraView,
        val leftKneeAngleDegrees: Float?,
        val rightKneeAngleDegrees: Float?,
    )

    private data class WindowAnalysis(
        val gaitScore: Float,
        val cadenceSpm: Int?,
        val movementIntensity: Float,
        val debugMetrics: MotionDebugMetrics,
        val visibility: PoseVisibility,
        val effectiveMovingThreshold: Float,
    )

    private data class CadenceEstimate(
        val cadenceSpm: Float,
        val periodicityScore: Float,
        val stepCount: Int,
        val stepTimestamps: List<Long>,
    )
    private data class Weights(
        val alternation: Float,
        val ankle: Float,
        val kneeCycle: Float,
        val periodicity: Float,
        val kneeAmplitude: Float,
        val bilateral: Float,
    ) {
        val total: Float = alternation + ankle + kneeCycle + periodicity + kneeAmplitude + bilateral
    }
}
