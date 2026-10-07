package ai.formflow.fitness

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.sparkstudios.myapplication.ui.MotionGuardApp
import com.sparkstudios.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private var workoutScreenVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MyApplicationTheme {
                MotionGuardApp(onWorkoutScreenChanged = { workoutScreenVisible = it })
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (workoutScreenVisible && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(9, 16))
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(true)
                }
                .build()
            enterPictureInPictureMode(params)
        }
    }
}
