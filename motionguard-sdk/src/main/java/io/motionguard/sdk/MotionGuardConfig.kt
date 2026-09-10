package motionguardsdk

import io.motionguard.core.MotionConfig
import io.motionguard.core.MotionConfigProfile
import io.motionguard.core.toMotionConfig

/** Public configuration for MotionGuard SDK instances. */
public data class MotionGuardConfig @JvmOverloads constructor(
    public val profile: MotionProfile = MotionProfile.DEFAULT,
    public val calibrationEnabled: Boolean = true,
    public val sensorFusionEnabled: Boolean = false,
    public val debugMetricsEnabled: Boolean = false,
    public val advancedConfig: MotionConfig? = null,
) {
    internal fun toCoreConfig(): MotionConfig {
        val base = advancedConfig ?: when (profile) {
            MotionProfile.DEFAULT -> MotionConfigProfile.DEFAULT.toMotionConfig()
            MotionProfile.CONSERVATIVE -> MotionConfigProfile.CONSERVATIVE.toMotionConfig()
            MotionProfile.RESPONSIVE -> MotionConfigProfile.RESPONSIVE.toMotionConfig()
        }
        return base.copy(calibration = base.calibration.copy(enabled = calibrationEnabled))
    }

    public companion object {
        /** Balanced defaults for general treadmill use. */
        @JvmStatic
        public fun default(): MotionGuardConfig = MotionGuardConfig()
    }
}
