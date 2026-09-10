package io.motionguard.core

class PoseReplayRunner(private val engine: MotionEngine) {
    fun replay(frames: List<PoseFrame>): List<MotionResult> = frames.map(engine::addFrame)
}
