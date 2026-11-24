package com.example.qrattendance.presentation.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.qrattendance.presentation.components.AppLogo
import com.example.qrattendance.presentation.components.PrimaryButton
import com.example.qrattendance.presentation.components.SecondaryButton
import com.example.qrattendance.presentation.components.headerBrush
import com.example.qrattendance.presentation.navigation.Route
import com.example.qrattendance.presentation.theme.Spacing

@Composable
fun SplashScreen(
    onNavigate: (Route) -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        (state as? SplashUiState.NavigateTo)?.let { onNavigate(it.route) }
    }

    SplashContent(
        state = state,
        onRetry = viewModel::retry,
        onLogout = viewModel::logout,
    )
}

@Composable
private fun SplashContent(
    state: SplashUiState,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
) {
    val logoScale = remember { Animatable(0.7f) }
    val contentAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        contentAlpha.animateTo(1f, tween(500))
    }
    LaunchedEffect(Unit) {
        logoScale.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(headerBrush())
            .systemBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(contentAlpha.value)
                .padding(horizontal = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppLogo(modifier = Modifier.scale(logoScale.value), size = 96.dp)
            Spacer(Modifier.height(Spacing.xxl))
            Text(
                text = "QR Attendance System",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "Scan. Verify. Present.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(Spacing.xxl),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                is SplashUiState.Error -> SplashErrorCard(
                    message = state.error.message,
                    onRetry = onRetry,
                    onLogout = onLogout,
                )

                else -> CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

@Composable
private fun SplashErrorCard(message: String, onRetry: () -> Unit, onLogout: () -> Unit) {
    Surface(
        modifier = Modifier.widthIn(max = Spacing.maxContentWidth),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = "We couldn't sign you in",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(text = "Try again", onClick = onRetry)
            SecondaryButton(text = "Log out", onClick = onLogout)
        }
    }
}
