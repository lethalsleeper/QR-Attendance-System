package com.example.qrattendance

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import com.example.qrattendance.presentation.components.FullScreenError
import com.example.qrattendance.presentation.navigation.QRAttendanceNavHost
import com.example.qrattendance.presentation.theme.QRAttendanceTheme
import com.example.qrattendance.utils.FirebaseInitializer
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Every screen has the indigo brand gradient (or the camera) behind the status
        // bar, so status bar icons are always light.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))

        setContent {
            QRAttendanceTheme {
                if (FirebaseInitializer.isInitialized) {
                    QRAttendanceNavHost()
                } else {
                    FullScreenError(
                        title = "Firebase is not configured",
                        message = FirebaseInitializer.errorMessage
                            ?: "Firebase could not be initialised. Check app/google-services.json and rebuild.",
                        icon = Icons.Rounded.CloudOff,
                        primaryActionLabel = "Close app",
                        onPrimaryAction = { finish() },
                    )
                }
            }
        }
    }
}
