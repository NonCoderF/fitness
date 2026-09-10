package motionguardsdk

import android.content.Context
import io.motionguard.core.ActivityType
import io.motionguard.core.CameraView
import io.motionguard.core.ConfidenceAdaptivePoseSmoother
import io.motionguard.core.DeviceMotionProvider
import io.motionguard.core.MotionEngine
import io.motionguard.core.MotionState
import io.motionguard.core.NoOpDeviceMotionProvider
import io.motionguard.core.PoseFrame
import io.motionguard.core.TrackingStatus
import io.motionguard.sensors.AndroidDeviceMotionProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Main developer-facing entry point for on-device treadmill movement analysis. */
public class MotionGuard private constructor(
    private val config: MotionGuardConfig,
    private val deviceMotionProvider: DeviceMotionProvider,
    private val appContext: Context?,
) {
    private val engine = MotionEngine(config.toCoreConfig())
    private val poseSmoother = ConfidenceAdaptivePoseSmoother(minimumWindow = 3, maximumWindow = 4, minimumAlpha = .45f, maximumAlpha = .78f)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _results = MutableStateFlow(unknownResult())
    private var listener: MotionGuardListener? = null
    private var sensorJob: Job? = null
    private var released = false
    private var started = false

    /** Latest and future analysis results. Collect from any Android lifecycle-aware coroutine scope. */
    public val results: StateFlow<MotionGuardResult> = _results.asStateFlow()

    /** Start optional background resources such as sensor fusion. */
    @Synchronized
    public fun start() {
        checkNotReleased()
        if (started) return
        started = true
        sensorJob = scope.launch {
            deviceMotionProvider.observe().collect { engine.updateDeviceMotion(it) }
        }
    }

    /** Stop optional background resources without clearing motion history. */
    @Synchronized
    public fun stop() {
        sensorJob?.cancel()
        sensorJob = null
        started = false
    }

    /** Clear calibration, cadence smoothing, state-machine history, and pose buffers. */
    @Synchronized
    public fun reset() {
        checkNotReleased()
        engine.reset()
        poseSmoother.reset()
        publish(unknownResult())
    }

    /** Process an internal pose frame from any camera or pose pipeline. */
    @Synchronized
    public fun process(poseFrame: PoseFrame): MotionGuardResult {
        checkNotReleased()
        val smoothedPose = poseSmoother.filter(poseFrame)
        val result = MotionGuardResult.fromCore(engine.addFrame(smoothedPose), config.debugMetricsEnabled).copy(poseFrame = smoothedPose)
        publish(result)
        return result
    }

    /** Creates an SDK-owned CameraX controller for the common live-camera integration. */
    public fun createCameraController(): MotionGuardCameraController {
        checkNotReleased()
        return MotionGuardCameraController(this, appContext ?: throw MotionGuardException("A Context is required for camera integration."))
    }

    /** Creates an on-device processor for a selected video reference. */
    public fun createReferenceProcessor(): ReferenceProcessor {
        checkNotReleased()
        return ReferenceProcessor(appContext ?: throw MotionGuardException("A Context is required for reference processing."))
    }

    /** Creates a matcher for a previously processed in-memory sequence. */
    public fun createReferenceMatcher(sequence: io.motionguard.core.MotionSequence, config: io.motionguard.core.MotionMatchConfig = io.motionguard.core.MotionMatchConfig()): ReferenceMatcher {
        checkNotReleased()
        return ReferenceMatcher(sequence, config)
    }

    /** Creates the TFLite-ready exercise detection layer without changing the existing camera pipeline. */
    public fun createExerciseEngine(config: io.motionguard.exercise.PosePipelineConfig = io.motionguard.exercise.PosePipelineConfig()): io.motionguard.exercise.ExerciseEngine {
        checkNotReleased()
        return io.motionguard.exercise.ExerciseEngine(config)
    }

    /** Register a Java-friendly listener that receives the same values emitted by [results]. */
    @Synchronized
    public fun setListener(listener: MotionGuardListener?) {
        this.listener = listener
    }

    /** Stop resources and reject future processing calls. */
    @Synchronized
    public fun release() {
        if (released) return
        stop()
        released = true
    }

    private fun publish(result: MotionGuardResult) {
        _results.value = result
        listener?.onResult(result)
    }

    private fun checkNotReleased() {
        if (released) throw MotionGuardException("MotionGuard has been released.")
    }

    public class Builder @JvmOverloads constructor(context: Context? = null) {
        private val appContext = context?.applicationContext
        private var config: MotionGuardConfig = MotionGuardConfig.default()
        private var provider: DeviceMotionProvider? = null

        /** Use an explicit SDK configuration. */
        public fun config(config: MotionGuardConfig): Builder = apply { this.config = config }

        /** Select a preset profile without replacing the whole config. */
        public fun profile(profile: MotionProfile): Builder = apply { config = config.copy(profile = profile) }

        /** Enable or disable the optional standing calibration phase. */
        public fun enableCalibration(enabled: Boolean): Builder = apply { config = config.copy(calibrationEnabled = enabled) }

        /** Enable or disable optional accelerometer/gyroscope down-weighting for phone movement. */
        public fun enableSensorFusion(enabled: Boolean): Builder = apply { config = config.copy(sensorFusionEnabled = enabled) }

        /** Include internal tuning metrics in emitted results. */
        public fun enableDebugMetrics(enabled: Boolean): Builder = apply { config = config.copy(debugMetricsEnabled = enabled) }

        /** Override sensor input, primarily for tests or custom integrations. */
        public fun deviceMotionProvider(provider: DeviceMotionProvider): Builder = apply { this.provider = provider }

        /** Build a MotionGuard instance. Call [MotionGuard.release] when finished. */
        public fun build(): MotionGuard {
            val motionProvider = provider ?: if (config.sensorFusionEnabled) {
                val context = appContext ?: throw MotionGuardException("A Context is required when sensor fusion is enabled.")
                AndroidDeviceMotionProvider(context)
            } else {
                NoOpDeviceMotionProvider
            }
            return MotionGuard(config, motionProvider, appContext)
        }
    }

    public companion object {
        /** Java-friendly factory for builder creation. */
        @JvmStatic
        @JvmOverloads
        public fun builder(context: Context? = null): Builder = Builder(context)

        /** Kotlin DSL for configuring the public SDK facade. */
        @JvmStatic
        public fun create(context: Context? = null, configure: MotionGuardDsl.() -> Unit = {}): MotionGuard =
            Builder(context).apply { MotionGuardDsl(this).apply(configure) }.build()
    }
}

/** Configuration DSL for the sample app and Kotlin consumers. */
public class MotionGuardDsl internal constructor(private val builder: MotionGuard.Builder) {
    public var profile: MotionProfile = MotionProfile.DEFAULT
        set(value) { field = value; builder.profile(value) }
    public var calibrationEnabled: Boolean = true
        set(value) { field = value; builder.enableCalibration(value) }
    public var sensorFusionEnabled: Boolean = false
        set(value) { field = value; builder.enableSensorFusion(value) }
    public var debugMetricsEnabled: Boolean = false
        set(value) { field = value; builder.enableDebugMetrics(value) }
}

private fun unknownResult(): MotionGuardResult = MotionGuardResult(
    motionState = MotionState.UNCERTAIN,
    activity = ActivityType.UNKNOWN,
    confidence = 0f,
    cadenceSpm = null,
    gaitSpeed = 0f,
    movementIntensity = 0f,
    poseQuality = 0f,
    trackingQuality = TrackingStatus.TRACKING_LOST,
    cameraView = CameraView.UNKNOWN,
    calibration = null,
    debug = null,
    stepEvents = emptyList(),
)
