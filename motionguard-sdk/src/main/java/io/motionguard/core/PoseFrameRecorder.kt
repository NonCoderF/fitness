package io.motionguard.core

class PoseFrameRecorder {
    private val frames = mutableListOf<PoseFrame>()

    fun record(frame: PoseFrame) {
        frames += frame
    }

    fun snapshot(): List<PoseFrame> = frames.toList()

    fun clear() {
        frames.clear()
    }
}
