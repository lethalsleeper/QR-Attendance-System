package com.example.qrattendance.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qrattendance.domain.model.AppError
import com.example.qrattendance.domain.model.AppResult
import com.example.qrattendance.domain.model.User
import com.example.qrattendance.domain.repository.AuthRepository
import com.example.qrattendance.utils.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** App-wide authentication state used by the role guards. */
sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: User) : SessionState
    data class Error(val error: AppError) : SessionState
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val profileError = MutableStateFlow<AppError?>(null)
    private var profileJob: Job? = null

    val sessionState: StateFlow<SessionState> = combine(
        authRepository.observeAuthState(),
        authRepository.currentUser,
        profileError,
    ) { signedIn, user, error ->
        when {
            !signedIn -> SessionState.SignedOut
            user != null -> SessionState.SignedIn(user)
            error != null -> SessionState.Error(error)
            else -> SessionState.Loading
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = authRepository.currentUser.value?.let { SessionState.SignedIn(it) }
            ?: if (authRepository.isSignedIn()) SessionState.Loading else SessionState.SignedOut,
    )

    /** Loads the profile when a guarded screen is shown after process death. */
    fun ensureProfileLoaded() {
        if (profileJob?.isActive == true) return
        profileJob = viewModelScope.launch {
            profileError.value = null
            val result = authRepository.loadCurrentUser()
            if (result is AppResult.Failure) profileError.value = result.error
        }
    }

    fun logout() {
        viewModelScope.launch {
            profileError.value = null
            authRepository.logout()
        }
    }
}
