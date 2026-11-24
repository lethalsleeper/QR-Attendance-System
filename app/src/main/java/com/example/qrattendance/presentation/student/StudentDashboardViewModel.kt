package com.example.qrattendance.presentation.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qrattendance.domain.model.AppResult
import com.example.qrattendance.domain.model.AttendanceStats
import com.example.qrattendance.domain.model.StudentAttendance
import com.example.qrattendance.domain.model.User
import com.example.qrattendance.domain.model.toAppError
import com.example.qrattendance.domain.repository.AttendanceRepository
import com.example.qrattendance.domain.repository.AuthRepository
import com.example.qrattendance.utils.DateTimeUtils
import com.example.qrattendance.utils.LoadState
import com.example.qrattendance.utils.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatsState(
    val isLoading: Boolean = true,
    val stats: AttendanceStats? = null,
    val errorMessage: String? = null,
)

data class StudentDashboardUiState(
    val user: User? = null,
    val greeting: String = "",
    val isHistoryLoading: Boolean = true,
    val historyError: String? = null,
    val recent: List<StudentAttendance> = emptyList(),
    val totalAttended: Int = 0,
    val stats: StatsState = StatsState(),
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class StudentDashboardViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val attendanceRepository: AttendanceRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val historyRetry = MutableStateFlow(0)
    private val statsRefresh = MutableStateFlow(0)
    private val statsState = MutableStateFlow(StatsState())

    /** Real-time history listener (Firestore → Flow), shared by the UI state and the stats trigger. */
    private val history: SharedFlow<LoadState<List<StudentAttendance>>> = combine(
        authRepository.currentUser.filterNotNull().map { it.uid }.distinctUntilChanged(),
        historyRetry,
    ) { uid, _ -> uid }
        .flatMapLatest { uid ->
            attendanceRepository.observeAttendanceHistory(uid)
                .map<List<StudentAttendance>, LoadState<List<StudentAttendance>>> { LoadState.Loaded(it) }
                .onStart { emit(LoadState.Loading) }
                .catch { emit(LoadState.Error(it.toAppError().message)) }
        }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val uiState: StateFlow<StudentDashboardUiState> = combine(
        authRepository.currentUser,
        history,
        statsState,
    ) { user, load, stats ->
        val records = (load as? LoadState.Loaded)?.data.orEmpty()
        StudentDashboardUiState(
            user = user,
            greeting = DateTimeUtils.greeting(timeProvider.now()),
            isHistoryLoading = load is LoadState.Loading,
            historyError = (load as? LoadState.Error)?.message,
            recent = records.take(RECENT_LIMIT),
            totalAttended = records.size,
            stats = stats,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudentDashboardUiState())

    init {
        // Statistics are recomputed by the backend whenever the history changes
        // (e.g. right after a successful scan) or when the user taps retry.
        viewModelScope.launch {
            combine(
                history.map { (it as? LoadState.Loaded)?.data?.size }.filterNotNull().distinctUntilChanged(),
                statsRefresh,
            ) { count, refresh -> count to refresh }
                .debounce(STATS_DEBOUNCE_MS)
                .collectLatest { loadStats() }
        }
    }

    fun retryHistory() = historyRetry.update { it + 1 }

    fun refreshStats() = statsRefresh.update { it + 1 }

    private suspend fun loadStats() {
        statsState.update { it.copy(isLoading = true, errorMessage = null) }
        when (val result = attendanceRepository.getAttendanceStats()) {
            is AppResult.Success -> statsState.value = StatsState(isLoading = false, stats = result.data)
            is AppResult.Failure -> statsState.update {
                it.copy(isLoading = false, errorMessage = result.error.message)
            }
        }
    }

    private companion object {
        const val RECENT_LIMIT = 5
        const val STATS_DEBOUNCE_MS = 400L
    }
}
