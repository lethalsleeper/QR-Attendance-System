package com.example.qrattendance.presentation.teacher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qrattendance.domain.model.AppResult
import com.example.qrattendance.domain.model.SessionExpiryPolicy
import com.example.qrattendance.domain.usecase.InputValidator
import com.example.qrattendance.domain.usecase.StartSessionUseCase
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

data class StartSessionUiState(
    val subject: String = "",
    val subjectError: String? = null,
    val durationMinutes: Int = SessionExpiryPolicy.DEFAULT_DURATION_MINUTES,
    val durationOptions: List<Int> = SessionExpiryPolicy.ALLOWED_DURATIONS_MINUTES,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface StartSessionEvent {
    data class SessionStarted(val sessionId: String) : StartSessionEvent
}

@HiltViewModel
class StartSessionViewModel @Inject constructor(
    private val startSession: StartSessionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StartSessionUiState())
    val uiState: StateFlow<StartSessionUiState> = _uiState.asStateFlow()

    private val _events = Channel<StartSessionEvent>(Channel.BUFFERED)
    val events: Flow<StartSessionEvent> = _events.receiveAsFlow()

    fun onSubjectChange(value: String) =
        _uiState.update { it.copy(subject = value.take(InputValidator.MAX_SUBJECT_LENGTH + 10), subjectError = null, errorMessage = null) }

    fun onDurationSelected(minutes: Int) {
        if (minutes in SessionExpiryPolicy.ALLOWED_DURATIONS_MINUTES) {
            _uiState.update { it.copy(durationMinutes = minutes, errorMessage = null) }
        }
    }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }

    fun start() {
        val state = _uiState.value
        if (state.isLoading) return

        val subjectError = InputValidator.validateSubject(state.subject)
        if (subjectError != null) {
            _uiState.update { it.copy(subjectError = subjectError) }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = startSession(state.subject, state.durationMinutes)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(StartSessionEvent.SessionStarted(result.data.sessionId))
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.message)
                }
            }
        }
    }
}
