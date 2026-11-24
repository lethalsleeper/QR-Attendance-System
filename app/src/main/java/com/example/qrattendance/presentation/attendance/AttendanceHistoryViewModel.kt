package com.example.qrattendance.presentation.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qrattendance.domain.model.StudentAttendance
import com.example.qrattendance.domain.model.toAppError
import com.example.qrattendance.domain.repository.AttendanceRepository
import com.example.qrattendance.domain.repository.AuthRepository
import com.example.qrattendance.utils.DateTimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class HistoryGroup(
    val title: String,
    val records: List<StudentAttendance>,
)

data class AttendanceHistoryUiState(
    val isLoading: Boolean = true,
    val groups: List<HistoryGroup> = emptyList(),
    val totalCount: Int = 0,
    val errorMessage: String? = null,
    val query: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AttendanceHistoryViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val attendanceRepository: AttendanceRepository,
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)
    private val query = MutableStateFlow("")

    fun onQueryChange(value: String) {
        query.value = value
    }

    val uiState: StateFlow<AttendanceHistoryUiState> = combine(
        authRepository.currentUser.filterNotNull().map { it.uid }.distinctUntilChanged(),
        retryTrigger,
    ) { uid, _ -> uid }
        .flatMapLatest { uid ->
            attendanceRepository.observeAttendanceHistory(uid)
                .combine(query) { records, q ->
                    val filtered = if (q.isBlank()) records
                    else records.filter { it.subject.contains(q.trim(), ignoreCase = true) }
                    AttendanceHistoryUiState(
                        isLoading = false,
                        groups = groupByMonth(filtered),
                        totalCount = records.size,
                        query = q,
                    )
                }
                .onStart { emit(AttendanceHistoryUiState(isLoading = true)) }
                .catch { emit(AttendanceHistoryUiState(isLoading = false, errorMessage = it.toAppError().message)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AttendanceHistoryUiState())

    fun retry() = retryTrigger.update { it + 1 }

    private fun groupByMonth(records: List<StudentAttendance>): List<HistoryGroup> =
        records
            .groupBy { DateTimeUtils.formatMonth(it.markedAtMillis) }
            .map { (month, items) -> HistoryGroup(month, items) }
}
