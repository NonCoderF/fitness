package com.sparkstudios.myapplication.coach

import android.speech.tts.TextToSpeech
import io.motionguard.core.ActivityType
import io.motionguard.core.MotionState
import io.motionguard.core.StepEvent
import motionguardsdk.MotionGuardResult
import java.util.Locale

data class VoiceCoachConfig(
    val enabled: Boolean = true,
    val countSteps: Boolean = true,
    val encouragementEnabled: Boolean = true,
    val milestoneInterval: Int = 10,
    val adaptiveCounting: Boolean = true,
)

enum class SpeechPriority { LOW, MEDIUM, HIGH }

interface SpeechEngine {
    val isBusy: Boolean
    fun speak(text: String, priority: SpeechPriority)
    fun stop()
    fun release()
}

/** Downstream coaching policy. It consumes confirmed MotionGuard events and never creates steps. */
class VoiceCoach(
    private val speech: SpeechEngine,
    initialConfig: VoiceCoachConfig = VoiceCoachConfig(),
) {
    var config: VoiceCoachConfig = initialConfig
        private set

    var steps: Int = 0
        private set

    private var lastMotionState = MotionState.UNCERTAIN
    private var lastActivity = ActivityType.UNKNOWN
    private var lastCadence: Int? = null
    private var lastAnnouncementMs = Long.MIN_VALUE
    private var lowCadenceSinceMs: Long? = null
    private var lastMilestone = 0
    private var released = false
    private var encouragementIndex = 0

    fun updateConfig(newConfig: VoiceCoachConfig) {
        check(!released) { "VoiceCoach has been released." }
        config = newConfig.copy(milestoneInterval = newConfig.milestoneInterval.coerceAtLeast(1))
    }

    fun onMotionResult(result: MotionGuardResult) {
        if (released) return
        val wasMoving = lastMotionState == MotionState.MOVING
        val isMoving = result.motionState == MotionState.MOVING
        val now = System.currentTimeMillis()

        if (config.enabled && config.encouragementEnabled) {
            when {
                !wasMoving && isMoving -> announce("Let's go.", SpeechPriority.HIGH, now)
                wasMoving && result.motionState == MotionState.NOT_MOVING ->
                    announce("Keep moving when you're ready.", SpeechPriority.MEDIUM, now)
                wasMoving.not() && isMoving && lastMotionState == MotionState.NOT_MOVING ->
                    announce("That's it. Keep going.", SpeechPriority.MEDIUM, now)
            }

            val cadence = result.cadenceSpm
            if (isMoving && cadence != null && lastCadence != null && cadence <= lastCadence!! - 20) {
                lowCadenceSinceMs = lowCadenceSinceMs ?: now
                if (now - (lowCadenceSinceMs ?: now) >= 3_000L) {
                    announce("Keep your pace.", SpeechPriority.MEDIUM, now)
                    lowCadenceSinceMs = now
                }
            } else if (cadence != null && (lastCadence == null || cadence > lastCadence!! - 10)) {
                lowCadenceSinceMs = null
            }
            lastCadence = cadence
        }
        lastMotionState = result.motionState
        lastActivity = result.activity
    }

    /** Returns the new session total. The caller must pass only MotionGuard-confirmed events. */
    fun onStep(event: StepEvent): Int {
        if (released) return steps
        steps += 1
        val now = event.timestampMs
        val interval = config.milestoneInterval
        if (config.enabled && config.encouragementEnabled && steps >= interval && steps / interval > lastMilestone) {
            lastMilestone = steps / interval
            val phrase = encouragementIndex++ % ENCOURAGEMENTS.size
            announce("${numberWord(steps)}! ${ENCOURAGEMENTS[phrase]}", SpeechPriority.HIGH, now)
        } else if (config.enabled && config.countSteps && shouldAnnounceStep()) {
            announce(numberWord(steps), SpeechPriority.LOW, now)
        }
        return steps
    }

    fun reset() {
        check(!released) { "VoiceCoach has been released." }
        steps = 0
        lastMotionState = MotionState.UNCERTAIN
        lastActivity = ActivityType.UNKNOWN
        lastCadence = null
        lowCadenceSinceMs = null
        lastMilestone = 0
        lastAnnouncementMs = Long.MIN_VALUE
        speech.stop()
    }

    fun start() = Unit

    fun stop() {
        if (!released) speech.stop()
    }

    fun release() {
        if (released) return
        released = true
        speech.release()
    }

    private fun shouldAnnounceStep(): Boolean {
        if (!config.adaptiveCounting) return true
        val cadence = lastCadence ?: return true
        return when {
            lastActivity == ActivityType.RUNNING || cadence >= 145 -> steps % 10 == 0
            cadence >= 105 -> steps % 5 == 0
            cadence >= 90 -> steps % 2 == 0
            else -> true
        }
    }

    private fun announce(text: String, priority: SpeechPriority, timestampMs: Long) {
        if (!config.enabled || released) return
        if (priority == SpeechPriority.MEDIUM && timestampMs - lastAnnouncementMs < 1_200L) return
        if (priority == SpeechPriority.LOW && speech.isBusy) return
        if (priority == SpeechPriority.HIGH) speech.stop()
        speech.speak(text, priority)
        lastAnnouncementMs = timestampMs
    }

    private fun numberWord(value: Int): String = when (value) {
        1 -> "One"; 2 -> "Two"; 3 -> "Three"; 4 -> "Four"; 5 -> "Five"
        6 -> "Six"; 7 -> "Seven"; 8 -> "Eight"; 9 -> "Nine"; 10 -> "Ten"
        11 -> "Eleven"; 12 -> "Twelve"; 13 -> "Thirteen"; 14 -> "Fourteen"; 15 -> "Fifteen"
        16 -> "Sixteen"; 17 -> "Seventeen"; 18 -> "Eighteen"; 19 -> "Nineteen"; 20 -> "Twenty"
        else -> value.toString()
    }

    private companion object {
        val ENCOURAGEMENTS = listOf(
            "Come on, you can do more!",
            "Great pace, keep going!",
            "Nice work, stay with it!",
            "You're doing great. Keep moving!",
        )
    }
}

