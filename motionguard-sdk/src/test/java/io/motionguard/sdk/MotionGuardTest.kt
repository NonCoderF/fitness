package motionguardsdk

import io.motionguard.core.ActivityType
import io.motionguard.core.DeviceMotionProvider
import io.motionguard.core.MotionState
import io.motionguard.core.Point2D
import io.motionguard.core.PoseFrame
import io.motionguard.core.TrackingStatus
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionGuardTest {
    @Test
    fun defaultConfigBuildsWithoutAndroidContextWhenSensorsDisabled() {
        val motionGuard = MotionGuard.Builder()
            .config(MotionGuardConfig.default())
            .build()

        assertEquals(MotionState.UNCERTAIN, motionGuard.results.value.motionState)
        assertNull(motionGuard.results.value.cadenceSpm)

        motionGuard.release()
    }

    @Test
    fun processPublishesResultToFlowAndListener() {
        val motionGuard = MotionGuard.Builder()
            .enableDebugMetrics(true)
            .build()
        var callbackResult: MotionGuardResult? = null
        motionGuard.setListener(MotionGuardListener { callbackResult = it })

        val result = motionGuard.process(standingFrame(0L))

        assertEquals(result, motionGuard.results.value)
        assertEquals(result, callbackResult)
        assertTrue(result.debug != null)
    }

    @Test
    fun resetClearsToUnknownResult() {
        val motionGuard = MotionGuard.Builder().build()
        motionGuard.process(standingFrame(0L))

        motionGuard.reset()

        assertEquals(MotionState.UNCERTAIN, motionGuard.results.value.motionState)
        assertEquals(ActivityType.UNKNOWN, motionGuard.results.value.activity)
        assertEquals(TrackingStatus.TRACKING_LOST, motionGuard.results.value.trackingQuality)
    }

    @Test(expected = MotionGuardException::class)
    fun processAfterReleaseThrowsPredictableException() {
        val motionGuard = MotionGuard.Builder().build()
        motionGuard.release()

        motionGuard.process(standingFrame(0L))
    }

    @Test
    fun sensorFusionCanUseInjectedProviderWithoutContext() {
        val provider = DeviceMotionProvider { emptyFlow() }
        val motionGuard = MotionGuard.Builder()
            .enableSensorFusion(true)
            .deviceMotionProvider(provider)
            .build()

        motionGuard.start()
        motionGuard.stop()

        assertEquals(MotionState.UNCERTAIN, motionGuard.results.value.motionState)
    }

    @Test
    fun calibrationFlowsThroughPublicResult() {
        val motionGuard = MotionGuard.Builder()
            .enableDebugMetrics(true)
            .build()

        var result = motionGuard.results.value
        for (time in 0L..2_500L step 100L) {
            result = motionGuard.process(standingFrame(time))
        }

        assertTrue(result.calibration != null)
        assertTrue(result.debug?.calibration != null)
    }

    private fun standingFrame(timestampMs: Long): PoseFrame = PoseFrame(
        timestampMs = timestampMs,
        leftShoulder = point(0.38f, 0.25f),
        rightShoulder = point(0.62f, 0.25f),
        leftHip = point(0.45f, 0.42f),
        rightHip = point(0.57f, 0.42f),
        leftKnee = point(0.44f, 0.62f),
        rightKnee = point(0.56f, 0.62f),
        leftAnkle = point(0.43f, 0.84f),
        rightAnkle = point(0.57f, 0.84f),
    )

    private fun point(x: Float, y: Float): Point2D = Point2D(x, y, 0.92f)
}
