package com.example.qrattendance.di

import com.example.qrattendance.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Firebase SDK singletons. Providers run lazily (first injection), which happens only
 * after MainActivity has confirmed that Firebase initialised successfully.
 * In debug builds with -PuseFirebaseEmulator=true every SDK points at the local
 * Firebase Emulator Suite (ports match firebase.json).
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    private const val AUTH_EMULATOR_PORT = 9099
    private const val FIRESTORE_EMULATOR_PORT = 8080
    private const val FUNCTIONS_EMULATOR_PORT = 5001

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance().also { auth ->
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            auth.useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, AUTH_EMULATOR_PORT)
        }
    }

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance().also { firestore ->
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            firestore.useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, FIRESTORE_EMULATOR_PORT)
        }
    }

    @Provides
    @Singleton
    fun provideFunctions(): FirebaseFunctions =
        FirebaseFunctions.getInstance(BuildConfig.FUNCTIONS_REGION).also { functions ->
            if (BuildConfig.USE_FIREBASE_EMULATOR) {
                functions.useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, FUNCTIONS_EMULATOR_PORT)
            }
        }
}
