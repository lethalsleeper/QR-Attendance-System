package com.example.qrattendance.presentation.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qrattendance.domain.model.AttendanceFailureReason
import com.example.qrattendance.domain.model.MarkAttendanceResult
import com.example.qrattendance.domain.usecase.MarkAttendanceUseCase
import com.example.qrattendance.utils.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ScannerUiState {
    /** Camera is live and looking for a QR code. */
    data object Scanning : ScannerUiState

    /** A QR code was detected; the backend is validating it. Scanning is paused. */
    data object Validating : ScannerUiState

    data class Success(
        val subject: String,
        val teacherName: String?,
        val markedAtMillis: Long,
    ) : ScannerUiState

    data class Failure(val reason: AttendanceFailureReason) : ScannerUiState
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val markAttendance: MarkAttendanceUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Scanning)
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var lastRawValue: String? = null
    private var lastCompletedAtMillis: Long = 0L

    /**
     * Called for every detected QR code. Only the first detection while in [ScannerUiState.Scanning]
     * is processed; everything else (frames arriving during validation, the same code still
     * in view right after "Scan again") is ignored.
     */
    fun onQrCodeDetected(rawValue: String) {
        if (_uiState.value != ScannerUiState.Scanning) return
        val now = timeProvider.now()
        if (rawValue == lastRawValue && now - lastCompletedAtMillis < SAME_CODE_COOLDOWN_MS) return

        lastRawValue = rawValue
        _uiState.value = ScannerUiState.Validating
        viewModelScope.launch {
            val result = markAttendance(rawValue)
            lastCompletedAtMillis = timeProvider.now()
            _uiState.value = when (result) {
                is MarkAttendanceResult.Success -> ScannerUiState.Success(
                    subject = result.subject,
                    teacherName = result.teacherName,
                    markedAtMillis = result.markedAtMillis,
                )

                is MarkAttendanceResult.Failure -> ScannerUiState.Failure(result.reason)
            }
        }
    }

    /** Resume scanning after a failure. */
    fun scanAgain() {
        if (_uiState.value is ScannerUiState.Failure) {
            lastCompletedAtMillis = timeProvider.now()
            _uiState.value = ScannerUiState.Scanning
        }
    }

    companion object {
        /** The same QR content is ignored for this long after a result, to avoid instant re-scans. */
        const val SAME_CODE_COOLDOWN_MS = 2_500L
    }
}
