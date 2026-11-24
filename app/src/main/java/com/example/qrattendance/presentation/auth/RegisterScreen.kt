package com.example.qrattendance.presentation.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.ui.text.input.KeyboardCapitalization
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
fun RegisterScreen(
    onRegistered: (UserRole) -> Unit,
    onBackToLogin: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val moveNext = { focusManager.moveFocus(FocusDirection.Down); Unit }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is RegisterEvent.Registered -> onRegistered(event.role)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
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
                title = "Create account",
                subtitle = "Join your class in a few seconds",
            )
            Column(
                modifier = Modifier
                    .widthIn(max = Spacing.maxContentWidth)
                    .fillMaxWidth()
                    .offset(y = (-32).dp)
                    .padding(horizontal = Spacing.xl),
            ) {
                AppCard(contentPadding = PaddingValues(Spacing.xl)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            text = "I am a",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        RoleSelector(
                            selected = state.role,
                            onSelect = viewModel::onRoleChange,
                            enabled = !state.isLoading,
                        )
                        AnimatedVisibility(visible = state.role == UserRole.TEACHER) {
                            InfoNote(
                                text = "Teacher accounts must be pre-approved by your administrator. " +
                                    "Registration will be rejected if your email is not on the approved list.",
                                modifier = Modifier.padding(top = Spacing.xs),
                            )
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        AppTextField(
                            value = state.name,
                            onValueChange = viewModel::onNameChange,
                            label = "Full Name",
                            leadingIcon = Icons.Rounded.Person,
                            errorMessage = state.nameError,
                            capitalization = KeyboardCapitalization.Words,
                            enabled = !state.isLoading,
                            onImeAction = moveNext,
                        )
                        AppTextField(
                            value = state.email,
                            onValueChange = viewModel::onEmailChange,
                            label = "Email",
                            leadingIcon = Icons.Rounded.Email,
                            errorMessage = state.emailError,
                            keyboardType = KeyboardType.Email,
                            enabled = !state.isLoading,
                            onImeAction = moveNext,
                        )
                        AppTextField(
                            value = state.rollNumber,
                            onValueChange = viewModel::onRollNumberChange,
                            label = if (state.role == UserRole.STUDENT) "Roll Number" else "Roll Number (optional)",
                            leadingIcon = Icons.Rounded.Badge,
                            errorMessage = state.rollNumberError,
                            capitalization = KeyboardCapitalization.Characters,
                            enabled = !state.isLoading,
                            onImeAction = moveNext,
                        )
                        AppTextField(
                            value = state.password,
                            onValueChange = viewModel::onPasswordChange,
                            label = "Password",
                            leadingIcon = Icons.Rounded.Lock,
                            errorMessage = state.passwordError,
                            supportingText = "At least 8 characters with letters and numbers",
                            isPassword = true,
                            enabled = !state.isLoading,
                            onImeAction = moveNext,
                        )
                        AppTextField(
                            value = state.confirmPassword,
                            onValueChange = viewModel::onConfirmPasswordChange,
                            label = "Confirm Password",
                            leadingIcon = Icons.Rounded.Lock,
                            errorMessage = state.confirmPasswordError,
                            isPassword = true,
                            enabled = !state.isLoading,
                            imeAction = ImeAction.Done,
                            onImeAction = {
                                focusManager.clearFocus()
                                viewModel.register()
                            },
                        )
                        if (state.errorMessage != null) {
                            ErrorBanner(message = state.errorMessage!!, onDismiss = viewModel::dismissError)
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        PrimaryButton(
                            text = "Register",
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.register()
                            },
                            isLoading = state.isLoading,
                            leadingIcon = Icons.Rounded.HowToReg,
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
                        text = "Already have an account?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onBackToLogin, enabled = !state.isLoading) {
                        Text("Login")
                    }
                }
            }
        }
    }
}
