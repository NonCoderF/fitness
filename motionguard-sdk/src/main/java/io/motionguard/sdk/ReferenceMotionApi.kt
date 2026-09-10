package motionguardsdk

import android.content.Context
import android.net.Uri
import io.motionguard.core.MotionMatchConfig
import io.motionguard.core.MotionMatchResult
import io.motionguard.core.MotionSequence
import io.motionguard.core.MotionSequenceMatcher
import io.motionguard.core.PoseFrame
import io.motionguard.video.ReferenceVideoProcessor
import io.motionguard.video.VideoProcessingConfig
import io.motionguard.video.VideoProcessingState

public sealed interface ReferenceProcessingState {
    public data object Idle : ReferenceProcessingState
    public data class Processing(val progress: Float) : ReferenceProcessingState
    public data class Completed(val sequence: MotionSequence) : ReferenceProcessingState
    public data class Failed(val message: String, val cause: Throwable? = null) : ReferenceProcessingState
}

/** Creates MotionSequence data from a gallery video entirely on-device. */
public class ReferenceProcessor internal constructor(context: Context) {
    private val delegate = ReferenceVideoProcessor(context.applicationContext)
    public suspend fun process(uri: Uri, config: VideoProcessingConfig = VideoProcessingConfig(), onState: (ReferenceProcessingState) -> Unit = {}): MotionSequence =
        delegate.process(uri, config) { state ->
            onState(
                when (state) {
                    VideoProcessingState.Idle -> ReferenceProcessingState.Idle
                    is VideoProcessingState.Processing -> ReferenceProcessingState.Processing(state.progress)
                    is VideoProcessingState.Completed -> ReferenceProcessingState.Completed(state.sequence)
                    is VideoProcessingState.Failed -> ReferenceProcessingState.Failed(state.message, state.cause)
                },
            )
        }
}

/** Matches live internal pose frames against a processed reference sequence. */
public class ReferenceMatcher internal constructor(sequence: MotionSequence, config: MotionMatchConfig) {
    private val delegate = MotionSequenceMatcher(sequence, config)
    public fun match(frame: PoseFrame): MotionMatchResult = delegate.match(frame)
    public fun reset(): Unit = delegate.reset()
}
