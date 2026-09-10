package io.motionguard.core

import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceMotionTest {
    @Test
    fun identicalPoseMatchesStrongly() {
        val sequence = sequenceOf(pose(0), pose(1000)).also { it.add(pose(2000)) }
        val matcher = MotionSequenceMatcher(sequence.build("test", 2000, 1f, 1f))
        assertTrue(matcher.match(pose(0)).overallScore > .8f)
    }

    @Test
    fun translationAndScaleAreNormalized() {
        val reference = pose(0)
        val shifted = pose(0, 0.3f, 0.2f, 1.6f)
        val a = ReferencePoseNormalizer.normalize(reference)!!
        val b = ReferencePoseNormalizer.normalize(shifted)!!
        val common = a.joints.keys.intersect(b.joints.keys)
        assertTrue(common.all { kotlin.math.abs(a.joints[it]!!.x - b.joints[it]!!.x) < .01f })
    }

    @Test
    fun mirrorModeSwapsSides() {
        val normal = ReferencePoseNormalizer.normalize(pose(0))!!
        val mirrored = ReferencePoseNormalizer.normalize(pose(0), MirrorMode.MIRRORED)!!
        assertTrue(mirrored.joints[BodyJoint.LEFT_KNEE]!!.x == -normal.joints[BodyJoint.RIGHT_KNEE]!!.x)
    }

    @Test
    fun missingPoseIsInvalid() {
        val sequence = sequenceOf(pose(0), pose(1000)).also { it.add(pose(2000)) }.build("test", 2000, 1f, 1f)
        val result = MotionSequenceMatcher(sequence).match(PoseFrame(0, leftHip = null, rightHip = null, leftKnee = null, rightKnee = null, leftAnkle = null, rightAnkle = null))
        assertTrue(!result.validPose)
    }

    private fun sequenceOf(vararg frames: PoseFrame) = MotionSequenceBuilder(minimumChange = 0f).let { builder ->
        object {
            fun add(frame: PoseFrame) = builder.add(frame)
            fun build(id: String, duration: Long, rate: Float, quality: Float) = builder.build(id, duration, rate, quality)
        }
    }.also { holder -> frames.forEach(holder::add) }

    private fun pose(t: Long, dx: Float = 0f, dy: Float = 0f, scale: Float = 1f) = PoseFrame(
        timestampMs = t,
        leftShoulder = p(.38f, .20f, scale, dx, dy), rightShoulder = p(.62f, .20f, scale, dx, dy),
        leftHip = p(.44f, .50f, scale, dx, dy), rightHip = p(.56f, .50f, scale, dx, dy),
        leftKnee = p(.42f, .72f, scale, dx, dy), rightKnee = p(.58f, .72f, scale, dx, dy),
        leftAnkle = p(.40f, .94f, scale, dx, dy), rightAnkle = p(.60f, .94f, scale, dx, dy),
    )

    private fun p(x: Float, y: Float, scale: Float, dx: Float, dy: Float) = Point2D(dx + x * scale, dy + y * scale, 1f)
}
