package io.motionguard.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import java.util.EnumMap

/** Major body joints used by reference-motion matching. */
public enum class BodyJoint {
    LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_ELBOW, RIGHT_ELBOW, LEFT_WRIST, RIGHT_WRIST,
    LEFT_HIP, RIGHT_HIP, LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE,
}

public enum class MirrorMode { NORMAL, MIRRORED }
public enum class TimingState { EARLY, ON_TIME, LATE, UNKNOWN }

public data class NormalizedPose(
    val timestampMs: Long,
    val joints: Map<BodyJoint, Point2D>,
    val quality: Float,
)

public enum class JointAngle { LEFT_ELBOW, RIGHT_ELBOW, LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_HIP, RIGHT_HIP, LEFT_KNEE, RIGHT_KNEE }

public data class MotionFeatures(
    val averageSpeed: Float = 0f,
    val directionX: Float = 0f,
    val directionY: Float = 0f,
)

public data class MotionKeyframe(
    val timestampMs: Long,
    val pose: NormalizedPose,
    val jointAngles: Map<JointAngle, Float>,
    val motionFeatures: MotionFeatures = MotionFeatures(),
    val keyframe: Boolean = false,
)

public data class MotionSequenceMetadata(
    val sourceQuality: Float,
    val processedSamples: Int,
    val keyframes: Int,
)

public data class MotionSequence(
    val id: String,
    val durationMs: Long,
    val sampleRateHz: Float,
    val frames: List<MotionKeyframe>,
    val metadata: MotionSequenceMetadata,
)

public data class MotionMatchConfig(
    val mirrorMode: MirrorMode = MirrorMode.NORMAL,
    val searchWindowSamples: Int = 8,
    val minimumPoseQuality: Float = .35f,
    val onTimeToleranceMs: Long = 250L,
    val poseWeight: Float = .50f,
    val angleWeight: Float = .30f,
    val motionWeight: Float = .15f,
    val timingWeight: Float = .05f,
)

public data class MotionMatchResult(
    val referenceTimeMs: Long,
    val progress: Float,
    val overallScore: Float,
    val poseScore: Float,
    val angleScore: Float,
    val motionScore: Float,
    val timingScore: Float,
    val timingOffsetMs: Long,
    val timingState: TimingState,
    val poseQuality: Float,
    val validPose: Boolean,
)

/** Applies body-relative normalization to full-body pose frames. */
public object ReferencePoseNormalizer {
    public fun normalize(frame: PoseFrame, mirrorMode: MirrorMode = MirrorMode.NORMAL): NormalizedPose? {
        val hips = listOfNotNull(frame.leftHip, frame.rightHip)
        if (hips.isEmpty()) return null
        val hipCenter = midpoint(hips.first(), hips.getOrNull(1) ?: hips.first())
        val shoulders = listOfNotNull(frame.leftShoulder, frame.rightShoulder)
        val shoulderCenter = shoulders.takeIf { it.isNotEmpty() }?.let { midpoint(it.first(), it.getOrNull(1) ?: it.first()) }
        val scale = max(.05f, if (shoulderCenter != null) distance(hipCenter, shoulderCenter) else estimateLegScale(frame, hipCenter))
        val source = frame.joints().mapNotNull { (joint, point) ->
            if (point.confidence <= 0f) return@mapNotNull null
            val mappedJoint = if (mirrorMode == MirrorMode.MIRRORED) joint.mirror() else joint
            val x = if (mirrorMode == MirrorMode.MIRRORED) -(point.x - hipCenter.x) else point.x - hipCenter.x
            mappedJoint to Point2D(x / scale, (point.y - hipCenter.y) / scale, point.confidence)
        }.toMap()
        val quality = source.values.map { it.confidence }.average().toFloat().coerceIn(0f, 1f)
        return NormalizedPose(frame.timestampMs, source, quality)
    }

    private fun estimateLegScale(frame: PoseFrame, center: Point2D): Float = listOfNotNull(frame.leftKnee, frame.rightKnee, frame.leftAnkle, frame.rightAnkle)
        .map { distance(center, it) }.average().toFloat().coerceAtLeast(.05f)

