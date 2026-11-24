package com.example.qrattendance.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qrattendance.domain.model.AppError
import com.example.qrattendance.domain.model.AppResult
import com.example.qrattendance.domain.repository.AuthRepository
import com.example.qrattendance.presentation.navigation.Route
import com.example.qrattendance.presentation.navigation.homeRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SplashUiState {
    data object Loading : SplashUiState
    data class NavigateTo(val route: Route) : SplashUiState
    data class Error(val error: AppError) : SplashUiState
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SplashUiState>(SplashUiState.Loading)
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        resolveDestination()
    }

    fun retry() = resolveDestination()

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = SplashUiState.NavigateTo(Route.Login)
        }
    }

    /** Signed out → Login. Signed in → fetch profile → role-specific home. */
    private fun resolveDestination() {
        _uiState.value = SplashUiState.Loading
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            val next: SplashUiState = if (!authRepository.isSignedIn()) {
                SplashUiState.NavigateTo(Route.Login)
            } else {
                when (val result = authRepository.loadCurrentUser(forceRefresh = true)) {
                    is AppResult.Success -> SplashUiState.NavigateTo(result.data.role.homeRoute())
                    is AppResult.Failure -> SplashUiState.Error(result.error)
                }
            }
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < MIN_SPLASH_MS) delay(MIN_SPLASH_MS - elapsed)
            android.util.Log.d("SplashDebug", "Splash decided: $next")
            _uiState.value = next
        }
    }

    private companion object {
        const val MIN_SPLASH_MS = 900L
    }
}
