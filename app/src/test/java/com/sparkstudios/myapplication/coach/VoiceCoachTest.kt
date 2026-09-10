package com.sparkstudios.myapplication.coach

import io.motionguard.core.ActivityType
import io.motionguard.core.MotionState
import io.motionguard.core.StepEvent
import io.motionguard.core.TrackingStatus
import motionguardsdk.MotionGuardResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCoachTest {
    @Test
    fun slowWalkingAnnouncesEachConfirmedStep() {
        val speech = FakeSpeechEngine()
        val coach = VoiceCoach(speech)

        for (step in 1..9) coach.onStep(StepEvent(step * 1_000L, confidence = 0.9f))

        assertEquals((1..9).map { number(it) }, speech.spoken)
    }

    @Test
    fun milestoneReplacesNumberWithEncouragement() {
        val speech = FakeSpeechEngine()
        val coach = VoiceCoach(speech)

        for (step in 1..10) coach.onStep(StepEvent(step * 1_000L, confidence = 0.9f))

        assertEquals("Ten! Come on, you can do more!", speech.spoken.last())
        assertEquals(10, coach.steps)
    }

    @Test
    fun runningSkipsIndividualNumbersButStillAnnouncesMilestone() {
        val speech = FakeSpeechEngine()
        val coach = VoiceCoach(speech)
        coach.onMotionResult(result(MotionState.MOVING, ActivityType.RUNNING, cadence = 165))

        for (step in 1..20) coach.onStep(StepEvent(step * 100L, confidence = 0.9f))

        assertTrue(speech.spoken.none { it == "One" || it == "Two" || it == "Three" })
        assertTrue(speech.spoken.any { it.startsWith("Ten!") })
        assertTrue(speech.spoken.any { it.startsWith("Twenty!") })
    }

    @Test
    fun busySpeechDropsLowPriorityNumbers() {
        val speech = FakeSpeechEngine(isBusy = true)
        val coach = VoiceCoach(speech)

        for (step in 1..9) coach.onStep(StepEvent(step.toLong(), confidence = 0.9f))

        assertTrue(speech.spoken.isEmpty())
    }

    @Test
    fun disabledVoiceDoesNotSpeakButStillCountsConfirmedEvents() {
        val speech = FakeSpeechEngine()
        val coach = VoiceCoach(speech, VoiceCoachConfig(enabled = false))

        coach.onStep(StepEvent(1L, confidence = 0.9f))

        assertEquals(1, coach.steps)
        assertTrue(speech.spoken.isEmpty())
    }

    @Test
    fun resetClearsSessionAndStopsSpeech() {
        val speech = FakeSpeechEngine()
        val coach = VoiceCoach(speech)
        coach.onStep(StepEvent(1L, confidence = 0.9f))

        coach.reset()

        assertEquals(0, coach.steps)
        assertTrue(speech.stopCount > 0)
    }

    @Test
    fun releasePreventsFurtherSpeech() {
        val speech = FakeSpeechEngine()
        val coach = VoiceCoach(speech)
        coach.release()

        coach.onStep(StepEvent(1L, confidence = 0.9f))

        assertTrue(speech.spoken.isEmpty())
        assertTrue(speech.releaseCount > 0)
    }

    private fun result(state: MotionState, activity: ActivityType, cadence: Int) = MotionGuardResult(
        motionState = state,
        activity = activity,
        confidence = 0.9f,
        cadenceSpm = cadence,
        movementIntensity = 0.8f,
        poseQuality = 0.9f,
        trackingQuality = TrackingStatus.TRACKING_GOOD,
        cameraView = io.motionguard.core.CameraView.FRONT,
        calibration = null,
        debug = null,
    )

    private fun number(value: Int) = when (value) {
        1 -> "One"; 2 -> "Two"; 3 -> "Three"; 4 -> "Four"; 5 -> "Five"
        6 -> "Six"; 7 -> "Seven"; 8 -> "Eight"; else -> "Nine"
    }

    private class FakeSpeechEngine(
        override var isBusy: Boolean = false,
    ) : SpeechEngine {
        val spoken = mutableListOf<String>()
        var stopCount = 0
        var releaseCount = 0

        override fun speak(text: String, priority: SpeechPriority) {
            spoken += text
        }

        override fun stop() {
            stopCount++
        }

        override fun release() {
            releaseCount++
        }
    }
}
