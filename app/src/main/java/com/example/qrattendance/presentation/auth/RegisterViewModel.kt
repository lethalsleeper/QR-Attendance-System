package com.example.qrattendance.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qrattendance.domain.model.AppResult
import com.example.qrattendance.domain.model.RegistrationRequest
import com.example.qrattendance.domain.model.UserRole
import com.example.qrattendance.domain.repository.AuthRepository
import com.example.qrattendance.domain.usecase.InputValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val role: UserRole = UserRole.STUDENT,
    val rollNumber: String = "",
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val rollNumberError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface RegisterEvent {
    data class Registered(val role: UserRole) : RegisterEvent
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _events = Channel<RegisterEvent>(Channel.BUFFERED)
    val events: Flow<RegisterEvent> = _events.receiveAsFlow()

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, nameError = null, errorMessage = null) }
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, emailError = null, errorMessage = null) }
    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value, passwordError = null, errorMessage = null) }

    fun onConfirmPasswordChange(value: String) =
        _uiState.update { it.copy(confirmPassword = value, confirmPasswordError = null, errorMessage = null) }

    fun onRollNumberChange(value: String) =
        _uiState.update { it.copy(rollNumber = value, rollNumberError = null, errorMessage = null) }

    fun onRoleChange(role: UserRole) = _uiState.update { it.copy(role = role, rollNumberError = null, errorMessage = null) }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }

    fun register() {
        val state = _uiState.value
        if (state.isLoading) return

        val validated = state.copy(
            nameError = InputValidator.validateName(state.name),
            emailError = InputValidator.validateEmail(state.email),
            passwordError = InputValidator.validateNewPassword(state.password),
            confirmPasswordError = InputValidator.validateConfirmPassword(state.password, state.confirmPassword),
            rollNumberError = InputValidator.validateRollNumber(state.rollNumber, state.role),
        )
        val hasErrors = listOf(
            validated.nameError,
            validated.emailError,
            validated.passwordError,
            validated.confirmPasswordError,
            validated.rollNumberError,
        ).any { it != null }
        if (hasErrors) {
            _uiState.value = validated
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val request = RegistrationRequest(
                name = state.name.trim(),
                email = state.email.trim(),
                password = state.password,
                role = state.role,
                rollNumber = state.rollNumber.trim().ifEmpty { null },
            )
            when (val result = authRepository.register(request)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, password = "", confirmPassword = "") }
                    _events.send(RegisterEvent.Registered(result.data.role))
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.message)
                }
            }
        }
    }
}
