package com.example.qrattendance.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.qrattendance.domain.model.UserRole
import com.example.qrattendance.presentation.components.AppCard
import com.example.qrattendance.presentation.components.AppTextField
import com.example.qrattendance.presentation.components.ErrorBanner
import com.example.qrattendance.presentation.components.ObserveAsEvents
import com.example.qrattendance.presentation.components.PrimaryButton
import com.example.qrattendance.presentation.theme.Spacing

@Composable
fun LoginScreen(
    onLoginSuccess: (UserRole) -> Unit,
    onRegisterClick: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is LoginEvent.LoggedIn -> onLoginSuccess(event.role)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AuthHeader(
                title = "Welcome back",
                subtitle = "Sign in to mark or manage attendance",
            )
            Column(
                modifier = Modifier
                    .widthIn(max = Spacing.maxContentWidth)
                    .fillMaxWidth()
                    .offset(y = (-32).dp)
                    .padding(horizontal = Spacing.xl),
            ) {
                AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.xl)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        AppTextField(
                            value = state.email,
                            onValueChange = viewModel::onEmailChange,
                            label = "Email",
                            leadingIcon = Icons.Rounded.Email,
                            errorMessage = state.emailError,
                            keyboardType = KeyboardType.Email,
                            enabled = !state.isLoading,
                            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                        )
                        AppTextField(
                            value = state.password,
                            onValueChange = viewModel::onPasswordChange,
                            label = "Password",
                            leadingIcon = Icons.Rounded.Lock,
                            errorMessage = state.passwordError,
                            isPassword = true,
                            enabled = !state.isLoading,
                            imeAction = ImeAction.Done,
                            onImeAction = {
                                focusManager.clearFocus()
                                viewModel.login()
                            },
                        )
                        if (state.errorMessage != null) {
                            ErrorBanner(message = state.errorMessage!!, onDismiss = viewModel::dismissError)
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        PrimaryButton(
                            text = "Login",
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.login()
                            },
                            isLoading = state.isLoading,
                            leadingIcon = Icons.AutoMirrored.Rounded.Login,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Don't have an account?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onRegisterClick, enabled = !state.isLoading) {
                        Text("Register")
                    }
                }
            }
        }
    }
}