/** Speaks the visible workout counter without coupling speech to pose detection. */
class WorkoutCounterSpeechCoach(
    private val speech: SpeechEngine,
) {
    private var lastCount = 0
    private var released = false

    fun onCounterChanged(count: Int, isStepCounter: Boolean) {
        if (released || count <= lastCount) return
        lastCount = count

        // Steps are intentionally announced less often so treadmill speech remains usable.
        if (isStepCounter && count % 10 != 0) return

        val countText = numberWord(count)
        if (count % 10 == 0) {
            speech.stop()
            speech.speak(
                "$countText. Come on, you are doing great. Ten more.",
                SpeechPriority.HIGH,
            )
        } else {
            speech.speak(countText, SpeechPriority.LOW)
        }
    }

    fun speakStartCue(text: String) {
        if (!released) speech.speak(text, SpeechPriority.HIGH)
    }

    fun reset() {
        if (!released) {
            lastCount = 0
            speech.stop()
        }
    }

    fun release() {
        if (released) return
        released = true
        speech.release()
    }

    private fun numberWord(value: Int): String {
        val small = listOf(
            "Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen",
            "Eighteen", "Nineteen",
        )
        if (value in small.indices) return small[value]
        if (value < 100) {
            val tens = listOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")
            return tens[value / 10] + if (value % 10 == 0) "" else " ${small[value % 10]}"
        }
        return value.toString()
    }
}

class AndroidSpeechEngine(context: android.content.Context) : SpeechEngine, TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pendingSpeech: Pair<String, SpeechPriority>? = null

    override val isBusy: Boolean get() = ready && tts.isSpeaking

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val languageStatus = tts.setLanguage(Locale.US)
        ready = languageStatus != TextToSpeech.LANG_MISSING_DATA && languageStatus != TextToSpeech.LANG_NOT_SUPPORTED
        pendingSpeech?.let { (text, priority) ->
            pendingSpeech = null
            speak(text, priority)
        }
    }

    override fun speak(text: String, priority: SpeechPriority) {
        if (!ready) {
            pendingSpeech = text to priority
            return
        }
        val queue = if (priority == SpeechPriority.HIGH) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts.speak(text, queue, null, "motionguard-${System.nanoTime()}")
    }

    override fun stop() {
        if (ready) tts.stop()
    }

    override fun release() {
        ready = false
        pendingSpeech = null
        tts.stop()
        tts.shutdown()
    }
}
