package com.example.qrattendance.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qrattendance.domain.model.AppResult
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

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface LoginEvent {
    data class LoggedIn(val role: UserRole) : LoginEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    fun onEmailChange(value: String) =
        _uiState.update { it.copy(email = value, emailError = null, errorMessage = null) }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value, passwordError = null, errorMessage = null) }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }

    fun login() {
        val state = _uiState.value
        if (state.isLoading) return

        val emailError = InputValidator.validateEmail(state.email)
        val passwordError = InputValidator.validateLoginPassword(state.password)
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = authRepository.login(state.email.trim(), state.password)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, password = "") }
                    _events.send(LoginEvent.LoggedIn(result.data.role))
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.message)
                }
            }
        }
    }
}
