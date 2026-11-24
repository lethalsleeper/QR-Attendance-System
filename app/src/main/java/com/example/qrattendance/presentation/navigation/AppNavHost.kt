package com.example.qrattendance.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.qrattendance.domain.model.UserRole
import com.example.qrattendance.presentation.attendance.AttendanceHistoryScreen
import com.example.qrattendance.presentation.auth.LoginScreen
import com.example.qrattendance.presentation.auth.RegisterScreen
import com.example.qrattendance.presentation.auth.SplashScreen
import com.example.qrattendance.presentation.components.OfflineBanner
import com.example.qrattendance.presentation.scanner.ScannerScreen
import com.example.qrattendance.presentation.student.StudentDashboardScreen
import com.example.qrattendance.presentation.teacher.ActiveSessionScreen
import com.example.qrattendance.presentation.teacher.StartSessionScreen
import com.example.qrattendance.presentation.teacher.TeacherDashboardScreen

private const val TRANSITION_MS = 260

/** Clears the whole back stack and opens [route] (used for login / logout / role home). */
private fun NavHostController.navigateClearingBackStack(route: Route) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavHostController.navigateToLogin() {
    if (currentDestination?.hasRoute(Route.Login::class) == true) return
    navigateClearingBackStack(Route.Login)
}

private fun NavHostController.navigateToHome(role: UserRole) = navigateClearingBackStack(role.homeRoute())

@Composable
fun QRAttendanceNavHost(
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val sessionState by appViewModel.sessionState.collectAsStateWithLifecycle()
    val isOnline by appViewModel.isOnline.collectAsStateWithLifecycle()

    val logout: () -> Unit = {
        appViewModel.logout()
        navController.navigateToLogin()
    }

    /** Wraps a role-specific destination in the [RoleGuard]. */
    @Composable
    fun Guarded(role: UserRole, content: @Composable () -> Unit) {
        RoleGuard(
            requiredRole = role,
            sessionState = sessionState,
            onEnsureProfile = appViewModel::ensureProfileLoaded,
            onSignedOut = { navController.navigateToLogin() },
            onGoHome = { navController.navigateToHome(it) },
            onLogout = logout,
            content = content,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        NavHost(
            navController = navController,
            startDestination = Route.Splash,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                fadeIn(tween(TRANSITION_MS)) + slideInHorizontally(tween(TRANSITION_MS)) { it / 10 }
            },
            exitTransition = { fadeOut(tween(TRANSITION_MS)) },
            popEnterTransition = { fadeIn(tween(TRANSITION_MS)) },
            popExitTransition = {
                fadeOut(tween(TRANSITION_MS)) + slideOutHorizontally(tween(TRANSITION_MS)) { it / 10 }
            },
        ) {
            composable<Route.Splash> {
                SplashScreen(onNavigate = { route -> navController.navigateClearingBackStack(route) })
            }

            composable<Route.Login> {
                LoginScreen(
                    onLoginSuccess = { role -> navController.navigateToHome(role) },
                    onRegisterClick = {
                        navController.navigate(Route.Register) { launchSingleTop = true }
                    },
                )
            }

            composable<Route.Register> {
                RegisterScreen(
                    onRegistered = { role -> navController.navigateToHome(role) },
                    onBackToLogin = {
                        if (!navController.popBackStack()) navController.navigateToLogin()
                    },
                )
            }

            // ----------------------------- Teacher -----------------------------
            composable<Route.TeacherDashboard> {
                Guarded(UserRole.TEACHER) {
                    TeacherDashboardScreen(
                        onStartSession = {
                            navController.navigate(Route.StartSession) { launchSingleTop = true }
                        },
                        onOpenSession = { sessionId ->
                            navController.navigate(Route.ActiveSession(sessionId)) { launchSingleTop = true }
                        },
                        onLogout = logout,
                    )
                }
            }

            composable<Route.StartSession> {
                Guarded(UserRole.TEACHER) {
                    StartSessionScreen(
                        onBack = { navController.popBackStack() },
                        onSessionStarted = { sessionId ->
                            navController.navigate(Route.ActiveSession(sessionId)) {
                                popUpTo(Route.StartSession) { inclusive = true }
                            }
                        },
                    )
                }
            }

            composable<Route.ActiveSession> {
                Guarded(UserRole.TEACHER) {
                    ActiveSessionScreen(
                        onBackToDashboard = {
                            if (!navController.popBackStack(Route.TeacherDashboard, inclusive = false)) {
                                navController.navigateToHome(UserRole.TEACHER)
                            }
                        },
                    )
                }
            }

            // ----------------------------- Student -----------------------------
            composable<Route.StudentDashboard> {
                Guarded(UserRole.STUDENT) {
                    StudentDashboardScreen(
                        onScanQr = { navController.navigate(Route.QRScanner) { launchSingleTop = true } },
                        onViewHistory = {
                            navController.navigate(Route.AttendanceHistory) { launchSingleTop = true }
                        },
                        onLogout = logout,
                    )
                }
            }

            composable<Route.QRScanner> {
                Guarded(UserRole.STUDENT) {
                    ScannerScreen(
                        onClose = {
                            if (!navController.popBackStack()) navController.navigateToHome(UserRole.STUDENT)
                        },
                    )
                }
            }

            composable<Route.AttendanceHistory> {
                Guarded(UserRole.STUDENT) {
                    AttendanceHistoryScreen(
                        onBack = {
                            if (!navController.popBackStack()) navController.navigateToHome(UserRole.STUDENT)
                        },
                    )
                }
            }
        }

        OfflineBanner(
            visible = !isOnline,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
