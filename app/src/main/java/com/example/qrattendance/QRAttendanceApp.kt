package com.example.qrattendance

import android.app.Application
import com.example.qrattendance.utils.FirebaseInitializer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class QRAttendanceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseInitializer.initialize(this)
    }
}
