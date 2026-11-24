package com.example.qrattendance.presentation.navigation

import com.example.qrattendance.domain.model.UserRole
import kotlinx.serialization.Serializable

/** Type-safe Navigation Compose destinations. */
sealed interface Route {
    @Serializable
    data object Splash : Route

    @Serializable
    data object Login : Route

    @Serializable
    data object Register : Route

    // Teacher-only
    @Serializable
    data object TeacherDashboard : Route

    @Serializable
    data object StartSession : Route

    @Serializable
    data class ActiveSession(val sessionId: String) : Route

    // Student-only
    @Serializable
    data object StudentDashboard : Route

    @Serializable
    data object QRScanner : Route

    @Serializable
    data object AttendanceHistory : Route
}

fun UserRole.homeRoute(): Route = when (this) {
    UserRole.TEACHER -> Route.TeacherDashboard
    UserRole.STUDENT -> Route.StudentDashboard
}
