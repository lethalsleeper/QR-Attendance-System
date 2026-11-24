package com.example.qrattendance.utils

import android.content.Context
import com.google.firebase.FirebaseApp

/**
 * Verifies that Firebase could be initialised. FirebaseApp is normally created by
 * FirebaseInitProvider from google-services.json; when that file was missing at build
 * time (or is invalid) we show a configuration screen instead of crashing.
 */
object FirebaseInitializer {

    @Volatile
    var isInitialized: Boolean = false
        private set

    @Volatile
    var errorMessage: String? = null
        private set

    fun initialize(context: Context) {
        try {
            val app = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
            isInitialized = app != null
            errorMessage = if (app == null) {
                "Firebase configuration was not found in this build. Add app/google-services.json " +
                    "from your Firebase project and rebuild the app."
            } else {
                null
            }
        } catch (e: Exception) {
            isInitialized = false
            errorMessage = "Firebase failed to initialise: ${e.message ?: e.javaClass.simpleName}"
        }
    }
}
