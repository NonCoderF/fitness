package io.motionguard.exercise

public interface RepStateMachine {
    public val repCount: Int
    public val stateLabel: String
    public fun process(phase: ExercisePhase): Int
    public fun reset()
}

public abstract class TwoPhaseRepStateMachine(
    private val upPhase: ExercisePhase,
    private val downPhase: ExercisePhase,
    private val upLabel: String,
    private val downLabel: String,
) : RepStateMachine {
    private var sawUp = false
    private var sawDown = false
    private var lastPhase = ExercisePhase.UNKNOWN
    final override var repCount: Int = 0
        private set
    final override var stateLabel: String = "Ready"
        private set

    override fun process(phase: ExercisePhase): Int {
        if (phase == lastPhase) return repCount
        when (phase) {
            downPhase -> {
                if (sawUp) sawDown = true
                stateLabel = downLabel
            }
            upPhase -> {
                if (sawUp && sawDown) repCount++
                sawUp = true
                sawDown = false
                stateLabel = upLabel
            }
            ExercisePhase.IDLE -> stateLabel = "Idle"
            ExercisePhase.UNKNOWN -> stateLabel = "Finding movement"
            else -> Unit
        }
        lastPhase = phase
        return repCount
    }

    override fun reset() {
        sawUp = false
        sawDown = false
        lastPhase = ExercisePhase.UNKNOWN
        repCount = 0
        stateLabel = "Ready"
    }
}

public class SquatRepStateMachine : TwoPhaseRepStateMachine(
    upPhase = ExercisePhase.SQUAT_UP,
    downPhase = ExercisePhase.SQUAT_DOWN,
    upLabel = "Standing",
    downLabel = "Going down",
)

public class PushUpRepStateMachine : TwoPhaseRepStateMachine(
    upPhase = ExercisePhase.PUSHUP_UP,
    downPhase = ExercisePhase.PUSHUP_DOWN,
    upLabel = "Top",
    downLabel = "Going down",
)

public class JumpingJackRepStateMachine : TwoPhaseRepStateMachine(
    upPhase = ExercisePhase.JUMPING_JACK_CLOSED,
    downPhase = ExercisePhase.JUMPING_JACK_OPEN,
    upLabel = "Closed",
    downLabel = "Open",
)