    private fun midpoint(a: Point2D, b: Point2D) = Point2D((a.x + b.x) / 2f, (a.y + b.y) / 2f, min(a.confidence, b.confidence))
    private fun distance(a: Point2D, b: Point2D) = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    private fun BodyJoint.mirror(): BodyJoint = when (this) {
        BodyJoint.LEFT_SHOULDER -> BodyJoint.RIGHT_SHOULDER; BodyJoint.RIGHT_SHOULDER -> BodyJoint.LEFT_SHOULDER
        BodyJoint.LEFT_ELBOW -> BodyJoint.RIGHT_ELBOW; BodyJoint.RIGHT_ELBOW -> BodyJoint.LEFT_ELBOW
        BodyJoint.LEFT_WRIST -> BodyJoint.RIGHT_WRIST; BodyJoint.RIGHT_WRIST -> BodyJoint.LEFT_WRIST
        BodyJoint.LEFT_HIP -> BodyJoint.RIGHT_HIP; BodyJoint.RIGHT_HIP -> BodyJoint.LEFT_HIP
        BodyJoint.LEFT_KNEE -> BodyJoint.RIGHT_KNEE; BodyJoint.RIGHT_KNEE -> BodyJoint.LEFT_KNEE
        BodyJoint.LEFT_ANKLE -> BodyJoint.RIGHT_ANKLE; BodyJoint.RIGHT_ANKLE -> BodyJoint.LEFT_ANKLE
    }
}

private fun PoseFrame.joints(): Map<BodyJoint, Point2D> = mapOf(
    BodyJoint.LEFT_SHOULDER to leftShoulder, BodyJoint.RIGHT_SHOULDER to rightShoulder,
    BodyJoint.LEFT_ELBOW to leftElbow, BodyJoint.RIGHT_ELBOW to rightElbow,
    BodyJoint.LEFT_WRIST to leftWrist, BodyJoint.RIGHT_WRIST to rightWrist,
    BodyJoint.LEFT_HIP to leftHip, BodyJoint.RIGHT_HIP to rightHip,
    BodyJoint.LEFT_KNEE to leftKnee, BodyJoint.RIGHT_KNEE to rightKnee,
    BodyJoint.LEFT_ANKLE to leftAnkle, BodyJoint.RIGHT_ANKLE to rightAnkle,
).filterValues { it != null }.mapValues { it.value!! }

/** Adaptive low-latency smoothing for live and reference pose streams. */
public class ReferencePoseEma(
    private val alpha: Float = .35f,
    private val referenceFrameIntervalMs: Float = 33.333f,
    private val resetAfterMissingMs: Long = 2_500L,
    private val beta: Float = .045f,
    /** Minimum cutoff multiplier for a low-confidence landmark. Lower means more smoothing. */
    private val lowConfidenceCutoffScale: Float = .35f,
) {
    private val filters = EnumMap<BodyJoint, OneEuroPointFilter>(BodyJoint::class.java)
    private var previousTimestampMs: Long? = null
    public fun reset() { filters.clear(); previousTimestampMs = null }
    public fun filter(frame: PoseFrame): PoseFrame {
        val previousTimestamp = previousTimestampMs
        if (previousTimestamp != null && (frame.timestampMs - previousTimestamp) > resetAfterMissingMs) reset()
        val values = frame.joints()
        fun s(joint: BodyJoint, p: Point2D?): Point2D? = p?.takeIf { it.confidence > 0f }?.let {
            val filter = filters.getOrPut(joint) { OneEuroPointFilter(alpha, referenceFrameIntervalMs, beta = beta) }
            filter.filter(it, frame.timestampMs, lowConfidenceCutoffScale)
        }
        previousTimestampMs = frame.timestampMs
        return frame.copy(
            leftShoulder = s(BodyJoint.LEFT_SHOULDER, values[BodyJoint.LEFT_SHOULDER]), rightShoulder = s(BodyJoint.RIGHT_SHOULDER, values[BodyJoint.RIGHT_SHOULDER]),
            leftElbow = s(BodyJoint.LEFT_ELBOW, values[BodyJoint.LEFT_ELBOW]), rightElbow = s(BodyJoint.RIGHT_ELBOW, values[BodyJoint.RIGHT_ELBOW]),
            leftWrist = s(BodyJoint.LEFT_WRIST, values[BodyJoint.LEFT_WRIST]), rightWrist = s(BodyJoint.RIGHT_WRIST, values[BodyJoint.RIGHT_WRIST]),
            leftHip = s(BodyJoint.LEFT_HIP, values[BodyJoint.LEFT_HIP]), rightHip = s(BodyJoint.RIGHT_HIP, values[BodyJoint.RIGHT_HIP]),
            leftKnee = s(BodyJoint.LEFT_KNEE, values[BodyJoint.LEFT_KNEE]), rightKnee = s(BodyJoint.RIGHT_KNEE, values[BodyJoint.RIGHT_KNEE]),
            leftAnkle = s(BodyJoint.LEFT_ANKLE, values[BodyJoint.LEFT_ANKLE]), rightAnkle = s(BodyJoint.RIGHT_ANKLE, values[BodyJoint.RIGHT_ANKLE]),
        )
    }
}

