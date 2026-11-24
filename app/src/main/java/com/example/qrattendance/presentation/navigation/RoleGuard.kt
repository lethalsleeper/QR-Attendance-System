package com.example.qrattendance.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.qrattendance.domain.model.UserRole
import com.example.qrattendance.presentation.components.FullScreenError
import com.example.qrattendance.presentation.components.FullScreenLoading

/**
 * Client-side role gate for role-specific destinations. Data access is enforced
 * independently by Firestore security rules and Cloud Functions; this guard makes sure
 * a user is never shown a screen intended for the other role.
 */
@Composable
fun RoleGuard(
    requiredRole: UserRole,
    sessionState: SessionState,
    onEnsureProfile: () -> Unit,
    onSignedOut: () -> Unit,
    onGoHome: (UserRole) -> Unit,
    onLogout: () -> Unit,
    content: @Composable () -> Unit,
) {
    when (sessionState) {
        SessionState.Loading -> {
            LaunchedEffect(Unit) { onEnsureProfile() }
            FullScreenLoading(message = "Loading your profile…")
        }

        SessionState.SignedOut -> {
            LaunchedEffect(Unit) { onSignedOut() }
            FullScreenLoading()
        }

        is SessionState.Error -> FullScreenError(
            title = "Couldn't load your profile",
            message = sessionState.error.message,
            icon = Icons.Rounded.CloudOff,
            primaryActionLabel = "Try again",
            onPrimaryAction = onEnsureProfile,
            secondaryActionLabel = "Log out",
            onSecondaryAction = onLogout,
        )

        is SessionState.SignedIn -> {
            val role = sessionState.user.role
            if (role == requiredRole) {
                content()
            } else {
                FullScreenError(
                    title = "Access denied",
                    message = "This screen is only available to ${requiredRole.displayName.lowercase()}s. " +
                        "You are signed in as a ${role.displayName.lowercase()}.",
                    icon = Icons.Rounded.Block,
                    primaryActionLabel = "Go to my dashboard",
                    onPrimaryAction = { onGoHome(role) },
                )
            }
        }
    }
}
