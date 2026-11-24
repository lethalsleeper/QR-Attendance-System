package com.example.qrattendance.presentation.teacher

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qrattendance.di.DefaultDispatcher
import com.example.qrattendance.domain.model.AppResult
import com.example.qrattendance.domain.model.AttendanceRecord
import com.example.qrattendance.domain.model.QrPayload
import com.example.qrattendance.domain.model.Session
import com.example.qrattendance.domain.model.SessionExpiryPolicy
import com.example.qrattendance.domain.model.SessionStatus
import com.example.qrattendance.domain.model.toAppError
import com.example.qrattendance.domain.repository.SessionRepository
import com.example.qrattendance.domain.usecase.QrPayloadCodec
import com.example.qrattendance.utils.LoadState
import com.example.qrattendance.utils.QrCodeGenerator
import com.example.qrattendance.utils.TimeProvider
import com.example.qrattendance.utils.tickerFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ActiveSessionUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val subject: String = "",
    val isActive: Boolean = false,
    val createdAtMillis: Long = 0L,
    val expiresAtMillis: Long = 0L,
    val durationMinutes: Int = 0,
    val countdownText: String = "00:00",
    val progress: Float = 0f,
    val qrCode: ImageBitmap? = null,
    val qrError: String? = null,
    val attendees: List<AttendanceRecord> = emptyList(),
    val attendanceCount: Int = 0,
    val isAttendanceLoading: Boolean = true,
    val attendanceError: String? = null,
    val isEnding: Boolean = false,
)

private sealed interface QrRender {
    data object None : QrRender
    data class Ready(val bitmap: ImageBitmap) : QrRender
    data object Failed : QrRender
}

/**
 * Real-time pipeline:
 * Firestore snapshot listeners → Flow (repository) → combine with a 1s ticker →
 * StateFlow<ActiveSessionUiState> → Compose (collectAsStateWithLifecycle).
 * Listeners are removed automatically 5s after the UI stops collecting.
 */
@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val qrCodeGenerator: QrCodeGenerator,
    private val timeProvider: TimeProvider,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    /** Navigation argument of Route.ActiveSession. */
    val sessionId: String = checkNotNull(savedStateHandle.get<String>("sessionId")) {
        "ActiveSession opened without a sessionId"
    }

    private val sessionState: SharedFlow<LoadState<Session>> = sessionRepository.observeSession(sessionId)
        .map<Session, LoadState<Session>> { LoadState.Loaded(it) }
        .catch { emit(LoadState.Error(it.toAppError().message)) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), replay = 1)

    private val attendanceState: Flow<LoadState<List<AttendanceRecord>>> =
        sessionRepository.observeAttendance(sessionId)
            .map<List<AttendanceRecord>, LoadState<List<AttendanceRecord>>> { LoadState.Loaded(it) }
            .onStart { emit(LoadState.Loading) }
            .catch { emit(LoadState.Error(it.toAppError().message)) }

    /** QR bitmap is regenerated only when the token changes, off the main thread. */
    private val qrState: Flow<QrRender> = sessionState
        .map { (it as? LoadState.Loaded)?.data?.token }
        .distinctUntilChanged()
        .map { token -> renderQr(token) }
        .onStart { emit(QrRender.None) }

    private val isEnding = MutableStateFlow(false)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    val uiState: StateFlow<ActiveSessionUiState> = combine(
        sessionState,
        attendanceState,
        qrState,
        tickerFlow(TICK_MS, timeProvider),
        isEnding,
    ) { session, attendance, qr, now, ending ->
        buildState(session, attendance, qr, now, ending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), ActiveSessionUiState())

    init {
        markExpiredWhenTimeRunsOut()
    }

    /** Teacher ends the session early. */
    fun endSession() {
        if (isEnding.value) return
        isEnding.value = true
        viewModelScope.launch {
            val result = sessionRepository.endSession(sessionId)
            isEnding.value = false
            if (result is AppResult.Failure) _messages.send(result.error.message)
        }
    }

    /**
     * When the countdown reaches zero, flip the stored status to "expired" so the
     * dashboard reflects it immediately. This is cosmetic: the Cloud Function already
     * rejects scans after expiresAt (server time), and a scheduled function cleans up
     * sessions whose teacher closed the app.
     */
    private fun markExpiredWhenTimeRunsOut() {
        viewModelScope.launch {
            val (state, now) = combine(sessionState, tickerFlow(TICK_MS, timeProvider)) { s, n -> s to n }
                .first { (s, n) -> s !is LoadState.Loaded || !s.data.isActive(n) }
            val session = (state as? LoadState.Loaded)?.data ?: return@launch
            if (session.status == SessionStatus.ACTIVE && now >= session.expiresAtMillis) {
                sessionRepository.endSession(sessionId)
            }
        }
    }

    private suspend fun renderQr(token: String?): QrRender {
        if (token == null) return QrRender.None
        return try {
            val content = QrPayloadCodec.encode(QrPayload(sessionId = sessionId, token = token))
            QrRender.Ready(withContext(defaultDispatcher) { qrCodeGenerator.generate(content, QR_SIZE_PX) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            QrRender.Failed
        }
    }

    private fun buildState(
        session: LoadState<Session>,
        attendance: LoadState<List<AttendanceRecord>>,
        qr: QrRender,
        now: Long,
        ending: Boolean,
    ): ActiveSessionUiState {
        val data = when (session) {
            is LoadState.Error -> return ActiveSessionUiState(isLoading = false, errorMessage = session.message)
            LoadState.Loading -> return ActiveSessionUiState()
            is LoadState.Loaded -> session.data
        }
        val isActive = data.isActive(now)
        val remaining = data.remainingMillis(now)
        val attendees = (attendance as? LoadState.Loaded)?.data.orEmpty()
        return ActiveSessionUiState(
            isLoading = false,
            subject = data.subject,
            isActive = isActive,
            createdAtMillis = data.createdAtMillis,
            expiresAtMillis = data.expiresAtMillis,
            durationMinutes = data.durationMinutes,
            countdownText = SessionExpiryPolicy.formatCountdown(remaining),
            progress = SessionExpiryPolicy.progress(remaining, data.totalDurationMillis),
            // The QR code is hidden as soon as the session is no longer active.
            qrCode = if (isActive) (qr as? QrRender.Ready)?.bitmap else null,
            qrError = if (isActive && qr is QrRender.Failed) "The QR code could not be generated." else null,
            attendees = attendees,
            attendanceCount = if (attendance is LoadState.Loaded) attendees.size else data.attendanceCount,
            isAttendanceLoading = attendance is LoadState.Loading,
            attendanceError = (attendance as? LoadState.Error)?.message,
            isEnding = ending,
        )
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val QR_SIZE_PX = 720
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