private fun Point2D.angle(b: Point2D, c: Point2D): Float {
    val abx = x - b.x; val aby = y - b.y; val cbx = c.x - b.x; val cby = c.y - b.y
    return Math.toDegrees(atan2((abx * cby - aby * cbx).toDouble(), (abx * cbx + aby * cby).toDouble())).toFloat().let { abs(it) }
}

public fun calculateJointAngles(pose: NormalizedPose): Map<JointAngle, Float> = buildMap {
    fun add(angle: JointAngle, a: BodyJoint, b: BodyJoint, c: BodyJoint) { val p = pose.joints; if (p[a] != null && p[b] != null && p[c] != null) put(angle, p[a]!!.angle(p[b]!!, p[c]!!)) }
    add(JointAngle.LEFT_KNEE, BodyJoint.LEFT_HIP, BodyJoint.LEFT_KNEE, BodyJoint.LEFT_ANKLE); add(JointAngle.RIGHT_KNEE, BodyJoint.RIGHT_HIP, BodyJoint.RIGHT_KNEE, BodyJoint.RIGHT_ANKLE)
    add(JointAngle.LEFT_ELBOW, BodyJoint.LEFT_SHOULDER, BodyJoint.LEFT_ELBOW, BodyJoint.LEFT_WRIST); add(JointAngle.RIGHT_ELBOW, BodyJoint.RIGHT_SHOULDER, BodyJoint.RIGHT_ELBOW, BodyJoint.RIGHT_WRIST)
    add(JointAngle.LEFT_SHOULDER, BodyJoint.LEFT_ELBOW, BodyJoint.LEFT_SHOULDER, BodyJoint.LEFT_HIP); add(JointAngle.RIGHT_SHOULDER, BodyJoint.RIGHT_ELBOW, BodyJoint.RIGHT_SHOULDER, BodyJoint.RIGHT_HIP)
    add(JointAngle.LEFT_HIP, BodyJoint.LEFT_SHOULDER, BodyJoint.LEFT_HIP, BodyJoint.LEFT_KNEE); add(JointAngle.RIGHT_HIP, BodyJoint.RIGHT_SHOULDER, BodyJoint.RIGHT_HIP, BodyJoint.RIGHT_KNEE)
}

/** Builds compact numerical keyframes; source images are never retained. */
public class MotionSequenceBuilder(
    private val emaAlpha: Float = .35f,
    private val minimumChange: Float = .015f,
    private val maxFrames: Int = 4_000,
    private val minimumSampleIntervalMs: Long = 250L,
) {
    private val ema = ReferencePoseEma(emaAlpha)
    private val frames = ArrayList<MotionKeyframe>(min(maxFrames, 512))
    private var previous: NormalizedPose? = null
    private var lastStoredTimestamp = Long.MIN_VALUE
    public fun add(frame: PoseFrame): Boolean {
        if (frames.size >= maxFrames) return false
        val pose = ReferencePoseNormalizer.normalize(ema.filter(frame)) ?: return false
        val motion = previous?.let { motionFeatures(it, pose) } ?: MotionFeatures()
        val changed = previous == null || motion.averageSpeed >= minimumChange ||
            lastStoredTimestamp == Long.MIN_VALUE || pose.timestampMs - lastStoredTimestamp >= minimumSampleIntervalMs
        if (changed) frames += MotionKeyframe(pose.timestampMs, pose, calculateJointAngles(pose), motion, keyframe = motion.averageSpeed >= minimumChange * 2f)
        if (changed) lastStoredTimestamp = pose.timestampMs
        previous = pose
        return changed
    }
    public fun build(id: String, durationMs: Long, sampleRateHz: Float, sourceQuality: Float): MotionSequence = MotionSequence(id, durationMs, sampleRateHz, frames.toList(), MotionSequenceMetadata(sourceQuality, frames.size, frames.count { it.keyframe }))
    private fun motionFeatures(a: NormalizedPose, b: NormalizedPose): MotionFeatures {
        val deltas = a.joints.mapNotNull { (j, p) -> b.joints[j]?.let { Point2D(it.x - p.x, it.y - p.y, min(p.confidence, it.confidence)) } }
        if (deltas.isEmpty()) return MotionFeatures()
        val speed = deltas.map { hypot(it.x.toDouble(), it.y.toDouble()).toFloat() }.average().toFloat()
        return MotionFeatures(speed, deltas.map { it.x }.average().toFloat(), deltas.map { it.y }.average().toFloat())
    }
}

