package io.motionguard.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

object NoOpDeviceMotionProvider : DeviceMotionProvider {
    override fun observe(): Flow<DeviceMotionSample> = emptyFlow()
}