private fun motionFeatures(a: NormalizedPose, b: NormalizedPose): MotionFeatures = MotionSequenceBuilder().run { val d = a.joints.mapNotNull { (j,p) -> b.joints[j]?.let { hypot((it.x-p.x).toDouble(), (it.y-p.y).toDouble()).toFloat() } }; MotionFeatures(d.average().toFloat()) }

/** Lightweight monotonic local alignment matcher for live normalized poses. */
public class MotionSequenceMatcher(private val sequence: MotionSequence, private val config: MotionMatchConfig = MotionMatchConfig()) {
    private val ema = ReferencePoseEma(.35f)
    private var liveStart = Long.MIN_VALUE
    private var index = 0
    private var previous: NormalizedPose? = null
    public fun reset() { ema.reset(); liveStart = Long.MIN_VALUE; index = 0; previous = null }
    public fun match(frame: PoseFrame): MotionMatchResult {
        val pose = ReferencePoseNormalizer.normalize(ema.filter(frame), config.mirrorMode)
        if (pose == null || pose.quality < config.minimumPoseQuality || sequence.frames.isEmpty()) return MotionMatchResult(0, 0f, 0f, 0f, 0f, 0f, 0f, 0, TimingState.UNKNOWN, pose?.quality ?: 0f, false)
        if (liveStart == Long.MIN_VALUE) liveStart = pose.timestampMs
        val expected = ((pose.timestampMs - liveStart).toDouble() / 1000.0 * sequence.sampleRateHz).toInt().coerceIn(0, sequence.frames.lastIndex)
        val center = max(index, expected).coerceIn(0, sequence.frames.lastIndex)
        val start = max(0, center - config.searchWindowSamples); val end = min(sequence.frames.lastIndex, center + config.searchWindowSamples)
        var best: MotionMatchResult? = null
        for (i in start..end) { val candidate = score(pose, sequence.frames[i], pose.timestampMs - liveStart); if (best == null || candidate.overallScore > best!!.overallScore) best = candidate }
        index = best?.let { sequence.frames.indexOfFirst { f -> f.timestampMs == it.referenceTimeMs }.coerceAtLeast(index) } ?: index
        previous = pose
        return best!!
    }
    private fun score(live: NormalizedPose, ref: MotionKeyframe, elapsed: Long): MotionMatchResult {
        val common = live.joints.keys.intersect(ref.pose.joints.keys)
        val poseScore = if (common.isEmpty()) 0f else common.map { val a = live.joints[it]!!; val b = ref.pose.joints[it]!!; (1f - (hypot((a.x-b.x).toDouble(), (a.y-b.y).toDouble()).toFloat() / 2f)).coerceIn(0f,1f) * min(a.confidence,b.confidence) }.average().toFloat().coerceIn(0f,1f)
        val liveAngles = calculateJointAngles(live); val angleKeys = liveAngles.keys.intersect(ref.jointAngles.keys)
        val angleScore = if (angleKeys.isEmpty()) .5f else angleKeys.map { (1f - abs(liveAngles[it]!! - ref.jointAngles[it]!!) / 180f).coerceIn(0f,1f) }.average().toFloat().coerceIn(0f,1f)
        val motionScore = previous?.let { val speed = motionFeatures(it, live).averageSpeed; (1f - abs(speed-ref.motionFeatures.averageSpeed)).coerceIn(0f,1f) } ?: .5f
        val offset = elapsed - ref.timestampMs; val timingScore = (1f - abs(offset).toFloat() / (sequence.durationMs.coerceAtLeast(1L))).coerceIn(0f,1f)
        val total = (poseScore*config.poseWeight + angleScore*config.angleWeight + motionScore*config.motionWeight + timingScore*config.timingWeight).coerceIn(0f,1f)
        val timing = when { abs(offset) <= config.onTimeToleranceMs -> TimingState.ON_TIME; offset < 0 -> TimingState.EARLY; else -> TimingState.LATE }
        return MotionMatchResult(ref.timestampMs, (ref.timestampMs.toFloat()/sequence.durationMs.coerceAtLeast(1L)).coerceIn(0f,1f), total, poseScore, angleScore, motionScore, timingScore, offset, timing, live.quality, true)
    }
}
